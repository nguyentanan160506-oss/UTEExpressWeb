-- Khởi tạo ba quyền hệ thống theo đặc tả UTEExpress.
-- Khi người dùng đăng ký, chỉ gán ROLE_USER tại tầng ứng dụng.
-- Không tự gán ROLE_MANAGER hoặc ROLE_ADMIN.
INSERT INTO dbo.roles (name)
SELECT 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = 'ROLE_USER');

INSERT INTO dbo.roles (name)
SELECT 'ROLE_MANAGER'
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = 'ROLE_MANAGER');

INSERT INTO dbo.roles (name)
SELECT 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles WHERE name = 'ROLE_ADMIN');
