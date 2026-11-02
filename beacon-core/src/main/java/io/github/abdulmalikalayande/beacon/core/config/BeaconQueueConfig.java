package io.github.abdulmalikalayande.beacon.core.config;

import io.github.abdulmalikalayande.beacon.api.port.NotificationQueue;
import io.github.abdulmalikalayande.beacon.core.queue.DatabaseNotificationQueue;
import io.github.abdulmalikalayande.beacon.core.repository.DeliveryTaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeaconQueueConfig {

	
    @Bean
    @ConditionalOnMissingBean(NotificationQueue.class)
    public NotificationQueue databaseNotificationQueue(Clock clock, DeliveryTaskRepository deliveryTaskRepository) {
        return new DatabaseNotificationQueue(clock, deliveryTaskRepository);
    }
}