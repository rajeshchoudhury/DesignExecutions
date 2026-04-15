package com.microservices.inventory.service;

import com.microservices.inventory.command.ReleaseInventoryCommand;
import com.microservices.inventory.command.ReserveInventoryCommand;
import com.microservices.inventory.command.RestockCommand;
import com.microservices.inventory.domain.InventoryReservation;
import com.microservices.inventory.domain.Product;
import com.microservices.inventory.domain.ReservationStatus;
import com.microservices.inventory.domain.StockMovement;
import com.microservices.inventory.dto.CreateProductRequest;
import com.microservices.inventory.dto.ProductResponse;
import com.microservices.inventory.dto.StockMovementResponse;
import com.microservices.inventory.event.InventoryReleasedEvent;
import com.microservices.inventory.event.InventoryReservationFailedEvent;
import com.microservices.inventory.event.InventoryReservedEvent;
import com.microservices.inventory.event.LowStockEvent;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.repository.ProductRepository;
import com.microservices.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);

    private final ProductRepository productRepository;
    private final InventoryReservationRepository reservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryEventPublisher eventPublisher;

    @Transactional
    public InventoryReservation reserveInventory(ReserveInventoryCommand command) {
        log.info("Reserving inventory: orderId={}, productId={}, sku={}, qty={}",
                command.getOrderId(), command.getProductId(), command.getSku(), command.getQuantity());

        Product product = resolveProduct(command.getProductId(), command.getSku());

        long modifiedCount = productRepository.reserveStock(
                product.getId(), command.getQuantity(), Instant.now());

        if (modifiedCount == 0) {
            log.warn("Insufficient stock for product={}, requested={}, available={}",
                    product.getId(), command.getQuantity(), product.getAvailableQuantity());

            eventPublisher.publishInventoryReservationFailed(
                    InventoryReservationFailedEvent.builder()
                            .orderId(command.getOrderId())
                            .productId(product.getId())
                            .reason("Insufficient stock. Requested: " + command.getQuantity()
                                    + ", Available: " + product.getAvailableQuantity())
                            .failedAt(Instant.now())
                            .build());

            throw new IllegalStateException("Insufficient stock for product: " + product.getSku());
        }

        Instant now = Instant.now();
        String reservationId = UUID.randomUUID().toString();

        InventoryReservation reservation = InventoryReservation.builder()
                .reservationId(reservationId)
                .orderId(command.getOrderId())
                .productId(product.getId())
                .sku(product.getSku())
                .quantity(command.getQuantity())
                .status(ReservationStatus.PENDING)
                .reservedAt(now)
                .expiresAt(now.plus(RESERVATION_TTL))
                .build();
        reservationRepository.save(reservation);

        recordMovement(product.getId(), StockMovement.MovementType.RESERVATION,
                command.getQuantity(), product.getAvailableQuantity(),
                product.getAvailableQuantity() - command.getQuantity(),
                command.getOrderId(), "Reservation for order " + command.getOrderId());

        eventPublisher.publishInventoryReserved(
                InventoryReservedEvent.builder()
                        .reservationId(reservationId)
                        .orderId(command.getOrderId())
                        .productId(product.getId())
                        .quantity(command.getQuantity())
                        .reservedAt(now)
                        .build());

        log.info("Inventory reserved: reservationId={}, orderId={}", reservationId, command.getOrderId());
        return reservation;
    }

    @Transactional
    public void releaseInventory(ReleaseInventoryCommand command) {
        log.info("Releasing inventory: reservationId={}, orderId={}, reason={}",
                command.getReservationId(), command.getOrderId(), command.getReason());

        InventoryReservation reservation = findReservation(command.getReservationId(), command.getOrderId());

        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            log.info("Reservation {} already released/expired, skipping", reservation.getReservationId());
            return;
        }

        Product product = productRepository.findById(reservation.getProductId())
                .orElseThrow(() -> new IllegalStateException("Product not found: " + reservation.getProductId()));

        long modifiedCount = productRepository.releaseStock(
                reservation.getProductId(), reservation.getQuantity(), Instant.now());

        if (modifiedCount == 0) {
            log.error("Failed to release stock atomically for product={}", reservation.getProductId());
            throw new IllegalStateException("Failed to release stock for product: " + reservation.getProductId());
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        reservationRepository.save(reservation);

        recordMovement(reservation.getProductId(), StockMovement.MovementType.RELEASE,
                reservation.getQuantity(), product.getAvailableQuantity(),
                product.getAvailableQuantity() + reservation.getQuantity(),
                reservation.getOrderId(), command.getReason());

        eventPublisher.publishInventoryReleased(
                InventoryReleasedEvent.builder()
                        .reservationId(reservation.getReservationId())
                        .orderId(reservation.getOrderId())
                        .releasedAt(Instant.now())
                        .build());

        log.info("Inventory released: reservationId={}", reservation.getReservationId());
    }

    @Transactional
    public Product restockInventory(RestockCommand command) {
        log.info("Restocking product={}, qty={}", command.getProductId(), command.getQuantity());

        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalStateException("Product not found: " + command.getProductId()));

        long modifiedCount = productRepository.restockProduct(
                command.getProductId(), command.getQuantity(), Instant.now());

        if (modifiedCount == 0) {
            throw new IllegalStateException("Failed to restock product: " + command.getProductId());
        }

        recordMovement(product.getId(), StockMovement.MovementType.RESTOCK,
                command.getQuantity(), product.getAvailableQuantity(),
                product.getAvailableQuantity() + command.getQuantity(),
                product.getId(), command.getReason());

        log.info("Product restocked: productId={}, addedQty={}", command.getProductId(), command.getQuantity());
        return productRepository.findById(command.getProductId()).orElse(product);
    }

    public ProductResponse getProductAvailability(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product not found: " + productId));
        return toProductResponse(product);
    }

    public List<StockMovementResponse> getStockMovements(String productId) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toMovementResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts()
                .stream()
                .map(this::toProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        log.info("Creating product: sku={}, warehouse={}", request.getSku(), request.getWarehouse());

        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .availableQuantity(request.getInitialQuantity())
                .reservedQuantity(0)
                .reorderThreshold(request.getReorderThreshold())
                .unitPrice(request.getUnitPrice())
                .warehouse(request.getWarehouse())
                .build();

        return productRepository.save(product);
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void checkLowStock() {
        log.debug("Running low-stock check");
        List<Product> lowStockProducts = productRepository.findLowStockProducts();

        for (Product product : lowStockProducts) {
            eventPublisher.publishLowStock(
                    LowStockEvent.builder()
                            .productId(product.getId())
                            .sku(product.getSku())
                            .currentQuantity(product.getAvailableQuantity())
                            .reorderThreshold(product.getReorderThreshold())
                            .build());
        }

        if (!lowStockProducts.isEmpty()) {
            log.warn("Low-stock alert: {} products below reorder threshold", lowStockProducts.size());
        }
    }

    @Scheduled(fixedRate = 60_000) // every minute
    @Transactional
    public void expireReservations() {
        log.debug("Running reservation expiration check");
        List<InventoryReservation> expired = reservationRepository
                .findByExpiresAtBeforeAndStatus(Instant.now(), ReservationStatus.PENDING);

        for (InventoryReservation reservation : expired) {
            try {
                releaseInventory(ReleaseInventoryCommand.builder()
                        .reservationId(reservation.getReservationId())
                        .orderId(reservation.getOrderId())
                        .reason("Reservation expired after " + RESERVATION_TTL.toMinutes() + " minutes")
                        .build());

                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);
            } catch (Exception e) {
                log.error("Failed to expire reservation {}: {}", reservation.getReservationId(), e.getMessage());
            }
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} reservations", expired.size());
        }
    }

    private Product resolveProduct(String productId, String sku) {
        if (productId != null && !productId.isBlank()) {
            return productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + productId));
        }
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalStateException("Product not found for SKU: " + sku));
    }

    private InventoryReservation findReservation(String reservationId, String orderId) {
        if (reservationId != null && !reservationId.isBlank()) {
            return reservationRepository.findByReservationId(reservationId)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found: " + reservationId));
        }
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new IllegalStateException("No reservations found for order: " + orderId);
        }
        return reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.CONFIRMED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active reservation for order: " + orderId));
    }

    private void recordMovement(String productId, StockMovement.MovementType type,
                                int quantity, int previousQty, int newQty,
                                String referenceId, String reason) {
        StockMovement movement = StockMovement.builder()
                .productId(productId)
                .movementType(type)
                .quantity(quantity)
                .previousQuantity(previousQty)
                .newQuantity(newQty)
                .referenceId(referenceId)
                .reason(reason)
                .build();
        stockMovementRepository.save(movement);
    }

    private ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .availableQuantity(product.getAvailableQuantity())
                .reservedQuantity(product.getReservedQuantity())
                .unitPrice(product.getUnitPrice())
                .warehouse(product.getWarehouse())
                .build();
    }

    private StockMovementResponse toMovementResponse(StockMovement movement) {
        return StockMovementResponse.builder()
                .movementType(movement.getMovementType())
                .quantity(movement.getQuantity())
                .previousQuantity(movement.getPreviousQuantity())
                .newQuantity(movement.getNewQuantity())
                .referenceId(movement.getReferenceId())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
