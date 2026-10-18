package com.ute.uteexpress.dto.manager;

import jakarta.validation.constraints.*;

public final class ManagerForms {
    private ManagerForms() { }
    public static class Request {
        @NotNull @PositiveOrZero private Long expectedVersion;
        @Size(max = 500) private String reason;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { reason = value; }
    }
    public static class Pickup extends Request {
        @NotNull @Positive private Long employeeId;
        @Positive private Long replacesAssignmentId;
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long value) { employeeId = value; }
        public Long getReplacesAssignmentId() { return replacesAssignmentId; }
        public void setReplacesAssignmentId(Long value) { replacesAssignmentId = value; }
    }
    public static class Intake extends Request {
        @NotBlank @Size(max = 100) private String receiptReference;
        @Size(max = 500) private String notes;
        @NotNull @AssertTrue private Boolean physicallyReceived = false;
        public String getReceiptReference() { return receiptReference; }
        public void setReceiptReference(String value) { receiptReference = value; }
        public String getNotes() { return notes; }
        public void setNotes(String value) { notes = value; }
        public Boolean getPhysicallyReceived() { return physicallyReceived; }
        public void setPhysicallyReceived(Boolean value) { physicallyReceived = value; }
    }
}
