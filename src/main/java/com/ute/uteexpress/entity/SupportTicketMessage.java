package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "support_ticket_messages", schema = "dbo", indexes = {@Index(name = "IX_support_ticket_messages_timeline", columnList = "ticket_id, created_at DESC, id DESC")}, check = {@CheckConstraint(name = "CK_support_ticket_messages_request", constraint = "(request_token IS NULL AND request_fingerprint IS NULL) OR (request_token IS NOT NULL AND LEN(request_token) = 32 AND request_fingerprint IS NOT NULL AND LEN(request_fingerprint) = 64 AND kind = 'PUBLIC')"), @CheckConstraint(name = "CK_support_ticket_messages_kind", constraint = "kind IN ('INTERNAL', 'PUBLIC')"), @CheckConstraint(name = "CK_support_ticket_messages_body", constraint = "LEN(LTRIM(RTRIM(body))) > 0")})
public class SupportTicketMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_messages_ticket_id"), updatable = false)
    private SupportTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "FK_support_ticket_messages_author_id"), updatable = false)
    private User author;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private TicketMessageKind kind;
    @Nationalized
    @Column(name = "body", nullable = false, length = 2000, updatable = false)
    private String body;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @ColumnDefault("SYSUTCDATETIME()")
    @Column(name = "created_at", nullable = false, secondPrecision = 7, columnDefinition = "datetime2(7)", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "request_token", length = 32, updatable = false)
    private String requestToken;
    @Column(name = "request_fingerprint", length = 64, updatable = false)
    private String requestFingerprint;
    public String getRequestToken(){return requestToken;}
    public void setRequestToken(String value){requestToken=value;}
    public String getRequestFingerprint(){return requestFingerprint;}
    public void setRequestFingerprint(String value){requestFingerprint=value;}
    @PrePersist void initializeDefaults() {
        if (createdAt == null) { createdAt = UtcTimestamps.now(); }
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public SupportTicket getTicket() { return ticket; }
    public void setTicket(SupportTicket value) { ticket = value; }
    public User getAuthor() { return author; }
    public void setAuthor(User value) { author = value; }
    public TicketMessageKind getKind() { return kind; }
    public void setKind(TicketMessageKind value) { kind = value; }
    public String getBody() { return body; }
    public void setBody(String value) { body = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
