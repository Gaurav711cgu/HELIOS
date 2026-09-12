package com.helios.orchestrator.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.orchestrator.core.EventBus;
import com.helios.orchestrator.core.WorkflowEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class KafkaEventBus implements EventBus, AutoCloseable {
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final CopyOnWriteArrayList<WorkflowEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<RuntimeException> publishFailure = new AtomicReference<>();

    public KafkaEventBus(String bootstrapServers, ObjectMapper mapper) {
        this.mapper = mapper;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "30000");
        this.producer = new KafkaProducer<>(props);
    }

    @Override
    public void publish(WorkflowEvent event) {
        events.add(event);
        RuntimeException previousFailure = publishFailure.get();
        if (previousFailure != null) {
            throw previousFailure;
        }
        producer.send(new ProducerRecord<>(event.topic(), event.workflowId(), encode(event)), (metadata, exception) -> {
            if (exception != null) {
                publishFailure.compareAndSet(null, new IllegalStateException("failed to publish Kafka event", exception));
            }
        });
    }

    @Override
    public List<WorkflowEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public void flush() {
        producer.flush();
        RuntimeException failure = publishFailure.get();
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        flush();
        producer.close();
    }

    private String encode(WorkflowEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize Kafka event", exception);
        }
    }
}
