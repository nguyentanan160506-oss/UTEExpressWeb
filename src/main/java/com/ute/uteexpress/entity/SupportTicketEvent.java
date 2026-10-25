package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "support_ticket_events", schema = "dbo", indexes = {@Index(name = "IX_support_ticket_events_customer_actions", columnList = "ticket_id, action, id"), @Index(name = "IX_support_ticket_events_timeline", columnList = "ticket_id, occurred_at DESC, id DESC"), @Index(name = "IX_support_ticket_events_work", columnList = "processed_at, retry_at, id")}, check = {@CheckConstraint(name = "CK_support_ticket_events_action", constraint = "action IN ('CREATED', 'ASSIGNED', 'REASSIGNED', 'PRIORITY_CHANGED', 'CATEGORY_CHANGED', 'STATUS_CHANGED', 'INTERNAL_NOTE', 'PUBLIC_REPLY', 'RESOLVED', 'CLOSED', 'REOPENED', 'CUSTOMER_MESSAGE', 'REOPEN_REQUESTED')"), @CheckConstraint(name = "CK_support_ticket_events_from", constraint = "from_status IS NULL OR from_status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'REOPENED')"), @CheckConstraint(name = "CK_support_ticket_events_to", constraint = "to_status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'REOPENED')"), @CheckConstraint(name = "CK_support_ticket_events_attempts", constraint = "attempts >= 0")})
public class SupportTicketEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_events_ticket_id"), updatable = false)
    private SupportTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_events_branch_id"), updatable = false)
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_events_actor_id"), updatable = false)
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assignee_id", nullable = true, foreignKey = @ForeignKey(name = "FK_support_ticket_events_assignee_id"), updatable = false)
    private User assignee;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "previous_assignee_id", nullable = true, foreignKey = @ForeignKey(name = "FK_support_ticket_events_previous_assignee_id"), updatable = false)
    private User previousAssignee;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "message_id", nullable = true, foreignKey = @ForeignKey(name = "FK_support_ticket_events_message_id"), updatable = false)
    private SupportTicketMessage message;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, length = 30, updatable = false)
    private TicketAction action;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", nullable = true, length = 30, updatable = false)
    private TicketStatus fromStatus;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private TicketStatus toStatus;
    @Nationalized
    @Column(name = "detail", nullable = true, length = 1000, updatable = false)
    private String detail;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "occurred_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    private LocalDateTime occurredAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "processed_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime processedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "retry_at", nullable = true, secondPrecision = 7, columnDefinition = "datetime2(7)")
    private LocalDateTime retryAt;
    @ColumnDefault("0")
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @PrePersist void initializeDefaults() {
        if (occurredAt == null) { occurredAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public SupportTicket getTicket() { return ticket; }
    public void setTicket(SupportTicket value) { ticket = value; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch value) { branch = value; }
    public User getActor() { return actor; }
    public void setActor(User value) { actor = value; }
    public User getAssignee() { return assignee; }
    public void setAssignee(User value) { assignee = value; }
    public User getPreviousAssignee() { return previousAssignee; }
    public void setPreviousAssignee(User value) { previousAssignee = value; }
    public SupportTicketMessage getMessage() { return message; }
    public void setMessage(SupportTicketMessage value) { message = value; }
    public TicketAction getAction() { return action; }
    public void setAction(TicketAction value) { action = value; }
    public TicketStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(TicketStatus value) { fromStatus = value; }
    public TicketStatus getToStatus() { return toStatus; }
    public void setToStatus(TicketStatus value) { toStatus = value; }
    public String getDetail() { return detail; }
    public void setDetail(String value) { detail = value; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { occurredAt = value; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime value) { processedAt = value; }
    public LocalDateTime getRetryAt() { return retryAt; }
    public void setRetryAt(LocalDateTime value) { retryAt = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
}
