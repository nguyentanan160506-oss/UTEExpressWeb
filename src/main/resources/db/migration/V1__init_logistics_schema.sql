-- UTEExpress - SQL Server schema (18 business tables)
-- Execute once through Flyway against an EMPTY UTEExpressDB database.
-- All date/time columns use UTC DATETIME2(7).
-- No ON DELETE CASCADE: all foreign keys use ON DELETE NO ACTION.
-- Note: user_roles is the documented exception to IDENTITY primary keys: composite (user_id, role_id).

-- 1. Users
CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1,1) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name NVARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_users_status DEFAULT ('ACTIVE'),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_users_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_users_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_users PRIMARY KEY (id),
    CONSTRAINT UQ_users_username UNIQUE (username),
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT UQ_users_phone UNIQUE (phone_number),
    CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'UNVERIFIED'))
);
CREATE INDEX IX_users_status ON dbo.users (status);

-- 2. Roles
CREATE TABLE dbo.roles (
    id BIGINT IDENTITY(1,1) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description NVARCHAR(255) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_roles_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_roles PRIMARY KEY (id),
    CONSTRAINT UQ_roles_name UNIQUE (name)
);

-- 3. User-role memberships (composite PK)
CREATE TABLE dbo.user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME2(7) NOT NULL CONSTRAINT DF_user_roles_assigned_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_user_roles_role FOREIGN KEY (role_id) REFERENCES dbo.roles (id) ON DELETE NO ACTION
);

-- 4. Email OTPs: store only hashes, never plaintext OTPs
CREATE TABLE dbo.email_otps (
    id BIGINT IDENTITY(1,1) NOT NULL,
    email VARCHAR(150) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    otp_type VARCHAR(30) NOT NULL,
    expires_at DATETIME2(7) NOT NULL,
    failed_attempts INT NOT NULL CONSTRAINT DF_email_otps_failed_attempts DEFAULT (0),
    max_attempts INT NOT NULL CONSTRAINT DF_email_otps_max_attempts DEFAULT (5),
    resend_available_at DATETIME2(7) NOT NULL,
    is_used BIT NOT NULL CONSTRAINT DF_email_otps_is_used DEFAULT (0),
    ip_address VARCHAR(50) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_email_otps_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_email_otps PRIMARY KEY (id),
    CONSTRAINT CK_email_otps_type CHECK (otp_type IN ('REGISTER_VERIFY', 'PASSWORD_RESET')),
    CONSTRAINT CK_email_otps_failed CHECK (failed_attempts >= 0),
    CONSTRAINT CK_email_otps_max CHECK (max_attempts >= 1)
);
CREATE INDEX IX_email_otps_lookup ON dbo.email_otps (email, otp_type, is_used, expires_at);

-- 5. Saved sender/recipient addresses
CREATE TABLE dbo.user_addresses (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    contact_name NVARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    province NVARCHAR(100) NOT NULL,
    district NVARCHAR(100) NOT NULL,
    ward NVARCHAR(100) NOT NULL,
    detail_address NVARCHAR(255) NOT NULL,
    is_default BIT NOT NULL CONSTRAINT DF_user_addresses_is_default DEFAULT (0),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_user_addresses_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_user_addresses_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_user_addresses PRIMARY KEY (id),
    CONSTRAINT FK_user_addresses_user FOREIGN KEY (user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION
);
CREATE INDEX IX_user_addresses_user ON dbo.user_addresses (user_id);

-- 6. Branches and central hubs
CREATE TABLE dbo.branches (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(30) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    branch_type VARCHAR(30) NOT NULL CONSTRAINT DF_branches_type DEFAULT ('LOCAL_BRANCH'),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150) NULL,
    province NVARCHAR(100) NOT NULL,
    district NVARCHAR(100) NOT NULL,
    ward NVARCHAR(100) NOT NULL,
    detail_address NVARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_branches_status DEFAULT ('ACTIVE'),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_branches_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_branches_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_branches PRIMARY KEY (id),
    CONSTRAINT UQ_branches_code UNIQUE (code),
    CONSTRAINT CK_branches_type CHECK (branch_type IN ('CENTRAL_HUB', 'LOCAL_BRANCH')),
    CONSTRAINT CK_branches_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX IX_branches_location ON dbo.branches (province, district);

-- 7. Manager-to-branch authorization scope
CREATE TABLE dbo.manager_branches (
    id BIGINT IDENTITY(1,1) NOT NULL,
    manager_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    is_primary BIT NOT NULL CONSTRAINT DF_manager_branches_is_primary DEFAULT (1),
    assigned_at DATETIME2(7) NOT NULL CONSTRAINT DF_manager_branches_assigned_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_manager_branches PRIMARY KEY (id),
    CONSTRAINT UQ_manager_branches UNIQUE (manager_id, branch_id),
    CONSTRAINT FK_manager_branches_manager FOREIGN KEY (manager_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_manager_branches_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION
);
CREATE INDEX IX_manager_branches_branch ON dbo.manager_branches (branch_id);

-- 8. Operational employees (no shipper login)
CREATE TABLE dbo.employees (
    id BIGINT IDENTITY(1,1) NOT NULL,
    branch_id BIGINT NOT NULL,
    employee_code VARCHAR(30) NOT NULL,
    full_name NVARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    position VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT DF_employees_status DEFAULT ('ACTIVE'),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_employees_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_employees_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_employees PRIMARY KEY (id),
    CONSTRAINT UQ_employees_code UNIQUE (employee_code),
    CONSTRAINT CK_employees_position CHECK (position IN ('DISPATCHER', 'COURIER_DRIVER', 'WAREHOUSE_STAFF')),
    CONSTRAINT CK_employees_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'INACTIVE')),
    CONSTRAINT FK_employees_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION
);
CREATE INDEX IX_employees_branch_status ON dbo.employees (branch_id, status);

-- 9. Shipping service catalog
CREATE TABLE dbo.shipping_services (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(255) NULL,
    estimated_delivery_hours INT NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_shipping_services_is_active DEFAULT (1),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipping_services_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipping_services_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_shipping_services PRIMARY KEY (id),
    CONSTRAINT UQ_shipping_services_code UNIQUE (code),
    CONSTRAINT CK_services_hours CHECK (estimated_delivery_hours > 0)
);

-- 10. Effective-dated shipping price rules
CREATE TABLE dbo.shipping_rate_rules (
    id BIGINT IDENTITY(1,1) NOT NULL,
    service_id BIGINT NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    base_weight_kg DECIMAL(10,2) NOT NULL,
    base_price DECIMAL(18,2) NOT NULL,
    extra_weight_step_kg DECIMAL(10,2) NOT NULL,
    extra_price_per_step DECIMAL(18,2) NOT NULL,
    effective_from DATETIME2(7) NOT NULL CONSTRAINT DF_shipping_rate_rules_effective_from DEFAULT (SYSUTCDATETIME()),
    effective_to DATETIME2(7) NULL,
    is_active BIT NOT NULL CONSTRAINT DF_shipping_rate_rules_is_active DEFAULT (1),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipping_rate_rules_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipping_rate_rules_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_shipping_rate_rules PRIMARY KEY (id),
    CONSTRAINT FK_rate_rules_service FOREIGN KEY (service_id) REFERENCES dbo.shipping_services (id) ON DELETE NO ACTION,
    CONSTRAINT CK_rate_rules_scope CHECK (scope_type IN ('INTRA_PROVINCE', 'INTER_PROVINCE', 'INTER_REGION')),
    CONSTRAINT CK_rate_rules_base_weight CHECK (base_weight_kg > 0),
    CONSTRAINT CK_rate_rules_base_price CHECK (base_price >= 0),
    CONSTRAINT CK_rate_rules_step_weight CHECK (extra_weight_step_kg > 0),
    CONSTRAINT CK_rate_rules_step_price CHECK (extra_price_per_step >= 0),
    CONSTRAINT CK_rate_rules_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX IX_rate_rules_search ON dbo.shipping_rate_rules
    (service_id, scope_type, is_active, effective_from, effective_to);

-- 11. Core shipment / tracking aggregate
CREATE TABLE dbo.shipments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    tracking_number VARCHAR(50) NOT NULL,
    sender_id BIGINT NOT NULL,
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
    origin_branch_id BIGINT NOT NULL,
    destination_branch_id BIGINT NOT NULL,
    current_branch_id BIGINT NULL,
    service_id BIGINT NOT NULL,
    package_description NVARCHAR(255) NOT NULL,
    weight_kg DECIMAL(10,2) NOT NULL,
    length_cm DECIMAL(10,2) NULL,
    width_cm DECIMAL(10,2) NULL,
    height_cm DECIMAL(10,2) NULL,
    cod_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_shipments_cod DEFAULT (0.00),
    declared_value DECIMAL(18,2) NOT NULL CONSTRAINT DF_shipments_declared DEFAULT (0.00),
    shipping_fee DECIMAL(18,2) NOT NULL,
    insurance_fee DECIMAL(18,2) NOT NULL CONSTRAINT DF_shipments_insurance_fee DEFAULT (0.00),
    total_fee DECIMAL(18,2) NOT NULL,
    status VARCHAR(50) NOT NULL CONSTRAINT DF_shipments_status DEFAULT ('CREATED'),
    delivery_attempt_count INT NOT NULL CONSTRAINT DF_shipments_attempts DEFAULT (0),
    max_delivery_attempts INT NOT NULL CONSTRAINT DF_shipments_max_attempts DEFAULT (3),
    payment_status VARCHAR(30) NOT NULL CONSTRAINT DF_shipments_payment_status DEFAULT ('UNPAID'),
    payment_payer VARCHAR(30) NOT NULL CONSTRAINT DF_shipments_payer DEFAULT ('SENDER'),
    note NVARCHAR(500) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipments_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipments_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_shipments PRIMARY KEY (id),
    CONSTRAINT UQ_shipments_tracking UNIQUE (tracking_number),
    CONSTRAINT FK_shipments_sender FOREIGN KEY (sender_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_shipments_origin_branch FOREIGN KEY (origin_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_shipments_dest_branch FOREIGN KEY (destination_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_shipments_current_branch FOREIGN KEY (current_branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_shipments_service FOREIGN KEY (service_id) REFERENCES dbo.shipping_services (id) ON DELETE NO ACTION,
    CONSTRAINT CK_shipments_weight CHECK (weight_kg > 0),
    CONSTRAINT CK_shipments_length CHECK (length_cm IS NULL OR length_cm > 0),
    CONSTRAINT CK_shipments_width CHECK (width_cm IS NULL OR width_cm > 0),
    CONSTRAINT CK_shipments_height CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT CK_shipments_cod CHECK (cod_amount >= 0),
    CONSTRAINT CK_shipments_declared CHECK (declared_value >= 0),
    CONSTRAINT CK_shipments_shipping_fee CHECK (shipping_fee >= 0),
    CONSTRAINT CK_shipments_insurance_fee CHECK (insurance_fee >= 0),
    CONSTRAINT CK_shipments_total_fee CHECK (total_fee >= 0),
    CONSTRAINT CK_shipments_status CHECK (status IN (
        'CREATED', 'CONFIRMED', 'PICKUP_ASSIGNED', 'PICKED_UP', 'AT_ORIGIN_HUB',
        'IN_TRANSIT', 'AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY', 'DELIVERED',
        'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELLED'
    )),
    CONSTRAINT CK_shipments_attempts CHECK (delivery_attempt_count >= 0),
    CONSTRAINT CK_shipments_max_attempts CHECK (max_delivery_attempts >= 1),
    CONSTRAINT CK_shipments_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    CONSTRAINT CK_shipments_payer CHECK (payment_payer IN ('SENDER', 'RECEIVER'))
);
CREATE INDEX IX_shipments_sender ON dbo.shipments (sender_id, created_at DESC);
CREATE INDEX IX_shipments_status ON dbo.shipments (status);
CREATE INDEX IX_shipments_branches ON dbo.shipments (origin_branch_id, destination_branch_id, current_branch_id);

-- 12. Immutable shipment status history is written by application transactions
CREATE TABLE dbo.shipment_events (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    actor_id BIGINT NULL,
    from_status VARCHAR(50) NULL,
    to_status VARCHAR(50) NOT NULL,
    event_title NVARCHAR(150) NOT NULL,
    description NVARCHAR(500) NULL,
    location_text NVARCHAR(255) NULL,
    occurred_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipment_events_occurred_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_shipment_events PRIMARY KEY (id),
    CONSTRAINT FK_shipment_events_shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_shipment_events_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches (id) ON DELETE NO ACTION,
    CONSTRAINT FK_shipment_events_actor FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION
);
CREATE INDEX IX_shipment_events_timeline ON dbo.shipment_events (shipment_id, occurred_at DESC);

-- 13. Assignments (pickup, delivery, transfer)
CREATE TABLE dbo.shipment_assignments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assignment_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_assignments_status DEFAULT ('ASSIGNED'),
    notes NVARCHAR(255) NULL,
    assigned_at DATETIME2(7) NOT NULL CONSTRAINT DF_assignments_assigned_at DEFAULT (SYSUTCDATETIME()),
    completed_at DATETIME2(7) NULL,
    CONSTRAINT PK_shipment_assignments PRIMARY KEY (id),
    CONSTRAINT FK_assignments_shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_assignments_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT FK_assignments_manager FOREIGN KEY (assigned_by) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_assignments_type CHECK (assignment_type IN ('PICKUP', 'DELIVERY', 'TRANSFER')),
    CONSTRAINT CK_assignments_status CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX IX_assignments_shipment ON dbo.shipment_assignments (shipment_id, status);
CREATE INDEX IX_assignments_employee ON dbo.shipment_assignments (employee_id, status);

-- 14. Delivery attempt details
CREATE TABLE dbo.delivery_attempts (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason NVARCHAR(255) NULL,
    recipient_signature NVARCHAR(255) NULL,
    photo_proof_url NVARCHAR(500) NULL,
    notes NVARCHAR(500) NULL,
    attempt_time DATETIME2(7) NOT NULL CONSTRAINT DF_delivery_attempts_attempt_time DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_delivery_attempts PRIMARY KEY (id),
    CONSTRAINT UQ_delivery_attempts UNIQUE (shipment_id, attempt_number),
    CONSTRAINT FK_delivery_attempts_shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_delivery_attempts_employee FOREIGN KEY (employee_id) REFERENCES dbo.employees (id) ON DELETE NO ACTION,
    CONSTRAINT CK_delivery_attempts_number CHECK (attempt_number >= 1),
    CONSTRAINT CK_delivery_attempts_status CHECK (status IN ('SUCCESS', 'FAILED'))
);
CREATE INDEX IX_delivery_attempts_employee ON dbo.delivery_attempts (employee_id);

-- 15. Shipping fee, COD collection, refunds
CREATE TABLE dbo.shipment_payments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    shipment_id BIGINT NOT NULL,
    payment_code VARCHAR(50) NOT NULL,
    payment_type VARCHAR(30) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_payments_status DEFAULT ('PENDING'),
    transaction_reference VARCHAR(100) NULL,
    paid_at DATETIME2(7) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_shipment_payments_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_shipment_payments PRIMARY KEY (id),
    CONSTRAINT UQ_shipment_payments_code UNIQUE (payment_code),
    CONSTRAINT FK_shipment_payments_shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT CK_payments_type CHECK (payment_type IN ('SHIPPING_FEE', 'COD_COLLECTION', 'REFUND')),
    CONSTRAINT CK_payments_method CHECK (payment_method IN ('CASH', 'VNPAY', 'MOMO', 'BANK_TRANSFER')),
    CONSTRAINT CK_payments_amount CHECK (amount >= 0),
    CONSTRAINT CK_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
CREATE INDEX IX_shipment_payments_shipment ON dbo.shipment_payments (shipment_id, status);

-- 16. User-facing notifications
CREATE TABLE dbo.notifications (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    title NVARCHAR(150) NOT NULL,
    message NVARCHAR(1000) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    related_shipment_id BIGINT NULL,
    is_read BIT NOT NULL CONSTRAINT DF_notifications_is_read DEFAULT (0),
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_notifications PRIMARY KEY (id),
    CONSTRAINT FK_notifications_user FOREIGN KEY (user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_notifications_shipment FOREIGN KEY (related_shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT CK_notifications_type CHECK (notification_type IN ('SHIPMENT_STATUS', 'PAYMENT', 'SUPPORT', 'SYSTEM'))
);
CREATE INDEX IX_notifications_user_read ON dbo.notifications (user_id, is_read, created_at DESC);

-- 17. Logged-in user support tickets
CREATE TABLE dbo.support_tickets (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_code VARCHAR(30) NOT NULL,
    user_id BIGINT NOT NULL,
    shipment_id BIGINT NULL,
    subject NVARCHAR(200) NOT NULL,
    content NVARCHAR(2000) NOT NULL,
    priority VARCHAR(20) NOT NULL CONSTRAINT DF_support_tickets_priority DEFAULT ('MEDIUM'),
    status VARCHAR(30) NOT NULL CONSTRAINT DF_support_tickets_status DEFAULT ('OPEN'),
    assigned_manager_id BIGINT NULL,
    resolution_notes NVARCHAR(2000) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_support_tickets_created_at DEFAULT (SYSUTCDATETIME()),
    updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_support_tickets_updated_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_support_tickets PRIMARY KEY (id),
    CONSTRAINT UQ_support_tickets_code UNIQUE (ticket_code),
    CONSTRAINT FK_support_tickets_user FOREIGN KEY (user_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_tickets_shipment FOREIGN KEY (shipment_id) REFERENCES dbo.shipments (id) ON DELETE NO ACTION,
    CONSTRAINT FK_support_tickets_manager FOREIGN KEY (assigned_manager_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_support_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT CK_support_tickets_status CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED'))
);
CREATE INDEX IX_support_tickets_user ON dbo.support_tickets (user_id, status);
CREATE INDEX IX_support_tickets_manager ON dbo.support_tickets (assigned_manager_id, status);

-- 18. Security and operational audit trail
CREATE TABLE dbo.audit_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    actor_id BIGINT NULL,
    actor_username VARCHAR(50) NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(50) NULL,
    ip_address VARCHAR(50) NULL,
    old_value NVARCHAR(MAX) NULL,
    new_value NVARCHAR(MAX) NULL,
    created_at DATETIME2(7) NOT NULL CONSTRAINT DF_audit_logs_created_at DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_audit_logs PRIMARY KEY (id),
    CONSTRAINT FK_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES dbo.users (id) ON DELETE NO ACTION,
    CONSTRAINT CK_audit_logs_resource CHECK (resource_type IN ('SHIPMENT', 'USER', 'RATE_RULE', 'BRANCH', 'EMPLOYEE', 'ROLE'))
);
CREATE INDEX IX_audit_logs_actor_time ON dbo.audit_logs (actor_id, created_at DESC);
CREATE INDEX IX_audit_logs_resource ON dbo.audit_logs (resource_type, resource_id);
