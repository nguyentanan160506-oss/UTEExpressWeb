package com.ute.uteexpress.service;

public class ShipmentCreationException extends RuntimeException {
    private final String field;
    public ShipmentCreationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
