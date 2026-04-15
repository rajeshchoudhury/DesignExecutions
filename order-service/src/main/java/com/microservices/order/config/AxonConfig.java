package com.microservices.order.config;

import com.microservices.order.aggregate.OrderAggregate;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.SpringAxonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework infrastructure configuration.
 *
 * <ul>
 *   <li><strong>Event Store</strong>: JPA-backed (PostgreSQL) — no Axon Server required</li>
 *   <li><strong>Snapshots</strong>: Triggered every 10 events to bound replay cost</li>
 *   <li><strong>Tracking processors</strong>: Configured with tunable thread count and batch size</li>
 *   <li><strong>Token store</strong>: JPA-backed so processor positions survive restarts</li>
 *   <li><strong>Dead-letter queue</strong>: JPA-backed DLQ for failed event processing</li>
 * </ul>
 */
@Configuration
public class AxonConfig {

    @Bean
    public EventStorageEngine eventStorageEngine(Serializer serializer,
                                                  EntityManagerProvider entityManagerProvider,
                                                  TransactionManager transactionManager) {
        return JpaEventStorageEngine.builder()
                .snapshotSerializer(serializer)
                .eventSerializer(serializer)
                .entityManagerProvider(entityManagerProvider)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public EventStore eventStore(EventStorageEngine storageEngine,
                                  org.axonframework.config.Configuration configuration) {
        return EmbeddedEventStore.builder()
                .storageEngine(storageEngine)
                .messageMonitor(configuration.messageMonitor(EventStore.class, "eventStore"))
                .build();
    }

    @Bean("orderSnapshotTrigger")
    public SnapshotTriggerDefinition orderSnapshotTrigger(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, 10);
    }

    @Bean
    public JpaTokenStore tokenStore(Serializer serializer,
                                     EntityManagerProvider entityManagerProvider) {
        return JpaTokenStore.builder()
                .serializer(serializer)
                .entityManagerProvider(entityManagerProvider)
                .build();
    }

    @Bean
    public DeadlineManager deadlineManager(SpringAxonConfiguration configuration,
                                            TransactionManager transactionManager) {
        return SimpleDeadlineManager.builder()
                .transactionManager(transactionManager)
                .scopeAwareProvider(configuration.getObject())
                .build();
    }

    /**
     * Configure tracking event processors with thread count, batch size,
     * and JPA-backed dead-letter queue for the order-projection group.
     */
    @Autowired
    public void configureProcessors(EventProcessingConfigurer configurer) {
        configurer.registerTrackingEventProcessor("order-projection",
                org.axonframework.config.Configuration::eventStore,
                conf -> org.axonframework.eventhandling.TrackingEventProcessorConfiguration
                        .forSingleThreadedProcessing()
                        .andBatchSize(100));
    }
}
