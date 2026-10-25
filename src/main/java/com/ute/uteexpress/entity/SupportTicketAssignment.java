package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "support_ticket_assignments", schema = "dbo", indexes = {@Index(name = "IX_support_ticket_assignments_manager", columnList = "manager_id, ended_at, ticket_id"), @Index(name = "IX_support_ticket_assignments_history", columnList = "ticket_id, assigned_at DESC, id DESC")}, check = {@CheckConstraint(name = "CK_support_ticket_assignments_end", constraint = "(ended_at IS NULL AND ended_by IS NULL) OR (ended_at IS NOT NULL AND ended_by IS NOT NULL AND ended_at >= assigned_at)")})
public class SupportTicketAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_assignments_ticket_id"), updatable = false)
    private SupportTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_assignments_manager_id"), updatable = false)
    private User manager;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_assignments_employee_id"), updatable = false)
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_assignments_assigned_by"), updatable = false)
    private User assignedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "ended_by", nullable = true, foreignKey = @ForeignKey(name = "FK_support_ticket_assignments_ended_by"))
    private User endedBy;
    @Nationalized
    @Column(name = "reason", nullable = true, length = 500, updatable = false)
    private String reason;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "assigned_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    private LocalDateTime assignedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "ended_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime endedAt;
    @PrePersist void initializeDefaults() {
        if (assignedAt == null) { assignedAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public SupportTicket getTicket() { return ticket; }
    public void setTicket(SupportTicket value) { ticket = value; }
    public User getManager() { return manager; }
    public void setManager(User value) { manager = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { employee = value; }
    public User getAssignedBy() { return assignedBy; }
    public void setAssignedBy(User value) { assignedBy = value; }
    public User getEndedBy() { return endedBy; }
    public void setEndedBy(User value) { endedBy = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime value) { assignedAt = value; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime value) { endedAt = value; }
}
