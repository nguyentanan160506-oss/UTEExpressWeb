package com.ute.uteexpress.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** UTC values with the 100 ns precision of SQL Server DATETIME2(7). */
final class UtcTimestamps {

    private UtcTimestamps() {
    }

    static LocalDateTime now() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return now.withNano(now.getNano() / 100 * 100);
    }
}
