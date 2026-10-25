package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "return_receipts", schema = "dbo",
    uniqueConstraints = {@UniqueConstraint(name = "UQ_return_receipts_request", columnNames = {"return_request_id"})},
    indexes = {@Index(name = "IX_return_receipts_reports_time", columnList = "received_at, id"), @Index(name = "IX_return_receipts_operations_period", columnList = "branch_id, received_at"), @Index(name = "IX_return_receipts_branch", columnList = "branch_id, received_at DESC")},
    check = {
        @CheckConstraint(name = "CK_return_receipts_recipient", constraint = "recipient_type IN ('SENDER', 'AUTHORIZED_REP')"),
        @CheckConstraint(name = "CK_return_receipts_text", constraint = "LEN(LTRIM(RTRIM(recipient_name))) > 0 AND LEN(LTRIM(RTRIM(reference))) > 0")
    })
public class ReturnReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false, foreignKey = @ForeignKey(name = "FK_return_receipts_return_request_id"))
    private ReturnRequest returnRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_return_receipts_actor_id"))
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_return_receipts_employee_id"))
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_return_receipts_branch_id"))
    private Branch branch;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "recipient_type", nullable = false, length = 30)
    private ReturnRecipientType recipientType;
    @Nationalized
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;
    @Nationalized
    @Column(name = "reference", nullable = false, length = 100)
    private String reference;
    @Nationalized
    @Column(name = "notes", nullable = true, length = 500)
    private String notes;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "received_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime receivedAt;
    @PrePersist void initializeDefaults() {
        if (receivedAt == null) { receivedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public ReturnRequest getReturnRequest() { return returnRequest; }
    public void setReturnRequest(ReturnRequest value) { returnRequest = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public ReturnRecipientType getRecipientType() { return recipientType; }
    public void setRecipientType(ReturnRecipientType value) { recipientType = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { recipientName = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { receivedAt = value; }
}
