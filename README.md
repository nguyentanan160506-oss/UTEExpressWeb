# UTEExpressWeb

Ứng dụng logistics của nhóm An (Logistics), Thuận (Core/Admin/Security/Pricing/Notifications/Reports), Tân (User/Guest/Support).
Java 21, Spring Boot 4.0.8, Security 7, Thymeleaf/Bootstrap, JPA/Hibernate 7, SQL Server Express, Flyway, WebSocket/STOMP.

## Trạng thái Commit22

**DEMO-READY cho một laptop Windows/Google Chrome/H2 localhost.** Browser headless thật350PASS/0FAIL/0BLOCKED,
gồm hai vận đơn đi trọn logistics/giao thành công hoặc hoàn trả, SMTP OTP test-only và WebSocket UI nhiều phiên/reconnect.
Full clean test/verify đều1.470tests/1.469passed/0failure/error/1skipped; JavaScript12/12, WebSocket regression17/17.
Kịch bản cửa sổ Chrome và phạm vi: [demo script](docs/demo-script.md), [QA](docs/browser-qa.md), [release checklist](docs/release-checklist.md).

SQL V1–V13/Flyway/Hibernate/business smoke/JAR+HTTP đã PASS từ lượt trước; không dùng SQL Server trong nghiệm thu H2 cuối.
Không cần SQL demo seed hoặc cloud/hosting/tên miền. **Production-ready chưa đạt** (SQL upgrade có dữ liệu/backup/TLS/load/real SMTP là scope riêng).

## Nghiệp vụ và quyền

Shop và khách lẻ cùng `ROLE_USER`. USER báo giá và tạo vận đơn bằng HOME_PICKUP hoặc BRANCH_DROPOFF,
xem lịch sử và gửi support ticket. Guest dùng `/tracking`, `/quote` và không được xem dữ liệu cá nhân vận đơn.
MANAGER chỉ xử lý trong ManagerBranch; SHIPPER chỉ thao tác nhiệm vụ hợp lệ. ADMIN quản trị tài khoản,
bảng giá, reports, CSV và audit, không tự có quyền MANAGER/SHIPPER/USER.

Mạng vận chuyển: chi nhánh nguồn → hub nguồn → hub đích → chi nhánh đích → giao người nhận.
Pickup, tiếp nhận vật lý, manifest/receipt, giao thất bại, giao lại, hoàn và bàn giao người gửi có state machine/lịch sử.
Giá lấy từ database, có phụ phí HOME_PICKUP, báo giá hết hạn và snapshot; không tính lại phí vận đơn cũ.
Support tách PUBLIC/INTERNAL; notifications theo ownership và WebSocket chỉ gửi tín hiệu refresh.

## Yêu cầu và build

Cài JDK 21, cấu hình JAVA_HOME đúng JDK; Maven Wrapper đã có. SQL Server Express cần database riêng được cấp phép.
Node dùng cho ba JS suites; không cần npm install. Maven lần đầu cần tải dependency.

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean test
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests compile
git diff --check
node src/test/js/notifications.test.mjs
node src/test/js/operations.test.mjs
node src/test/js/reports.test.mjs
```

`verify` chạy Surefire và đóng gói `target/uteexpress-0.0.1-SNAPSHOT.jar`; không ngầm kiểm thử SQL Server.
Test schema-validation SQL Server cũ là opt-in, dùng Windows integrated authentication, không phù hợp SQL Authentication
của môi trường này. Không bật tùy tiện; dùng quy trình SQL QA riêng bên dưới.

## Cấu hình SQL Server và chạy JAR

Xem [quy trình SQL Server](docs/sql-server-validation.md) trước. Cấu hình dev hiện trỏ tới UTEExpressDB;
**không dùng database này làm test/demo**. Không commit `.env`, mật khẩu, backup hoặc runtime logs.

Chỉ sau khi xác nhận database test/demo và phạm vi migration, dùng profile `release` với biến môi trường:

| Biến | Ý nghĩa |
|---|---|
| DB_URL | JDBC URL đầy đủ, database được cấp phép; không chứa username/password/token |
| DB_USERNAME, DB_PASSWORD | SQL Authentication, nhận từ môi trường/secret store ngoài Git |
| SPRING_PROFILES_ACTIVE | `release` khi chạy JAR bằng cấu hình triển khai |
| SESSION_COOKIE_SECURE | Mặc định `true`; chỉ đặt `false` cho HTTP localhost cô lập |
| SERVER_PORT | Tùy chọn, mặc định 8080 |
| MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM | Cấu hình email/OTP thật ngoài Git |
| MAIL_SMTP_AUTH, MAIL_STARTTLS, MAIL_SSL | Theo nhà cung cấp SMTP; không tắt TLS tùy tiện |

Không đặt giá trị credential trong command arguments hoặc tài liệu. Có thể cấp password trong PowerShell bằng
`Read-Host -AsSecureString`, chuyển vào biến môi trường của tiến trình rồi xóa biến khi xong; không ghi file.

```powershell
# Chỉ chạy sau khi target/migration được cho phép; DB_URL/credentials đã được cấp ngoài Git.
$env:SPRING_PROFILES_ACTIVE = 'release'
java -jar target/uteexpress-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --debug=false
```

Profile release giữ Flyway enabled/checksum validation, cấm clean và baseline tự động; Hibernate `validate`,
không update/create schema. Tắt SQL/bind logging, stack trace client và H2 console; cookie HttpOnly/SameSite=Lax.
JAR khởi động sẽ áp dụng migration chưa chạy: phải xác nhận quyền migration trước lệnh trên.
Public deployment cần HTTPS thật và cookie Secure; không coi HTTP localhost là cấu hình production.

Mở `/`: ứng dụng chuyển đến `/tracking` công khai. Các trang `/login`, `/register`, `/quote` cũng dành cho Guest; route nghiệp vụ vẫn yêu cầu đúng quyền.
Không có Admin mặc định khi startup; cấp tài khoản quản trị theo quy trình quản trị đã được phê duyệt.

## Demo cục bộ, không cần SQL Server

[Demo setup](docs/demo-setup.md) có tài khoản giả và 18 scenario. Chạy tường minh:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8081 -TestMail
```

Bypass chỉ cho tiến trình chạy script repo đã review, không đổi ExecutionPolicy toàn máy. Nếu chính sách tổ chức cấm,
không thay đổi chính sách; dùng các lệnh Maven/Java trực tiếp trong demo setup hoặc nhờ quản trị viên.
Script hỏi password riêng bằng secure prompt nếu chưa có UTE_DEMO_PASSWORD. Không in/lưu password.
Đây là **H2 memory demo** qua test classpath, chỉ bind 127.0.0.1; không chạy Flyway hoặc chứng minh tương thích SQL Server.
Seeder chỉ chạy từ test entry point được chọn rõ ràng; H2, seeder và credential demo không nằm trong JAR release.
Tắt tiến trình rồi chạy lại tạo một memory database mới; không cần xóa/reset database hiện hữu.

## Tài liệu và giới hạn

- [QA cuối](docs/final-qa-report.md), [release gates](docs/release-checklist.md), [Browser QA/runner](docs/browser-qa.md):350 PASS/0 FAIL/0 BLOCKED trên Chrome headless/H2.
- [SQL Server validation](docs/sql-server-validation.md), [demo setup](docs/demo-setup.md), [kịch bản trình diễn/UI QA](docs/demo-script.md).
- [Security integration](docs/security-system-integration-testing.md), [logistics integration](docs/logistics-integration-testing.md).

SQL Server fresh/upgrade, mapping/concurrency và startup JAR với SQL Server phải có bằng chứng riêng.
HTTP integration không phải browser E2E; WebSocket regression cũ có network thật nhưng persistence mock.
H2 demo không gửi email ra ngoài, vì vậy register/OTP gửi SMTP không nằm trong demo đã nghiệm thu.
Owner shipment timeline content policy, thu hồi mọi session/in-flight ngay lập tức, report snapshot dưới writer và
TLS/broker/phân tán vẫn cần review trước public production.

Lỗi thường gặp: JAVA_HOME và java trên PATH khác JDK; Maven metadata Flyway HTTP 401; cổng bận;
thiếu DB/SMTP env; SQL login không được map vào đúng database; thiếu bảng giá/phạm vi phục vụ;
cookie Secure trên HTTP localhost không được trình duyệt gửi. Không chữa bằng tắt CSRF, validate hoặc reset DB.

## Phạm vi đồ án đã nghiệm thu

DEMO-READY cho một laptop Windows/Google Chrome/H2 localhost: hai hành trình logistics nhất quán, SMTP/OTP test-only và WebSocket UI đã chạy browser thật. Production-ready là phạm vi riêng, không cần cloud/hosting/tên miền để đóng Commit22. Xem [release checklist](docs/release-checklist.md).
-TestMail bật SMTP loopback dành H2, inbox chỉAdmin tại /admin/demo-mailbox; register dùng email mới @example.test, không SMTP cá nhân và không bỏ OTP. Tooling này không vào release JAR.
