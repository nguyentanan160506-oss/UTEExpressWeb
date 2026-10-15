package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.ShipmentStatusLabels;
import com.ute.uteexpress.dto.tracking.PublicTrackingView;
import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.repository.ShipmentEventRepository;
import com.ute.uteexpress.repository.ShipmentRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicTrackingService {
    private static final Pattern TRACKING_NUMBER = Pattern.compile("UTE[A-F0-9]{32}", Pattern.CASE_INSENSITIVE);
    public static final int MAX_PUBLIC_EVENTS = 20;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;

    public PublicTrackingService(ShipmentRepository shipments, ShipmentEventRepository events) {
        this.shipments = shipments; this.events = events;
    }

    public Optional<PublicTrackingView> lookup(String input) {
        String number = normalize(input);
        var found = shipments.findPublicTrackingSummary(number);
        if (found.isEmpty() || !PublicTrackingEventPolicy.allowsStatus(found.get().getStatus())
                || found.get().getUpdatedAt() == null) {
            // Missing and non-public summaries share the same result; never disclose the reason.
            return Optional.empty();
        }
        var summary = found.get();
        var timeline = events.findPublicTrackingEvents(number, ShipmentStatus.CREATED,
                PublicTrackingEventPolicy.CREATED_TITLE, PageRequest.of(0, MAX_PUBLIC_EVENTS)).stream()
            .filter(PublicTrackingEventPolicy::allowsEvent)
            .limit(MAX_PUBLIC_EVENTS)
            .map(event -> new PublicTrackingView.Event(PublicTrackingEventPolicy.CREATED_TITLE, event.getOccurredAt()))
            .toList();
        return Optional.of(new PublicTrackingView(number, ShipmentStatusLabels.VALUES.get(summary.getStatus()),
            summary.getUpdatedAt(), timeline));
    }

    public static String normalize(String input) {
        if (input == null || input.length() > 50) { throw new InvalidTrackingNumberException(); }
        // Validate ASCII before case conversion: Unicode ligatures must not expand into valid hex.
        String normalized = input.strip();
        if (!TRACKING_NUMBER.matcher(normalized).matches()) { throw new InvalidTrackingNumberException(); }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static final class InvalidTrackingNumberException extends IllegalArgumentException {
        private InvalidTrackingNumberException() { super("Invalid tracking number"); }
    }
}
