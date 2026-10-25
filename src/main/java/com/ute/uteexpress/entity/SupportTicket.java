package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.TicketPriority;
import com.ute.uteexpress.enums.TicketStatus;
import com.ute.uteexpress.enums.TicketCategory;
import com.ute.uteexpress.enums.TicketSource;
import jakarta.persistence.Version;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "support_tickets",
    schema = "dbo",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_support_tickets_code", columnNames = {"ticket_code"})
    },
    indexes = {@Index(name = "IX_support_tickets_operations_period", columnList = "handling_branch_id, created_at"), @Index(name = "IX_support_tickets_operations_resolution", columnList = "handling_branch_id, resolved_at"),
        @Index(name = "IX_support_tickets_user", columnList = "user_id, status"),
        @Index(name = "IX_support_tickets_manager", columnList = "assigned_manager_id, status"),
        @Index(name = "IX_support_tickets_branch", columnList = "handling_branch_id, status, priority, created_at DESC, id DESC"),
        @Index(name = "IX_support_tickets_shipment", columnList = "shipment_id"),
        @Index(name = "IX_support_tickets_warning", columnList = "handling_branch_id, warning_due_at, status")
    },
    check = {
        @CheckConstraint(name = "CK_support_tickets_managed_state", constraint = "handling_branch_id IS NULL OR ((status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'REOPENED') AND resolved_at IS NULL AND closed_at IS NULL) OR (status = 'RESOLVED' AND resolved_at IS NOT NULL AND closed_at IS NULL AND resolution_notes IS NOT NULL AND LEN(LTRIM(RTRIM(resolution_notes))) > 0) OR (status = 'CLOSED' AND resolved_at IS NOT NULL AND closed_at IS NOT NULL AND resolution_notes IS NOT NULL AND LEN(LTRIM(RTRIM(resolution_notes))) > 0))"),
        @CheckConstraint(name = "CK_support_tickets_managed_assignee", constraint = "handling_branch_id IS NULL OR status = 'REOPENED' OR (status = 'OPEN' AND assigned_manager_id IS NULL) OR (status IN ('ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED') AND assigned_manager_id IS NOT NULL)"),

        @CheckConstraint(name = "CK_support_tickets_category", constraint = "category IS NULL OR category IN ('DELAY', 'NOT_RECEIVED', 'DELIVERY_FAILURE', 'LOSS_REPORTED', 'DAMAGE_REPORTED', 'WRONG_RECIPIENT', 'ADDRESS', 'SHIPPING_FEE', 'RETURN', 'STAFF_CONDUCT', 'OTHER')"),
        @CheckConstraint(name = "CK_support_tickets_source", constraint = "source IS NULL OR source IN ('MANAGER_PHONE', 'MANAGER_EMAIL', 'MANAGER_COUNTER', 'CUSTOMER')"),
        @CheckConstraint(name = "CK_support_tickets_version", constraint = "version >= 0"),
        @CheckConstraint(name = "CK_support_tickets_managed", constraint = "handling_branch_id IS NULL OR (shipment_id IS NOT NULL AND created_by IS NOT NULL AND category IS NOT NULL AND source IS NOT NULL AND request_token IS NOT NULL AND request_fingerprint IS NOT NULL)"),
        @CheckConstraint(name = "CK_support_tickets_resolved_time", constraint = "resolved_at IS NULL OR (resolved_at >= created_at AND status IN ('RESOLVED', 'CLOSED'))"),
        @CheckConstraint(name = "CK_support_tickets_closed_time", constraint = "closed_at IS NULL OR (closed_at >= created_at AND status = 'CLOSED')"),
        @CheckConstraint(name = "CK_support_tickets_warning_time", constraint = "warning_due_at IS NULL OR warning_due_at >= created_at"),

        @CheckConstraint(name = "CK_support_tickets_priority", constraint = "priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')"),
        @CheckConstraint(name = "CK_support_tickets_status", constraint = "status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'REOPENED')")
    }
)
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "ticket_code", nullable = false, length = 30)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_tickets_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "shipment_id", nullable = true, foreignKey = @ForeignKey(name = "FK_support_tickets_shipment"))
    private Shipment shipment;

    @Nationalized
    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Nationalized
    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "priority", nullable = false, length = 20)
    @ColumnDefault("'MEDIUM'")
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    @ColumnDefault("'OPEN'")
    private TicketStatus status = TicketStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assigned_manager_id", nullable = true, foreignKey = @ForeignKey(name = "FK_support_tickets_manager"))
    private User assignedManager;

    @Nationalized
    @Column(name = "resolution_notes", nullable = true, length = 2000)
    private String resolutionNotes;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)")
    @ColumnDefault("SYSUTCDATETIME()")
    private LocalDateTime updatedAt;


    @Version @Column(name = "version", nullable = false) @ColumnDefault("0")
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handling_branch_id", foreignKey = @ForeignKey(name = "FK_support_tickets_branch"))
    private Branch handlingBranch;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "FK_support_tickets_creator"))
    private User createdBy;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "source", length = 30)
    private TicketSource source;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "category", length = 30)
    private TicketCategory category;
    @Column(name = "request_token", length = 32)
    private String requestToken;
    @Column(name = "request_fingerprint", length = 64, updatable = false)
    private String requestFingerprint;
    public String getRequestFingerprint(){return requestFingerprint;}
    public void setRequestFingerprint(String value){requestFingerprint=value;}
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "resolved_at", columnDefinition = "datetime2(7)", secondPrecision = 7)
    private LocalDateTime resolvedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "closed_at", columnDefinition = "datetime2(7)", secondPrecision = 7)
    private LocalDateTime closedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "warning_due_at", columnDefinition = "datetime2(7)", secondPrecision = 7, updatable = false)
    private LocalDateTime warningDueAt;
    public Long getVersion() { return version; }
    public Branch getHandlingBranch() { return handlingBranch; }
    public void setHandlingBranch(Branch v) { handlingBranch = v; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User v) { createdBy = v; }
    public TicketSource getSource() { return source; }
    public void setSource(TicketSource v) { source = v; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory v) { category = v; }
    public String getRequestToken() { return requestToken; }
    public void setRequestToken(String v) { requestToken = v; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime v) { resolvedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime v) { closedAt = v; }
    public LocalDateTime getWarningDueAt() { return warningDueAt; }
    public void setWarningDueAt(LocalDateTime v) { warningDueAt = v; }

    public SupportTicket() {
    }

    @PrePersist
    void initializeDefaults() {
        LocalDateTime now = UtcTimestamps.now();
        if (priority == null) {
            priority = TicketPriority.MEDIUM;
        }
        if (status == null) {
            status = TicketStatus.OPEN;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = UtcTimestamps.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public User getAssignedManager() {
        return assignedManager;
    }

    public void setAssignedManager(User assignedManager) {
        this.assignedManager = assignedManager;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
