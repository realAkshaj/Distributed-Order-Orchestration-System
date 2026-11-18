package com.orderorchestration.common;

import com.orderorchestration.common.messaging.CommandType;
import com.orderorchestration.common.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

@Configuration
@EnableScheduling
@EntityScan("com.orderorchestration")
@EnableJpaRepositories("com.orderorchestration")
public class CommonConfig {

    private static final int PARTITIONS = 3;

    private static Map<String, Object> producerProps(String servers, Class<?> valueSerializer) {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.LINGER_MS_CONFIG, 5,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    }

    @Bean
    KafkaTemplate<String, String> outboxKafkaTemplate(@Value("${spring.kafka.bootstrap-servers}") String servers) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps(servers, StringSerializer.class)));
    }

    @Bean
    KafkaTemplate<String, Object> deadLetterKafkaTemplate(@Value("${spring.kafka.bootstrap-servers}") String servers) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps(servers, JsonSerializer.class)));
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2);
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate), backOff);
    }

    @Bean
    KafkaAdmin.NewTopics sagaTopics() {
        return new KafkaAdmin.NewTopics(Stream.concat(
                        Stream.of(Topics.SAGA_EVENTS),
                        Arrays.stream(CommandType.values()).map(CommandType::topic).distinct())
                .flatMap(name -> Stream.of(name, name + ".DLT"))
                .map(name -> TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build())
                .toArray(NewTopic[]::new));
    }
}
