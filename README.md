# UTEExpressWeb

Website logistics phục vụ đồ án trên một laptop Windows: Guest, USER (shop và khách lẻ), MANAGER, SHIPPER, ADMIN. Spring Boot 4.0.8, Java 21, Thymeleaf/Bootstrap, Spring Security, JPA/Hibernate 7, SQL Server/Flyway và WebSocket.

Mục tiêu sản phẩm cuối: **SQL Server demo riêng có dữ liệu bền vững**, thao tác Chrome localhost. Không cần cloud, hosting hoặc tên miền. H2 memory là môi trường QA độc lập, không giữ tài khoản/vận đơn qua restart.

**Commit 23 đang nghiệm thu:** tooling SQL demo đã chuẩn bị; chưa được cấp phép ghi/chưa kiểm thử persistence trên UTEExpress_Demo. Không suy ra SQL demo DEMO-READY từ Chrome/H2 PASS. Xem [audit sản phẩm](docs/final-product-audit.md) và [QA Commit 23](docs/final-website-qa.md).

## Chức năng

- Guest đăng ký → OTP → đăng nhập; tracking công khai và báo giá không lộ dữ liệu riêng tư.
- User quản lý hồ sơ/sổ địa chỉ; tạo HOME_PICKUP hoặc BRANCH_DROPOFF, xem báo giá trước xác nhận, lịch sử, timeline, support, notifications.
- Manager xác nhận/phân công/nhận hàng, gom nhiều kiện, chuyến qua hub, biên nhận từng kiện, xử lý giao thất bại và hoàn.
- Shipper lấy hàng, bàn giao độc lập cho chi nhánh, giao hàng, proof/failure/recovery.
- Admin quản lý người dùng/quyền, catalog/bảng giá có version, reports/CSV/audit.
- DB quyết định phí; quote có hạn/chủ sở hữu, snapshot và chống tạo lặp. Shop không có ROLE_VENDOR; Admin không tự kế thừa quyền vận hành.

## Cài đặt và khởi động Windows

Cài JDK 21, SQL Server Express và SSMS; JAVA_HOME trỏ JDK 21. Maven Wrapper có sẵn; lần build đầu cần tải dependencies.

Làm theo [SQL demo setup](docs/sql-demo-setup.md): chủ sở hữu tạo **UTEExpress_Demo**, login **uteexpress_demo** chỉ cho DB này; credential DPAPI ngoài Git. Sau khi được cho phép, chạy Inspect → Migrate V1–V13 trên DB trống → Seed mô phỏng một lần.

Mỗi lần trình diễn chạy một script, chờ READY và mở Chrome:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Run -ApprovedDatabase UTEExpress_Demo -Port 8081
```

Chỉ chạy sau khi được cho phép. Startup kiểm tra identity/checksum, Hibernate validate; không tự seed/migrate hoặc đổi target. Ctrl+C dừng ứng dụng; SQL data còn nguyên. Danh sách account và cách cấp mật khẩu ở [SQL demo setup](docs/sql-demo-setup.md). Đăng ký email mới @example.test, nhận OTP ở inbox ADMIN; SMTP giả lập chỉ loopback/test classpath, ngoài production JAR.

Không chạy cấu hình dev để demo: application.properties hiện trỏ UTEExpressDB. Không dùng UTEExpressDB hoặc UTEExpress_Test làm demo. Script không dừng H2 đang chiếm cổng; có thể chọn 8083 nếu cần giữ phiên H2.

## Build và QA riêng biệt

```powershell
.\mvnw.cmd clean test -Duteexpress.sqlserver.validate=false
.\mvnw.cmd verify -Duteexpress.sqlserver.validate=false
.\mvnw.cmd -DskipTests compile
git diff --check
node src/test/js/notifications.test.mjs
node src/test/js/operations.test.mjs
node src/test/js/reports.test.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\browser-qa.ps1
```

Browser runner dùng Chrome headless và H2 mới ở 18082, dừng tiến trình riêng, không dùng SQL credentials. Không cần Playwright/Selenium/npm. Nếu không có Node CLI, có thể chạy JS suites bằng Node runtime giữ nguyên assertions; ghi rõ trong báo cáo. Opt-in SQL test cũ tắt trong unit/regression để tránh cấu hình dev.

H2 để QA nhanh (mất dữ liệu khi tắt):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8083 -TestMail
```

H2, seeder, fake SMTP và demo launcher không nằm trong target/uteexpress-0.0.1-SNAPSHOT.jar. JAR production dùng cấu hình release/SMTP thật được cấp riêng; HTTP localhost không phải cấu hình production Internet.

## Tài liệu

- [Audit và chức năng → class/method](docs/final-product-audit.md).
- [Windows, SQL demo, credential, seed, persistence checklist](docs/sql-demo-setup.md).
- [Kịch bản trình diễn](docs/demo-script.md), [sơ đồ tái sử dụng trong Word](docs/product-diagrams.md).
- [QA Commit 23](docs/final-website-qa.md), [Browser QA](docs/browser-qa.md), [release checklist](docs/release-checklist.md).
- [SQL Server validation Commit 22](docs/sql-server-validation.md), [điều tra dropdown](docs/branch-dropdown-qa.md).

Không có bản đồ/danh mục địa danh toàn quốc, thanh toán online hoặc bảo hiểm thương mại. Backup/restore, TLS, SMTP thật, tải lớn, giám sát và upgrade checksum cũ cần đánh giá riêng trước production. Không tự repair migration hoặc reset DB để vượt lỗi.
