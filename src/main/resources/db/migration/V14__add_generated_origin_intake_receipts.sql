-- Preserve all historical handwritten references, including duplicates. No backfill or data rewrite.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

ALTER TABLE dbo.shipment_intakes ADD system_receipt_code VARCHAR(45) NULL;
ALTER TABLE dbo.shipment_intakes ADD request_version BIGINT NULL;
GO

ALTER TABLE dbo.shipment_intakes ADD CONSTRAINT CK_intakes_system_receipt CHECK (
    (system_receipt_code IS NULL AND request_version IS NULL)
    OR (system_receipt_code IS NOT NULL AND request_version IS NOT NULL
        AND request_version >= 0 AND LEN(system_receipt_code) = 45
        AND system_receipt_code = receipt_reference)
);

CREATE UNIQUE INDEX UX_intakes_system_receipt_code
    ON dbo.shipment_intakes (system_receipt_code)
    WHERE system_receipt_code IS NOT NULL;
