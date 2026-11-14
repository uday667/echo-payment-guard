package com.echo.paymentguard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "echo.stream.kafka.enabled", havingValue = "true")
public class KafkaStreamConfiguration {
    @Bean
    NewTopic incomingPaymentsTopic() {
        return TopicBuilder.name("payments.incoming").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic incomingPaymentsDeadLetterTopic() {
        return TopicBuilder.name("payments.incoming.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler paymentEventErrorHandler(
            KafkaTemplate<Object, Object> template,
            @Value("${echo.stream.listener.retry-delay:1s}") java.time.Duration retryDelay) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryDelay.toMillis(), 2));
    }
}
