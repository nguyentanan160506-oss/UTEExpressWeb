SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

-- Existing roots and all history remain unchanged. No automatic customer visibility backfill or routing seeds.
CREATE TABLE dbo.support_ticket_routes (
    id BIGINT IDENTITY(1,1) NOT NULL,
    origin_branch_id BIGINT NOT NULL,
    handling_branch_id BIGINT NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_support_ticket_routes_active DEFAULT (1),
    CONSTRAINT PK_support_ticket_routes PRIMARY KEY (id),
    CONSTRAINT UQ_support_ticket_routes_origin UNIQUE (origin_branch_id),
    CONSTRAINT FK_support_ticket_routes_origin FOREIGN KEY (origin_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_ticket_routes_handling FOREIGN KEY (handling_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION
);

ALTER TABLE dbo.support_ticket_messages ADD request_token VARCHAR(32) NULL, request_fingerprint VARCHAR(64) NULL;
GO

ALTER TABLE dbo.support_ticket_messages ADD CONSTRAINT CK_support_ticket_messages_request CHECK ((request_token IS NULL AND request_fingerprint IS NULL) OR (request_token IS NOT NULL AND LEN(request_token) = 32 AND request_fingerprint IS NOT NULL AND LEN(request_fingerprint) = 64 AND kind = 'PUBLIC'));

CREATE UNIQUE INDEX UX_support_ticket_messages_request ON dbo.support_ticket_messages (request_token) WHERE request_token IS NOT NULL;

CREATE INDEX IX_support_tickets_customer ON dbo.support_tickets (user_id, created_at DESC, id DESC);

ALTER TABLE dbo.support_ticket_events DROP CONSTRAINT CK_support_ticket_events_action;

ALTER TABLE dbo.support_ticket_events ADD CONSTRAINT CK_support_ticket_events_action CHECK (action IN ('CREATED', 'ASSIGNED', 'REASSIGNED', 'PRIORITY_CHANGED', 'CATEGORY_CHANGED', 'STATUS_CHANGED', 'INTERNAL_NOTE', 'PUBLIC_REPLY', 'RESOLVED', 'CLOSED', 'REOPENED', 'CUSTOMER_MESSAGE', 'REOPEN_REQUESTED'));

CREATE INDEX IX_support_ticket_events_customer_actions ON dbo.support_ticket_events (ticket_id, action, id);
