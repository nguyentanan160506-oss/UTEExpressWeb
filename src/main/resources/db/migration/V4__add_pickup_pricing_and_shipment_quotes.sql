-- Quote/pricing foundation. No price rows, inferred areas or legacy fee updates.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

-- Explicit delivery coverage; the existing shipment_method describes origin service only.
ALTER TABLE dbo.branch_service_areas ADD supports_delivery BIT NOT NULL CONSTRAINT DF_service_areas_delivery DEFAULT (0);

CREATE TABLE dbo.pickup_fee_rules (
    id BIGINT IDENTITY(1,1) NOT NULL,
    branch_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    effective_from DATETIME2(7) NOT NULL CONSTRAINT DF_pickup_fee_rules_effective_from DEFAULT (SYSUTCDATETIME()),
    effective_to DATETIME2(7) NULL,
    is_active BIT NOT NULL CONSTRAINT DF_pickup_fee_rules_is_active DEFAULT (1),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_pickup_fee_rules_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_pickup_fee_rules_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_pickup_fee_rules PRIMARY KEY (id),
    CONSTRAINT FK_pickup_fee_rules_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_pickup_fee_rules_service FOREIGN KEY (service_id) REFERENCES dbo.shipping_services (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_pickup_fee_rules_start UNIQUE (branch_id, service_id, effective_from),
    CONSTRAINT CK_pickup_fee_rules_amount CHECK (amount > 0),
    CONSTRAINT CK_pickup_fee_rules_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX IX_pickup_fee_rules_lookup ON dbo.pickup_fee_rules (branch_id, service_id, is_active, effective_from, effective_to);

CREATE TABLE dbo.shipment_quotes (
    id BIGINT IDENTITY(1,1) NOT NULL,
    requester_id BIGINT NOT NULL,
    origin_branch_id BIGINT NOT NULL,
    destination_branch_id BIGINT NOT NULL,
    origin_hub_id BIGINT NOT NULL,
    destination_hub_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    shipping_rate_rule_id BIGINT NOT NULL,
    pickup_fee_rule_id BIGINT NULL,
    token VARCHAR(32) NOT NULL,
    shipment_method VARCHAR(30) NOT NULL,
    sender_name NVARCHAR(100) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    sender_province NVARCHAR(100) NOT NULL,
    sender_district NVARCHAR(100) NOT NULL,
    sender_ward NVARCHAR(100) NOT NULL,
    sender_address NVARCHAR(255) NOT NULL,
    receiver_name NVARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    receiver_province NVARCHAR(100) NOT NULL,
    receiver_district NVARCHAR(100) NOT NULL,
    receiver_ward NVARCHAR(100) NOT NULL,
    receiver_address NVARCHAR(255) NOT NULL,
    package_description NVARCHAR(255) NOT NULL,
    weight_kg DECIMAL(10,2) NOT NULL,
    length_cm DECIMAL(10,2) NULL,
    width_cm DECIMAL(10,2) NULL,
    height_cm DECIMAL(10,2) NULL,
    cod_amount DECIMAL(18,2) NOT NULL,
    declared_value DECIMAL(18,2) NOT NULL,
    payment_payer VARCHAR(30) NOT NULL,
    note NVARCHAR(500) NULL,
    shipping_fee DECIMAL(18,2) NOT NULL,
    pickup_fee DECIMAL(18,2) NOT NULL,
    insurance_fee DECIMAL(18,2) NOT NULL,
    total_fee DECIMAL(18,2) NOT NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipment_quotes_created_at DEFAULT (SYSUTCDATETIME()),
    expires_at DATETIME2(7) NOT NULL,
    consumed_at DATETIME2(7) NULL,
    CONSTRAINT PK_shipment_quotes PRIMARY KEY (id),
    CONSTRAINT FK_quotes_requester FOREIGN KEY (requester_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_origin_branch FOREIGN KEY (origin_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_destination_branch FOREIGN KEY (destination_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_origin_hub FOREIGN KEY (origin_hub_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_destination_hub FOREIGN KEY (destination_hub_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_service FOREIGN KEY (service_id) REFERENCES dbo.shipping_services (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_shipping_rate FOREIGN KEY (shipping_rate_rule_id) REFERENCES dbo.shipping_rate_rules (id) ON DELETE NO ACTION,
    CONSTRAINT FK_quotes_pickup_rule FOREIGN KEY (pickup_fee_rule_id) REFERENCES dbo.pickup_fee_rules (id) ON DELETE NO ACTION,
    CONSTRAINT UQ_shipment_quotes_token UNIQUE (token),
    CONSTRAINT CK_quotes_method CHECK (shipment_method IN ('HOME_PICKUP', 'BRANCH_DROPOFF')),
    CONSTRAINT CK_quotes_payer CHECK (payment_payer IN ('SENDER', 'RECEIVER')),
    CONSTRAINT CK_quotes_weight CHECK (weight_kg > 0),
    CONSTRAINT CK_quotes_length CHECK (length_cm IS NULL OR length_cm > 0),
    CONSTRAINT CK_quotes_width CHECK (width_cm IS NULL OR width_cm > 0),
    CONSTRAINT CK_quotes_height CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT CK_quotes_amounts CHECK (cod_amount >= 0 AND declared_value >= 0 AND shipping_fee >= 0 AND pickup_fee >= 0 AND insurance_fee >= 0 AND total_fee = shipping_fee + pickup_fee + insurance_fee),
    CONSTRAINT CK_quotes_pickup CHECK ((shipment_method = 'HOME_PICKUP' AND pickup_fee_rule_id IS NOT NULL AND pickup_fee > 0) OR (shipment_method = 'BRANCH_DROPOFF' AND pickup_fee_rule_id IS NULL AND pickup_fee = 0)),
    CONSTRAINT CK_quotes_expiry CHECK (expires_at > created_at),
    CONSTRAINT CK_quotes_consumed CHECK (consumed_at IS NULL OR (consumed_at >= created_at AND consumed_at < expires_at))
);
CREATE INDEX IX_shipment_quotes_owner ON dbo.shipment_quotes (requester_id, created_at DESC);
CREATE INDEX IX_shipment_quotes_expiry ON dbo.shipment_quotes (expires_at, consumed_at);

-- NULL preserves legacy fees/methods without inventing a zero pickup snapshot.
ALTER TABLE dbo.shipments ADD pickup_fee DECIMAL(18,2) NULL;
ALTER TABLE dbo.shipments ADD quote_id BIGINT NULL;
ALTER TABLE dbo.shipments ADD CONSTRAINT FK_shipments_quote FOREIGN KEY (quote_id) REFERENCES dbo.shipment_quotes (id) ON DELETE NO ACTION;
CREATE UNIQUE INDEX UX_shipments_quote_nonnull ON dbo.shipments (quote_id) WHERE quote_id IS NOT NULL;
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_pickup_fee CHECK (pickup_fee IS NULL OR pickup_fee >= 0);
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_quote_snapshot CHECK (quote_id IS NULL OR (shipment_method IS NOT NULL AND origin_hub_id IS NOT NULL AND destination_hub_id IS NOT NULL AND pickup_fee IS NOT NULL AND total_fee = shipping_fee + pickup_fee + insurance_fee));
