package com.bloodbank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the application clock as a bean.
 *
 * <p>Services take a {@link Clock} instead of calling {@code LocalDate.now()},
 * which is what lets the eligibility boundary (89 / 90 / 91 days) and the
 * donation-interval rules be unit-tested deterministically with a fixed clock
 * rather than by waiting for real time to pass.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
