package com.ute.uteexpress.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TrackingNumberGenerator {
    public String next() {
        return "UTE" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
