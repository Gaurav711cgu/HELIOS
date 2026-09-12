package com.helios.orchestrator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.orchestrator.core.DagExecutor;
import com.helios.orchestrator.core.DagValidator;
import com.helios.orchestrator.core.DurableWorkflowStateStore;
import com.helios.orchestrator.core.EventBus;
import com.helios.orchestrator.core.FileEventBus;
import com.helios.orchestrator.core.NaturalLanguageWorkflowSynthesizer;
import com.helios.orchestrator.core.QuotaManager;
import com.helios.orchestrator.core.TenantQuotaManager;
import com.helios.orchestrator.core.TaskRouter;
import com.helios.orchestrator.core.WorkflowStateStore;
import com.helios.orchestrator.core.WorkflowSynthesizer;
import com.helios.orchestrator.infrastructure.AnthropicWorkflowSynthesizer;
import com.helios.orchestrator.infrastructure.DynamoDbWorkflowStateStore;
import com.helios.orchestrator.infrastructure.KafkaEventBus;
import com.helios.orchestrator.infrastructure.RedisQuotaManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class HeliosConfig {
    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "file", matchIfMissing = true)
    WorkflowStateStore workflowStateStore(@Value("${helios.state.snapshot-path:./data/helios-state.bin}") String snapshotPath) {
        return new DurableWorkflowStateStore(Path.of(snapshotPath));
    }

    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "real")
    WorkflowStateStore dynamoDbWorkflowStateStore(
            DynamoDbClient dynamoDbClient,
            ObjectMapper objectMapper,
            @Value("${helios.dynamodb.table-name:helios-workflow-state}") String tableName
    ) {
        return new DynamoDbWorkflowStateStore(dynamoDbClient, objectMapper, tableName);
    }

    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "real")
    DynamoDbClient dynamoDbClient(
            @Value("${helios.dynamodb.endpoint:http://localhost:8000}") String endpoint,
            @Value("${helios.aws.region:us-east-1}") String region
    ) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .build();
    }

    @Bean
    DagValidator dagValidator() {
        return new DagValidator();
    }

    @Bean
    TaskRouter taskRouter() {
        return new TaskRouter();
    }

    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "file", matchIfMissing = true)
    EventBus eventBus(@Value("${helios.events.log-path:./data/helios-events.log}") String eventLogPath) {
        return new FileEventBus(Path.of(eventLogPath));
    }

    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "real")
    EventBus kafkaEventBus(
            @Value("${helios.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            ObjectMapper objectMapper
    ) {
        return new KafkaEventBus(bootstrapServers, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "file", matchIfMissing = true)
    QuotaManager tenantQuotaManager(
            @Value("${helios.quota.capacity:10000}") int capacity,
            @Value("${helios.quota.refill-per-second:10000}") double refillPerSecond
    ) {
        return new TenantQuotaManager(capacity, refillPerSecond);
    }

    @Bean
    @ConditionalOnProperty(name = "helios.infrastructure.mode", havingValue = "real")
    QuotaManager redisQuotaManager(
            @Value("${helios.redis.host:localhost}") String host,
            @Value("${helios.redis.port:6379}") int port,
            @Value("${helios.quota.capacity:10000}") int capacity,
            @Value("${helios.quota.refill-per-second:10000}") double refillPerSecond
    ) {
        return new RedisQuotaManager(new JedisPooled(host, port), capacity, refillPerSecond);
    }

    @Bean
    DagExecutor dagExecutor(
            WorkflowStateStore stateStore,
            DagValidator validator,
            TaskRouter taskRouter,
            EventBus eventBus,
            QuotaManager quotaManager
    ) {
        return new DagExecutor(stateStore, validator, taskRouter, eventBus, quotaManager);
    }

    @Bean
    @ConditionalOnProperty(name = "helios.synthesis.mode", havingValue = "parser", matchIfMissing = true)
    NaturalLanguageWorkflowSynthesizer synthesizer() {
        return new NaturalLanguageWorkflowSynthesizer();
    }

    @Bean
    @ConditionalOnProperty(name = "helios.synthesis.mode", havingValue = "anthropic")
    WorkflowSynthesizer anthropicSynthesizer(
            ObjectMapper objectMapper,
            DagValidator validator,
            @Value("${helios.synthesis.anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
            @Value("${helios.synthesis.anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${helios.synthesis.anthropic.messages-uri:https://api.anthropic.com/v1/messages}") String messagesUri
    ) {
        return new AnthropicWorkflowSynthesizer(objectMapper, validator, apiKey, model, URI.create(messagesUri));
    }

    @Bean
    CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
