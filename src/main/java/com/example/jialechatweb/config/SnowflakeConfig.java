package com.example.jialechatweb.config;

import com.example.jialechatweb.util.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        // For simplicity in this demo, using fixed workerId=1, datacenterId=1
        // In production, these should be configurable based on environment/instance ID
        return new SnowflakeIdGenerator(1, 1);
    }
}
