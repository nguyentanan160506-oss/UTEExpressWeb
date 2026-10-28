# Cập nhật Commit 23 — sản phẩm SQL Server demo

Phạm vi mới yêu cầu dữ liệu bền vững trên **UTEExpress_Demo**. Kết luận DEMO-READY bên dưới thuộc H2/Commit 22, không chứng nhận mục tiêu SQL demo mới. **Commit 23 chưa đủ điều kiện đóng** khi chưa cấp phép và chưa chạy migration/seed/startup/persistence sau restart trên DB demo riêng. Không thực hiện thao tác SQL Server trong giai đoạn chuẩn bị này.

Kết quả chuẩn bị: Maven clean verify **1.492 passed / 1 skipped**, Chrome H2 **365 PASS / 0 FAIL / 0 BLOCKED**, JavaScript **12/12**, WebSocket backend **17/17**, production JAR không có tooling/SMTP/H2/test secrets. Đây không phải persistence SQL demo PASS.

Xem [QA Commit 23](final-website-qa.md), [audit](final-product-audit.md), [hướng dẫn SQL demo và các bước cần duyệt](sql-demo-setup.md).

---

# Commit 22 — Release checklist cho đồ án một laptop

Cập nhật28/10/2026,21:05 UTC+7. Branch feature/team-final-qa-demo-release, HEAD4e71056.
Phạm vi cuối: Windows laptop, Google Chrome, localhost/H2 demo riêng. Không cloud/hosting/tên miền/máy khác.
**DEMO-READY: PASS trong phạm vi này. PRODUCTION-READY: chưa đạt và không thuộc điều kiện đóng đồ án hiện tại.**

| Gate demo | Kết quả | Bằng chứng / giới hạn |
|---|---|---|
| Browser thật không Computer Use | PASS |Chrome headless, profile TEMP,350 kiểm tra PASS/0FAIL/0BLOCKED; [JSON](browser-qa-results.json) |
| Logistics hai hành trình nhất quán | PASS |Hai đơn HOME_PICKUP mới; cùng manifest,3leg forward; DELIVERED/proof hoặc fail→recovery→3leg reverse→RETURNED/sender receipt |
| Giá/ownership/tracking | PASS |Quote từDB/snapshot/idempotent, phí không đổi; owner200/other404/GuestID401/public privacy |
| Register/SMTP/OTP/login | PASS |SMTP loopback thật, inbox Admin H2, UNVERIFIED/wrong/reuse bị chặn; chỉ OTP đúng mới ACTIVE/login |
| WebSocket UI | PASS |Phiên User/Manager độc lập, MESSAGE và DOM mới không reload; other không được nhận/mở; offline/backoff/reconnect lấy trạng thái lưu |
| Role pages/Auth/support/Admin | PASS trong suite |Guest/User/Manager/Shipper/Admin, CSRF/logout/403, support message/private marker, reports/CSV response/audit page |
| Visual smoke | PASS giới hạn |Tracking1366×768 và390×844 đã đọc ảnh; không gọi mọi viewport/a11y PASS |
| Targeted regression | PASS |80/80 gồm SMTP/H2/mailbox ACL, secret-report guard và57 release/parser cases |
| Full clean test | PASS |1.470tests/1.469passed/0failure/error/1skip,3:09,kết thúc21:01:50 |
| Full verify/JAR | PASS |Cùng1470tests,3:18,kết thúc21:05:49; Surefire XML khớp |
| JavaScript / backend WebSocket | PASS |JS12/12; WebSocket17/17; UI WS có bằng chứng riêng ở trên |
| Runtime secrets trong report | PASS |Guard kiểm password/OTP thực trước serialize; không raw frames/token/cookie/HAR |
| JAR isolation | PASS |13migration source-identical; không H2/DevTools/SMTPfake/mailbox/browser tools/SQL QA tooling/test credentials |
| Diff/format | PASS |git diff --check; parse hai script; UTF-8/whitespace/links kiểm riêng |
| Migration review | PASS source-only |7file chỉ thêm15GO; không thay hash từ đầu lượt; checksum risk bên dưới |
| SQL Server giai đoạn này | NOT RUN — đúng giới hạn |Không kết nối/SELECT/write/migrate/seed hai DB; giữ bằng chứng trước, không lấy H2 thành SQL test |
| Presenter Chrome rehearsal | RECOMMENDED |Chạy [demo-script](demo-script.md) trước bảo vệ; chưa tuyên bố người trình bày đã bấm tay |
| Đóng Commit22 theo scope đồ án | RECOMMEND |Không còn blocker kỹ thuật đã biết trong phạm vi demo; người dùng review/Git thủ công |

Skipped duy nhất UteexpressApplicationTests.contextLoads là SQL opt-in cũ, giữ false vì SQL không được phép dùng ở lượt này.
Không thêm skipped để che lỗi. Maven metadata repository401/cache, Mockito agent và unchecked test warnings không làm build fail.

## Trình diễn trực tiếp

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8081 -TestMail
~~~

Password qua secure prompt, không giá trị cố định. Chrome http://127.0.0.1:8081/.
User ở cửa sổ thường, actor vận hành ở Incognito, logout/login khi đổi actor; nhiều Incognito cùng profile chia sẻ session.
Inbox SMTP test /admin/demo-mailbox chỉ Admin; register dùng email mới @example.test. Không SMTP cá nhân.
Chuẩn bị dependencies trước buổi trình diễn. Restart H2 mất dữ liệu theo thiết kế; không reset/đụng database SQL thật.
Không cần SQL demo seed để đáp ứng scope một laptop.

## Migration/checksum

Bảy fileV3/V4/V8/V9/V10/V11/V12 có15GO từ các lượt được duyệt trước; lượt này không sửa thêm.
Không đổi ý nghĩa SQL, constraint, index hoặc seed. ReleaseSafetyTests kiểm parser và old/new checksum.
Bảng checksum đầy đủ ở [browser-qa.md](browser-qa.md).

Database đã SUCCESS checksum cũ sẽ bị Flyway validate chặn với source đã vá. Không tự repair/history edit/clean/baseline.
Cần quy trình chủ dự án duyệt sau khi đối chiếu history/schema từng môi trường. Xác nhận người dùng trước đây chỉ bao phủ một máy:
UTEExpressDBV1/V2; UTEExpress_Test đãV1–V13 bản vá. Không truy vấn lại các DB lượt này.
SQL upgrade có dữ liệu chưa được coi PASS, nhưng không chặn H2 demo không Flyway.

## Ngoài scope demo — không dùng để mở rộng Commit22 vô hạn

Production chưa sẵn sàng: SMTP thật/deliverability, HTTPS/cookie/proxy, upgrade có dữ liệu và backup/restore,
load/SQL concurrency nhiều instance, broker phân tán, triển khai/quyền vận hành. Cloud/hosting/tên miền không được yêu cầu.
Mọi viewport/a11y, mọi nhánh nhập liệu, cửa sổ Save/Excel và mọi tình huống mất mạng chưa được nghiệm thu toàn diện.

SQL V1–V13/Flyway/Hibernate/business smoke/JAR startup đã PASS ở lượt trước; xem [SQL validation](sql-server-validation.md).
Không chạy lại SQL hoặc SQL JAR startup trong phase H2 này. Không production business logic/JS/schema/dependency/default config change ở lượt này.
Không git add/commit/push/merge/stash/PR, không Commit23.
