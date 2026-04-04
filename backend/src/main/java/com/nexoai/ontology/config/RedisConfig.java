package com.nexoai.ontology.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis is only activated when CDC is enabled.
 * Without this, the app starts fine without a Redis server.
 */
@Configuration
@ConditionalOnProperty(name = "nexo.cdc.enabled", havingValue = "true", matchIfMissing = false)
@Import(RedisAutoConfiguration.class)
public class RedisConfig {
}
