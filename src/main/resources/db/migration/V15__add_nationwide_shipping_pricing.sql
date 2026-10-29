-- Additive nationwide pricing. No seed prices, backfill, or historical address/price rewrite.
-- A NULL branch is an explicit default pickup tariff, never a fabricated branch.
-- Rebuild only the existing dependent indexes when making branch_id nullable.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
DROP INDEX IX_pickup_fee_rules_lookup ON dbo.pickup_fee_rules;
DROP INDEX IX_pickup_rules_version ON dbo.pickup_fee_rules;
DROP INDEX UX_pickup_legacy_start ON dbo.pickup_fee_rules;
DROP INDEX UX_pickup_rules_version_branch ON dbo.pickup_fee_rules;
ALTER TABLE dbo.pickup_fee_rules ALTER COLUMN branch_id BIGINT NULL;
CREATE INDEX IX_pickup_fee_rules_lookup ON dbo.pickup_fee_rules (branch_id, service_id, is_active, effective_from, effective_to);
CREATE INDEX IX_pickup_rules_version ON dbo.pickup_fee_rules (pricing_version_id, branch_id);
CREATE UNIQUE INDEX UX_pickup_legacy_start ON dbo.pickup_fee_rules (branch_id, service_id, effective_from) WHERE pricing_version_id IS NULL;
CREATE UNIQUE INDEX UX_pickup_rules_version_branch ON dbo.pickup_fee_rules (pricing_version_id, branch_id) WHERE pricing_version_id IS NOT NULL;

ALTER TABLE dbo.shipping_rate_rules DROP CONSTRAINT CK_rate_rules_scope;
ALTER TABLE dbo.shipping_rate_rules ADD CONSTRAINT CK_rate_rules_scope CHECK (scope_type IN ('INTRA_PROVINCE', 'INTER_PROVINCE', 'INTER_REGION', 'SAME_REGION', 'NORTH_CENTRAL', 'CENTRAL_SOUTH', 'NORTH_SOUTH'));

ALTER TABLE dbo.shipment_quotes ADD rate_scope VARCHAR(30) NULL;
ALTER TABLE dbo.shipment_quotes ADD origin_province_code VARCHAR(2) NULL;
ALTER TABLE dbo.shipment_quotes ADD destination_province_code VARCHAR(2) NULL;
ALTER TABLE dbo.shipment_quotes ADD base_shipping_fee DECIMAL(18,2) NULL;
ALTER TABLE dbo.shipment_quotes ADD extra_weight_fee DECIMAL(18,2) NULL;
ALTER TABLE dbo.shipment_quotes ADD priced_base_weight_kg DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipment_quotes ADD priced_step_weight_kg DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipment_quotes ADD priced_extra_price DECIMAL(18,2) NULL;

ALTER TABLE dbo.shipments ADD rate_scope VARCHAR(30) NULL;
ALTER TABLE dbo.shipments ADD origin_province_code VARCHAR(2) NULL;
ALTER TABLE dbo.shipments ADD destination_province_code VARCHAR(2) NULL;
ALTER TABLE dbo.shipments ADD base_shipping_fee DECIMAL(18,2) NULL;
ALTER TABLE dbo.shipments ADD extra_weight_fee DECIMAL(18,2) NULL;
ALTER TABLE dbo.shipments ADD priced_base_weight_kg DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipments ADD priced_step_weight_kg DECIMAL(10,2) NULL;
ALTER TABLE dbo.shipments ADD priced_extra_price DECIMAL(18,2) NULL;

GO
ALTER TABLE dbo.shipment_quotes ADD CONSTRAINT CK_quotes_nationwide_price CHECK ((rate_scope IS NULL AND origin_province_code IS NULL AND destination_province_code IS NULL AND base_shipping_fee IS NULL AND extra_weight_fee IS NULL AND priced_base_weight_kg IS NULL AND priced_step_weight_kg IS NULL AND priced_extra_price IS NULL) OR (rate_scope IS NOT NULL AND origin_province_code IS NOT NULL AND destination_province_code IS NOT NULL AND base_shipping_fee IS NOT NULL AND extra_weight_fee IS NOT NULL AND priced_base_weight_kg IS NOT NULL AND priced_step_weight_kg IS NOT NULL AND priced_extra_price IS NOT NULL AND rate_scope IN ('INTRA_PROVINCE', 'SAME_REGION', 'NORTH_CENTRAL', 'CENTRAL_SOUTH', 'NORTH_SOUTH') AND LEN(origin_province_code) = 2 AND LEN(destination_province_code) = 2 AND base_shipping_fee >= 0 AND extra_weight_fee >= 0 AND priced_base_weight_kg > 0 AND priced_step_weight_kg > 0 AND priced_extra_price >= 0 AND shipping_fee = base_shipping_fee + extra_weight_fee));
ALTER TABLE dbo.shipments ADD CONSTRAINT CK_shipments_nationwide_price CHECK ((rate_scope IS NULL AND origin_province_code IS NULL AND destination_province_code IS NULL AND base_shipping_fee IS NULL AND extra_weight_fee IS NULL AND priced_base_weight_kg IS NULL AND priced_step_weight_kg IS NULL AND priced_extra_price IS NULL) OR (rate_scope IS NOT NULL AND origin_province_code IS NOT NULL AND destination_province_code IS NOT NULL AND base_shipping_fee IS NOT NULL AND extra_weight_fee IS NOT NULL AND priced_base_weight_kg IS NOT NULL AND priced_step_weight_kg IS NOT NULL AND priced_extra_price IS NOT NULL AND rate_scope IN ('INTRA_PROVINCE', 'SAME_REGION', 'NORTH_CENTRAL', 'CENTRAL_SOUTH', 'NORTH_SOUTH') AND LEN(origin_province_code) = 2 AND LEN(destination_province_code) = 2 AND base_shipping_fee >= 0 AND extra_weight_fee >= 0 AND priced_base_weight_kg > 0 AND priced_step_weight_kg > 0 AND priced_extra_price >= 0 AND shipping_fee = base_shipping_fee + extra_weight_fee));

