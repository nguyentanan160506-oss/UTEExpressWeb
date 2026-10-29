# SQL Server demo bền vững — Windows một laptop

> Commit 24: mapping biên nhận nguồn mới yêu cầu V14. Nội dung V1–V13 bên dưới là quy trình Commit 23. Launcher hiện validate V1–V14; không chạy bản mới với schema 13. Nâng cấp DB demo hiện có cần phê duyệt riêng; không chạy lại seed/clean/repair/reset. Xem [biên nhận tự sinh](automatic-origin-receipts.md).

Trạng thái: tooling đã chuẩn bị để review. Chưa được cấp phép ghi SQL Server trong Commit 23; chưa chạy migration/seed/startup/persistence trên UTEExpress_Demo. H2 là QA riêng, không phải database chính của sản phẩm.

## Mục tiêu và quyền

Target cố định `127.0.0.1:1433`, instance `SQLEXPRESS`, database `UTEExpress_Demo`, SQL login `uteexpress_demo`. Không sử dụng login QA, sa, UTEExpressDB hoặc UTEExpress_Test. Runner xác minh machine Windows, instance, ORIGINAL_LOGIN(), JDBC catalog, DB_NAME() và quyền VIEW DEFINITION trên mỗi kết nối.

Chủ sở hữu tạo database trống và login riêng trong SSMS trước. Thao tác tạo DB/login này cũng cần được chủ sở hữu đồng ý; các lệnh dưới đây là hướng dẫn, chưa được agent thực thi.

Trong SSMS nối `(local)\SQLEXPRESS` bằng tài khoản quản trị của bạn, kiểm tra đúng instance. Tạo database tên **UTEExpress_Demo** bằng New Database. Không thay database cũ. Tạo SQL login **uteexpress_demo** qua Security → Logins → New Login, nhập mật khẩu riêng tại SSMS, giữ password policy, chọn default database UTEExpress_Demo. User Mapping chỉ đánh dấu UTEExpress_Demo và db_owner cho demo cô lập. Không ánh xạ/cấp quyền DB gốc hoặc DB QA. db_owner là quyền rộng cho môi trường đồ án riêng, không phải thiết kế production.

Nếu cần lệnh tạo database, chỉ chạy sau khi kiểm tra tên chưa tồn tại:

```sql
SELECT SERVERPROPERTY('InstanceName') AS instance_name, DB_ID(N'UTEExpress_Demo') AS demo_database_id;
-- Chỉ khi instance đúng và demo_database_id NULL, sau khi chủ sở hữu cho phép:
CREATE DATABASE [UTEExpress_Demo];
```

Mật khẩu login chỉ nhập trong giao diện SSMS, không ghi vào SQL script hoặc chat. Không copy login/credential QA.

## Build và credential

JDK 21, SQL Server Express đang chạy và TCP đúng 1433; không giả định port trỏ đúng instance. Chưa cần Node/npm hoặc cloud. `JAVA_HOME` phải trỏ JDK 21. Build lần đầu cần Maven tải dependencies.

```powershell
cd D:\WebCode\uteexpress\uteexpress
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Credential
```

Get-Credential yêu cầu username/password SQL. DPAPI của tài khoản Windows hiện tại ghi `%TEMP%\uteexpress-sql-demo.credential.xml` ngoài repository, không dùng file QA. Không in nội dung file. Mất file thì chạy lại Credential mode; không mất dữ liệu SQL. Không đổi ExecutionPolicy toàn máy.

## Các lệnh tách biệt

1. Đọc identity/schema history, không migrate hoặc khởi động ứng dụng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Inspect
```

2. **Sau khi chủ sở hữu xác nhận database trống, đúng instance/login và cho phép migration**, áp dụng V1–V13:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Migrate -ApprovedDatabase UTEExpress_Demo
```

Migrate yêu cầu không có user object, VIEW DEFINITION/CREATE TABLE/ALTER dbo, không baseline/clean/repair. Thất bại thì dừng, giữ history và điều tra; không tự chạy lại trên schema dở. Không sửa migration đã áp dụng.

3. **Sau khi chủ sở hữu duyệt seed và nội dung bên dưới**, khởi tạo dữ liệu tham chiếu một lần:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Seed -ApprovedDatabase UTEExpress_Demo
```

Secure prompt yêu cầu mật khẩu website demo ban đầu (ít nhất 16 ký tự, tối đa 72 UTF-8 bytes), chỉ lưu BCrypt. Đây là mật khẩu website, khác credential SQL. Không lưu plaintext hoặc in ra log. Không tạo đơn/hành trình/chứng từ giả bằng seed.

4. **Sau khi chủ sở hữu cho phép ứng dụng ghi nghiệp vụ/worker trên DB demo**, mỗi lần trình diễn chỉ cần:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Run -ApprovedDatabase UTEExpress_Demo -Port 8081
```

Nếu cổng 8081 đang có H2, runner từ chối và không dừng tiến trình cũ. Có thể chọn 8083 rồi mở `http://127.0.0.1:8083/`. Không dừng H2 đang chứa dữ liệu người dùng nếu chưa chấp nhận mất dữ liệu đó.

Chờ dòng `READY http://127.0.0.1:...`, mở Chrome URL đó. Main Spring chạy Hibernate validate và Flyway validate trước, bắt buộc 13 version SUCCESS/checksum đúng, không auto migrate/seed. Ctrl+C dừng tiến trình do script tạo; SQL data giữ lại. Session đăng nhập và hộp thư SMTP memory không được giữ qua restart.

Nếu SQL chưa chạy/credential sai/identity lệch: báo STOPPED với SQLState/code nếu có, không lộ provider message/password, không fallback. Nếu Spring validate lỗi, runner chỉ báo dừng an toàn; cần điều tra có lọc secret, không bật logging bind để dò lỗi.

## Nội dung seed cần phê duyệt

Tất cả là dữ liệu mô phỏng: 4 chi nhánh/hub ACTIVE (DEMO23-DN, HUB-DN, HUB-HCM, HCM), 6 tuyến xuôi/ngược, 4 service areas cho hai phương thức, 1 service/1 version PUBLISHED, 2 rate rules, 2 pickup rules, 2 support routes, 9 web users, 6 employees, 4 ManagerBranch. Các role lấy từ Flyway.

Coverage: `Đà Nẵng / Khu vực demo / Phường demo` và `Hồ Chí Minh / Khu vực demo / Phường demo`. Không tự nhận là phục vụ địa chỉ thực khác.

Giá mô phỏng khởi tạo DB: 1kg cơ bản 20.000 VND nội tỉnh / 40.000 VND khác tỉnh; mỗi 0,5kg vượt thêm 5.000 VND; HOME_PICKUP 7.500 VND tại mỗi đầu; BRANCH_DROPOFF không pickup. Backend đọc các rule này trong DB, không hardcode thuật toán giá demo. 1,01kg DN→HCM: 45.000/52.500 VND, COD và declared value tách riêng. Admin có thể quản lý version/rule qua UI. Không có INTER_REGION hoặc bảo hiểm được bán.

| Actor | Email / username prefix |
|---|---|
| User / User khác | demo23-owner@example.test / demo23-other@example.test |
| Admin | demo23-admin@example.test |
| Manager nguồn | demo23-manager@example.test |
| Manager hub nguồn | demo23-hub-origin-manager@example.test |
| Manager hub đích | demo23-hub-dest-manager@example.test |
| Manager đích | demo23-destination-manager@example.test |
| Shipper lấy / giao | demo23-pickup-courier@example.test / demo23-shipper@example.test |

Seed SERIALIZABLE trong một transaction; lần đầu yêu cầu users/branches/services trống, role đã có. Lặp lại khi namespace đầy đủ: no-op, không reset password, kích hoạt user, sửa giá hay ghi đè thay đổi khách. Namespace thiếu thì dừng để điều tra. Không tự chữa seed. Hai seed đồng thời có thể bị deadlock/rollback; chạy tuần tự và kiểm tra kết quả.

## OTP trên một laptop

Runner dùng SMTP giả lập loopback của test classpath, không nằm trong production JAR. Đăng ký email mới `@example.test`, mã gửi qua SMTP thật nội bộ, không tự kích hoạt. Admin mở `/admin/demo-mailbox` trong phiên Chrome khác, đọc mã rồi nhập vào phiên Guest; mã không ghi file/log. Guest 401, USER 403. Mã đúng mới kích hoạt; sai/hết hạn/dùng lại bị chặn. Hộp thư mất khi restart; resend theo quy tắc ứng dụng.

## Checklist persistence bắt buộc, chưa được chạy

- Chụp kết quả Inspect (identity, 13 checksum), không credential.
- Seed lần một; đếm cấu hình; seed lần hai no-op, hash/tổng row không đổi.
- Đăng ký/OTP/login User mới; sửa hồ sơ và lưu địa chỉ; tạo quote và vận đơn qua UI. Ghi tracking number, snapshot phí, timeline/chứng từ (không secret).
- Dừng và Run lại, không Seed/Migrate; đăng nhập lại: User, địa chỉ, shipment ID/tracking, phí, sự kiện và chứng từ còn nguyên. Kiểm tra số lượng không tăng do restart.
- Chạy hành trình logistics và hoàn bằng cùng mã qua các actor; kiểm tra privacy, CSV/audit, WebSocket hai phiên trên SQL demo.
- Ghi PASS/FAIL thực tế; chưa có bằng chứng thì BLOCKED. Không coi H2 PASS là persistence SQL PASS.
