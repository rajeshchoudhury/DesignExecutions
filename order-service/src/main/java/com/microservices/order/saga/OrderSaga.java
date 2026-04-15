package com.microservices.order.saga;

import com.microservices.order.command.api.ApproveOrderCommand;
import com.microservices.order.command.api.CancelOrderCommand;
import com.microservices.order.command.api.RejectOrderCommand;
import com.microservices.order.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.axonframework.modelling.saga.SagaLifecycle.end;

/**
 * Orchestrating saga for the Order lifecycle.
 *
 * <h3>Happy path</h3>
 * <pre>
 * OrderCreated → ReserveInventory → InventoryReserved → ProcessPayment
 *             → PaymentProcessed → ApproveOrder → (saga ends)
 * </pre>
 *
 * <h3>Compensation flows</h3>
 * <ul>
 *   <li>Inventory reservation fails → reject order, end saga</li>
 *   <li>Payment fails → release inventory (compensate), reject order, end saga</li>
 *   <li>Deadline expires → compensate all completed steps, cancel order</li>
 * </ul>
 *
 * Every state transition is logged for operational visibility.
 * The saga tracks its own {@link SagaState} so the deadline handler
 * knows exactly which compensating actions to dispatch.
 */
@Saga
@Slf4j
public class OrderSaga {

    private static final String SAGA_DEADLINE_NAME = "order-saga-timeout";
    private static final Duration SAGA_TIMEOUT = Duration.ofMinutes(30);

    private transient CommandGateway commandGateway;
    private transient DeadlineManager deadlineManager;

    private String orderId;
    private String customerId;
    private List<String> productIds;
    private BigDecimal totalAmount;
    private SagaState sagaState;
    private String deadlineId;

    // ── Saga start ──────────────────────────────────────────────────────

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent event) {
        this.orderId = event.getOrderId();
        this.customerId = event.getCustomerId();
        this.productIds = event.getItems().stream()
                .map(item -> item.getProductId())
                .toList();
        this.totalAmount = event.getTotalAmount();
        this.sagaState = SagaState.STARTED;

        log.info("[OrderSaga] STARTED for orderId={}, customerId={}, amount={}",
                orderId, customerId, totalAmount);

        this.deadlineId = deadlineManager.schedule(SAGA_TIMEOUT, SAGA_DEADLINE_NAME);

        transitionTo(SagaState.INVENTORY_RESERVING);

        // Dispatch command to Inventory Service
        commandGateway.send(new ReserveInventoryCommand(orderId, productIds))
                .exceptionally(ex -> {
                    log.error("[OrderSaga] Failed to send ReserveInventoryCommand for orderId={}", orderId, ex);
                    return null;
                });
    }

    // ── Inventory reserved (happy path) ─────────────────────────────────

    @SagaEventHandler(associationProperty = "orderId")
    public void on(InventoryReservedEvent event) {
        log.info("[OrderSaga] Inventory reserved for orderId={}", orderId);
        transitionTo(SagaState.INVENTORY_RESERVED);

        transitionTo(SagaState.PAYMENT_PROCESSING);

        commandGateway.send(new ProcessPaymentCommand(orderId, customerId, totalAmount))
                .exceptionally(ex -> {
                    log.error("[OrderSaga] Failed to send ProcessPaymentCommand for orderId={}", orderId, ex);
                    return null;
                });
    }

    // ── Payment processed (happy path) ──────────────────────────────────

    @SagaEventHandler(associationProperty = "orderId")
    public void on(PaymentProcessedEvent event) {
        log.info("[OrderSaga] Payment processed for orderId={}", orderId);
        transitionTo(SagaState.PAYMENT_PROCESSED);

        cancelDeadline();

        transitionTo(SagaState.COMPLETING);

        commandGateway.send(ApproveOrderCommand.builder()
                        .orderId(orderId)
                        .build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[OrderSaga] Failed to approve orderId={}", orderId, ex);
                    } else {
                        log.info("[OrderSaga] Order approved, saga COMPLETED for orderId={}", orderId);
                    }
                });

        transitionTo(SagaState.COMPLETED);
        end();
    }

    // ── Compensation: inventory reservation failed ──────────────────────

    @SagaEventHandler(associationProperty = "orderId")
    public void on(InventoryReservationFailedEvent event) {
        log.warn("[OrderSaga] Inventory reservation FAILED for orderId={}, reason={}",
                orderId, event.getReason());
        transitionTo(SagaState.COMPENSATING);

        cancelDeadline();

        commandGateway.send(RejectOrderCommand.builder()
                        .orderId(orderId)
                        .reason("Inventory reservation failed: " + event.getReason())
                        .build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[OrderSaga] Failed to reject orderId={} after inventory failure", orderId, ex);
                    }
                });

        transitionTo(SagaState.FAILED);
        end();
    }

    // ── Compensation: payment failed → release inventory ────────────────

    @SagaEventHandler(associationProperty = "orderId")
    public void on(PaymentFailedEvent event) {
        log.warn("[OrderSaga] Payment FAILED for orderId={}, reason={}", orderId, event.getReason());
        transitionTo(SagaState.COMPENSATING);

        cancelDeadline();

        // Step 1: compensate by releasing previously reserved inventory
        commandGateway.send(new ReleaseInventoryCommand(orderId, productIds))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[OrderSaga] Failed to release inventory for orderId={}", orderId, ex);
                    } else {
                        log.info("[OrderSaga] Inventory released (compensation) for orderId={}", orderId);
                    }
                });

        // Step 2: reject the order
        commandGateway.send(RejectOrderCommand.builder()
                        .orderId(orderId)
                        .reason("Payment failed: " + event.getReason())
                        .build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[OrderSaga] Failed to reject orderId={} after payment failure", orderId, ex);
                    }
                });

        transitionTo(SagaState.FAILED);
        end();
    }

    // ── Deadline handler: saga timed out ────────────────────────────────

    @DeadlineHandler(deadlineName = SAGA_DEADLINE_NAME)
    public void onSagaTimeout() {
        log.error("[OrderSaga] DEADLINE EXPIRED for orderId={}, sagaState={} — initiating compensation",
                orderId, sagaState);

        transitionTo(SagaState.COMPENSATING);

        // Compensate based on how far the saga progressed
        if (sagaState == SagaState.INVENTORY_RESERVED
                || sagaState == SagaState.PAYMENT_PROCESSING
                || sagaState == SagaState.PAYMENT_PROCESSED) {
            log.info("[OrderSaga] Releasing inventory as part of timeout compensation for orderId={}", orderId);
            commandGateway.send(new ReleaseInventoryCommand(orderId, productIds))
                    .exceptionally(ex -> {
                        log.error("[OrderSaga] Compensation: failed to release inventory for orderId={}", orderId, ex);
                        return null;
                    });
        }

        commandGateway.send(CancelOrderCommand.builder()
                        .orderId(orderId)
                        .compensationReason("Saga timeout after " + SAGA_TIMEOUT.toMinutes() + " minutes")
                        .build())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[OrderSaga] Compensation: failed to cancel orderId={}", orderId, ex);
                    } else {
                        log.info("[OrderSaga] Order cancelled due to timeout for orderId={}", orderId);
                    }
                });

        transitionTo(SagaState.COMPENSATED);
        end();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void transitionTo(SagaState newState) {
        log.info("[OrderSaga] orderId={}: {} → {}", orderId, sagaState, newState);
        this.sagaState = newState;
    }

    private void cancelDeadline() {
        if (deadlineId != null) {
            deadlineManager.cancelSchedule(SAGA_DEADLINE_NAME, deadlineId);
            deadlineId = null;
        }
    }

    @org.axonframework.spring.stereotype.Saga
    public void setCommandGateway(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @org.axonframework.spring.stereotype.Saga
    public void setDeadlineManager(DeadlineManager deadlineManager) {
        this.deadlineManager = deadlineManager;
    }

    // ─────────────────────────────────────────────────────────────────────
    // External service commands & events used by this saga.
    // In a full implementation these would live in common-lib or the
    // respective service modules. Defined here as lightweight records
    // so the saga compiles and demonstrates the orchestration pattern.
    // ─────────────────────────────────────────────────────────────────────

    public record ReserveInventoryCommand(
            @org.axonframework.modelling.command.TargetAggregateIdentifier String orderId,
            java.util.List<String> productIds) {}

    public record ProcessPaymentCommand(
            @org.axonframework.modelling.command.TargetAggregateIdentifier String orderId,
            String customerId,
            java.math.BigDecimal amount) {}

    public record ReleaseInventoryCommand(
            @org.axonframework.modelling.command.TargetAggregateIdentifier String orderId,
            java.util.List<String> productIds) {}

    public static class InventoryReservedEvent {
        private String orderId;
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
    }

    public static class InventoryReservationFailedEvent {
        private String orderId;
        private String reason;
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class PaymentProcessedEvent {
        private String orderId;
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
    }

    public static class PaymentFailedEvent {
        private String orderId;
        private String reason;
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
