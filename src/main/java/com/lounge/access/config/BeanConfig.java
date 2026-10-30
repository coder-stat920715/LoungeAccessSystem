package com.lounge.access.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoungeProperties.class)
public class BeanConfig {

    /** Single injectable clock so every date-window rule is deterministic and testable. */
    @Bean
    public Clock clock(@Value("${lounge.timezone:Asia/Kolkata}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
