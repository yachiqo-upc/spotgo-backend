package com.yachiqo.spotgo.shared.infrastructure.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TimeConfiguration {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Lima"));
    }
}
