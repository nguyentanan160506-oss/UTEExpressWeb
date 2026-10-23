package com.ute.uteexpress.dto.delivery;

import com.ute.uteexpress.enums.*;
import jakarta.validation.constraints.*;

public final class DeliveryForms {
    private DeliveryForms() { }
    public static class Request {
        @NotNull @PositiveOrZero private Long expectedVersion;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long v) { expectedVersion = v; }
    }
    public static class Assign extends Request {
        @NotNull @Positive private Long employeeId;
        @Positive private Long replacesAssignmentId;
        @Size(max = 255) private String reason;
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long v) { employeeId = v; }
        public Long getReplacesAssignmentId() { return replacesAssignmentId; }
        public void setReplacesAssignmentId(Long v) { replacesAssignmentId = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { reason = v; }
    }
    public static class Action extends Request {
        private boolean confirmed;
        @NotBlank @Size(max = 100) private String reference;
        @Size(max = 500) private String notes;
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean v) { confirmed = v; }
        public String getReference() { return reference; }
        public void setReference(String v) { reference = v; }
        public String getNotes() { return notes; }
        public void setNotes(String v) { notes = v; }
    }
    public static class Prepare extends Action {
        @NotNull @Positive private Long assignmentId;
        public Long getAssignmentId() { return assignmentId; }
        public void setAssignmentId(Long v) { assignmentId = v; }
    }
    public static class Complete extends Action {
        @NotNull private DeliveryRecipientType recipientType;
        @NotBlank @Size(max = 100) private String recipientName;
        public DeliveryRecipientType getRecipientType() { return recipientType; }
        public void setRecipientType(DeliveryRecipientType v) { recipientType = v; }
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String v) { recipientName = v; }
    }
    public static class Fail extends Request {
        @NotNull private DeliveryFailureCode code;
        @NotBlank @Size(max = 255) private String reason;
        @Size(max = 500) private String notes;
        private boolean confirmed;
        public DeliveryFailureCode getCode() { return code; }
        public void setCode(DeliveryFailureCode v) { code = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { reason = v; }
        public String getNotes() { return notes; }
        public void setNotes(String v) { notes = v; }
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean v) { confirmed = v; }
    }
    public static class Recover extends Request {
        @Size(max = 500) private String notes;
        public String getNotes() { return notes; }
        public void setNotes(String v) { notes = v; }
    }
    public static class Review extends Request {
        @NotNull @Positive private Long recoveryId;
        @Size(max = 100) private String reference;
        @Size(max = 500) private String reason;
        private boolean confirmed;
        public Long getRecoveryId() { return recoveryId; }
        public void setRecoveryId(Long v) { recoveryId = v; }
        public String getReference() { return reference; }
        public void setReference(String v) { reference = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { reason = v; }
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean v) { confirmed = v; }
    }
    public static class Decision extends Request {
        @Positive private Long employeeId;
        @NotBlank @Size(max = 500) private String reason;
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long v) { employeeId = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { reason = v; }
    }
    public static class Sender extends Action {
        @NotNull private ReturnRecipientType recipientType;
        @NotBlank @Size(max = 100) private String recipientName;
        public ReturnRecipientType getRecipientType() { return recipientType; }
        public void setRecipientType(ReturnRecipientType v) { recipientType = v; }
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String v) { recipientName = v; }
    }
}
