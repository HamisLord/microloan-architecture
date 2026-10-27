package com.microloan.scoringservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // время через бин, чтобы в тестах можно было подставить управляемые часы
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
