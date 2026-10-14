package com.ute.uteexpress.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OtpConfig {
    @Bean
    Clock otpClock() { return Clock.systemUTC(); }
}
