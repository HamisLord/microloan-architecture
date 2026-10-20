package com.microloan.gateway.listener;

import com.microloan.gateway.dto.ScoringResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Собирает десериализатор значения ровно так, как его соберёт Spring Boot из application.yml,
 * и проверяет его на сообщениях, которые шлёт scoring-service.
 */
class ScoringResultDeserializationTest {

    private Deserializer<Object> deserializer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        KafkaProperties kafkaProperties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("spring.kafka", KafkaProperties.class)
                .get();

        Map<String, Object> config = kafkaProperties.buildConsumerProperties();
        Object deserializerClass = config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        Class<?> clazz = deserializerClass instanceof Class<?> c ? c : Class.forName(deserializerClass.toString());

        deserializer = (Deserializer<Object>) BeanUtils.instantiateClass(clazz);
        deserializer.configure(config, false);
    }

    @Test
    @DisplayName("Ответ scoring-service десериализуется в ScoringResult")
    void deserializesScoringServicePayload() {
        String json = """
                {"id":"3f1c2a7e-1111-2222-3333-444455556666","approved":false,"rejectReason":"срок кредита превышает допустимые 60 месяцев"}
                """;

        Object result = deserializer.deserialize("scoring-results", new RecordHeaders(), json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isInstanceOf(ScoringResult.class);
        ScoringResult scoringResult = (ScoringResult) result;
        assertThat(scoringResult.getId()).isEqualTo("3f1c2a7e-1111-2222-3333-444455556666");
        assertThat(scoringResult.isApproved()).isFalse();
        assertThat(scoringResult.getRejectReason()).isEqualTo("срок кредита превышает допустимые 60 месяцев");
    }

    @Test
    @DisplayName("Битое сообщение не бросает исключение, а помечается заголовком для error handler")
    void malformedPayloadDoesNotBlockConsumer() {
        RecordHeaders headers = new RecordHeaders();

        Object result = deserializer.deserialize("scoring-results", headers, "not a json".getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNull();
        assertThat(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNotNull();
    }
}
