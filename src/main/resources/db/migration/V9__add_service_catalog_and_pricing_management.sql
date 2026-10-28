-- Additive management metadata; legacy rates are not invented published versions.
ALTER TABLE dbo.shipping_services ADD version BIGINT NOT NULL CONSTRAINT DF_services_version DEFAULT (0);
ALTER TABLE dbo.shipping_services ADD created_by BIGINT NULL;
ALTER TABLE dbo.shipping_services ADD updated_by BIGINT NULL;
ALTER TABLE dbo.shipping_services ADD max_weight_kg DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipping_services ADD max_length_cm DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipping_services ADD max_width_cm DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipping_services ADD max_height_cm DECIMAL(10,2) NULL;
GO
ALTER TABLE dbo.shipping_services ADD CONSTRAINT FK_services_created_by FOREIGN KEY (created_by) REFERENCES dbo.users (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipping_services ADD CONSTRAINT FK_services_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.users (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipping_services ADD CONSTRAINT CK_services_limits CHECK ((max_weight_kg IS NULL OR max_weight_kg > 0) AND (max_length_cm IS NULL OR max_length_cm > 0) AND (max_width_cm IS NULL OR max_width_cm > 0) AND (max_height_cm IS NULL OR max_height_cm > 0));

CREATE TABLE dbo.pricing_versions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    service_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_pricing_versions_status DEFAULT ('DRAFT'),
    currency VARCHAR(3) NOT NULL CONSTRAINT DF_pricing_versions_currency DEFAULT ('VND'),
    valid_from DATETIME2(7) NOT NULL,
    valid_to DATETIME2(7) NULL,
    retired_from DATETIME2(7) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    published_by BIGINT NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_pricing_versions_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_pricing_versions_updated_at DEFAULT (SYSUTCDATETIME()),
    published_at DATETIME2(7) NULL,
    version BIGINT NOT NULL CONSTRAINT DF_pricing_versions_version DEFAULT (0),
    CONSTRAINT PK_pricing_versions PRIMARY KEY (id),
    CONSTRAINT UQ_pricing_versions_code UNIQUE (service_id, code),
    CONSTRAINT FK_pricing_versions_service FOREIGN KEY (service_id) REFERENCES dbo.shipping_services (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pricing_versions_created_by FOREIGN KEY (created_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pricing_versions_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pricing_versions_published_by FOREIGN KEY (published_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_pricing_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT CK_pricing_versions_currency CHECK (currency = 'VND'),
    CONSTRAINT CK_pricing_versions_code CHECK (LEN(LTRIM(RTRIM(code))) > 0),
    CONSTRAINT CK_pricing_versions_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT CK_pricing_versions_publish CHECK ((status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL AND retired_from IS NULL) OR (status = 'PUBLISHED' AND published_by IS NOT NULL AND published_at IS NOT NULL)),
    CONSTRAINT CK_pricing_versions_retirement CHECK (retired_from IS NULL OR (retired_from > valid_from AND retired_from > published_at AND (valid_to IS NULL OR retired_from < valid_to)))
);
CREATE INDEX IX_pricing_versions_lookup ON dbo.pricing_versions (service_id, status, valid_from, valid_to);

ALTER TABLE dbo.shipping_rate_rules ADD pricing_version_id BIGINT NULL;
ALTER TABLE dbo.shipping_rate_rules ADD retired_from DATETIME2(7) NULL;
GO
ALTER TABLE dbo.shipping_rate_rules ADD CONSTRAINT FK_rate_rules_pricing_version FOREIGN KEY (pricing_version_id) REFERENCES dbo.pricing_versions (id) ON DELETE NO ACTION;
ALTER TABLE dbo.shipping_rate_rules ADD CONSTRAINT CK_rate_rules_retirement CHECK (retired_from IS NULL OR (pricing_version_id IS NULL AND retired_from > effective_from AND (effective_to IS NULL OR retired_from < effective_to)));
CREATE INDEX IX_rate_rules_version ON dbo.shipping_rate_rules (pricing_version_id, scope_type);

ALTER TABLE dbo.pickup_fee_rules ADD pricing_version_id BIGINT NULL;
ALTER TABLE dbo.pickup_fee_rules ADD retired_from DATETIME2(7) NULL;
GO
ALTER TABLE dbo.pickup_fee_rules ADD CONSTRAINT FK_pickup_rules_pricing_version FOREIGN KEY (pricing_version_id) REFERENCES dbo.pricing_versions (id) ON DELETE NO ACTION;
ALTER TABLE dbo.pickup_fee_rules ADD CONSTRAINT CK_pickup_rules_retirement CHECK (retired_from IS NULL OR (pricing_version_id IS NULL AND retired_from > effective_from AND (effective_to IS NULL OR retired_from < effective_to)));
CREATE INDEX IX_pickup_rules_version ON dbo.pickup_fee_rules (pricing_version_id, branch_id);

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
-- Legacy uniqueness is retained without preventing independent drafts with the same planned start.
ALTER TABLE dbo.pickup_fee_rules DROP CONSTRAINT UQ_pickup_fee_rules_start;
CREATE UNIQUE INDEX UX_pickup_legacy_start ON dbo.pickup_fee_rules (branch_id, service_id, effective_from) WHERE pricing_version_id IS NULL;
CREATE UNIQUE INDEX UX_rate_rules_version_scope ON dbo.shipping_rate_rules (pricing_version_id, scope_type) WHERE pricing_version_id IS NOT NULL;
CREATE UNIQUE INDEX UX_pickup_rules_version_branch ON dbo.pickup_fee_rules (pricing_version_id, branch_id) WHERE pricing_version_id IS NOT NULL;

ALTER TABLE dbo.audit_logs DROP CONSTRAINT CK_audit_logs_resource;
ALTER TABLE dbo.audit_logs ADD CONSTRAINT CK_audit_logs_resource CHECK (resource_type IN ('SHIPMENT', 'USER', 'RATE_RULE', 'BRANCH', 'EMPLOYEE', 'ROLE', 'SHIPPING_SERVICE', 'PRICE_VERSION', 'PICKUP_RULE'));
