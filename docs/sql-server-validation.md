# Commit 22 — SQL Server validation

## Nghiệm thu laptop tiếp theo — không truy cập SQL Server

28/10/2026,21:05 UTC+7: Chrome headless thật/H2 đạt350PASS/0FAIL/0BLOCKED; [báo cáo](browser-qa.md).
Hai hành trình logistics nhất quán, SMTP/OTP local test-only và WebSocket UI/reconnect đã đạt; DEMO-READY cho một laptop.
Full clean test/verify1.470tests/1.469passed/0failure/error/1skipped, JS12/12, backendWS17/17; JAR13migration khớp, không test/demo tooling.
Lượt này không import DPAPI, không JDBC/SELECT/migration/write/seed hai database SQL. Không đổi production business code/schema/default config.
Bảy migration giữ nguyên SHA256 đầu lượt, diff so HEAD chỉ15GO; bảng checksum/rủi ro với DB áp dụng source cũ ở browser-qa.md.
SQL validation/smoke/startup JAR dưới đây là kết quả các lượt trước, không tuyên bố tái SQL validate/startup sau các bổ sung H2/browser.
Production upgrade có dữ liệu, backup/restore/TLS/load/SMTP thật còn scope riêng, không chặn demo H2 không Flyway.

## Kết quả mới nhất — V1–V13, Flyway, Hibernate và SQL smoke PASS

SQL cập nhật **28/10/2026, 14:20–14:49 UTC+7**, branch feature/team-final-qa-demo-release, HEAD 4e71056.
**V12 PASS; V13 PASS; đủ V1–V13 SUCCESS/checksum khớp; Flyway validate PASS; Hibernate validate PASS;
SQL business smoke và MockMvc/Thymeleaf trên SQL Server PASS.** Database có 42 bảng, không coi là DB trống.
JAR thật/release profile + HTTP loopback PASS ở lượt SQL trước; browser SQL/upgrade có dữ liệu chưa nghiệm thu. Browser H2 cuối đã PASS trong scope laptop.
Demo/release readiness chưa đạt chỉ từ các PASS này; production readiness NOT READY.

Chỉ kết nối SQL Authentication uteexpress_qa qua DPAPI do người dùng cấp tới 127.0.0.1:1433 / UTEExpress_Test.
Đã xác minh local SQLEXPRESS, login, JDBC catalog và DB_NAME() đều đúng; db_owner/DDL/metadata permissions đạt.
Không dùng sa/fallback, không ghi UTEExpressDB, không thay cấu hình mặc định/pom, không clean/repair/reset hoặc SQL demo seed.
Người dùng xác nhận SSMS UTEExpressDB chỉ V1/V2, không môi trường quản lý khác áp dụng V12 cũ; dự án một máy.
Đó là xác nhận người dùng, không phải agent kết nối lại database ứng dụng.

## V12: root cause, patch, checksum và parser

V12__add_customer_support_portal.sql vào Git ở 9ee9612. Log runtime trước vá (28/10 13:45:43):
SQLState=S0001/errorCode=207, actual messages Invalid column name 'request_token'. / Invalid column name 'request_fingerprint'.
V12 dòng gốc 21 ADD hai cột cho support_ticket_messages; CK_support_ticket_messages_request dòng 23 và
filtered unique index UX_support_ticket_messages_request dòng 25 dùng chúng ngay trong cùng batch.
Hai FK của support_ticket_routes không dùng hai cột này. V12 không INSERT/UPDATE/seed/backfill.
Preflight SELECT: history V1–V11 đúng checksum, 41 bảng; không route table/hai cột message hoặc named
constraints/defaults/keys/index V12 áp dụng dở, event action CHECK V11 retained; không lỗi typo/tên cột.

Chỉ thêm **một GO sau dòng gốc 21**; không đổi SQL nghiệp vụ/kiểu/cột/constraint/index.
Checksum **-1357082083 → -1057022707**; SQLServerParser thực **1 → 2 batch**, bắt đầu dòng 1/24,
cả hai canExecuteInTransaction=true. Bỏ GO khôi phục source/checksum gốc; diff Git đúng một dòng thêm.
GO là client delimiter, không tự COMMIT. V1–V11/V13 giữ nguyên SHA-256 so đầu lượt.
Người dùng xác nhận không DB trong phạm vi quản lý cần checksum V12 cũ; không dùng repair để che xung đột.
Target V12 **PASS 14:23:17**, đúng một migration; success=true/checksum=-1057022707.
Schema/data hậu kiểm: support_ticket_routes 4 cột/identity, 2 cột VARCHAR(32)/(64) nullable trên messages;
2 trusted NO ACTION FK/đúng cột đích, CHECK request + action được mở rộng, PK/UQ, DEFAULT active=1;
3 index đúng thứ tự cột/unique/filter. Route/messages không seed/backfill, baseline không có dữ liệu legacy.

## V13 và nghiệm thu schema

V13 giữ nguyên source/checksum **1007916434**, một transactional batch chỉ CREATE INDEX trên cột đã có.
Preflight xác minh V1–V12 đúng checksum, schema V12 và 10 index V13 chưa có.
Lần gọi ban đầu bị verifier V11 cũ đòi đúng số cột message trước V12, dừng trước target V13;
đã sửa preflight target13 theo contract V12 hiện tại, không bỏ history/schema guards. Không phải migration V13 FAIL.
Target V13 **PASS 14:24:39**, một migration; 10 index report/audit đúng tên, parent, thứ tự cột/ASC-DESC.
History đủ 13 version success=true/checksum đúng source; Flyway validateWithResult PASS.
Hibernate 7.2.24.Final **ddl-auto=validate PASS 14:24:42** trên Microsoft SQL Server 16.0, JDBC 13.2.1.
Metadata sys.* lúc validate: 153 foreign_keys, 144 check_constraints, 70 key_constraints, 359 indexes
(đây là catalog counts, không diễn giải thành số index nghiệp vụ độc lập).

## SQL business smoke — PASS, fixture luôn rollback

Công cụ test-only SqlServerBusinessSmoke, không tự chạy trong default Maven/JAR. Mode SqlSmoke có guard
URL/instance/login/catalog/history V1–V13 và write attestation. Context dùng DataSource đã kiểm tra,
sqlserver-qa.properties, Hibernate validate, Flyway off (đã validate bằng tool), worker off và SMTP loopback.
Không gọi LocalDemoSeed/LocalDemoApplication, không seed demo. Test fixture tổng hợp trong transaction bắt buộc rollback;
so sánh row count **mọi bảng dbo** trước/sau đều khớp, kể cả khi test lỗi. SQL identity counters có thể tăng dù rollback;
không reseed/reset identity. Role lấy từ dữ liệu đã seed, không thêm role hoặc quyền.

Lần hoàn chỉnh **14:37:59–14:38**, các bước PASS qua service/repository thật:
- Authentication bằng BCrypt và role DB; HOME_PICKUP có phụ phí từ fixture DB, BRANCH_DROPOFF phụ phí 0;
  quote confirm lặp trả cùng shipment, thành phần phí snapshot.
- Manager confirm/assign pickup, shipper collection và branch handoff/receipt thực.
- Ba chặng COLLECTION/LINEHAUL/DISTRIBUTION, manifest/depart/arrive/physical receipt/close;
  arrival chưa tự tạo custody; enum VARCHAR round-trip và candidate query DRAFT chạy ở cả forward/reverse.
- Delivery proof/DELIVERED; thất bại ABSENT, recovery receipt, return request, ba chặng ngược và sender receipt/RETURNED.
- Support customer routing từ bảng route, tạo ticket/reply, retry message cùng token chỉ một dòng (V12 filtered index).
- Notification processor logic + SQL inbox/query/visibility. Processor được gọi unwrapped trong transaction rollback;
  **không xác nhận REQUIRES_NEW/worker retries/concurrency hoặc SQL-to-WebSocket end-to-end**.
- Mọi dataset AdminReportService, AdminAuditService UNION, AuditLog NVARCHAR(MAX) tiếng Việt >5.000 ký tự round-trip.
- MockMvc trên full SQL context: Guest tracking, login CSRF, BCrypt/session fixation, User Thymeleaf 200,
  RBAC 403, logout CSRF và session invalidation. Đây là servlet integration, chưa browser/network JAR.
- Commit20 LOG-20-01: origin/destination capability probe + arrive/receipt các chặng không gây rollback-only.
  LOG-20-02: real candidate query với TransportLeg trên SQL/Hibernate7 không lỗi binding.
- Commit21: giữ cached Authentication/EntityManager, sửa trạng thái tài khoản/employee/branch và role của chính fixture
  bằng SQL trong cùng transaction; LogisticsAccessService refresh/recheck từ DB và từ chối đúng.
  **Chưa kiểm thử revocation race commit từ connection khác trên SQL Server.**

Các lỗi harness đã sửa: compile tên thuộc tính/signature gọi Entity/service; DevTools restart entry point,
BeanDefinition DataSource, và SecurityContext của MockMvc. Không đổi production Java/business rules để test pass.
Log cuối chỉ in nhãn bước/status, type/frame và error code/allowlisted message; không in raw exception/credentials.
SQL smoke PASS tổng thể, row count toàn bộ bảng sau rollback không đổi; không dữ liệu demo SQL được giữ lại.

## JAR thật, HTTP và hậu kiểm cuối — PASS

Mode SqlJar chạy sau verify và preflight đủ V1–V13/Flyway/Hibernate validate; JAR thực tế
target/uteexpress-0.0.1-SNAPSHOT.jar, profile release, kết nối ghim Test, server.address=127.0.0.1/port18081.
Đã loại ambient datasource/Flyway/config overrides khỏi child environment; credentials chỉ qua environment tạm,
không trong process arguments. SMTP loopback, notification workers off, Flyway validate-on-migrate/clean-disabled,
Hibernate validate. Cookie Secure=false chỉ cho HTTP loopback, không phải kiểm chứng TLS production.

**14:46 JAR startup/HTTP PASS**:
- 200 /login, /register, /tracking, /quote, /css/vendor/bootstrap.min.css.
- HTML thực có thẻ html; form login render CSRF.
- 401 Guest /user/shipments, /admin/users, /api/notifications.
- Không tài khoản demo được tạo để login qua network JAR; authenticated SQL tested qua MockMvc.
Tiến trình JAR do tool tạo được dừng sau kiểm tra, không dừng process khác. Log TEMP đã redact SQL password.
Không browser E2E/JS tương tác/UI responsive hoặc notification network pipeline end-to-end.

JAR isolation PASS: release profile có; 13 migration SHA-256 khớp source; quét config/private-key patterns cho 0 literal-secret findings (không in giá trị); không H2/DevTools/demo seeder/test tooling/
sqlserver-qa.properties/QA credential files. Không đặt credential vào Git.
Preflight trước JAR xác nhận toàn bộ dbo FK/CHECK enabled/trusted, không disabled user-table indexes.
SELECT hậu kiểm cuối: đủ V1–V13 đúng checksum, 42 bảng; tổng business rows ngoài roles/history=0,
seeded roles=4. Không demo data SQL lưu lại; schema history được bảo toàn sau smoke và JAR.

Full regression: clean test 2:49, verify 2:38, mỗi lượt 1.462 tests/1.461 passed/0 failures/errors/1 skipped.
JavaScript12/12, WebSocket17/17 PASS. Không cộng số tests qua các lượt hoặc biến skipped SQL test cũ thành PASS.
Warnings: release profile vẫn open-in-view enabled mặc định (chỉ báo cáo, không đổi cấu hình ngoài phạm vi);
Maven metadata HTTP401/cache, Mockito/ByteBuddy dynamic agent Java21 và unchecked test compilation không làm build fail.
Demo readiness BLOCKED vì chưa browser rehearsal và chưa được phép SQL demo dataset; production NOT READY.
## Chạy lại kiểm tra opt-in, không migration mới

Chỉ dùng phạm vi Test đã được xác nhận. Các mode từ chối DB khác/login khác/history không khớp.
Không chạy SqlFresh hoặc target cũ trên database đã lên V13.

~~~powershell
$env:UTE_QA_SQLSERVER_DATABASE = 'UTEExpress_Test'
$env:UTE_QA_SQLSERVER_APPROVED_DATABASE = 'UTEExpress_Test'
$env:UTE_QA_SQLSERVER_INSTANCE = 'SQLEXPRESS'
$env:UTE_QA_SQLSERVER_URL = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_Test;encrypt=true;trustServerCertificate=true;loginTimeout=10;socketTimeout=20000'
$qaCredential = Join-Path $env:TEMP 'uteexpress-sql-qa.credential.xml'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .scriptselease-tools.ps1 -Mode SqlInspect -CredentialFile $qaCredential
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .scriptselease-tools.ps1 -Mode SqlValidate -CredentialFile $qaCredential
# Chỉ khi được phép ghi fixture trong transaction luôn rollback:
$env:UTE_QA_SQLSERVER_ALLOW_MIGRATE = 'YES'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .scriptselease-tools.ps1 -Mode SqlSmoke -CredentialFile $qaCredential
# Chạy Maven verify trước; chọn port loopback chưa có process khác dùng:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .scriptselease-tools.ps1 -Mode SqlJar -Port 18081 -CredentialFile $qaCredential
~~~

SqlSmoke không tự chạy trong Maven default. SqlJar không dùng password argument và dừng process riêng sau HTTP checks.
Mọi lệnh vẫn cần DPAPI của Windows account hiện tại; không gửi password/log thô vào chat hoặc Git.
## Schema history hiện tại

| Version | Trạng thái | Checksum history |
|---|---|---|
| V1 | SUCCESS, giữ nguyên | 189739357 |
| V2 | SUCCESS, giữ nguyên | 1191373203 |
| V3 | SUCCESS, giữ nguyên | -1720389847 |
| V4 | SUCCESS, giữ nguyên | -1466061241 |
| V5 | SUCCESS, giữ nguyên | 1925825129 |
| V6 | SUCCESS, giữ nguyên | -1705174727 |
| V7 | SUCCESS, giữ nguyên | 444247411 |
| V8 | SUCCESS, giữ nguyên | 2036200466 |
| V9 | SUCCESS, giữ nguyên | 61067824 |
| V10 | SUCCESS, giữ nguyên | 236607534 |
| V11 | SUCCESS, giữ nguyên | 2051557719 |
| V12 | SUCCESS sau một GO | -1057022707 |
| V13 | SUCCESS, source giữ nguyên | 1007916434 |

Clean test mới: 1.462 tests/1.461 passed/0 failures/errors/1 skipped, BUILD SUCCESS 2:49.
JavaScript 12/12 PASS. Verify BUILD SUCCESS 2:38, cùng 1.462/1.461 passed/0 failures/errors/1 skipped; Surefire XML tổng khớp, WebSocket17/17 PASS.
Skip SQL opt-in cũ được giữ tắt; không dùng case skipped đó làm bằng chứng SQL validation.
Bằng chứng mới ngoài Git trong TEMP: uteexpress-commit22-v12-preflight.log, -v12-targeted.log, -v12-target.log,
-v13-preflight-guard.log, -v13-target.log, -sql-business-smoke.log, -v12-clean-test.log.
Các phần bên dưới là lịch sử trước V12/V13 PASS, không thay thế trạng thái mới nhất phía trên.
## Lịch sử V11 — root cause và bằng chứng

File V11__add_operations_tickets.sql được đưa vào Git bởi 0306610.
Log cũ TEMP/uteexpress-commit22-v11-v13-migration.log ghi runtime SQLState=S0001/errorCode=207 và rollback,
với 12 cột: category, source, version, handling_branch_id, created_by, request_token, request_fingerprint,
resolved_at, closed_at, warning_due_at, source_ticket_event_id, related_ticket_id.
**Nguyên văn message/stack trace V11 không được giữ lại**: tool cũ chỉ xuất mã/cột nhằm tránh lộ secret.
Không giả lập message hoặc coi phân tích tĩnh là message runtime; không chạy lại V11 lỗi để lấy message.

V1 support_tickets chưa có 10 cột mới; V10 notifications chưa có hai cột ticket. Tên/kiểu ADD khớp Entity.
V11 gốc được SQLServerParser gửi như một batch bắt đầu dòng 3. Các câu tiêu thụ cột mới:
- CK_support_tickets_category/source/version dùng category/source/version.
- CK_support_tickets_managed dùng handling_branch_id, created_by, category, source, request_token, request_fingerprint.
- CK_support_tickets_resolved_time/closed_time/warning_time dùng resolved_at/closed_at/warning_due_at.
- CK_support_tickets_managed_state/managed_assignee và các index branch/warning/resolution cũng dùng cột mới.
- UX_support_tickets_request lọc WHERE request_token IS NOT NULL.
- CK_notifications_single_source và CK_notifications_ticket_context dùng source_ticket_event_id/related_ticket_id.
- UX_notifications_ticket_recipient lọc WHERE source_ticket_event_id IS NOT NULL; hai FK mới cũng tham chiếu cột vừa ADD.

Đây là lỗi compile/binding cột mới trong cùng SQL batch, không phải thiếu cột do schema lệch/typo:
SELECT trước vá chứng minh không partial V11; tên ADD/consumer đúng; parser một batch;
chỉ thay delimiter đã làm toàn migration thành công trên SQL Server. Độ chắc chắn cao.
Không có UPDATE/INSERT/backfill/seed trong V11. DROP CHECK trạng thái cũ là câu SQL nghiệp vụ có sẵn,
được giữ nguyên và thực hiện trong transaction, không phải DROP/reset database.

## Lịch sử patch V11, checksum và transaction

Thêm đúng hai dòng GO:
1. Sau dòng gốc 14: version BIGINT NOT NULL CONSTRAINT DF_support_tickets_version DEFAULT (0);
2. Sau dòng gốc 118: ALTER TABLE dbo.notifications ADD source_ticket_event_id BIGINT NULL, related_ticket_id BIGINT NULL;

Diff Git chỉ hai dòng thêm. Bỏ GO trả source/checksum gốc 275192352; source mới 2051557719.
Parser thực: 1 → 3 batch, bắt đầu dòng 3/17/122, cả ba canExecuteInTransaction=true.
GO là delimiter phía client/Flyway, không tự COMMIT; không đổi SQL nghiệp vụ/kiểu/FK/CHECK/index/seed.
V1–V10/V12/V13 giữ nguyên SHA-256 so đầu lượt.
Nếu một DB ngoài phạm vi xác nhận đã áp dụng checksum V11 cũ thì source mới sẽ xung đột; không dùng repair để che.
Phạm vi dự án người dùng đã xác nhận không có trường hợp đó.

ReleaseSafetyTests bổ sung parser/checksum/boundaries, exact history V11 và write consent; giữ các test cũ.
Chẩn đoán 207 chỉ lấy excerpt message thực khớp allowlist, không in raw exception/secret.
Targeted 53 safety + 11 core mapping = **64/64 PASS**, chạy lại sau sửa công cụ.

## V12: runtime mới và đề xuất riêng, chưa sửa

Lần đầu gọi SqlPending bị guard số bảng cố định của công cụ dừng trước “Starting approved pending target V12”.
Log này được giữ riêng ở TEMP/uteexpress-commit22-v12-preflight-guard.log.
Đã sửa kiểm tra số bảng theo history: tại V10/V11-preflight vẫn 38, sau V11 đúng 41, không bỏ kiểm tra.
Regression đạt lại trước lần gọi tiếp; lần này preflight và V11 metadata đều PASS.

**13:45:43 target V12 FAIL**, SQLState S0001 / errorCode 207, actual nested messages được trích xuất an toàn:
- Invalid column name 'request_token'.
- Invalid column name 'request_fingerprint'.

Flyway xác nhận rollback. Không retry/sửa V12, không chạy V13.
SELECT hậu kiểm: V1–V11 SUCCESS, không hàng V12/V13, 41 bảng; support_ticket_routes chưa tồn tại,
request_token/request_fingerprint trên support_ticket_messages chưa tồn tại, named constraints/defaults/keys/index V12 chưa tồn tại;
CK_support_ticket_events_action cũ còn trusted và chưa có CUSTOMER_MESSAGE/REOPEN_REQUESTED.

V12 dòng 21 ADD hai cột trên support_ticket_messages, sau đó CK_support_ticket_messages_request dòng 23
và UX_support_ticket_messages_request dòng 25 dùng chúng ngay trong cùng batch.
Parser source hiện tại: 1 transactional batch, checksum -1357082083.
**Chỉ đề xuất trong bộ nhớ**, chưa áp dụng: GO sau dòng gốc 21, 2 transactional batch (dòng 1/24),
checksum dự kiến -1057022707; strip GO trả SQL gốc. Cần xác minh phạm vi checksum V12 và phê duyệt riêng trước sửa/retry.
Không thay V12 bằng V14 hoặc clean/repair/reset.
V13 checksum 1007916434, một transactional batch/10 CREATE INDEX trên cột trước đó; chưa thấy nguy cơ ADD/consumer,
nhưng chưa chạy nên không tuyên bố PASS.

## Lịch sử schema history sau V11

| Version | Trạng thái | Checksum history |
|---|---|---|
| V1 | SUCCESS, giữ nguyên | 189739357 |
| V2 | SUCCESS, giữ nguyên | 1191373203 |
| V3 | SUCCESS, giữ nguyên | -1720389847 |
| V4 | SUCCESS, giữ nguyên | -1466061241 |
| V5 | SUCCESS, giữ nguyên | 1925825129 |
| V6 | SUCCESS, giữ nguyên | -1705174727 |
| V7 | SUCCESS, giữ nguyên | 444247411 |
| V8 | SUCCESS, giữ nguyên | 2036200466 |
| V9 | SUCCESS, giữ nguyên | 61067824 |
| V10 | SUCCESS, giữ nguyên | 236607534 |
| V11 | SUCCESS sau hai GO | 2051557719 |
| V12 | FAIL attempt/rollback; Flyway.info PENDING | Không có hàng |
| V13 | PENDING, chưa chạy | Không có hàng |

Log ngoài Git trong TEMP: uteexpress-commit22-v11-preflight.log, -v11-targeted.log, -v11-target.log,
-v11-inspect.log, -v12-preflight-guard.log, -v12-v13-migration.log, -v12-rollback.log.
Các mục bên dưới là lịch sử lượt trước, không thay thế trạng thái mới nhất phía trên.
## Lịch sử V10 — bản vá đã thực hiện 28/10, 13:01

File: src/main/resources/db/migration/V10__add_shipment_notifications.sql, đưa vào Git từ c6452c8.
Log runtime lần trước ở TEMP/uteexpress-commit22-v10-v13-migration.log ghi lỗi 207 / SQLState S0001
cho notification_assignment_status, audience, source_event_id và rollback. Chỉ giữ diagnostic mã/cột an toàn;
không lưu raw exception message hoặc một statement con duy nhất: Flyway đã gửi toàn file như một batch.

V1 tạo shipment_events/notifications chưa có những cột này; V10 ADD đúng tên/kiểu, sau đó tham chiếu ngay trong:
- CHECK CK_shipment_events_notification_assignment_status (dòng gốc 11).
- CHECK CK_notifications_audience (dòng gốc 29).
- Filtered unique index UX_notifications_source_recipient_audience, WHERE source_event_id IS NOT NULL (dòng gốc 31).

SQL Server compile các tham chiếu cột mới trong cùng batch gây 207; FK/index còn lại cũng được tách sau nhóm ADD tương ứng.
Không có UPDATE, ALTER COLUMN hay seed trong V10. Không có lỗi tên cột khác với Entity.
Nguyên nhân có độ chắc chắn cao: parser thực cho 1 batch, metadata trước vá không có cột, runtime khớp,
và chỉ thêm hai delimiter đã làm target V10 thành công trên cùng SQL Server.

Patch duy nhất: GO sau dòng gốc 10 (notification_assignment_status VARCHAR(30) NULL;) và 25
(delivery_attempts INT NOT NULL CONSTRAINT DF_notifications_delivery_attempts DEFAULT (0);).
Diff Git chỉ hai dòng thêm; bỏ hai GO khôi phục SQL gốc/checksum 518542243. Source mới checksum 236607534,
parser SQLServerParser chia 3 batch bắt đầu dòng 2/12/28, tất cả canExecuteInTransaction=true.
GO là delimiter Flyway/client, không commit transaction; V10 đã chạy như một migration transactional.
Không đổi SQL nghiệp vụ, kiểu, constraint, index, dữ liệu. V1–V9/V11–V13 byte-identical so đầu lượt.
Checksum mới chỉ phù hợp phạm vi đã xác nhận chưa áp dụng V10 cũ; không dùng repair để che xung đột.

ReleaseSafetyTests thêm regression parser/checksum/ranh giới và exact history V10; tổng targeted
50 release safety + 11 core mapping = **61/61 PASS**. SqlV10 là mode riêng, SqlPending chỉ bắt đầu V11 sau history V10 đúng checksum.
Test tooling và DPAPI launcher không nằm trong production JAR.

## Lịch sử V11 thất bại trước bản vá

Runtime thực lúc 13:02:29: SQLState S0001 / errorCode 207 trên category, source, version, handling_branch_id,
created_by, request_token, request_fingerprint, resolved_at, closed_at, warning_due_at,
source_ticket_event_id, related_ticket_id. Flyway báo “Changes successfully rolled back”.
Không retry V11, không sửa V11/V12/V13. Chỉ SELECT sau lỗi qua SqlInspect để xác nhận history/rollback.

Rà soát tĩnh để bàn giao (chưa áp dụng):
- V11 hiện 1 transactional batch, checksum 275192352. Nhóm ADD support_tickets kết thúc dòng 14;
  các CHECK category/source/version/context/timestamps và filtered index request dùng cột mới.
  Nhóm ADD notifications kết thúc dòng 118; CHECK single_source/ticket_context và filtered index ticket_recipient dùng cột mới.
  Đề xuất phân tích/duyệt riêng hai GO sau dòng 14 và 118, kiểm lại parser/checksum và điều kiện tương thích V11 trước khi sửa.
- V12 hiện 1 transactional batch, checksum -1357082083. ADD request_token/request_fingerprint cho support_ticket_messages
  ở dòng 21 rồi CHECK và filtered unique index tham chiếu ngay; đề xuất xem xét GO sau dòng 21 khi tới lượt.
  Đây chỉ là nguy cơ tĩnh, chưa có runtime V12.
- V13 hiện 1 transactional batch, checksum 1007916434, chỉ CREATE INDEX trên các cột trước đó;
  không thấy nhóm ADD COLUMN trong cùng batch. Chưa chạy nên không tuyên bố PASS.

## Lịch sử schema history sau V10

| Version | Trạng thái | Checksum history |
|---|---|---|
| V1 | SUCCESS, giữ nguyên | 189739357 |
| V2 | SUCCESS, giữ nguyên | 1191373203 |
| V3 | SUCCESS, giữ nguyên | -1720389847 |
| V4 | SUCCESS, giữ nguyên | -1466061241 |
| V5 | SUCCESS, giữ nguyên | 1925825129 |
| V6 | SUCCESS, giữ nguyên | -1705174727 |
| V7 | SUCCESS, giữ nguyên | 444247411 |
| V8 | SUCCESS, giữ nguyên | 2036200466 |
| V9 | SUCCESS, giữ nguyên | 61067824 |
| V10 | SUCCESS sau hai GO | 236607534 |
| V11 | FAIL attempt/rollback; Flyway.info PENDING | Không có hàng |
| V12 | PENDING, chưa chạy | Không có hàng |
| V13 | PENDING, chưa chạy | Không có hàng |

Bằng chứng ngoài Git trong TEMP: uteexpress-commit22-v10-preflight.log, -v10-targeted.log, -v10-target.log,
-v10-inspect.log, -v11-v13-migration.log, -v11-rollback.log. Không có credential trong báo cáo.
Các mục bên dưới là lịch sử các lượt trước, không thay thế trạng thái mới nhất phía trên.
## Lịch sử V9 sau xác nhận SSMS mới — 28/10/2026, 12:19

Đọc đầy đủ V9 và các định nghĩa shipping_services/shipping_rate_rules/audit_logs ở V1, pickup_fee_rules ở V4.
Runtime V9 cũ 207/S0001 ghi sáu cột max_weight_kg/max_length_cm/max_width_cm/max_height_cm/retired_from/pricing_version_id.
V9 thêm cột rồi tham chiếu trong CK_services_limits, FK/CK_retirement/index của rate rules và pickup rules, cùng một batch.
Không có UPDATE/INSERT/DELETE hoặc seed. Chỉ thêm GO sau các dòng gốc **8, 45, 51**, tức sau nhóm ADD COLUMN của từng bảng.
Checksum **1441625975 → 61067824**, parser **1 → 4** batches, đều transactional. Bỏ ba GO khôi phục SQL gốc/checksum;
không thay đổi kiểu dữ liệu, constraint/index hoặc ý nghĩa cập nhật legacy uniqueness/audit được V9 quy định.

User SSMS xác nhận không V9 ở UTEExpressDB trước sửa; QA JDBC lại kiểm local SQLEXPRESS/1433, login uteexpress_qa,
catalog và DB_NAME UTEExpress_Test, history V1–V8 đúng checksum, không đối tượng V9 dở. **12:19:45 target V9 SUCCESS**.
History V9 success=true/checksum61067824, V1–V8 giữ nguyên. 38 bảng. Hậu kiểm pricing_versions/15 tên cột,
11 cột thêm đúng kiểu/nullable/default0, tám trusted/enabled NO ACTION FKs, 10 CHECKs, hai PK/UQ, sáu index/ba filtered unique.
UQ_pickup_fee_rules_start cũ được thay bằng UX_pickup_legacy_start đúng script; CHECK audit có SHIPPING_SERVICE/PRICE_VERSION/PICKUP_RULE.
Không pricing seed, các version link/retirement mới ở bảng giá cũ NULL, metadata dịch vụ khởi tạo version0/fields NULL.
Không có legacy data fixture SQL để tuyên bố đầy đủ tương thích nghiệp vụ.

**12:20:20 target V10 FAIL**, runtime **207/S0001** cho notification_assignment_status, audience, source_event_id.
Flyway báo rollback. Đã dừng ghi, không retry hoặc sửa V10, không chạy V11–V13. Đây là runtime mới, khác cảnh báo tĩnh trước đó.
SELECT hậu kiểm: V1–V9 SUCCESS, V10–V13 PENDING/no V10 row; 38 bảng. Mười bảy cột, bảy FK/hai CHECK/ba default và bốn index
V10 được kiểm tra chưa tồn tại. V9 schema/uniqueness/audit vẫn đúng. Không clean/repair/reset hoặc ghi database ứng dụng.

| Migration | Trạng thái mới nhất | Checksum history |
|---|---|---:|
| V1 | SUCCESS, giữ nguyên | 189739357 |
| V2 | SUCCESS, giữ nguyên | 1191373203 |
| V3 | SUCCESS, giữ nguyên | -1720389847 |
| V4 | SUCCESS, giữ nguyên | -1466061241 |
| V5 | SUCCESS, giữ nguyên | 1925825129 |
| V6 | SUCCESS, giữ nguyên | -1705174727 |
| V7 | SUCCESS, giữ nguyên | 444247411 |
| V8 | SUCCESS, giữ nguyên | 2036200466 |
| V9 | SUCCESS sau ba GO | 61067824 |
| V10 | FAIL runtime, rollback; Flyway.info PENDING | Không có hàng |
| V11 | Chưa chạy, PENDING | Không có hàng |
| V12 | Chưa chạy, PENDING | Không có hàng |
| V13 | Chưa chạy, PENDING | Không có hàng |

Hibernate SQL validate, SQL business smoke/regression và SQL JAR startup BLOCKED cho đến đủ V13. Không khởi động JAR để retry V10.
Maven/JavaScript không dùng SQL Server chạy riêng, chi tiết final-qa-report.md. Log TEMP: uteexpress-commit22-v9-preflight.log,
uteexpress-commit22-v9-targeted.log, uteexpress-commit22-v9-migration.log, uteexpress-commit22-v10-v13-migration.log,
uteexpress-commit22-post-v10-readonly.log. Không credential trong Git/report.
Sau V9: clean test/verify BUILD SUCCESS, 1.452 tests/1.451 passed/0 failures/errors/1 SQL opt-in cũ skipped; thời gian 3:54 và 3:41.
JavaScript 12/12, WebSocket 17/17, JAR build/isolation/source hashes PASS, không phải SQL validation/smoke/startup PASS.

## Lịch sử V8 được duyệt — 28/10/2026, 10:31

Điều tra trước vá phân biệt rõ: runtime V8 cũ chỉ lưu 207/S0001, không lưu tên cột; SELECT DMV phân tích tĩnh trả
207/delivered_at, 207/returned_at, 1769/FK_delivery_attempts_assignment/assignment_id và 1750 đi kèm. Không coi thông báo
tĩnh là runtime được khôi phục; không replay migration cũ để lấy lỗi. V8 được thêm tại c5e7507, merge PR14/e0a6429, có trong main.

Sau xác nhận tương thích của người dùng, V8 chỉ thêm đúng ba GO sau các dòng gốc 170, 176, 182: nhóm shipments thời gian,
nhóm delivery_attempts ngữ cảnh và transport_trip_items.return_request_id. Không đổi tên/schema/CHECK/index/DML nào khác.
V8 không INSERT/UPDATE/DELETE dữ liệu nghiệp vụ. Checksum **-1022568645 → 2036200466**, parser **1 → 4** transactional batches.
Regression bỏ ba GO khôi phục checksum gốc và một batch, đồng thời xác minh từng nhóm cột nằm trước batch FK/CHECK/index sử dụng.

SqlV8 kiểm login/instance/local machine/port, JDBC catalog và DB_NAME đúng UTEExpress_Test, VIEW DEFINITION/quyền migration;
history V1–V7 khớp checksum và không partial V8 trước ghi. **10:31:40: target V8 SUCCESS**, history ghi checksum 2036200466.
37 bảng sau V8. Hậu kiểm sáu bảng mới với đủ 65 tên cột, bảy cột thêm trên bảng cũ đúng type/scale/nullable, 31 trusted/enabled
NO ACTION FKs, 23 CHECKs, 12 PK/UQ và 13 index. Bốn filtered unique index có predicate đúng được in vào log metadata.
Sáu bảng mới rỗng; các cột ngữ cảnh/giao/hoàn mới giữ NULL trên bảng cũ. Không seed bằng chứng, vận đơn hoặc dữ liệu demo.
Không có legacy shipment fixture SQL để tuyên bố đầy đủ business compatibility; Hibernate toàn model vẫn chờ V13.

**10:32:04: target V9 FAIL**, SQLState S0001 / errorCode 207; nested diagnostics chỉ ra max_weight_kg, max_length_cm,
max_width_cm, max_height_cm, retired_from, pricing_version_id. Đây là runtime mới thu được, không phải cảnh báo tĩnh từ lượt trước.
Flyway báo rollback V9. Đã dừng toàn bộ ghi, không sửa/retry V9 hoặc chạy V10–V13. Source V9 thêm các cột rồi dùng trong
CK_services_limits/CK_retirement/filtered indexes cùng một batch; cần chẩn đoán và bản vá riêng, không áp dụng hàng loạt GO.

SELECT hậu kiểm: history chỉ V1–V8 SUCCESS, V9–V13 PENDING/no V9 row; 37 bảng. Mười một cột mới, pricing_versions,
các FK/CHECK và sáu index V9 được kiểm tra đều chưa tồn tại. UQ_pickup_fee_rules_start và CK_audit_logs_resource cũ còn nguyên,
CHECK audit enabled/trusted và chưa thêm SHIPPING_SERVICE. V8 còn đầy đủ; không ghi UTEExpressDB.

| Migration | Trạng thái mới nhất | Checksum history |
|---|---|---:|
| V1 | SUCCESS, giữ nguyên | 189739357 |
| V2 | SUCCESS, giữ nguyên | 1191373203 |
| V3 | SUCCESS, giữ nguyên | -1720389847 |
| V4 | SUCCESS, giữ nguyên | -1466061241 |
| V5 | SUCCESS, giữ nguyên | 1925825129 |
| V6 | SUCCESS, giữ nguyên | -1705174727 |
| V7 | SUCCESS, giữ nguyên | 444247411 |
| V8 | SUCCESS sau ba GO được duyệt | 2036200466 |
| V9 | FAIL runtime, rollback; Flyway.info PENDING | Không có hàng |
| V10 | Chưa chạy, PENDING | Không có hàng |
| V11 | Chưa chạy, PENDING | Không có hàng |
| V12 | Chưa chạy, PENDING | Không có hàng |
| V13 | Chưa chạy, PENDING | Không có hàng |

Hibernate SQL validate, SQL business smoke và SQL JAR startup BLOCKED vì schema chưa đủ V13. Không tắt Flyway hoặc schema update
để vượt gate. Regression Maven/JS không dùng SQL Server được báo riêng trong final-qa-report.md.
Mới nhất sau V8: clean test/verify BUILD SUCCESS, 1.449 tests/1.448 passed/0 failures/errors/1 SQL opt-in cũ skipped,
thời gian 2:40 và 2:33. JavaScript 12/12, WebSocket 17/17 đạt; JAR build/isolation/source hashes đạt, không phải SQL startup PASS.
Log TEMP: uteexpress-commit22-v8-targeted.log, uteexpress-commit22-v8-migration.log,
uteexpress-commit22-v9-v13-migration.log, uteexpress-commit22-post-v9-readonly.log. Không credential trong Git/log báo cáo.

## Lịch sử 28/10, 09:24: điều tra và vá V4

Đã đọc toàn bộ V4 và V1–V3 liên quan. V1–V3 chưa có shipments.pickup_fee/quote_id; V4 thêm hai cột ở dòng gốc 97–98,
tham chiếu trong FK_shipments_quote (99), UX_shipments_quote_nonnull (100), CK_shipments_pickup_fee (101),
CK_shipments_quote_snapshot (102). V4 chỉ có SET/CREATE/ALTER, không INSERT/UPDATE/DELETE dữ liệu.
ALTER supports_delivery chỉ được dùng để thêm cột; các cột của hai bảng mới được định nghĩa ngay trong CREATE TABLE.

Runtime migrate ngày 27/10 đã lưu **207/S0001, quote_id và pickup_fee**. Parser Flyway xác minh V4 gốc đúng một batch;
đây là vấn đề thêm cột rồi biên dịch tham chiếu cột mới trong cùng batch. Log không chứa line number riêng cho từng SQL statement;
không khẳng định duy nhất một CHECK hoặc index là nguồn tất cả lỗi trong batch.

Trước khi sửa đã báo vị trí và rủi ro checksum cho người dùng. Bản vá thêm đúng **một GO sau ADD quote_id BIGINT NULL;**,
trước FK_shipments_quote. Không đổi câu SQL nào khác. Parser: 1 → 2 batch, đều transactional;
checksum **-2103371131 → -1466061241**. Regression khôi phục script cũ bằng bỏ một GO rồi kiểm checksum cũ.

Preflight chỉ SELECT xác minh login uteexpress_qa, instance local SQLEXPRESS/cổng1433, JDBC catalog và DB_NAME đều UTEExpress_Test,
VIEW DEFINITION/CREATE TABLE/ALTER dbo, history V1–V3 đúng checksum và không partial V4. Không kết nối ghi UTEExpressDB.
Target V4 thành công lúc **09:24:32**. Sau migrate kiểm tra hai bảng mới, ba cột đúng type/nullability/default,
11 trusted/enabled NO ACTION FKs, 14 CHECKs, bốn index, unique filtered quote_id IS NOT NULL và key column quote_id.
22 bảng sau V4, pickup_fee_rules/shipment_quotes chưa có dữ liệu, các snapshot mới ở vận đơn cũ vẫn NULL,
supports_delivery khởi tạo false. Không seed dữ liệu giá/demo; không có legacy shipment fixture để chứng minh business compatibility SQL đầy đủ.

Từng target V5, V6, V7 được migrate riêng và validate sau thành công, khoảng **09:25:05**. Target V8 runtime FAIL,
**errorCode=207 / SQLState=S0001**, Flyway báo rollback. Column trong log hiện là unclassified vì allowlist diagnostic
chưa chứa các cột riêng của V8; không suy ra tên cột runtime hoặc statement cụ thể từ dữ liệu chưa được lưu.
Đã dừng toàn bộ ghi; không retry hoặc sửa V8, không chạy V9–V13.

SELECT sau lỗi xác nhận 31 bảng, history đúng V1–V7 success=true; V8–V13 PENDING, không có hàng V8 thất bại.
Không có bảy cột V8 mới, sáu bảng mới, các FK/CHECK và bốn index trên bảng cũ được chỉ định trong kiểm tra rollback.
V4 còn đầy đủ; pickup_fee_rules/shipment_quotes/shipments đều 0 dòng. Không repair/reset/clean hoặc ghi DB khác.

| Migration | Trạng thái thực tế mới nhất | Checksum history |
|---|---|---:|
| V1 | SUCCESS, giữ nguyên | 189739357 |
| V2 | SUCCESS, giữ nguyên | 1191373203 |
| V3 | SUCCESS, giữ nguyên bản vá đã chạy | -1720389847 |
| V4 | SUCCESS sau một GO | -1466061241 |
| V5 | SUCCESS | 1925825129 |
| V6 | SUCCESS | -1705174727 |
| V7 | SUCCESS | 444247411 |
| V8 | FAIL runtime, rollback; Flyway.info PENDING | Không có hàng |
| V9 | Chưa chạy, PENDING | Không có hàng |
| V10 | Chưa chạy, PENDING | Không có hàng |
| V11 | Chưa chạy, PENDING | Không có hàng |
| V12 | Chưa chạy, PENDING | Không có hàng |
| V13 | Chưa chạy, PENDING | Không có hàng |

Hibernate SQL validate, SQL business smoke/regression và JAR SQL startup BLOCKED. Không gọi Hibernate trên schema thiếu,
không tắt Flyway/schema update để vượt gate. Maven/JavaScript không dùng SQL Server được kiểm tra riêng trong final-qa-report.md.
Mới nhất: clean test và verify đều BUILD SUCCESS, 1.446 tests/1.445 passed/0 failures/errors/1 SQL opt-in cũ skipped;
thời gian 1:57 và 1:47. JavaScript 12/12 PASS; JAR build/isolation/source hash PASS, không phải SQL startup PASS.
Log TEMP: uteexpress-commit22-v4-preflight-20261028.log, uteexpress-commit22-v4-targeted-20261028.log,
uteexpress-commit22-v4-migration-20261028.log, uteexpress-commit22-v5-v13-20261028.log,
uteexpress-commit22-post-v8-readonly-20261028.log, uteexpress-commit22-v8-artifacts-20261028.log.

## Lịch sử 27/10: vá V3 được duyệt và blocker V4 cũ

V3 chỉ thêm GO sau employees.user_id, branches.parent_hub_id và nhóm cột shipments (sau version).
Không đổi câu SQL nghiệp vụ. Regression dùng parser Flyway thật xác nhận bốn batch và có thể chạy transaction;
bỏ ba GO khôi phục checksum cũ -68259337, bản mới -1720389847. SQL thực tế target V3 thành công lúc 22:58:40.

Sau V3, kiểm tra metadata: sáu cột mới đúng kiểu/độ dài/nullable (shipment_method VARCHAR(30), các ID BIGINT NULL,
version BIGINT NOT NULL); bảng branch_service_areas; năm FK trusted/enabled/NO ACTION; sáu CHECK mới được chỉ định;
CHECK status mở rộng; ba index, bao gồm employees.user_id unique có filter IS NOT NULL. ROLE_SHIPPER có một dòng.
Không demo seed khách hàng/nhân viên/chi nhánh hoặc bảng giá trên SQL Server.

22:59:05: pending migration tiếp tục từ V3, thất bại tại V4. Nested SQL exceptions được thu theo mã/allowlist,
**SQLState=S0001, errorCode=207, sourceColumn=quote_id và pickup_fee**. Đây là lỗi runtime từ migrate, khác bằng chứng
phân tích tĩnh của V3 ở lần đầu. V4 thêm shipments.pickup_fee/quote_id rồi dùng trong filtered index/CHECK cùng batch;
đây là vị trí cần review riêng. Chưa sửa V4, không retry hoặc tự động vá migration khác.

23:00:23: SqlInspect chỉ SELECT xác nhận history còn đúng V1/V2/V3 thành công, V4–V13 PENDING; 20 user tables.
Các cột V4 (supports_delivery, pickup_fee, quote_id), bảng pickup_fee_rules/shipment_quotes, FK/CHECK mới và
UX_shipments_quote_nonnull được kiểm tra đều chưa có, phù hợp rollback V4. V3 vẫn đầy đủ.

| Migration | Kết quả thực thi / trạng thái hiện tại |
|---|---|
| V1 | SUCCESS, checksum 189739357 giữ nguyên |
| V2 | SUCCESS, checksum 1191373203 giữ nguyên |
| V3 | SUCCESS sau vá được duyệt, checksum -1720389847 |
| V4 | FAIL runtime 207/S0001, rollback; history không có hàng V4, Flyway.info PENDING |
| V5 | Chưa chạy, PENDING |
| V6 | Chưa chạy, PENDING |
| V7 | Chưa chạy, PENDING |
| V8 | Chưa chạy, PENDING |
| V9 | Chưa chạy, PENDING |
| V10 | Chưa chạy, PENDING |
| V11 | Chưa chạy, PENDING |
| V12 | Chưa chạy, PENDING |
| V13 | Chưa chạy, PENDING |

Hibernate validate toàn bộ model, SQL business smoke và JAR SQL startup BLOCKED vì schema chưa đủ V13.
Không tắt Flyway hoặc dùng schema update để vượt gate. Maven H2/offline regression không ghi SQL Server được chạy riêng;
clean test và verify đều BUILD SUCCESS, 1.444 tests / 1.443 passed / 0 failures/errors / 1 SQL opt-in cũ skipped.
Thời gian 3:12 và 3:04; JAR đóng gói/isolation đạt, chưa startup SQL. Chi tiết trong final-qa-report.md.
Không repair, clean, baseline, DROP/TRUNCATE/reset hoặc ghi database khác.

Log TEMP: uteexpress-commit22-v3-patch-tests.log, uteexpress-commit22-v3-migration.log,
uteexpress-commit22-v4-v13-migration.log, uteexpress-commit22-post-v4-readonly.log. Không có credential/XML/password trong Git.

## Lịch sử trước bản vá: fresh migration thất bại V3 — 27/10/2026, 22:30

22:29: SqlInspect PASS. Trước migration, Flyway.info cho V1–V13 đều PENDING, database không có user objects/tables.
22:30: SqlFresh xác minh lại target, identity, quyền và empty DB rồi mới gọi migrate. Flyway tạo history, áp dụng V1, V2,
thất bại tại `V3__extend_shipper_identity_and_shipment_lifecycle.sql` với FlywayMigrateException và thông báo rollback V3.
Không chạy lại migration, không repair/baseline/reset, không thay đổi migration cũ hoặc tiếp tục Hibernate.

22:34: SqlInspect chỉ đọc xác nhận **19 user tables** (18 bảng V1 + history), history có đúng V1/V2 thành công.
`roles=3`; `users`, `employees`, `branches`, `shipments`, `shipment_events` đều 0 dòng. Đây là các bảng đã đếm,
không phải kết luận đã chạy smoke hoặc kiểm kê mọi bảng. Cột employees.user_id, branches.parent_hub_id,
shipments.shipment_method và bảng branch_service_areas chưa tồn tại; ROLE_SHIPPER có 0 dòng, phù hợp rollback V3.
Database test **không còn trống**; không được chạy lại SqlFresh trên trạng thái hiện tại.

Chẩn đoán bổ sung dùng SELECT từ `sys.dm_exec_describe_first_result_set`, truyền nguyên văn V3 như tham số NVARCHAR,
**không thực thi DDL của V3**. SQL Server trả phân tích tĩnh **error 207, type SYNTAX, cột user_id**,
kèm 11501 cho batch không phân tích được. Không coi 11501 là lỗi nghiệp vụ độc lập.
V3 thêm employees.user_id rồi tham chiếu cột mới trong filtered unique index, cùng một batch không có GO.
Kết quả tĩnh chỉ ra vấn đề phân giải cột/batch tại đoạn này. Mã lỗi gốc lồng trong exception migrate chưa được tool lưu;
không gán mã 207 của phân tích tĩnh thành mã lỗi runtime đã thu được từ lần migrate.
Xem [Microsoft: mô tả metadata bằng phân tích tĩnh](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-describe-first-result-set-transact-sql)
và [DMV describe first result set](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-objects/sys-dm-exec-describe-first-result-set-transact-sql).

Hướng xử lý cần review riêng: chiến lược chia batch và chính sách version/checksum của migration V3 đã tồn tại.
Không tự thêm GO/sửa V3 trong Commit 22. Chỉ thêm V14 sẽ không giải quyết fresh install đang dừng ở V3.
Giữ nguyên test DB để đối chiếu; không reset hoặc chạy lại DDL để lấy lỗi. Sau khi có phương án migration được duyệt,
cần đánh giá trạng thái V1/V2 hiện hữu trước khi tiếp tục; không bỏ qua failed gate bằng tắt Flyway/ddl-auto=update.

Bằng chứng ngoài Git trong TEMP: `uteexpress-commit22-sql-inspect-20261027.log`,
`uteexpress-commit22-sql-fresh-20261027.log`, `uteexpress-commit22-sql-postfailure-20261027.log`.
Maven test-compile/build-classpath cho ba lần lần lượt BUILD SUCCESS (7,963 / 4,068 / 4,072 giây).
Đây không phải Maven clean test/verify. Không demo seed SQL Server, không ghi database khác, không sửa cấu hình mặc định.

## Lịch sử đã được thay thế: SQL login riêng — 26/10/2026, khoảng 13:25 UTC+7

Người dùng đã tạo SQL login **uteexpress_qa**, báo mapping/db_owner chỉ trong UTEExpress_Test và đã tạo lại DPAPI.
Agent bổ sung guard: credential username phải là uteexpress_qa trước JDBC, từ chối sa/login khác; sau connection còn kiểm
ORIGINAL_LOGIN và membership db_owner từ server. Không sử dụng credential mặc định hoặc ghi username/password từ DPAPI vào log.

SqlInspect (chỉ đọc, không write flag) thực tế trả **SQLServerException, errorCode=18488, SQLState=S0001**.
Theo [Microsoft SQL Server error catalog](https://learn.microsoft.com/en-us/sql/relational-databases/errors-events/database-engine-events-and-errors-18000-to-18999),
18488 nghĩa là password của login phải được thay đổi. Không tự đổi/reset password, bỏ MUST_CHANGE hoặc tắt password policy.

| Giai đoạn | Kết quả thực tế |
|---|---|
| 1 DPAPI import + client login allowlist | PASS, file do người dùng tạo; credential không được hiển thị |
| 1 JDBC authentication | FAIL, 18488/S0001; không có connection để SELECT |
| 1 Actual instance/catalog/DB_NAME/quyền/db_owner/empty | BLOCKED, chưa kiểm chứng server-side; thông tin người dùng không thay bằng chứng JDBC |
| 2 Flyway V1–V13/history/Hibernate/FK/index/enum | BLOCKED, không chạy vì giai đoạn 1 thất bại |
| 3 SQL smoke/Auth/Shipment/Logistics/Transport/Delivery/Returns/Support/Reports/Audit | BLOCKED |
| 3 Regression Commit 20–21, JAR SQL startup, full clean test/verify | BLOCKED, chưa chạy ở lượt này |

Maven test-compile/build-classpath đạt (3,667 giây); đây không phải full Maven regression. Không retry hoặc fallback
sau lỗi, không dùng sa, không migration/DML/seed/drop/truncate/reset database nào. Không sửa V1–V13 hoặc cấu hình mặc định.
Không có chứng cứ về production code/schema bug mới; blocker hiện tại là cấu hình vòng đời SQL login password.
Latest unit/mock guards `ReleaseSafetyTests`: **34 passed, 0 failures/errors/skipped**; không chạy JDBC trong các unit cases này.

Người dùng tự xử lý trong SSMS, không gửi secret:

1. Kết nối `127.0.0.1,1433`, SQL Server Authentication `uteexpress_qa`, Connection Properties → Connect to database
   `UTEExpress_Test`. Tự hoàn tất đổi password nếu SSMS yêu cầu; nếu không có prompt, nhờ DBA hỗ trợ password change đúng login.
2. Sau khi kết nối được, xác minh chỉ đọc `SELECT DB_NAME(), SERVERPROPERTY('InstanceName');` cho ra UTEExpress_Test/SQLEXPRESS.
3. Tự xóa đúng file credential QA cũ trong TEMP và chạy lại set-sql-qa-credential.ps1 với password mới. Script không overwrite.
4. Báo “Đã đổi password và tạo lại DPAPI”. Agent sẽ retry giai đoạn 1; vẫn không migration trước khi mọi identity/empty/permissions check đạt.
   Không gửi password, XML hoặc connection string chứa secret. Giữ nguyên password policy và quyền trên database khác.

## Lịch sử đã được thay thế: lần chỉ đọc — 26/10/2026, khoảng 13:10 UTC+7

Chạy SqlInspect bằng credential DPAPI người dùng cấp, chỉ định databaseName=UTEExpress_Test, instance kỳ vọng SQLEXPRESS;
không đặt write flag, không chạy SqlFresh. Endpoint 127.0.0.1:1433 là endpoint cần xác minh, không được coi đã xác định instance.
TLS encrypt bật; trustServerCertificate chỉ dùng cho instance test cục bộ có certificate tự ký, không cấu hình production.
Maven test-compile/build-classpath BUILD SUCCESS; sau import credential, JDBC trả:

```text
SQLServerException: SQLState=S0001, errorCode=18456
```

Thông báo đã loại username/password/connection secret/stack trace JDBC. Đây là SQL Server phản hồi từ chối đăng nhập,
không phải bằng chứng instance/catalog/DB_NAME đúng hoặc DB trống. Registry static port không thay thế kiểm tra server-side.
Chưa thực hiện SELECT metadata/permissions/Unicode/UTC, chưa gọi Flyway.info/migrate/validate hoặc Hibernate validation.
Giai đoạn 1 thất bại nên **dừng toàn bộ giai đoạn 2**, không thử credential khác, Windows integrated authentication,
named endpoint khác hoặc database khác. Không migration/ghi dữ liệu/seed/reset, không sửa V1–V13/cấu hình mặc định.
DPAPI file vẫn ở TEMP; chỉ import bằng PowerShell, không hiển thị XML/credential đã giải mã và không đưa vào Git.

Error 18456 có nhiều nguyên nhân; client SQLState S0001 không phải State chi tiết trong SQL Server Error Log.
Không kết luận password sai khi chưa có Reason/State server-side. Tham khảo
[Microsoft: MSSQLSERVER_18456](https://learn.microsoft.com/en-us/sql/relational-databases/errors-events/mssqlserver-18456-database-engine-error).

Hướng xử lý người dùng/DBA, chưa được agent thực hiện:

1. Trong SSMS, thử **SQL Server Authentication** đúng credential đã nhập, server **127.0.0.1,1433**,
   Connection Properties → Connect to database **UTEExpress_Test**. Không thử UTEExpressDB làm workaround.
2. Nếu vẫn lỗi, DBA xem SQL Server Error Log đúng thời điểm trên, lấy riêng **State + Reason đã loại username/PII**;
   kiểm tra login tồn tại/enabled, password/expiration, SQL Authentication mode, database online và mapping/quyền vào UTEExpress_Test.
   Không tự cấp sysadmin hoặc sửa quyền database khác để vượt lỗi.
3. Nếu SSMS login được nhưng JDBC chưa được, kiểm tra credential export đúng tài khoản SQL/Windows user/instance;
   chỉ tạo lại file DPAPI sau khi người dùng tự review file cũ, không gửi password hay XML vào chat.
4. Sau khi khắc phục, báo kết quả không chứa secret. Chỉ retry lại **giai đoạn 1**; migration chỉ được chạy sau identity/empty/permissions PASS.

## Tạo database riêng bằng SSMS

1. Connect Database Engine `127.0.0.1,1433`, SQL Server Authentication; dùng credential sẵn có của bạn, không gửi chat.
2. Kiểm tra instance và database chỉ đọc:

```sql
SELECT SERVERPROPERTY('ServerName') AS server_name,
       SERVERPROPERTY('InstanceName') AS instance_name;
SELECT name, state_desc FROM sys.databases
WHERE name IN (N'UTEExpressDB', N'UTEExpress_Test');
```

3. Nếu chưa có tên đích, SSMS → Databases → New Database → UTEExpress_Test. Hoặc tại cửa sổ query `master`, chạy riêng:

```sql
CREATE DATABASE [UTEExpress_Test];
```

Tên đã tồn tại làm lệnh thất bại, không overwrite. Không DROP/TRUNCATE/DELETE/reset hoặc restore đè database khác.
Chưa đủ quyền tạo DB thì nhờ DBA; không tự cấp thêm quyền SQL Server. Database test mới cần một SQL login được map
đúng và có quyền DDL trong **database test** để Flyway chạy; không mặc định tài khoản dev có quyền đó.

4. Báo đã tạo xong. Agent sẽ hỏi xác nhận **instance/endpoint, database chính xác, fresh hay upgrade, phạm vi cho phép**
(inspect, migration V1–V13, Hibernate validate, smoke ghi dữ liệu giả và có/không demo seed).
5. Chỉ sau xác nhận mới cấu hình secret ngoài Git và thực thi công cụ. Tên có chữ Test không tự chứng minh an toàn.

## Công cụ opt-in được chuẩn bị

### Hướng dẫn cấp hoặc cấp lại DPAPI credential

Script `scripts/set-sql-qa-credential.ps1` **không kết nối SQL Server**, không chạy Maven/Flyway/Hibernate hoặc seed.
Script do người dùng tự chạy; agent không nhập/tạo password. Credential cấp lại đã giúp JDBC PASS ngày 27/10;
không cần tạo lại credential chỉ vì lỗi migration. Dùng đúng tài khoản Windows của phiên Codex,
trên cùng máy; không chạy bằng một Windows user khác. Nhập SQL Authentication login hiện có được phép dùng UTEExpress_Test,
không nhập Windows login hoặc gửi password vào chat.

```powershell
cd D:\WebCode\uteexpress\uteexpress
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\set-sql-qa-credential.ps1
```

ExecutionPolicy chỉ áp dụng tiến trình này, không sửa policy toàn máy. Nếu policy tổ chức cấm thì không đổi policy,
nhờ quản trị viên cho phép script đã review. Script hỏi username qua Read-Host, password qua Read-Host -AsSecureString;
không tạo password, không dùng default credential, không convert password thành plaintext trong bước export.

File duy nhất được tạo là **TEMP\uteexpress-sql-qa.credential.xml**, ngoài repository; script từ chối TEMP trong repo hoặc
file đã tồn tại (NoClobber). Windows Export-Clixml mã hóa SecureString bằng DPAPI của Windows user/máy hiện tại;
username vẫn là metadata XML, vì vậy không gửi/đưa cả file vào Git. ACL chỉ giữ current user và SYSTEM.
Không lưu password plaintext/script/command history hoặc in connection string có secret.

Chỉ kiểm tra tồn tại, không đọc/in nội dung:

```powershell
Test-Path -LiteralPath (Join-Path $env:TEMP 'uteexpress-sql-qa.credential.xml')
```

Nếu True và script không lỗi, báo **“Đã tạo credential DPAPI”**. Không gửi password, username thực hoặc XML.
Sau đó agent dùng `-CredentialFile` trên release-tools: kiểm tra scope trước import; giải mã sau Maven, chỉ truyền credential
vào môi trường SQL tool child rồi phục hồi env trong finally. File không tự bị xóa; người dùng xóa file TEMP này sau khi QA xong.

SQL tool khóa **UTEExpress_Test**, URL phải khai báo đúng một databaseName=UTEExpress_Test, chỉ local endpoint;
không nhận default database, alias/duplicate database, remote host hoặc failoverPartner. Trước migration/ghi kiểm JDBC catalog +
`SELECT DB_NAME()`, SERVERPROPERTY InstanceName/MachineName đúng SQLEXPRESS/local machine; phải có VIEW DEFINITION
để count không bị thiếu do metadata visibility. Fresh còn yêu cầu không user objects và đủ CREATE TABLE/ALTER dbo.
Lỗi identity/auth/permission/migration dừng, không fallback hoặc repair/reset. Không đọc/ghi UTEExpressDB.

`scripts/release-tools.ps1` compile test tooling và gọi `SqlServerQaTool`. Không chạy trong startup/JAR/default tests.
Các biến cần cấp **sau xác nhận**, không dùng giá trị credential trong command line:

| Biến | Nội dung |
|---|---|
| UTE_QA_SQLSERVER_URL | JDBC SQL Server URL chỉ target test, không credentials nhúng |
| UTE_QA_SQLSERVER_DATABASE | Tên database test chính xác |
| UTE_QA_SQLSERVER_INSTANCE | `SQLEXPRESS`, phải khớp instance thực và local machine qua JDBC |
| UTE_QA_SQLSERVER_APPROVED_DATABASE | Cùng tên, operator attestation sau xác nhận an toàn |
| UTE_QA_SQLSERVER_USERNAME/PASSWORD | SQL Authentication test riêng; environment hoặc secure prompt ngoài Git |
| UTE_QA_SQLSERVER_ALLOW_MIGRATE | Chỉ `YES` khi đã được phép fresh migration |

Attestation chỉ ghi nhận operator đã xác nhận; không chứng minh độc lập database là non-production.
Cần kiểm tra instance, dữ liệu và quyền thực trước. Công cụ còn từ chối system DB và UTEExpressDB,
kiểm tra actual catalog/product, và fresh chỉ khi **không có bất kỳ user table**. Không chạy song song tác vụ khác vào DB QA.

```powershell
# Mỗi lệnh sau chỉ được chạy khi đã có xác nhận phạm vi tương ứng.
# Đây là tham chiếu mode, không phải hướng dẫn retry sau các migration đã SUCCESS; không retry/reset hoặc chạy SqlFresh trên DB này.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlInspect
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlFresh
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV3
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV4
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV8
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV9
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV10
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV11
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlV12
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlPending
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode SqlValidate
```

Inspect chỉ đọc catalog/history + SELECT Unicode/UTC, không migrate/repair/clean/Hibernate tooling.
Khi history dừng ở V2, Inspect còn đếm sáu bảng được chỉ định và kiểm tra dấu vết V3; khi ở V3, kiểm tra V3 và rollback V4.
Không chuyển script đã có GO vào DMV vì GO là delimiter của client, không phải T-SQL. Chẩn đoán nested exception chỉ in
SQLState/mã/cột thuộc allowlist, không in raw SQL exception hoặc dữ liệu cá nhân.
Fresh áp dụng migrations, validate checksum, Hibernate `validate`, đọc catalog counts. Validate không migrate.
SqlV3 yêu cầu đúng history V1/V2 và không có đối tượng V3, target=3; không gọi Hibernate trước đủ V13.
SqlV4 yêu cầu history V1/V2/V3 đúng checksum, không partial V4, target=4 và kiểm metadata/data trước Hibernate.
SqlV8 yêu cầu history V1–V7 đúng checksum, 31 bảng và không partial V8, target=8; kiểm schema/data trước Hibernate.
SqlV9 yêu cầu history V1–V8 đúng checksum, 37 bảng và không partial V9, target=9; kiểm schema/data trước Hibernate.
SqlV10 yêu cầu history V1–V9 đúng checksum, 38 bảng và không có 17 cột/constraint/index V10 áp dụng dở, target=10; kiểm schema/data trước Hibernate.
SqlV11 yêu cầu history V1–V10 đúng checksum, 38 bảng và không partial V11; target=11, hậu kiểm 41 bảng/schema/history.
SqlV12 yêu cầu history V1–V11/checksum, 41 bảng, không partial V12; chỉ target=12 và hậu kiểm.
SqlPending yêu cầu history V1–V12/checksum/schema hiện tại, 42 bảng, không index V13; chỉ target=13, hậu kiểm rồi Hibernate validate.
SqlSmoke yêu cầu đủ V1–V13 và write attestation YES; fixture trong transaction luôn rollback, không demo seed.
SqlJar yêu cầu đủ V1–V13/Flyway/Hibernate validate, JAR đã verify; ghim URL Test/loopback, worker off, không secret trong argument, kiểm HTTP rồi dừng đúng tiến trình đã tạo.
Các mode migration phải có write attestation YES; không nới guard zero-table của SqlFresh. Không retry migration đã thành công, không clean/repair/reset.
Các mode không clean/reset/repair/baseline/upgrade. Lỗi dừng, không tự sửa lịch sử Flyway.
Công cụ sanitize exception summary vì JDBC/Flyway messages có thể chứa dữ liệu; xem diagnostic chi tiết trực tiếp
trong môi trường bảo mật nếu cần, không gửi credential/log thô vào Git hoặc chat.

Guard thiếu xác nhận có unit tests; **không coi guard test là SQL Server integration**.
Sau fresh thành công, chạy SqlValidate lần hai và đối chiếu history; không chạy SqlFresh vào database đã có bảng.

## Đối chiếu catalog sau migration

Thực hiện chỉ trong database QA đã xác nhận. Hibernate validate không xác minh đầy đủ CHECK/index/default;
counts từ tool chưa chứng minh từng constraint đúng. DBA/QA cần đối chiếu định nghĩa với V1–V13:

```sql
SELECT installed_rank, version, description, checksum, success
FROM dbo.flyway_schema_history ORDER BY installed_rank;
SELECT t.name AS table_name, c.name AS column_name, ty.name AS sql_type,
       c.max_length, c.precision, c.scale, c.is_nullable, dc.definition AS default_value
FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id
WHERE SCHEMA_NAME(t.schema_id)=N'dbo' ORDER BY t.name,c.column_id;
SELECT name, definition, is_disabled, is_not_trusted FROM sys.check_constraints;
SELECT name, is_disabled, is_not_trusted FROM sys.foreign_keys;
SELECT OBJECT_NAME(object_id) AS table_name, name, is_unique, filter_definition
FROM sys.indexes WHERE object_id IN (SELECT object_id FROM sys.tables);
```

NVARCHAR max_length tính **byte**, MAX là -1; kiểm tra enum VARCHAR, DECIMAL precision/scale, DATETIME2(7), identity,
composite UserRole, nullable/defaults, FK và filtered unique assignment/manifest/receipt. Không sửa mappings dựa chỉ H2.

## Startup, business smoke và upgrade

Sau khi được phép startup migration, dùng profile release/DB env trỏ **đúng QA target**, build JAR, bind loopback.
Quan sát Flyway history, Hibernate validate, fatal errors, `/login`, assets, session/CSRF và WebSocket endpoint.
Không chữa lỗi bằng `ddl-auto=update`, tắt Flyway hoặc clean/repair.

Smoke qua service/UI cần dữ liệu reference giả được phép: BCrypt login/User creation; quote hai phương thức và phí snapshot;
Manager confirm/pickup; physical intake; TransportLeg candidate query với enum binding Commit 20; ba leg forward + receipts;
proof delivery; failed/recovery/redelivery/ba leg reverse/final sender receipt; support PUBLIC/INTERNAL; persisted notifications;
Admin audit/reports/CSV. Đối chiếu custody/event/receipt/counts; SQL concurrency riêng, không thay bằng H2.
LocalDemoSeed cố ý **chỉ hỗ trợ H2 memory**, không được trỏ sang SQL Server. SQL demo seed/smoke hiện chưa được thực thi.

Upgrade cần database version cũ riêng, dữ liệu giả, backup và restore rehearsal được kiểm chứng, explicit permission.
Ghi version/counts Users/Shipments/events/trips/attempts/returns/tickets/audit trước/sau; migrate pending, kiểm checksum/FK/unique,
Hibernate validate và startup lại. Không có môi trường đó thì NOT TESTED; không suy upgrade PASS từ fresh PASS.

## Bổ sung Browser/H2 phase — không thay bằng chứng SQL

28/10: giữ nguyên V1–V13/Flyway/Hibernate/SQL smoke/JAR evidence; phase Browser/H2 không chạy SQL migration/seed.
H2 demo đã startup18scenario trên loopback18082 sau sửa DevTools test-only entry point.
Browser API không có Chrome kết nối; native UI trước bị runtime URL/approval block, chưa có browser E2E PASS.
Kế hoạch SQL demo chỉ phân tích trong demo-setup.md, không nới H2 guard; cần quyền persistent seed riêng trước ghi.
Current idempotency chỉ same-context H2; không đánh PASS SQL restart/concurrency/worker seed.
Không cần re-run SQL database validation để cập nhật manual checklist. Xem demo-script.md và release-checklist.md.
