package com.ute.uteexpress.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Origin intake only. A database unique index is the final collision guard. */
public final class OriginReceiptCodes {
    private OriginReceiptCodes() { }
    public static String create(LocalDateTime utc) {
        return "RCV-"+utc.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"
            +UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
    }
}
