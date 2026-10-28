# Cập nhật Commit 23 — Chrome headless / H2

Kết quả mới nhất: **365 PASS / 0 FAIL / 0 BLOCKED**, 2026-10-28T16:02:51Z. Maven clean verify: **1.492 passed, 1 skipped, 0 failure/error**. Có User mới register/OTP → hồ sơ/địa chỉ → tạo vận đơn; 2 logistics journeys; WebSocket hai phiên và reconnect. Lỗi checkbox unchecked đã được browser phát hiện, tái hiện regression FAIL trước patch và sửa; chi tiết [QA Commit 23](final-website-qa.md). Không SQL Server hoặc Computer Use trong lượt này. SQL demo bền vững/persistence vẫn BLOCKED chờ cấp phép, không suy ra PASS từ H2. Nội dung bên dưới là lịch sử QA Commit 22/dropdown trước đó.

---

# Commit 22 — Nghiệm thu demo một laptop Windows

## Cập nhật dropdown sau nghiệm thu Commit22

28/10/2026, main/017a4af: đã xử lý phản hồi rỗng và bổ sung regression từ tài khoản mới đăng ký đến lookup/quote/create, dùng cả input Núi Thành do người dùng cung cấp. **Browser mới358PASS/0FAIL/0BLOCKED**, verify1.472tests/1.471passed/1skip/0failure/error. Xem [phân tích nguyên nhân, vùng phục vụ và file sửa](branch-dropdown-qa.md). JSON liên kết bên dưới nay là lượt358case; các số350, ID và Git manifest trong phần báo cáo cũ dưới đây là mốc lịch sử trước bản sửa này.


Branch feature/team-final-qa-demo-release, HEAD 4e71056. Phạm vi đã chốt: Google Chrome trên một laptop, H2 memory riêng, loopback; không cloud/hosting/tên miền hoặc truy cập máy khác.

**Browser Chrome headless thật: 350 PASS / 0 FAIL / 0 BLOCKED** ở lượt cuối 28/10/2026 21:05:22 UTC+7 (2026-10-28T14:05:22Z), runtimeSecretCheck=PASS. Kết quả chi tiết ở [JSON](browser-qa-results.json).
Số trên là từng kiểm tra trang/thao tác trong cùng lượt có state, không phải350 hành trình độc lập. Không dùng Computer Use hoặc MockMvc thay browser.

## Ba gate thiếu đã được thực thi

| Gate | Bằng chứng thực thi |
|---|---|
| Logistics nhất quán | Hai vận đơn HOME_PICKUP mới từ quote/confirm UI, theo cùng tracking đến DELIVERED hoặc RETURNED; không thay bằng vận đơn seed |
| SMTP/OTP | Đăng ký → SMTP loopback thật → inbox Admin test → sai OTP bị chặn → OTP nhận được kích hoạt → login USER → mã dùng lại bị chặn |
| WebSocket UI | Manager đổi trạng thái, inbox owner ở phiên độc lập nhận MESSAGE và hiện notice không reload; other không nhận và không mở được notice đó; offline/reconnect lấy lại trạng thái đã lưu |

Mọi mutation logistics dùng form Thymeleaf thật với CSRF/version/token; không gọi service hoặc SQL trực tiếp từ runner.
Không sửa production business logic, entity, enum, schema, kiến trúc WebSocket hoặc JavaScript để làm các bước đạt.
Fixture LocalDemoSeed đã có đủ5 điểm/6 tuyến đi-ngược; không cần bổ sung fixture. Guard H2/idempotency cùng context giữ nguyên.

## Hai hành trình đầu-cuối

Lượt quan sát có shipment21 và22; ID phải lấy lại từ UI sau mỗi restart, không dùng số đó làm cấu hình.

1. Owner báo giá HOME_PICKUP, kiểm52.500 VND từ rule DB, xác nhận và xác nhận lại không nhân đôi.
2. Manager nguồn xác nhận/phân công đúng courier; courier lấy thực tế với reference, yêu cầu bàn giao.
3. Manager độc lập xác nhận biên nhận nguồn. Không suy từ phân công/lấy hàng thành custody chi nhánh.
4. Gom cả hai kiện cùng manifest: Đà Nẵng → Hub miền Trung → Hub phía Nam → chi nhánh TP.HCM.
   Mỗi leg có create/manifest/seal/depart/arrive/biên nhận đúng từng tracking/close; đổi đúng Manager giữa hai đầu.
5. Tại chi nhánh đích, phân công và bàn giao DELIVERY, courier accept/start.
6. Kiện thứ nhất complete với người thực nhận/reference, Manager xem proof đã lưu, trạng thái DELIVERED.
7. Kiện thứ hai fail ABSENT, Manager quyết định hoàn, courier yêu cầu thu hồi và Manager nhận lại thực tế.
8. Đi ba chặng ngược TP.HCM → Hub phía Nam → Hub miền Trung → Đà Nẵng, đủ biên nhận/close mỗi leg.
9. Manager nguồn bàn giao cho người gửi/đại diện có sender receipt, mới RETURNED.
10. Kiểm lịch sử đi/hoàn và chứng từ; owner xem detail, phí snapshot giữ nguyên; other404, Guest ID401,
    tracking công khai đúng kết quả và không lộ phone/reference chứng từ nội bộ.

Các flow cũ Guest/BRANCH_DROPOFF/User/support/message/notifications/Manager/dashboard/Shipper/Admin/CSV/audit vẫn chạy trong cùng suite.
CSV response attachment/header/body được kiểm trong browser, chưa nghiệm thu hộp thoại Save/Excel của hệ điều hành.

## SMTP giả lập và cách nhận OTP khi trình diễn

Chỉ src/test có DemoSmtpServer, DemoMailConfiguration và DemoMailboxController.
Bật tường minh bằng -TestMail ở H2 Demo; mặc định tắt. DataSource phải là H2 memory thuộc prefix do entry point tạo trước khi mở SMTP.
SMTP bind127.0.0.1 cổng ngẫu nhiên, không AUTH/relay, chỉ nhận địa chỉ @example.test; không có SMTP username/password.
SmtpOtpMailSender production, hash OTP, TTL, sai mã, single-use và trạng thái UNVERIFIED vẫn được dùng bình thường.

Thư chỉ trong bộ nhớ, tối đa50, tự hết hạn hiển thị sau5phút, mất khi dừng process. Không file mail/OTP log.
Inbox /admin/demo-mailbox dùng chính session/ROLE_ADMIN, Guest401, USER403, Cache-Control:no-store, UTF-8 và escape recipient.
Không bypass OTP/tự kích hoạt. Người trình bày dùng Admin ở cửa sổ riêng để đọc mã rồi nhập vào form verify của khách.
Không có fake SMTP/controller/inbox/test credential trong production JAR.

Automation dùng BrowserContext riêng cho Admin inbox; OTP chỉ vào biến RAM và input verify, không ghi case detail/JSON.
Guard report từ chối serialize nếu có password/OTP thực vừa dùng; không giữ hoặc in raw STOMP frames chứa CSRF.

## WebSocket UI thực tế

Tạo riêng một vận đơn mới cho owner. Dùng BrowserContext Chrome độc lập cho owner và other, bên cạnh phiên Manager.
Hai user nhận CONNECTED thật. Khi Manager confirm, owner nhận MESSAGE và DOM có đúng tracking/title mới trong15giây,
timeOrigin của document không đổi; không đợi polling60giây và không reload.
Other không có nội dung tracking; gọi trực tiếp /notifications/{id}/open trả404.

Cho riêng target owner offline, đóng socket; Manager cancel trong lúc owner mất kết nối, chờ quá nhịp worker3000ms.
Khôi phục mạng target: production backoff/reconnect nối lại và reconcile DB, UI hiện “Đã hủy” mà timeOrigin không đổi.
Instrumentation chỉ nằm trong DevTools test: giữ reference socket để ngắt kết nối và đếm CONNECTED/MESSAGE; không thay source JS/kiến trúc ứng dụng.
Không lưu payload/token/cookie/HAR. Lỗi network dự kiến khi chủ động offline không bị gọi là lỗi JS nghiệp vụ.
Tham khảo giao thức chính thức [Target](https://chromedevtools.github.io/devtools-protocol/tot/Target/) / [Network](https://chromedevtools.github.io/devtools-protocol/tot/Network/).

## Chạy lại và trình diễn trực tiếp

Chuẩn bị Java21/JAVA_HOME, Google Chrome sẵn có, dependency cache; không cài extension/browser driver.
Từ repository, chuẩn bị trước buổi trình diễn:

~~~powershell
.\mvnw.cmd -DskipTests test-compile dependency:build-classpath "-Dmdep.outputFile=target/release-tools-classpath.txt"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\browser-qa.ps1
~~~

Automation cần cổng18082/19222 trống, không tự dừng process đang chiếm cổng. Tự tạo Chrome profile TEMP,
password ngẫu nhiên chỉ RAM, H2 mới đủ18scenario; cuối lượt dừng đúng các process nó tạo.
Return code khác0 khi FAIL/abort; vẫn phải đọc JSON, không suy từ code0 thành kiểm hết mọi trường hợp.
Bypass chỉ trong process PowerShell, không đổi execution policy hệ thống.

Để xem website thật bằng Chrome có cửa sổ, dùng command dành cho presenter:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8081 -TestMail
~~~

Script hỏi password demo bằng secure prompt, không password cố định. Mở http://127.0.0.1:8081/.
Không chạy JAR/default profile để thay lệnh này: mode demo nằm test classpath và không dùng Flyway/SQL Server.
Hai cửa sổ đủ dùng: cửa sổ thường giữ User; một Incognito cho actor vận hành, logout/login mỗi lần đổi actor.
Nhiều Incognito của cùng profile dùng chung session; không coi chúng là các phiên độc lập.
Account owner/other, manager/hub-origin-manager/hub-dest-manager/destination-manager, pickup-courier/shipper/admin:
email dạng username@example.test; password là giá trị người trình bày tự đặt. Register dùng email mới @example.test.

Xem [kịch bản trực tiếp](demo-script.md) và [setup](demo-setup.md). Start server/chuẩn bị dependencies trước buổi bảo vệ.
Restart tạo H2 mới, không giữ dữ liệu phiên trước; không cần SQL demo seed hoặc reset database.

## Build và giới hạn chứng cứ

Targeted regression80/80 PASS: mail SMTP thật/H222cases, report secret guard1case + ReleaseSafetyTests57cases (tổng80).
Full clean test PASS:1.470tests/1.469passed/0failures/errors/1skipped,3:09,kết thúc21:01:50 UTC+7.
Verify PASS:1.470tests/1.469passed/0failures/errors/1skipped,3:18,kết thúc21:05:49 UTC+7. Surefire XML khớp; skipped duy nhất UteexpressApplicationTests.contextLoads (SQL opt-in giữ false). JavaScript12/12 và backend WebSocket17/17 PASS.
JAR13migration source-identical, không H2/DevTools/SMTPfake/mailbox/browser/test/SQLQA tooling. Runtime report guard PASS, static high-confidence secret scan13file code/result không phát hiện key/private-key; không đọc SQL DPAPI.
Lượt browser cuối không abort,0 uncaught JS/console.error/HTTP5xx. Temp evidence: uteexpress-browser-evidence-4e2a92be0a144e24a8e9e14a22d5596e; hai ảnh tracking1366/390 lưu tại đó. Log %TEMP%/uteexpress-c22-laptop-browser-final.log; full Maven uteexpress-c22-laptop-clean-test.log / uteexpress-c22-laptop-verify.log.
Skipped cũ là SQL opt-in contextLoads, giữ false; không thêm skipped để che lỗi.

Lượt này không kết nối/SELECT/migration/ghi UTEExpressDB hoặc UTEExpress_Test; không đọc DPAPI.
SQL validation trước đó là bằng chứng riêng, không chạy lại và không lấy H2 thành kiểm thử SQL Server.
Không git add/commit/push/merge/stash/PR hoặc Commit23.

## Migration audit — không sửa thêm trong lượt laptop

So HEAD, bảy file chỉ thêm15 dòng GO, không có dòng SQL bị xóa/đổi. SHA256 vẫn khớp đầu lượt này.
ReleaseSafetyTests kiểm parser/batch/checksum và khôi phục checksum gốc khi bỏ GO.

| Version | Số GO thêm | Checksum HEAD cũ | Checksum source đã vá |
|---|---:|---:|---:|
| V3 |3|-68259337|-1720389847|
| V4 |1|-2103371131|-1466061241|
| V8 |3|-1022568645|2036200466|
| V9 |3|1441625975|61067824|
| V10 |2|518542243|236607534|
| V11 |2|275192352|2051557719|
| V12 |1|-1357082083|-1057022707|

**Rủi ro checksum**: database từng SUCCESS với source cũ sẽ bị Flyway validate từ chối khi dùng bản vá.
Không tự repair/history edit/clean/baseline. Phải đối chiếu history và schema từng môi trường, được chủ dự án duyệt quy trình tương thích trước nâng cấp.
Trong phạm vi người dùng đã xác nhận: DB ứng dụng chỉV1/V2, DB test đã chạy bản váV1–V13, không có môi trường khác đã biết.
Lượt này chỉ kiểm source/parser, không truy vấn lại database. Rủi ro upgrade không có dữ liệu thử đủ vẫn thuộc production, không chặn H2 demo không Flyway.

## Quyết định trong phạm vi

**DEMO-READY một laptop Windows/Chrome/H2: PASS. Khuyến nghị đóng Commit22 sau review và thao tác Git thủ công.** Không còn blocker trong scope demo đã chốt; không triển khai Commit23.
Production: NOT READY — SMTP thật, HTTPS/cookie/proxy, SQL upgrade có dữ liệu/backup-restore, load/concurrency nhiều instance,
broker/deployment và quyền vận hành thực cần nghiệm thu riêng; cloud/hosting/tên miền không thuộc đồ án hiện tại.
Headed Chrome/presenter rehearsal, mọi viewport/a11y, mọi nhánh nhập liệu và hộp thoại Save/Excel chưa được gọi PASS.
Đây là giới hạn test/việc chuẩn bị người trình bày, không mở rộng vô hạn hoặc giữ Commit22 vì yêu cầu cloud không có trong scope.

Danh sách file thay đổi và Git status ở phần dưới; bảy migration và production source đã modified từ các lượt trước, không sửa thêm lượt laptop.

## File thay đổi và Git status cuối lượt

Lượt laptop tạo **8 file**, sửa **12 file hiện hữu**, xóa **0 file repository**. Các file production và bảy migration có diff từ lượt trước được giữ nguyên hash; toàn working tree so HEAD có **44 file =14 tracked modified +30 untracked**, không staged/deleted.

File mới (đều src/test):

- src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java
- src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java
- src/test/java/com/ute/uteexpress/operations/BrowserQaSafetyTests.java
- src/test/java/com/ute/uteexpress/operations/BrowserWebSocketJourney.java
- src/test/java/com/ute/uteexpress/operations/DemoMailConfiguration.java
- src/test/java/com/ute/uteexpress/operations/DemoMailboxController.java
- src/test/java/com/ute/uteexpress/operations/DemoSmtpServer.java
- src/test/java/com/ute/uteexpress/operations/LocalDemoMailTests.java

File hiện hữu sửa trong lượt:

- README.md
- docs/browser-qa-results.json
- docs/browser-qa.md
- docs/demo-script.md
- docs/demo-setup.md
- docs/final-qa-report.md
- docs/release-checklist.md
- docs/sql-server-validation.md
- scripts/browser-qa.ps1
- scripts/release-tools.ps1
- src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java
- src/test/java/com/ute/uteexpress/operations/LocalDemoApplication.java

Git status đầy đủ so HEAD (bao gồm thay đổi từ lượt trước):

~~~text
 M src/main/java/com/ute/uteexpress/config/SecurityConfig.java
 M src/main/java/com/ute/uteexpress/controller/AuthController.java
 M src/main/resources/db/migration/V10__add_shipment_notifications.sql
 M src/main/resources/db/migration/V11__add_operations_tickets.sql
 M src/main/resources/db/migration/V12__add_customer_support_portal.sql
 M src/main/resources/db/migration/V3__extend_shipper_identity_and_shipment_lifecycle.sql
 M src/main/resources/db/migration/V4__add_pickup_pricing_and_shipment_quotes.sql
 M src/main/resources/db/migration/V8__add_last_mile_delivery_and_returns.sql
 M src/main/resources/db/migration/V9__add_service_catalog_and_pricing_management.sql
 M src/main/resources/templates/manager/shipments.html
 M src/main/resources/templates/shipments/fragments.html
 M src/main/resources/templates/shipments/history.html
 M src/test/java/com/ute/uteexpress/security/SecurityConfigTests.java
 M src/test/java/com/ute/uteexpress/security/UserShipmentControllerTests.java
?? README.md
?? docs/browser-qa-results.json
?? docs/browser-qa.md
?? docs/demo-script.md
?? docs/demo-setup.md
?? docs/final-qa-report.md
?? docs/release-checklist.md
?? docs/sql-server-validation.md
?? scripts/browser-qa.ps1
?? scripts/release-tools.ps1
?? scripts/set-sql-qa-credential.ps1
?? src/main/java/com/ute/uteexpress/config/FormBindingAdvice.java
?? src/main/resources/application-release.properties
?? src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java
?? src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java
?? src/test/java/com/ute/uteexpress/operations/BrowserQaSafetyTests.java
?? src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java
?? src/test/java/com/ute/uteexpress/operations/BrowserWebSocketJourney.java
?? src/test/java/com/ute/uteexpress/operations/DemoMailConfiguration.java
?? src/test/java/com/ute/uteexpress/operations/DemoMailboxController.java
?? src/test/java/com/ute/uteexpress/operations/DemoSmtpServer.java
?? src/test/java/com/ute/uteexpress/operations/LocalDemoApplication.java
?? src/test/java/com/ute/uteexpress/operations/LocalDemoMailTests.java
?? src/test/java/com/ute/uteexpress/operations/LocalDemoQaTests.java
?? src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java
?? src/test/java/com/ute/uteexpress/release/ReleaseSafetyTests.java
?? src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java
?? src/test/java/com/ute/uteexpress/release/SqlServerQaTool.java
?? src/test/resources/demo-local.properties
?? src/test/resources/sqlserver-qa.properties
~~~

Entry point trình diễn cũng đã smoke thật: release-tools -Mode Demo -Port18083 -TestMail, seed18, login/session/Admin inbox200/no-store; chỉ H2. Process riêng đã dừng khi xong. Log %TEMP%/uteexpress-c22-presenter-demo.log.
Secret check:13file code/result không có high-confidence key/private-key;3config đóng gói không có cùng pattern; report runtimeSecretCheck=PASS. Đây là kiểm tra có phạm vi cụ thể, không chứng nhận quét mọi loại secret có thể có. Không đọc credential DPAPI.
Format/script/link checks được chốt cuối; cảnh báo LF→CRLF theo Git config, Maven metadata401/cache và Mockito/unchecked warnings không phải failed tests.
