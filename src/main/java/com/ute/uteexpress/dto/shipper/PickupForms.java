package com.ute.uteexpress.dto.shipper;

import com.ute.uteexpress.enums.PickupResult;
import jakarta.validation.constraints.*;

public final class PickupForms {
    private PickupForms() { }
    public static class Request {
        @NotNull @PositiveOrZero private Long expectedVersion;
        @Size(max = 500) private String notes;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion = value; }
        public String getNotes() { return notes; }
        public void setNotes(String value) { notes = value; }
    }
    public static class Collection extends Request {
        @NotNull private PickupResult result;
        private boolean physicallyCollected;
        @Size(max = 100) private String reference;
        public PickupResult getResult() { return result; }
        public void setResult(PickupResult value) { result = value; }
        public boolean isPhysicallyCollected() { return physicallyCollected; }
        public void setPhysicallyCollected(boolean value) { physicallyCollected = value; }
        public String getReference() { return reference; }
        public void setReference(String value) { reference = value; }
    }
    public static class Review {
        @NotNull @PositiveOrZero private Long expectedVersion;
        private boolean physicallyReceived;
        @Size(max = 500) private String reason;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion = value; }
        public boolean isPhysicallyReceived() { return physicallyReceived; }
        public void setPhysicallyReceived(boolean value) { physicallyReceived = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { reason = value; }
    }
}
