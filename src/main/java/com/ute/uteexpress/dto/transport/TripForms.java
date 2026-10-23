package com.ute.uteexpress.dto.transport;

import com.ute.uteexpress.enums.TripDiscrepancyKind;
import jakarta.validation.constraints.*;
import java.util.List;

public final class TripForms {
    private TripForms() { }
    public static class Create {
        @NotNull @Positive private Long routeId;
        @NotBlank @Pattern(regexp = "[a-f0-9]{32}") private String requestToken;
        public Long getRouteId() { return routeId; }
        public void setRouteId(Long value) { routeId = value; }
        public String getRequestToken() { return requestToken; }
        public void setRequestToken(String value) { requestToken = value; }
    }
    public static class Request {
        @NotNull @PositiveOrZero private Long expectedVersion;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion = value; }
    }
    public static class Manifest extends Request {
        @NotEmpty @Size(max = 50) private List<@NotNull @Positive Long> shipmentIds;
        public List<Long> getShipmentIds() { return shipmentIds; }
        public void setShipmentIds(List<Long> value) { shipmentIds = value; }
    }
    public static class Action extends Request {
        private boolean confirmed;
        @NotBlank @Size(max = 100) private String reference;
        @Size(max = 500) private String notes;
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean value) { confirmed = value; }
        public String getReference() { return reference; }
        public void setReference(String value) { reference = value; }
        public String getNotes() { return notes; }
        public void setNotes(String value) { notes = value; }
    }
    public static class Receive extends Action {
        @NotNull @Positive private Long itemId;
        private boolean resolveDiscrepancy;
        public Long getItemId() { return itemId; }
        public void setItemId(Long value) { itemId = value; }
        public boolean isResolveDiscrepancy() { return resolveDiscrepancy; }
        public void setResolveDiscrepancy(boolean value) { resolveDiscrepancy = value; }
    }
    public static class Discrepancy extends Action {
        @Positive private Long itemId;
        @NotNull private TripDiscrepancyKind kind;
        @Override @NotBlank public String getNotes() { return super.getNotes(); }
        public Long getItemId() { return itemId; }
        public void setItemId(Long value) { itemId = value; }
        public TripDiscrepancyKind getKind() { return kind; }
        public void setKind(TripDiscrepancyKind value) { kind = value; }
    }
    public static class Resolve extends Action {
        @NotNull @Positive private Long discrepancyId;
        @Override @NotBlank public String getNotes() { return super.getNotes(); }
        public Long getDiscrepancyId() { return discrepancyId; }
        public void setDiscrepancyId(Long value) { discrepancyId = value; }
    }
    public static class LocalReady extends Action {
        @NotNull @Positive private Long shipmentId;
        public Long getShipmentId() { return shipmentId; }
        public void setShipmentId(Long value) { shipmentId = value; }
    }
}
