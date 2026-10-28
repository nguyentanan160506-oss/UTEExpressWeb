# Cập nhật Commit 23 — sản phẩm SQL Server demo

Phạm vi mới yêu cầu dữ liệu bền vững trên **UTEExpress_Demo**. Kết luận DEMO-READY bên dưới thuộc H2/Commit 22, không chứng nhận mục tiêu SQL demo mới. **Commit 23 chưa đủ điều kiện đóng** khi chưa cấp phép và chưa chạy migration/seed/startup/persistence sau restart trên DB demo riêng. Không thực hiện thao tác SQL Server trong giai đoạn chuẩn bị này.

Kết quả chuẩn bị: Maven clean verify **1.492 passed / 1 skipped**, Chrome H2 **365 PASS / 0 FAIL / 0 BLOCKED**, JavaScript **12/12**, WebSocket backend **17/17**, production JAR không có tooling/SMTP/H2/test secrets. Đây không phải persistence SQL demo PASS.

Xem [QA Commit 23](final-website-qa.md), [audit](final-product-audit.md), [hướng dẫn SQL demo và các bước cần duyệt](sql-demo-setup.md).

---

# Commit 22 — Final QA report

QA ban đầu 26/10/2026; cập nhật SQL Server **28/10/2026, 14:20–14:49 UTC+7**.
Branch feature/team-final-qa-demo-release, HEAD 4e71056, có Commit 01–21. Không stage/commit/push/merge/stash/PR.

**V12 PASS; V13 PASS; V1–V13 SUCCESS/checksum khớp; Flyway validate PASS; Hibernate SQL validate PASS;
SQL business smoke + MockMvc/Thymeleaf PASS.** UTEExpress_Test có 42 bảng.
JAR thật/release profile + HTTP loopback PASS ở lượt SQL trước; Browser H2 hiện350PASS/0FAIL/0BLOCKED, có full logistics/OTP/live WS trong scope demo. SQL upgrade có dữ liệu chưa nghiệm thu.
**DEMO-READY một laptop Windows/Chrome/H2; PRODUCTION NOT READY**. Kết luận mới dựa trên browser thực và regression, không suy từ service tests đơn thuần.
Chi tiết migration/history/scope ở [SQL validation](sql-server-validation.md), quyết định ở [release checklist](release-checklist.md).

## Nghiệm thu cuối theo scope một laptop — 28/10/2026

**DEMO-READY trong phạm vi Google Chrome/Windows/H2 localhost.** Khuyến nghị đóng Commit22 sau review và thao tác Git thủ công.
Production-ready là đánh giá riêng, chưa đạt; cloud/hosting/tên miền hoặc truy cập máy khác không thuộc yêu cầu và không chặn demo.

- Chrome headless thật350/350PASS,0FAIL/0BLOCKED: logistics nhất quán hai đơn mới, đủ3leg forward và3leg return,
  proof DELIVERED, failed/recovery/sender receipt RETURNED, tracking/ownership/phí snapshot.
- SMTP nội bộ test-only: register→SMTP→inbox Admin→wrong OTP denied→verify đúng→USER login→reuse denied.
  Không bypass OTP/autoactivate/personal SMTP. Inbox chỉAdmin/no-store, sink loopback/example.test, bounded RAM.
- WebSocket UI: User/Manager ở BrowserContext riêng; MESSAGE/DOM matching tracking không reload; other404;
  offline/reconnect reconcile trạng thái đã lưu. Không sửa kiến trúc WebSocket/production JavaScript.
- Targeted80/80 PASS. Clean test1.470tests/1.469passed/0failures/errors/1skipped,3:09,kết thúc21:01:50.
  Verify cùng totals PASS3:18,kết thúc21:05:49. Surefire XML khớp, skipped cũ SQL opt-in contextLoads giữ false.
- JavaScript12/12, backend WebSocket17/17 PASS. JAR13migration khớp source; không H2/DevTools/SMTPfake/mailbox/browser/test/SQLQA tooling.
- Guard chống serialize credential/OTP thực chạy PASS; không raw frames/CSRF/cookie/HAR trong report. Secret/static artifact scan chốt ở browser-qa.md.
- Không truy cập SQL Server/DPAPI, không migration/seed/SELECT/write trên UTEExpressDB/UTEExpress_Test. Bảy migration giữ nguyên hash,
  diff so HEAD chỉ15GO; risk checksum cho DB áp dụng source cũ được ghi trong [checklist](release-checklist.md).

Không cần bổ sung fixture: LocalDemoSeed đủ5điểm/6tuyến vàidempotency guard H2 vẫn đạt.
Tất cả thay đổi code ở lượt này nằm test/demo tooling; không production business logic/entity/enum/schema hoặc dependency/default config change.
Các lỗi root401/nhãn/form-header đã sửa lượt trước vẫn được regression; không nới CSRF/RBAC để làm browser đạt.

Lượt này8filemới/12filehiệnhữusửa/0xóa; Git tổng44file(14tracked modified+30untracked),không staged. Danh sách đầy đủ ở browser-qa.md.
Lệnh presenter -ModeDemo -TestMail cũng đã smoke thật trên H2 mới:seed18/login/session/Admin inbox200/no-store.
Có thể trình diễn đầy đủ các critical flows trên Chrome có cửa sổ bằng:
~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8081 -TestMail
~~~
Mở http://127.0.0.1:8081/, dùng secure prompt, email test@example.test và Admin inbox /admin/demo-mailbox.
Kịch bản/hướng dẫn: [demo-script](demo-script.md), [demo-setup](demo-setup.md); case/evidence/files: [browser-qa](browser-qa.md), [JSON](browser-qa-results.json).
Headed presenter rehearsal và mọi viewport/a11y/Save dialog chưa gọi PASS; nên tập trước bảo vệ, không phải yêu cầu cloud/production để giữ Commit22.

Ngoài scope production còn SMTP thật/TLS/deployment, SQL upgrade có dữ liệu/backup/restore, load/multi-instance/broker.
Giữ rõ ranh giới DEMO-READY và PRODUCTION-READY. Không Git mutation hoặc Commit23.

## Lịch sử QA SQL — giữ nguyên bằng chứng các lượt trước

Các FAIL/BLOCKED có ngày/giờ trong phần lịch sử dưới đây là trạng thái tại thời điểm đó, không phải kết luận demo hiện tại.
Không chạy lại SQL trong lượt H2 cuối; không diễn giải H2 thành SQL Server validation.

## V12 và V13 — nghiệm thu database

Người dùng xác nhận một máy triển khai, SSMS UTEExpressDB chỉ V1/V2, không môi trường quản lý khác áp dụng V12 cũ.
Agent chỉ dùng DPAPI uteexpress_qa trên 127.0.0.1:1433 / local SQLEXPRESS / UTEExpress_Test;
xác minh login/catalog/DB_NAME/history/quyền trước migration, không đọc lại DB ứng dụng bằng QA.
Preflight V12: V1–V11 SUCCESS, V11 checksum 2051557719, 41 bảng; không partial V12.

Root cause V12: ADD request_token/request_fingerprint ở dòng gốc 21 rồi CHECK request/filtered unique index dùng ngay
trong cùng batch. Runtime thực 207/S0001: Invalid column name 'request_token'. / 'request_fingerprint'.
Chỉ thêm một GO sau dòng gốc 21; checksum -1357082083 → -1057022707; parser 1 → 2 transactional batch (dòng 1/24).
Bỏ GO khôi phục source gốc. Không đổi SQL nghiệp vụ/constraints/types/index/data; V1–V11/V13 SHA-256 giữ nguyên.
Target V12 PASS **14:23:17**; schema/history/checksum đúng. Bảng route/4 cột, 2 cột message, FK/CHECK/DEFAULT/PK/UQ/3index đạt.

V13 source giữ nguyên, checksum 1007916434, một batch/10 CREATE INDEX.
Guard lần đầu dừng trước migrate vì verifier V11 cũ không tính 2 cột message V12; đã sửa preflight target13 theo contract V12.
Không phải SQL migration fail, không nới history guards. Target V13 PASS **14:24:39**; đủ 10 index/parent/key order đúng.
V1–V13 success=true/checksum đúng source; Flyway validate PASS; Hibernate7.2.24/SQLServer16 ddl-auto=validate PASS **14:24:42**.
42 bảng gồm các bảng nghiệp vụ và Flyway history. Không clean/repair/reset/drop database/SQL demo seed, không sửa V1–V11/V13.

## SQL smoke — PASS, rollback-only fixtures

Công cụ mới test-only SqlServerBusinessSmoke.java và sqlserver-qa.properties, chạy opt-in SqlSmoke sau guard đủ V13.
Fixture riêng, không dùng LocalDemoSeed. DataSource được ghim vào Test, Hibernate validate, Flyway off trong context
vì tool đã validate, notification workers off, SMTP loopback. Transaction luôn rollback và row count mọi bảng trước/sau khớp.
SQL identity counters có thể tăng, không reseed/reset. Role DB có sẵn được tái sử dụng, không seed role/quyền/tài khoản demo lưu lại.

Lần hoàn chỉnh **14:37:59–14:38** đạt 10 nhóm kiểm tra thực:
1. BCrypt/Auth/role từ database, hai phương thức gửi và snapshot phí từ fixture DB; quote confirm idempotent.
2. Manager xác nhận/assign pickup, Shipper collection, branch handoff/physical intake.
3. Ba chặng forward, arrive không tự tạo custody, receipt/close; cả candidate query DRAFT và TransportLeg VARCHAR binding.
4. Delivery proof/DELIVERED.
5. BRANCH_DROPOFF, delivery ABSENT/recovery, return request, ba chặng reverse và sender receipt/RETURNED.
6. Customer support routing, ticket/message, gửi lại token không nhân đôi message qua schema V12.
7. Notification processor logic và SQL inbox/visibility.
8. Mọi dataset reports, audit UNION và NVARCHAR(MAX) tiếng Việt >5.000 ký tự round-trip.
9. MockMvc SQL context: Guest tracking, login/logout CSRF, BCrypt/session fixation, User Thymeleaf 200/RBAC403/session invalidation.
10. Commit21 cached Authentication/EntityManager: đổi account/employee/branch status và role của fixture bằng SQL,
    LogisticsAccessService refresh/recheck và từ chối đúng. Commit20 arrival/probe và candidate-query regressions đã chạy ở các chặng.

Giới hạn: notification processors unwrapped để tham gia outer rollback transaction; không xác nhận REQUIRES_NEW/retry/worker concurrency
hoặc SQL-to-WebSocket end-to-end. Cache refresh dùng thay đổi trong cùng transaction, chưa race commit từ connection khác.
Các negative/locking/concurrency permutations đầy đủ vẫn chủ yếu là H2 regression, không được nâng thành SQL PASS.

Các lỗi harness đã sửa: tên thuộc tính/signature Entity/service, DevTools restart main, DataSource bean definition,
SecurityContext MockMvc và preflight schema cũ. Không sửa production business Java, dependency hoặc cấu hình mặc định để test pass.
Log chỉ nhãn/status/codes/allowlisted message/type/source frames, không password/DPAPI XML.

## Regression và packaging hiện tại

Targeted safety/mapping sau thêm smoke consent: **68/68 PASS** (57 safety + 11 mapping).
clean test **BUILD SUCCESS**, 1.462 tests / 1.461 passed / 0 failures/errors / 1 skipped, **2:49**, kết thúc 14:41:31.
SQL opt-in cũ contextLoads vẫn skipped với -Duteexpress.sqlserver.validate=false; không coi case skipped là SQL validate.
JavaScript **12/12 PASS**. Verify BUILD SUCCESS 2:38, cùng1.462/1.461 passed/0 failures/errors/1 skipped, hoàn tất14:44:44; XML tổng khớp. WebSocket17/17 PASS.
Các mốc 1.455/1.458 phía sau là lịch sử. Không cộng số tests qua nhiều lần chạy.

Lượt này sửa 7 file hiện hữu (V12, SqlServerQaTool, ReleaseSafetyTests, release-tools.ps1, 3 docs) và tạo 2 file test-only;
0 file xóa. Tổng so HEAD 24 file = 7 migration modified + 17 untracked; 0 staged/deleted.
Manifest đầy đủ phía dưới. Credential/logs/probes trong TEMP, target artifacts ngoài Git. git diff --check, UTF-8/final newline/whitespace cho24 file, Markdown links và PowerShell AST parser PASS; hash đầu/cuối xác nhận12 migration ngoài V12 không đổi.
Các phần sau là lịch sử các lượt trước, không thay thế trạng thái mới nhất phía trên.
## JAR/HTTP và kết quả cuối

JAR đã verify khởi động thật bằng release profile trên loopback18081/UTEExpress_Test: **PASS 14:46**.
Tool preflight kiểm instance/login/catalog/DB_NAME/history đủ13, Flyway/Hibernate validate; pin URL/password qua child environment,
không process-argument secret hoặc inherited datasource/config fallback. Workers off/SMTP loopback/cookie Secure=false chỉ local test.
HTTP200 login/register/tracking/quote/BootstrapCSS, HTML/loginCSRF render; Guest401 user/admin/notificationsAPI.
Authenticated SQL workflow đã kiểm qua MockMvc; chưa login hoạt động qua network JAR vì fixture không commit.
Process JAR đã dừng đúng process do tool tạo; kiểm tra port18081 không còn listener sau QA.
SELECT cuối: history13/checksum khớp, 42 bảng; business rows ngoài roles/history=0, seeded roles4.

JAR isolation **PASS**: 13 migration resource SHA-256 khớp source, release profile; không H2/DevTools/LocalDemo/
SqlServerQaTool/SqlServerBusinessSmoke/ReleaseSafetyTests/demo-local.properties/sqlserver-qa.properties/QA credential files.
All dbo FK/CHECK enabled/trusted, không disabled user-table indexes. Quét packaged config/private-key patterns: 0 literal-secret findings; không in giá trị.
Logs ngoài Git: TEMP/uteexpress-commit22-sql-jar-http.log, -sql-jar-startup.log (password redacted), -v13-final-inspect.log.
Full clean test/verify lần lượt2:49/2:38; Surefire XML1462/0failure/0error/1skip; JavaScript12/12 và WebSocket17/17.
SQL opt-in cũ vẫn skipped, không phải bằng chứng SQL; SQL evidence từ guarded tools và rollback smoke riêng.

Warnings còn tồn tại: open-in-view bật mặc định trong release profile; metadata repository HTTP401/cache,
Mockito/ByteBuddy dynamic agent Java21/unchecked test warnings. Không nới tests hoặc sửa cấu hình mặc định để che warnings.
**Database/SQL smoke/JAR technical gates PASS; full demo/release BLOCKED và production NOT READY.**
Cần browser/manual rehearsal, SQL demo data nếu được phép, upgrade legacy/backup/restore, worker/cross-connection concurrency,
performance/TLS/proxy/broker/DR trước các kết luận rộng hơn. Không dùng H2/MockMvc/HttpClient thay browser E2E.
## Lịch sử lượt vá V11 được phép — 28/10/2026

Người dùng xác nhận không có môi trường quản lý nào áp dụng V11 cũ; SSMS UTEExpressDB chỉ V1/V2,
test V1–V10, V11 rollback. Agent không dùng QA credential trên database ứng dụng.
JDBC xác minh local SQLEXPRESS / catalog và DB_NAME UTEExpress_Test, login uteexpress_qa, quyền DDL/metadata.
Preflight: 38 bảng, V1–V10 checksum đúng source, không partial V11; V10 checksum 236607534 giữ nguyên.

Runtime V11 trước vá: nested 207/S0001 trên 12 cột mới thuộc support_tickets/notifications.
Log trước chỉ giữ mã/cột, không còn raw message/stack trace. Không dựng message hoặc chạy lại DDL lỗi để lấy log.
SQL chính xác liên quan: CHECK category/source/version/managed/time/managed_state/assignee, filtered index request_token,
CHECK notifications_single_source/ticket_context và filtered index source_ticket_event_id dùng cột vừa ADD trong cùng batch.
Tên/kiểu cột ADD khớp Entity/V1/V10; parser một batch; chỉ thêm delimiter đã giúp target chạy thành công:
root cause compile/binding cột mới trong cùng batch có độ chắc chắn cao.

**Target V11 PASS lúc 13:42:54**: đúng một migration, history success=true/checksum 2051557719.
Hậu kiểm SQL PASS: 3 bảng mới/30 cột + 12 cột bổ sung, đúng types/length/nullable/identity/DATETIME2(7);
17 trusted NO ACTION FKs/đúng cột đích, 19 trusted CHECKs, 5 UTC/zero defaults, 3 PK mới,
ticket PK/unique code cũ retained, 21 index đúng key order/unique/filter (3 filtered unique).
Ba bảng mới và support_tickets 0 dòng; không INSERT/UPDATE/seed/backfill; chưa kiểm chứng upgrade có dữ liệu.
GO chỉ delimiter client, không đổi transaction/SQL nghiệp vụ. Source V1–V10/V12/V13 SHA-256 không đổi.
Flyway validateWithResult ở target V11 PASS; log “validated 13 migrations” chỉ resources/history, chưa đủ V13.

Công cụ SqlPending ban đầu dừng trước target V12 vì verifier V10 cố định 38 bảng sau khi V11 tạo 41.
Đã sửa số bảng kỳ vọng theo history (V10/V11-preflight=38, sau V11=41), giữ kiểm tra strict; không phải migration V12 FAIL.
Log guard cũ giữ riêng TEMP/-v12-preflight-guard.log. Regression đạt lại trước lần gọi tiếp.

**Target V12 FAIL lúc 13:45:43**, actual nested SQLException SQLState=S0001/errorCode=207:
Invalid column name 'request_token'. / Invalid column name 'request_fingerprint'.
Flyway báo rollback; dừng ngay ghi/migration, không sửa/retry V12, không chạy V13.
SELECT hậu kiểm: V1–V11 SUCCESS, V12/V13 PENDING/no history; 41 bảng; không support_ticket_routes,
hai cột message hoặc named constraints/defaults/keys/index V12; CHECK event action cũ còn trusted.
V11 schema/history còn nguyên. SQL business smoke/enum persistence/Commit 20–21 SQL regression và JAR SQL startup BLOCKED.

V12 đề xuất riêng trong bộ nhớ: GO sau dòng gốc 21, 1 → 2 transactional batch,
checksum -1357082083 → -1057022707. Source V12 chưa sửa; cần kiểm kê checksum/phê duyệt riêng trước retry.
V13 một batch/10 CREATE INDEX, checksum 1007916434, chưa thấy ADD/consumer cùng batch nhưng chưa chạy.
Browser E2E/manual rehearsal, upgrade dữ liệu/backup/restore, SQL concurrency và production readiness chưa kiểm chứng.

Targeted 53 safety + 11 mapping = **64/64 PASS**; giữ test cũ, thêm parser/checksum/history/consent.
Diagnostic mới chỉ trích message Invalid column name khớp allowlist từ SQLException thực; random secret suffix không bị in qua test.
JavaScript đã chạy lại: notifications 6 + operations 3 + reports 3 = **12/12 PASS**, không browser.

Lượt này sửa đúng 7 file hiện hữu: V11, SqlServerQaTool.java, ReleaseSafetyTests.java, release-tools.ps1 và ba tài liệu QA.
Không tạo/xóa file repository. Tổng so HEAD: 21 file = 6 tracked migrations modified (V3/V4/V8/V9/V10 đã có trước + V11)
và 15 untracked đã có trước; không staged/deleted. Danh sách đầy đủ trong File manifest.
Các mốc phía sau là lịch sử, không thay thế trạng thái mới nhất hoặc chứng minh SQL/browser PASS.
## Lịch sử regression ngoài SQL sau bản vá V11

Full clean test -Duteexpress.sqlserver.validate=false: **BUILD SUCCESS**, 1.458 tests / 1.457 passed /
0 failures / 0 errors / 1 skipped, **2:50**, hoàn tất 13:49:13 UTC+7.
Skip duy nhất là UteexpressApplicationTests.contextLoads, SQL opt-in cũ trỏ cấu hình ứng dụng, giữ tắt.
JavaScript chạy lại 12/12 PASS. Targeted parser/mapping/safety 64/64 PASS.
git diff --check, UTF-8/final newline/trailing whitespace cho 21 file, PowerShell AST parser PASS.
Hash đầu/cuối: đúng 7 file hiện hữu đổi, 12 migration ngoài V11 giữ nguyên; không tạo/xóa/stage file repository.
Full verify -Duteexpress.sqlserver.validate=false: **BUILD SUCCESS**, cùng 1.458 tests / 1.457 passed /
0 failures / 0 errors / 1 skipped, **2:39**, hoàn tất 13:52:16 UTC+7. Surefire XML tổng khớp và chỉ một case contextLoads skipped.
WebSocket **17/17 PASS**, JavaScript **12/12 PASS**; STOMP/network thật nhưng persistence mock, chưa SQL E2E.
JAR repackage/isolation PASS: release profile có, 13 migration resource SHA-256 khớp source hiện tại,
không H2/DevTools/LocalDemo/SqlServerQaTool/ReleaseSafetyTests/demo-local.properties/QA credential files.
Chưa SQL JAR startup. Logs ngoài Git: TEMP/uteexpress-commit22-v11-clean-test.log và -v11-verify.log.
Warnings còn tồn tại: Maven metadata repository HTTP 401/cache (không chặn build), Mockito/ByteBuddy dynamic agent Java 21,
unchecked test compilation và negative constraint/STOMP logs có chủ đích.
Những kết quả này không thay thế Hibernate/SQL smoke/JAR SQL startup/browser E2E.
## Lịch sử lượt vá V10 được phép — 28/10/2026

Điều kiện checksum: người dùng xác nhận dự án triển khai trên một máy, không môi trường khác áp dụng V10 cũ;
SSMS UTEExpressDB chỉ V1/V2. Agent không dùng QA credential để kiểm tra lại database ứng dụng.
JDBC xác minh đúng local SQLEXPRESS, catalog/DB_NAME UTEExpress_Test, login uteexpress_qa, db_owner/DDL/metadata permissions.
SELECT trước vá: V1–V9 đúng checksum, 38 bảng; không có 17 cột, 7FK/2CHECK/3DEFAULT/4index V10.

Root cause: V10 gốc là một batch; CHECK notification_assignment_status, CHECK audience và filtered index
WHERE source_event_id IS NOT NULL tham chiếu cột vừa ADD. Log runtime trước có 207/S0001 trên đúng ba cột;
không giữ raw exception message hoặc xác định một statement con duy nhất ngoài whole batch.
Chỉ thêm GO sau dòng gốc 10 và 25, không đổi SQL nghiệp vụ. Parser thực xác minh ba batch đều transactional,
strip GO trả SQL/checksum gốc; regression parser/ranh giới/history/consent đạt 61/61 (50 safety + 11 mapping).

**Target V10 PASS lúc 13:01:56**: một migration được thực thi, history/checksum 236607534 đúng source.
SELECT metadata đạt 17 cột/kiểu/nullable, 3 DEFAULT=0, 7 trusted FK NO ACTION/đúng cột đích,
2 trusted CHECK, 4 index đúng thứ tự cột và filtered unique. Bảng shipment_events/notifications đều 0 dòng;
không tạo fixtures/seed/backfill, chưa kiểm chứng upgrade dữ liệu legacy.
Flyway validateWithResult PASS ở target V10; log “validated 13 migrations” là kiểm tra resources/history,
không phải chứng minh đủ 13 version đã áp dụng hoặc Hibernate đạt.

**Target V11 FAIL lúc 13:02:29**, nested SQLException 207/S0001 trên category/source/version/handling_branch_id/
created_by/request_token/request_fingerprint/resolved_at/closed_at/warning_due_at/source_ticket_event_id/related_ticket_id.
Flyway báo rollback. Dừng ngay ghi/migration, không sửa/retry V11, không chạy V12/V13.
Hậu kiểm chỉ SELECT: V1–V10 SUCCESS, V11–V13 PENDING/no V11 history; 38 bảng; 12 cột/3 bảng và named
constraints/defaults/indexes V11 chưa tồn tại; CHECK trạng thái support_tickets cũ vẫn trusted; V10 còn nguyên.

Rà soát tĩnh: V11 một batch có hai nhóm ADD/consumer (đề xuất xem xét GO sau dòng 14 và 118);
V12 một batch có ADD request_token/request_fingerprint và CHECK/filtered index (xem xét sau dòng 21).
V13 chỉ thêm index, một batch, chưa thấy loại lỗi ADD/consumer này; chưa runtime. Giữ nguyên cả ba file.
Mọi patch tiếp theo cần chẩn đoán/kiểm kê checksum, kiểm parser và được phép riêng; không tự sửa hàng loạt.

Hibernate SQL validate/enum persistence, SQL smoke toàn module và regression Commit 20–21 trên SQL Server,
SQL JAR startup đều BLOCKED tới khi đủ V13. Browser E2E/manual rehearsal và upgrade/backup/locking cũng chưa kiểm chứng.
Không dùng thành công H2/HTTP/JS/WebSocket để đánh dấu các SQL gates.

Lượt này chỉnh 7 file hiện hữu: V10, SqlServerQaTool.java, ReleaseSafetyTests.java, scripts/release-tools.ps1
và ba tài liệu QA. Không tạo/xóa file repository. Tổng so HEAD: 20 file, gồm 5 tracked migrations sửa
(V3/V4/V8/V9 đã có trước và V10 mới) + 15 untracked đã có trước; 0 staged, 0 deleted.
Các công cụ QA/test và credential ngoài JAR; runtime logs ở TEMP. Danh sách đầy đủ trong manifest dưới đây.
## Lịch sử regression ngoài SQL sau bản vá V10

Full clean test với -Duteexpress.sqlserver.validate=false: **BUILD SUCCESS, 1.455 tests, 1.454 passed,
0 failures/errors, 1 skipped; 2:40**, hoàn tất 13:05:32 UTC+7. Test skipped là UteexpressApplicationTests.contextLoads,
SQL opt-in cũ dùng cấu hình ứng dụng; giữ tắt để không truy cập UTEExpressDB.
JavaScript chạy lại qua Node: **12/12 PASS** (notifications 6, operations 3, reports 3).
Full verify với -Duteexpress.sqlserver.validate=false: **BUILD SUCCESS, 1.455 tests, 1.454 passed,
0 failures/errors, 1 skipped; 2:54**, hoàn tất 13:08:54 UTC+7.
Đối chiếu toàn bộ Surefire XML: tổng 1.455/0 failures/0 errors/1 skipped; đúng một case contextLoads nêu trên.
WebSocket **17/17 PASS** trong cả clean test và verify; network/STOMP thật, persistence mock, không SQL E2E.
JAR repackage PASS: application-release.properties có mặt, 13 migration resource SHA-256 khớp source,
không H2/DevTools/LocalDemo/SqlServerQaTool/ReleaseSafetyTests/demo-local.properties/file QA credential.
Đây là packaging/isolation, chưa SQL JAR startup. Log Maven ở TEMP/uteexpress-commit22-v10-clean-test.log và -v10-verify.log.
git diff --check, UTF-8/final newline/trailing whitespace cho đủ 20 file và PowerShell AST parse PASS.
Hash trước/sau xác nhận đúng 7 file đổi; 12 migration ngoài V10 giữ nguyên. Không tạo/xóa/stage file repository.
Warnings còn tồn tại: metadata repository HTTP 401 (cache đủ, build đạt), Mockito/ByteBuddy dynamic agent Java 21,
unchecked test compilation và các negative constraint/STOMP-deny logs có chủ đích.
Các kết quả này không mở gate SQL Server hoặc browser E2E.
## Preflight và baseline

PATH Java: Temurin 21.0.12.1; JAVA_HOME/Maven: Oracle 21.0.2, đều Java 21. Maven Wrapper 3.10.0,
Spring Boot 4.0.8/Hibernate 7.2.24.Final. Cần thống nhất JAVA_HOME/PATH khi nhóm tái hiện, không tự sửa môi trường máy.
Preflight compile BUILD SUCCESS. Baseline chạy lại trước thay đổi: **1.389 tests, 1.388 passed,
0 failures, 0 errors, 1 skipped; clean test BUILD SUCCESS, 1 phút 58 giây**. Khớp Commit 21, không lấy lại mốc 1.309.

Microsoft SQL Server JDBC runtime **13.2.1.jre11** + flyway-sqlserver, H2 test dependency đã có; không đổi pom.
Datasource dev endpoint/database đã loại secret xem [SQL guide](sql-server-validation.md). Dev Flyway enabled, ddl-auto validate,
show_sql true; profile release mới tắt SQL logging và bắt buộc DB env không defaults. Không sửa application.properties.
Không có profile active/DB/SMTP env trong agent; server mặc định 8080, local demo 8081 hoặc random port ở test.
Assets Bootstrap/CSS/JS nội bộ và Thymeleaf templates đã có. Không đọc `.env` hoặc hiển thị connection secrets.

## Phần đã triển khai

- Explicit release profile: env DB URL/user/password, Hibernate validate/Flyway checksum/clean-disabled, logs/client errors/cookie guards.
- Test-only LocalDemoApplication và seed: H2 memory mới, loopback, config location riêng, không default/prod seed hoặc H2 console.
  Reference data dùng fixture cũ; 18 shipment scenarios qua real quote/lifecycle/transport/delivery/support services, real receipts/events.
- Seed idempotent cùng context thành công, từ chối resume partial failure; không reset database cũ hoặc direct SQL status update.
- Full embedded application HTTP integration: BCrypt login, role templates, assets, CSRF/logout/session, cross-user/support privacy.
- Test-only SQL QA tool và launcher: inspect/fresh/validate với explicit target/write attestation, actual catalog check, empty-table guard,
  không clean/repair/upgrade. Bổ sung SqlV3/SqlV4/SqlV8/SqlV9/SqlPending với write flag, history/checksum và partial-schema guards;
  không nới guard empty DB của SqlFresh. SqlInspect chỉ SELECT; exception chỉ báo SQLState/mã/cột allowlist.
- README, SQL validation/setup, demo setup/script/browser manual checklist và release gates.

Đã sửa V3 ba GO, V4 một GO, V8 ba GO, V9 ba GO, V10 hai GO và V11 hai GO theo phê duyệt, kiểm chứng SQL thật; V12 một GO đã PASS; V13 source giữ nguyên và PASS. Xem kết quả mới nhất ở đầu báo cáo.
Không có production business code thay đổi trong lượt này. Hai expected test mới trước đó được chỉnh theo HTTP server thật:
login redirect có absolute URL về đúng origin/account và Guest root 401. Không đổi production security/route để test pass.
Guard BCrypt demo dùng UTF-8 byte limit; không mật khẩu hardcode hoặc secret trong Git.

QA-22-01, lỗi trong entry point demo mới (không phải production regression): property source môi trường có thể override
server.address trong resource. Tái hiện an toàn bằng system property 127.0.0.2: HTTP test vào 127.0.0.1 lỗi ConnectException.
Đã pin loopback/config mode/Flyway disabled/H2 console disabled/SQL bind logs off/debug=false bằng launch arguments;
fixture giữ hostile loopback property để regression chứng minh entry point không bị override. Không từng bind public interface.
Môi trường có DEBUG được đặt từ bên ngoài; resource debug=false một mình chưa đủ ưu tiên, lệnh release cũng thêm --debug=false.

## Mốc full regression 26/10/2026, trước bổ sung DPAPI guards

Full clean test tại mốc này: **1.429 tests, 1.428 passed, 0 failures, 0 errors, 1 skipped; BUILD SUCCESS, 2 phút 19 giây**.
Không cộng các lượt chạy thành số case khác nhau.

| Kiểm tra ở mốc cũ | Actual result |
|---|---|
| `./mvnw.cmd clean test` | BUILD SUCCESS; 1.429 tests / 1.428 passed / 0 failures / 0 errors / 1 skipped; 2:19 |
| `./mvnw.cmd verify` | BUILD SUCCESS; cùng số test/kết quả; JAR + Boot repackage thành công; 2:10 |
| `./mvnw.cmd -DskipTests compile` | BUILD SUCCESS; up to date sau clean test/verify; 3,084 giây |
| JAR content inspection | PASS: profile release + 13 migration resource byte-identical; không H2/demo/test tools/DevTools |
| JavaScript Node runtime | 12/12 checks đạt; không browser |
| WebSocket/STOMP regression | 17/17 đạt; network thật, persistence mock |
| New HTTP/H2 tests | 16/16 đạt; embedded full app/repositories/BCrypt thật, không browser |
| New safety guards | 24/24 đạt; unit/mock, không SQL Server |
| git diff --check, UTF-8/whitespace/newline/Markdown links | PASS, gồm 14 untracked files |
| SQL Server fresh/validate/business smoke/JAR startup SQL | Chưa chạy ở mốc này; xem kết quả 27/10 bên dưới |
| SQL Server upgrade/backup | NOT TESTED |
| Browser E2E/manual demo/mobile UI | BLOCKED / NOT TESTED, browser inventory rỗng |

Surefire XML sau verify được đối chiếu tổng và số test mới. Một skipped là UteexpressApplicationTests.contextLoads
opt-in SQL Server cũ, không thêm skipped mới. File migration và test/source cũ không thay đổi.
Test mới: **40 cases** — LocalDemoQaTests **16** (HTTP thật + H2; không browser), ReleaseSafetyTests **24**
(unit/mock guards; không SQL Server). Cả 40 targeted tests đạt sau khi chốt source.
Không sửa/xóa/disable test cũ; một skipped SQL validate opt-in cũ vẫn giữ nguyên.
Số test cũ 1.389; test cũ sửa/xóa/disable mới: **0/0/0**. JavaScript đã chạy qua Node runtime công cụ
(Node CLI không có trên PATH): Notifications 6 + Operations 3 + Reports 3 = **12/12 checks đạt**.
`NotificationWebSocketTests` **17/17 đạt** trong clean test; script syntax parse PASS, missing-authorization launcher guard PASS.

Cross-module QA giữ regression Commit 01–21: User/Guest/pricing/fee snapshot, support/notifications, Manager/pickup/intake,
TransportLeg candidate query, forward/reverse/custody/receipt/discrepancy, delivery/return, Admin/audit/reports/CSV/security.
WebSocket/STOMP 17 tests cũ dùng network thật nhưng persistence mock; không tuyên bố network + SQL Server E2E.
Các ca H2/real repository không thay thế SQL Server transaction/collation/isolation/pessimistic lock behavior.

## Lịch sử SQL Server sau bản vá V3 ngày 27/10, browser, startup và demo

SQL Server Express service có; người dùng đã xác nhận tạo UTEExpress_Test trên (local)\SQLEXPRESS và cấp quyền
JDBC/fresh V1–V13/validate/smoke/JAR startup chỉ trong database này, không SQL demo seed. Credential DPAPI đã được cấp/import.
Các lần 26/10 gặp lỗi login 18456 rồi 18488; đã được khắc phục sau khi người dùng đổi password và tạo lại DPAPI.
Ngày 27/10, SqlInspect PASS: login uteexpress_qa; thực tế local SQLEXPRESS/port 1433/SQL Server 16.0;
JDBC catalog và DB_NAME() đều UTEExpress_Test; trước migration 0 objects/tables; db_owner, VIEW DEFINITION,
CREATE TABLE, ALTER dbo đều đạt. SELECT Unicode NVARCHAR(MAX)/DATETIME2 UTC đạt; chưa kiểm persistence entity.

Lần đầu V3 thất bại/rollback; bằng chứng phân tích tĩnh là 207/user_id, runtime SQL code không được log cũ lưu.
Người dùng sau đó xác nhận UTEExpressDB cũng chỉ có V1/V2 và nhóm không có DB cần bảo toàn checksum V3 cũ;
cho phép thêm đúng ba GO. Git diff xác nhận không đổi câu SQL khác. Checksum V3 -68259337 → -1720389847.
Parser Flyway thực xác nhận một batch cũ → bốn batch mới, đều transactional.

Target V3 thành công lúc 22:58:40. History V1=189739357, V2=1191373203 giữ nguyên; V3=-1720389847 success=true.
Đối chiếu sáu cột/kiểu/độ dài/nullable, bảng service area, năm trusted NO ACTION FKs, CHECKs và ba index;
filtered unique employees.user_id IS NOT NULL đạt. Không thay đổi mapping/Entity hoặc nghiệp vụ.

Pending target V13 bắt đầu từ V3 lúc 22:59:05, **V4 thất bại và rollback**. Runtime nested SQL exceptions:
**207/S0001, cột quote_id và pickup_fee**; không phải mã suy từ phân tích tĩnh. V4 thêm các cột rồi tham chiếu trong
filtered index/CHECK cùng batch. Dừng toàn bộ ghi; không retry/sửa V4, không clean/repair/reset.
SELECT sau lỗi xác nhận 20 bảng, history chỉ V1/V2/V3 thành công; V4–V13 PENDING. Không có các cột/bảng/FK/CHECK/index
mới của V4 được kiểm tra; V3 vẫn đầy đủ. Chi tiết từng migration nằm trong sql-server-validation.md.

**SQL database gate FAIL**: Hibernate validate/mapping/FK/index/enum đầy đủ, SQL business smoke,
Commit 20 TransportLeg/enum và Commit 21 authorization-cache regression trên SQL Server, SQL JAR startup đều BLOCKED.
Maven H2/offline regression được chạy riêng sau lỗi; không phải SQL Server validation. Upgrade NOT TESTED.
Không fallback, không sửa application.properties/pom, không ghi UTEExpressDB hoặc demo seed SQL Server.
SQL tool guard không credentials đã được kiểm thử ở mức unit; không suy SQL Server PASS từ đó.
Launcher thực tế đã chạy khi không có cấu hình SQL QA: compile/classpath thành công, tool từ chối missing approval trước connection.

Browser automation entry iab không khả dụng; CUA inventory apps/browsers đều rỗng. Không có browser screenshot/E2E/manual QA.
Checklist Chrome/Edge trong demo-script chỉ là hướng dẫn, chưa PASS. Không cài thêm browser hoặc vượt chính sách máy.
H2 demo startup/server/pages/roles qua HTTP đã chạy; chưa khởi động JAR với SQL Server thật. Không dùng tắt Flyway để chữa startup SQL.

Demo accounts 11, 5 branch/hub, 6 routes, service/rates/version/areas/support routing trong memory H2; 18 shipment scenarios,
evidence/proof/receipts/attempts/return/ticket/notice qua service. Giá mẫu trong DB demo, không fallback giá của production.
Không có seed SQL Server; không production Admin/default password. Local SMTP không gửi, register/OTP mail chưa demo.

## Warnings, blockers và quyết định

Warnings baseline: Maven Flyway metadata HTTP 401 (build vẫn thành công); Mockito/ByteBuddy dynamic agent Java 21;
unchecked LastMile test; SQLServerDialect offline test. Negative constraint/STOMP-deny/worker-deferred logs có chủ đích.
PowerShell policy máy chặn chạy .ps1 trực tiếp; lệnh tiến trình riêng trong setup không đổi policy máy. Nếu tổ chức cấm, dùng direct Java.

Blockers hiện tại: browser/manual rehearsal, SQL demo dataset chưa được phép, upgrade/backup có dữ liệu;
SQL migration/Flyway/Hibernate/business smoke/JAR startup và HTTP đã PASS trong scope mô tả ở đầu báo cáo;
browser rehearsal/responsive/JS observation; SQL demo data. Upgrade/backup chưa có môi trường riêng.
Known limitations: owner timeline content policy; immediate global session/in-flight revocation; report interleaving snapshot;
production TLS/cookie/proxy/broker/distributed security, performance/plans và DR chưa xác minh.

**Release recommendation: NOT READY** cho full SQL Server release/demo; H2 service/HTTP demo là tập con đã xác minh.
Production readiness NOT READY. Chi tiết từng gate ở [release checklist](release-checklist.md); các bước nhóm cần chạy ở
[SQL guide](sql-server-validation.md), [demo setup](demo-setup.md) và [manual demo/UI checklist](demo-script.md).

## File manifest

**17 untracked + 7 tracked migrations modified = 24 file so HEAD; 0 deleted/staged.**
Lượt V12 sửa7 file hiện hữu và tạo2 file test-only, không xóa file repository.

~~~text
README.md
docs/demo-script.md
docs/demo-setup.md
docs/final-qa-report.md
docs/release-checklist.md
docs/sql-server-validation.md
scripts/release-tools.ps1
scripts/set-sql-qa-credential.ps1
src/main/resources/application-release.properties
src/test/java/com/ute/uteexpress/operations/LocalDemoApplication.java
src/test/java/com/ute/uteexpress/operations/LocalDemoQaTests.java
src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java
src/test/java/com/ute/uteexpress/release/ReleaseSafetyTests.java
src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java
src/test/java/com/ute/uteexpress/release/SqlServerQaTool.java
src/test/resources/demo-local.properties
src/test/resources/sqlserver-qa.properties
~~~

Tracked file sửa: `src/main/resources/db/migration/V3__extend_shipper_identity_and_shipment_lifecycle.sql` — chỉ thêm ba GO.
Và `src/main/resources/db/migration/V4__add_pickup_pricing_and_shipment_quotes.sql` — chỉ thêm một GO sau ADD quote_id.
Và `src/main/resources/db/migration/V8__add_last_mile_delivery_and_returns.sql` — chỉ thêm ba GO ở nhóm cột đã phân tích.
Và `src/main/resources/db/migration/V9__add_service_catalog_and_pricing_management.sql` — chỉ thêm ba GO sau nhóm ADD COLUMN của ba bảng.
Và `src/main/resources/db/migration/V10__add_shipment_notifications.sql` — chỉ thêm hai GO sau hai nhóm ADD COLUMN.
Và `src/main/resources/db/migration/V11__add_operations_tickets.sql` — chỉ thêm hai GO sau dòng gốc 14 và 118.
Và `src/main/resources/db/migration/V12__add_customer_support_portal.sql` — chỉ thêm một GO sau dòng gốc 21.

`git diff --stat` không bao gồm nội dung 17 untracked files nên không dùng để kết luận không có thay đổi.
Đối chiếu thêm git status --untracked-files=all và git ls-files --others --exclude-standard.
Log đã loại credential nằm TEMP với tiền tố uteexpress-commit22-, XML trong target/surefire-reports.
Kiểm UTF-8/final newline/trailing whitespace và Markdown links cho cả untracked; script được parse syntax và chạy guard thực.

## Lịch sử 26/10: bổ sung sau xác nhận database và lần JDBC login thất bại

Script secure prompt tạo PSCredential Export-Clixml/DPAPI trong TEMP ngoài Git; password không được in hoặc hardcode.
Thêm guard scope UTEExpress_Test/SQLEXPRESS/local endpoint, DB_NAME/catalog, instance identity và full metadata visibility.
Latest `ReleaseSafetyTests`: **34 passed, 0 failures/errors/skipped**, unit/mock, không SQL Server.
Các lượt full clean test/verify 1.429 ở trên là mốc trước bổ sung guard này; **chưa chạy lại full suite sau bổ sung DPAPI**.
Không coi tổng Surefire trộn giữa targeted run/full run là một full regression mới. SQL target/empty trước migrate đã PASS ngày 27/10;
fresh migrate FAIL V3, Hibernate validate vẫn BLOCKED. Các đoạn dưới là lịch sử 26/10, không phải kết quả hiện tại.

Lần tiếp tục SQL: chỉ sửa SqlServerQaTool để SqlInspect in count user objects và quyền CREATE_TABLE/ALTER_DBO cùng
VIEW_DEFINITION khi SELECT thành công. Maven test-compile/build-classpath BUILD SUCCESS (3,781 giây);
JDBC login bị từ chối trước các SELECT này, không có kết quả count/quyền. Không chạy lại full tests/verify sau failure,
không sửa application.properties, pom hoặc migrations; không chạy SQL demo seed. Không phát hiện production code/schema bug mới;
lỗi 18456 ở lần đó chưa xác định được Reason/State server-side. Không ghi credential vào log/Git.

Lượt tiếp tục với uteexpress_qa: SqlServerQaTool khóa login trước JDBC và kiểm ORIGINAL_LOGIN/db_owner sau connection;
ReleaseSafetyTests bổ sung hai negative cases từ chối sa/login khác. Không kết nối thành công nên các SELECT này chưa chạy.
Lỗi mới 18488 có nghĩa password phải đổi, khác lỗi 18456 chung trước đó; hướng xử lý trong sql-server-validation.md.
Không thực hiện giai đoạn 2–3, không tự đổi password hoặc tắt policy, không chạy SQL demo seed.

## Lịch sử lượt tiếp tục 27/10/2026, 22:34 — trước khi được duyệt sửa V3

Chỉnh 4 file Commit 22 đã tồn tại dưới dạng untracked: SqlServerQaTool.java và ba tài liệu sql-server-validation.md,
final-qa-report.md, release-checklist.md. Tool chỉ bổ sung chẩn đoán read-only trong SqlInspect sau history V2;
không thêm đường chạy migration/ghi hoặc phụ thuộc mới. Không thêm/xóa file trong lượt này.
Tổng working tree vẫn 15 file mới so với HEAD, 0 tracked file sửa, 0 file xóa/staged; manifest bên trên vẫn áp dụng.
Kiểm tra cuối: `git diff --check` PASS; đối chiếu Git cho pom/application.properties/migrations không có thay đổi;
15 untracked files đều UTF-8 hợp lệ, không trailing whitespace và có final newline. Không stage/commit/push/merge/PR.

## Lượt vá V3 được duyệt — 27/10/2026, 22:58 trở đi

Chỉnh 7 file trong lượt này; không thêm hoặc xóa file repository:

- src/main/resources/db/migration/V3__extend_shipper_identity_and_shipment_lifecycle.sql: đúng ba GO, không đổi SQL khác.
- src/test/java/com/ute/uteexpress/release/SqlServerQaTool.java: target V3/pending V13, history/checksum/schema guards,
  nested SQL diagnostics có allowlist, kiểm tra rollback V4 chỉ đọc; bỏ DMV phân tích script đã có GO.
- src/test/java/com/ute/uteexpress/release/ReleaseSafetyTests.java: thêm 5 cases (34 → 39), gồm parser/checksum,
  write attestation, history từng stage và nested diagnostic không lộ raw message.
- scripts/release-tools.ps1: expose SqlV3/SqlPending, giữ DPAPI runtime/finally.
- docs/sql-server-validation.md, docs/final-qa-report.md, docs/release-checklist.md: trạng thái thực tế mới nhất.

Targeted trước migration: **50/50 PASS** (11 CoreDatabaseMappingTests + 39 ReleaseSafetyTests), 11,947 giây.
Full `mvnw.cmd clean test -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, 1.444 tests,
1.443 passed, 0 failures, 0 errors, 1 skipped; 3 phút 12 giây, kết thúc 23:03:57 UTC+7.
Property giữ test SQL opt-in cũ trỏ UTEExpressDB ở trạng thái tắt như mặc định; không thêm skipped mới.
Đây là toàn bộ suite H2/offline/mock/embedded HTTP, không phải SQL business smoke hoặc Hibernate SQL validation.
Các regression Logistics/Transport/Delivery/Returns/Support/Security/Reports/Audit nằm trong suite này.

V3 SQL PASS không mở gate V13: V4 runtime FAIL, V5–V13 chưa chạy. Không tự vá V4, không retry hoặc khởi động JAR
với SQL Server vì startup có thể retry migration. Không demo seed SQL Server hoặc thay đổi cấu hình mặc định.

Full `mvnw.cmd verify -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, 1.444 tests,
1.443 passed, 0 failures/errors, 1 skipped; 3 phút 04 giây, kết thúc 23:07:24 UTC+7. Surefire XML đối chiếu đúng tổng,
skipped duy nhất UteexpressApplicationTests.contextLoads. Không cộng hai lượt full run thành số test mới.
JAR uteexpress-0.0.1-SNAPSHOT.jar đóng gói/repackage thành công; SHA-256 của 13 migration resources trong JAR
khớp source hiện tại (bao gồm V3 đã vá), có application-release.properties; không H2/DevTools/demo/test tooling.
Đây là kiểm tra nội dung JAR, không phải startup JAR với SQL Server.

Kiểm tra cuối: git diff --check PASS; UTF-8/trailing whitespace/final newline cho cả 16 file thay đổi PASS;
PowerShell launcher parse PASS. Tracked diff V3 là 3 insertions/0 deletions; V1/V2, V4–V13, pom và cấu hình mặc định
không đổi. Không thêm/xóa file trong lượt vá; 7 file hiện hữu được chỉnh, tổng working tree 15 untracked + 1 tracked modified.
Không stage/commit/push/merge hoặc tạo PR. Cảnh báo còn lại: Maven metadata Flyway repository HTTP 401 và Mockito/ByteBuddy
dynamic agent Java 21, đều không làm build thất bại. Blocker release hiện tại là migration V4 và các SQL/browser gates chưa đạt.

Log TEMP mới: uteexpress-commit22-v3-clean-test.log, uteexpress-commit22-v3-verify.log; log migration và targeted tests
được liệt kê trong sql-server-validation.md. Không ghi SQL credential/password/XML vào repository hoặc báo cáo.

## Lượt điều tra và vá V4 theo điều kiện được duyệt — 28/10/2026

Đã đọc V4/V1–V3, xác minh runtime 207/S0001 từ log V4 cũ, parser một batch và tham chiếu cột mới ở FK/index/CHECK.
Không UPDATE/INSERT/DELETE dữ liệu trong V4. Đã báo trước bản vá/chính sách checksum; thông tin SSMS người dùng cho biết
UTEExpressDB chỉ có V1/V2, test V4 rollback/no history, không có bằng chứng DB cần giữ checksum V4 cũ. Không truy cập bằng credential khác
hoặc nâng quyền QA. Không tuyên bố đã kiểm kê mọi database ngoài phạm vi đã biết.

V4 chỉ thêm GO sau ADD quote_id; checksum -2103371131 → -1466061241, parser 1 → 2 batch transactional.
Target V4 SQL PASS; đối chiếu hai bảng mới, ba cột/default/nullability, 11 FKs, 14 CHECKs, bốn index,
filtered unique quote_id IS NOT NULL và key column. Không seed bảng giá/quote; legacy snapshots giữ NULL;
không có legacy shipment fixture để kiểm chứng business compatibility SQL đầy đủ.

Từng target V5/V6/V7 migrate và validate PASS. V8 là lỗi đầu tiên, runtime **207/S0001**, rollback; dừng ghi ngay,
không chạy V9–V13, không sửa V8. Column runtime hiện unclassified do allowlist; không suy ra statement/tên cột chính xác.
SELECT hậu kiểm xác nhận 31 bảng, history V1–V7 SUCCESS, V8–V13 PENDING/no history. Không có bảy cột V8 mới,
sáu bảng V8, các FK/CHECK và bốn index trên bảng cũ được kiểm tra. V4 và dữ liệu V1–V7 giữ nguyên sau rollback.
Chi tiết từng migration/checksum ở sql-server-validation.md. Hibernate SQL validate/SQL smoke/regression/SQL JAR startup BLOCKED.

Targeted regression **52/52 PASS**: 11 CoreDatabaseMappingTests + 41 ReleaseSafetyTests, 13,682 giây.
Tăng 2 cases so baseline 1.444: một case write attestation cho mode v4 và một parser/checksum/batch V4;
history guard tests hiện hữu được mở rộng cho V1–V4/không được bỏ qua target V4.
JavaScript chạy lại bằng Node tool runtime (Node CLI không có trên PATH): Notifications 6 + Operations 3 + Reports 3 = **12/12 PASS**.
Không browser E2E hoặc SQL persistence trong các ca JavaScript.

Lượt này sửa 7 file hiện hữu, không thêm/xóa file repository:

- src/main/resources/db/migration/V4__add_pickup_pricing_and_shipment_quotes.sql.
- src/test/java/com/ute/uteexpress/release/SqlServerQaTool.java.
- src/test/java/com/ute/uteexpress/release/ReleaseSafetyTests.java.
- scripts/release-tools.ps1.
- docs/sql-server-validation.md.
- docs/final-qa-report.md.
- docs/release-checklist.md.

V1–V3 không đổi so với đầu lượt này; V5–V13/pom/application.properties không đổi. Tổng working tree vẫn 15 untracked
cộng 2 tracked modified từ các lượt Commit 22, không file xóa/staged. Không commit/push/merge/PR.

Full `mvnw.cmd clean test -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, 1.446 tests, 1.445 passed,
0 failures/errors, 1 skipped; 1 phút 57 giây, kết thúc **28/10/2026 09:31:29 UTC+7**. Giữ test SQL opt-in cũ trỏ UTEExpressDB tắt;
không sửa/xóa/disable test cũ hoặc thêm skipped mới. Toàn bộ regression không dùng SQL Server đạt, không đồng nghĩa SQL smoke PASS.

Full `mvnw.cmd verify -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, cùng 1.446 tests/1.445 passed/0 failures/errors/1 skipped;
1 phút 47 giây, kết thúc **28/10/2026 09:34:06 UTC+7**. Surefire XML đối chiếu đúng tổng; skipped duy nhất
UteexpressApplicationTests.contextLoads, là SQL opt-in cũ. Không cộng số tests giữa các lượt chạy.
Boot JAR packaging/repackage đạt. SHA-256 của đủ 13 migration trong JAR khớp source hiện tại gồm V3/V4 đã vá;
có release profile, không H2/DevTools/demo/test tooling. Chưa startup JAR với SQL Server vì sẽ retry V8.

Git diff V4 chỉ 1 insertion/0 deletions. Đối chiếu SHA-256 file trước/sau lượt xác nhận chính xác 7 file đã sửa,
0 file mới/xóa; V3 không đổi. Tổng so HEAD: 15 untracked + 2 tracked modified. git diff --check và UTF-8/trailing whitespace/final newline
cho 17 file thay đổi PASS; PowerShell launcher parse PASS. Không sửa pom/config mặc định hoặc ghi UTEExpressDB.

**Release BLOCKED / NOT READY**: migration gate FAIL V8; Hibernate SQL validate, SQL smoke/regression,
JAR SQL startup còn BLOCKED. Cần điều tra riêng V8, giải quyết migration từng bước đến V13 trên DB hiện có, giữ V1–V7;
sau đó thực hiện SQL/business/startup QA và đánh giá các browser/deployment gates. Không reset/clean/repair hoặc tự seed demo SQL Server.
Warnings còn lại: Maven Flyway community metadata HTTP 401, Mockito/ByteBuddy Java agent Java21 và các warning baseline;
build vẫn thành công. Log TEMP mới: uteexpress-commit22-v4-clean-test-20261028.log và uteexpress-commit22-v4-verify-20261028.log.

## Lượt áp dụng V8 sau xác nhận tương thích — 28/10/2026, 10:31

Người dùng rút lựa chọn chỉ báo cáo và xác nhận toàn bộ dự án chạy một máy, các GitHub account không đại diện môi trường DB khác;
không DB nào đã áp dụng V8 cũ. UTEExpressDB chỉ V1/V2; test V1–V7/V8 rollback. Đã cho phép đúng ba GO, không cần repair checksum.
Không đọc UTEExpressDB bằng login QA hoặc nâng quyền. Ba GO sau dòng gốc 170/176/182; checksum -1022568645 → 2036200466;
parser 1 → 4 transactional batches. Bỏ ba GO phục hồi checksum gốc; diff không có thay đổi SQL khác.

Runtime cũ chỉ lưu 207/S0001; tên delivered_at/returned_at và assignment_id được tìm từ chẩn đoán tĩnh, không giả làm runtime cũ.
Target V8 thực tế SUCCESS lúc 10:31:40, xác nhận bản sửa trên SQL Server thật. Trước ghi, JDBC identity đúng uteexpress_qa,
local SQLEXPRESS/port1433/UTEExpress_Test, history V1–V7 checksum đúng và không partial V8. Sau ghi V1–V7 giữ nguyên,
V8 history success=true/checksum2036200466; 37 bảng. Kiểm đủ sáu bảng/65 tên cột, bảy cột thêm đúng kiểu/nullable,
31 enabled/trusted NO ACTION FKs, 23 CHECKs, 12 PK/UQ, 13 index và bốn filtered unique predicates. Sáu bảng mới rỗng,
không seed proof/return/demo hoặc suy diễn dữ liệu lịch sử. Không có legacy fixture SQL để tuyên bố đầy đủ business compatibility.

Tiếp tục từng target từ V9; **V9 FAIL lúc 10:32:04**, rollback, runtime 207/S0001 cho max_weight_kg/max_length_cm/
max_width_cm/max_height_cm/retired_from/pricing_version_id. Dừng ngay, không sửa/retry V9, không chạy V10–V13.
SELECT sau lỗi xác nhận V1–V8 SUCCESS, V9–V13 PENDING/no history V9; 37 bảng. Mười một cột mới, pricing_versions,
named FK/CHECK/index V9 được kiểm tra đều chưa tồn tại; UQ_pickup_fee_rules_start và CK_audit_logs_resource cũ giữ nguyên.
V8 còn đầy đủ. Hibernate SQL validate, SQL smoke/regression và SQL JAR startup BLOCKED đến khi đủ V13.

Targeted regression **55/55 PASS** (44 ReleaseSafetyTests + 11 CoreDatabaseMappingTests), 12,263 giây.
Tăng ba cases so baseline 1.446: write attestation mode v8, guard V1–V7/V8 checksum và parser/batch/checksum V8;
diagnostic test hiện hữu mở rộng returned_at và vẫn không lộ raw message/secret. JavaScript chạy lại **12/12 PASS** qua Node runtime,
không browser hoặc SQL persistence.

Lượt này sửa 7 file hiện hữu, không tạo/xóa file repository:

- src/main/resources/db/migration/V8__add_last_mile_delivery_and_returns.sql.
- src/test/java/com/ute/uteexpress/release/SqlServerQaTool.java.
- src/test/java/com/ute/uteexpress/release/ReleaseSafetyTests.java.
- scripts/release-tools.ps1.
- docs/sql-server-validation.md.
- docs/final-qa-report.md.
- docs/release-checklist.md.

Chỉ migration V8 đổi trong lượt này; snapshot hash V1–V7 và V9–V13 dùng để đối chiếu sau cùng. Tổng working tree 15 untracked
cộng 3 tracked modified (V3/V4 từ lượt trước, V8 mới), không staged/deleted. Không commit/push/merge/stash/PR.

Full `mvnw.cmd clean test -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, 1.449 tests/1.448 passed/
0 failures/errors/1 skipped, **2 phút 40 giây**, kết thúc 28/10/2026 **10:36:24 UTC+7**. SQL opt-in cũ trỏ UTEExpressDB
giữ tắt; không thêm skipped mới. Toàn suite là regression không dùng SQL Server, không thay thế SQL smoke bị BLOCKED.

Full `mvnw.cmd verify -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, cùng 1.449 tests/1.448 passed/
0 failures/errors/1 skipped, **2 phút 33 giây**, kết thúc 28/10/2026 **10:39:27 UTC+7**. Surefire XML tổng khớp;
skipped duy nhất UteexpressApplicationTests.contextLoads, không sửa test cũ. WebSocket 17/17 đạt (network thật/persistence mock),
JavaScript đã chạy lại 12/12; không SQL persistence/browser QA trong các kết quả này. Không cộng số test giữa các lượt.

JAR build/repackage đạt; đủ 13 migration resources SHA-256 khớp source hiện tại gồm V8 đã vá, có release profile;
không H2/DevTools/demo/test tooling hoặc file credential QA. Kiểm nội dung JAR không thay thế startup SQL; startup SQL chưa chạy
để tránh tự retry V9. Không tắt Flyway hoặc dùng ddl-auto=update để mở gate.

Hash trước/sau xác nhận chính xác 7 file sửa, 0 file tạo/xóa; V1–V7 và V9–V13 không đổi. git diff --check,
UTF-8/trailing whitespace/final newline cho 18 file thay đổi/untracked và PowerShell launcher parse PASS.
V3/V4 là thay đổi từ lượt trước, không chỉnh thêm trong lượt V8. Không stage/commit/push/merge/stash/PR.
SQL write dừng sau V9 rollback; hậu kiểm chỉ SELECT. Không ghi UTEExpressDB hoặc seed demo SQL Server.

**Release BLOCKED / NOT READY**: V8 fix đã PASS nhưng V9 migration gate FAIL; cần điều tra/bản vá V9 riêng rồi tiếp tục
từng target đến V13 trên test DB hiện có, giữ V1–V8. Hibernate SQL validate, SQL business smoke/regression, lỗi production Commit20–21
trên SQL Server và SQL JAR startup vẫn BLOCKED. Các browser/deployment/backup gates chưa có bằng chứng đầy đủ như checklist.
Log TEMP mới: uteexpress-commit22-v8-clean-test.log và uteexpress-commit22-v8-verify.log; log SQL/targeted ở sql-server-validation.md.

## Lượt vá V9 sau xác nhận SSMS mới — 28/10/2026, 12:19

Đã đọc V9 và các schema liên quan từ V1/V4; runtime 207/S0001 nêu sáu cột max_weight_kg/max_length_cm/max_width_cm/
max_height_cm/retired_from/pricing_version_id. Các nhóm ADD COLUMN được dùng ngay trong FK/CHECK/index cùng batch.
Không UPDATE/INSERT/DELETE/seed trong V9. Trước sửa, test JDBC/history/rollback PASS; audit history ứng dụng bằng Windows auth
không đọc được (18452), sau lỗi cấu hình client ban đầu được sửa an toàn. Không nâng quyền/thử credential khác hoặc đọc ứng dụng bằng QA.
Agent dừng đề xuất và hỏi; người dùng xác minh SSMS lại: UTEExpressDB vẫn chỉ V1/V2 SUCCESS, không V9. Cùng thông tin một máy/không
môi trường V9 khác đã biết, đáp ứng gate tương thích trong phạm vi đã xác minh; không giả định agent đã SELECT thành công trên ứng dụng.

Chỉ thêm đúng ba GO sau dòng gốc 8/45/51. Checksum **1441625975 → 61067824**, parser **1 → 4** transactional batches;
regression bỏ ba GO phục hồi checksum gốc và xác minh từng batch định nghĩa cột trước các ràng buộc sử dụng.
Target V9 SQL **PASS lúc 12:19:45**, V1–V8 giữ nguyên history/checksum, V9 success=true/checksum61067824. Kiểm pricing_versions/15 tên cột,
11 cột thêm đúng kiểu/nullability/default0, tám trusted NO ACTION FKs, 10 CHECKs, hai PK/UQ, sáu index/ba filtered unique.
Legacy uniqueness thay bằng filtered index đúng source; audit CHECK mở rộng đúng ba resource mới. Không pricing seed/version link backfill.
Không legacy fixture SQL để tuyên bố đầy đủ tương thích nghiệp vụ. 38 bảng sau V9.

Target V10 **FAIL lúc 12:20:20**, runtime **207/S0001**, cột notification_assignment_status/audience/source_event_id; Flyway rollback.
Dừng ngay, không sửa/retry V10, không chạy V11–V13. Hậu kiểm SELECT xác nhận V1–V9 SUCCESS, V10–V13 PENDING/no V10 history,
38 bảng; không có 17 cột mới, bảy FK/hai CHECK/ba default và bốn index V10 được kiểm tra. V9 còn đầy đủ.
Hibernate SQL validate, SQL smoke/regression và SQL startup JAR BLOCKED cho đến đủ V13; không bypass bằng schema update/tắt Flyway.

Targeted regression **58/58 PASS** (47 ReleaseSafetyTests + 11 CoreDatabaseMappingTests), 22,525 giây.
Tăng ba cases so mốc 1.449: mode v9 write consent, history V1–V8/V9 checksum, parser/batch/checksum V9.
JavaScript chạy lại **12/12 PASS** bằng Node runtime (6 notifications + 3 operations + 3 reports), không browser/SQL persistence.

Lượt này sửa 7 file hiện hữu, không tạo/xóa file repository:

- src/main/resources/db/migration/V9__add_service_catalog_and_pricing_management.sql.
- src/test/java/com/ute/uteexpress/release/SqlServerQaTool.java.
- src/test/java/com/ute/uteexpress/release/ReleaseSafetyTests.java.
- scripts/release-tools.ps1.
- docs/sql-server-validation.md.
- docs/final-qa-report.md.
- docs/release-checklist.md.

Snapshot hashes V1–V8/V10–V13 dùng để xác minh không đổi ngoài V9. Tổng so HEAD: 15 untracked + 4 tracked modified (V3/V4/V8 từ trước,
V9 lượt này), không staged/deleted. Không Git commit/push/merge/stash/PR; không ghi UTEExpressDB hoặc seed SQL demo.

Full `mvnw.cmd clean test -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, 1.452 tests/1.451 passed/
0 failures/errors/1 skipped, **3 phút 54 giây**, kết thúc 28/10/2026 **12:25:44 UTC+7**. SQL opt-in cũ trỏ UTEExpressDB
giữ tắt như mặc định, không sửa test cũ hoặc thêm skipped mới. Đây là regression không SQL Server, không phải SQL smoke PASS.

Full `mvnw.cmd verify -Duteexpress.sqlserver.validate=false`: **BUILD SUCCESS**, cùng 1.452 tests/1.451 passed/
0 failures/errors/1 skipped, **3 phút 41 giây**, kết thúc 28/10/2026 **12:29:45 UTC+7**. Surefire XML đối chiếu đúng tổng,
skipped duy nhất UteexpressApplicationTests.contextLoads. WebSocket 17/17 đạt (network thật/persistence mock), JavaScript 12/12 chạy lại đạt.
Không cộng số cases giữa các lượt. Negative STOMP-deny log không phải failed test; không production SQL binding/concurrency evidence.

JAR build/repackage đạt; 13 migration resources SHA-256 khớp source hiện tại gồm V9 đã vá, có release profile;
không H2/DevTools/demo/test tooling/file credential QA. Chưa startup SQL vì sẽ retry V10; không tắt Flyway hoặc dùng schema update.
Hash trước/sau xác nhận đúng 7 file sửa, 0 file mới/xóa; migration ngoài V9 giữ nguyên. git diff --check,
UTF-8/trailing whitespace/final newline cho 19 file thay đổi/untracked và PowerShell launcher parse PASS.
V3/V4/V8 là thay đổi từ lượt trước, không sửa thêm. Không Git stage/commit/push/merge/stash/PR, không ghi UTEExpressDB.

**Release BLOCKED / NOT READY**: V9 fix PASS nhưng V10 migration gate FAIL. Cần điều tra và bản vá V10 riêng, giữ V1–V9,
tiếp tục từng target tới V13 rồi Flyway validate/Hibernate SQL validate/SQL business smoke/Commit20–21 regression/SQL JAR startup.
Các browser/deployment/backup gates cũng cần bằng chứng riêng. Không clean/repair/reset hoặc tự demo seed SQL Server.
Log TEMP: uteexpress-commit22-v9-clean-test.log, uteexpress-commit22-v9-verify.log; log SQL/targeted liệt kê trong sql-server-validation.md.
