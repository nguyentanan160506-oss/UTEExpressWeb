package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.ShipmentStatus;
import java.time.LocalDateTime;

public record ShipmentSummary(Long id, String trackingNumber, String receiverName,
        LocalDateTime createdAt, ShipmentStatus status) { }
