# Commit 22 — H2 demo trên một laptop Windows

Mục tiêu đã chốt: website chạy trên cùng laptop và Google Chrome. Không cloud/hosting/tên miền hoặc máy khác.
Demo này dùng app/service/security thật với **H2 memory riêng**, bind127.0.0.1. Không kết nối SQL Server, không Flyway.
Đóng process thì dữ liệu H2 mất; restart dựng lại fixture18scenario. Không cần seed/reset UTEExpressDB/UTEExpress_Test.
SQL validation đã PASS ở lượt trước là bằng chứng riêng ở [SQL guide](sql-server-validation.md), không phải mode demo này.

## Khởi động để trình diễn

Chuẩn bị JDK21/JAVA_HOME, Chrome và dependency cache trước buổi bảo vệ. Từ project:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8081 -TestMail
~~~

Script hỏi password demo riêng bằng secure prompt: tối thiểu16ký tự, tối đa72UTF-8 bytes theo BCrypt.
Không dùng password thật, không ghi literal vào script/command history/Git/log. Seed11account dùng password demo vừa đặt.
Mở Chrome http://127.0.0.1:8081/. Root chuyển/tracking; login/register/quote công khai, business routes giữ role/ownership.
Ctrl+C dừng. Script không mở firewall, không đổi ExecutionPolicy hệ thống; Bypass chỉ trong process chạy script.
Không truyền SQL credential, không dùng ModeSql hoặc java-jar/default profile để thay mode demo.

Tại buổi bảo vệ nên start trước, không chờ Maven tải dependencies. Nếu cần chạy không Maven sau chuẩn bị,
entry point/test classpath và hướng dẫn environment trong scripts/release-tools.ps1 là nguồn chuẩn; không đổi datasource để “chữa” startup.
H2 console bị tắt, config location riêng không tải application.properties dev, datasource và loopback được ghim bởi entry point.

## Tài khoản và cửa sổ Chrome

Email là username@example.test (thay username theo bảng), password qua secure prompt, không có password cố định trong repository.

| Username | Quyền / phạm vi |
|---|---|
|owner|USER, chủ18vận đơn seed|
|other|USER khác, kiểm ownership|
|manager / second-manager|MANAGER nguồn Đà Nẵng|
|hub-origin-manager|MANAGER Hub miền Trung|
|hub-dest-manager|MANAGER Hub phía Nam|
|destination-manager|MANAGER đích TP.HCM và ticket demo|
|outsider|MANAGER ngoài phạm vi|
|pickup-courier|SHIPPER lấy hàng tại nguồn|
|shipper|SHIPPER giao tại đích|
|admin|ADMIN; không tự kế thừa USER/MANAGER/SHIPPER|

Cửa sổ thường giữ User; một Incognito cho actor vận hành, logout/login khi chuyển actor.
Nhiều Incognito cùng profile dùng chung cookie/session, không phải các phiên độc lập. Có thể dùng profile riêng do người trình bày tự chọn.
Trong automation, BrowserContext độc lập đã kiểm cookie/storage isolation thực.

## Đăng ký và SMTP/OTP

-TestMail chỉ hợp lệ cùng ModeDemo. Nó bật UTE_DEMO_TEST_MAIL=YES cho child H2; mặc định không switch thì mailtest tắt.
Trước mở SMTP, DataSource phải là owned H2 memory. SMTP listener chỉ127.0.0.1/cổngngẫu nhiên, không relay/AUTH, không SMTP cá nhân.
Chỉ nhận địa chỉ @example.test, ví dụ email mới cho mỗi lần đăng ký. Domain bên ngoài bị từ chối và transaction user/role/OTP rollback.
Không giảm TTL/cooldown, không bypass hash/verify, không tự active tài khoản.

Mở /admin/demo-mailbox ở phiên Admin để đọc mã cho đúng email rồi nhập /verify-email ở phiên khách.
Sai OTP/UNVERIFIED login/reuse đều bị chặn; đúng mã mới ACTIVE/login. Thư chỉ trong RAM, tối đa50, hiển thị tối đa5phút;
no-store, chỉAdmin, không ghi nội dung/OTP ra file hoặc log. Restart mất thư; muốn mã mới tuân cooldown60giây và giới hạn nghiệp vụ.
Không chụp OTP/password/CSRF vào tài liệu bảo vệ. Fake SMTP/controller/inbox chỉ src/test, không nằm production JAR.

## Mạng lưới, giá và dữ liệu form

5điểm: DEMO-DN, DEMO-HUB-DN, DEMO-HUB-HCM, DEMO-HCM và DEMO-OTHER ngoài scope.
6route forward/reverse: nguồn→hubnguồn→hubđích→chi nhánhđích và ngược lại, đúng COLLECTION/LINEHAUL/DISTRIBUTION.
BranchServiceArea cho tỉnh/district/ward demo, active service/version/rates/pickup fee và support route có trong database memory.

Form: Đà Nẵng/Khu vực demo/Phường demo → Hồ Chí Minh/Khu vực demo/Phường demo, địa chỉ/name giả, phone0901234567/0901234568,
Sách demo1,01kg, DEMO-STANDARD, COD/declared0. Bấm Tìm chi nhánh, chọn branch hợp lệ.
DB fixture: shipping45000, pickup7500; HOME_PICKUP52500 / BRANCH_DROPOFF45000; insurance0.
Đây là giá giả lập có ruleDB, không giá thị trường/hardcode service tính tiền. Cùng tỉnh dùng INTRA_PROVINCE, khác tỉnh INTER_PROVINCE, chưa INTER_REGION.
Quote có hạn15phút; gửi lại quote đã dùng mở cùng đơn. Snapshot giữ nguyên sau vận chuyển/hoàn.

## 18 scenario seed

NEW, CONFIRMED, HOME_PICKUP, BRANCH_DROPOFF, PICKED_UP, ORIGIN_INTAKE, SOURCE_HUB, LINEHAUL, DESTINATION,
DELIVERY_ASSIGNED, DELIVERED, FAILED, REDELIVERY, RETURNING, RETURNED, DISCREPANCY, SUPPORT, NOTIFICATION.
ID lấy từ manifest/list thật sau restart. DISCREPANCY cố ý open MISSING, không receipt giả để ép close.
SUPPORT có PUBLIC reply và INTERNAL marker chỉ Manager được thấy. Các case có chứng từ/history tạo qua service thật.

Seeder chỉ chấp nhận newly allocated H2 prefix và usercount0; không được dùng cho SQL Server.
Seed lại trong cùng context đã hoàn thành là no-op/idempotent; nếu seed lỗi phải dừng disposable process, không resume trạng thái áp dụng dở.
Không tạo role/branch/rate giả trong runtime business để che thiếu cấu hình. Seed không được nhúng vào normal startup/JAR.

## QA, readiness và phạm vi còn lại

[Browser QA](browser-qa.md):350PASS/0FAIL/0BLOCKED gồm hai đơn mới đi trọn luồng giao thành công/hoàn hàng, SMTP OTP và live WebSocket.
[Demo script](demo-script.md) là trình tự bấm tay chi tiết; presenter rehearsal chưa được agent thực hiện qua cửa sổ Chrome.
DEMO-READY cho laptop/H2. Production-ready chưa đạt: real SMTP/TLS, SQL upgrade có dữ liệu/backup, load/multi-instance/deployment là scope riêng.
Không cần SQL demo seed hoặc cloud để hoàn tất Commit22 theo mục tiêu hiện tại.
