# SQL Server QA V15 — nghiệm thu PASS

Ngày: 2026-10-29. Branch: `feature/auto-intake-receipts`.

**Hiện trạng: SQL Server V15 PASS trong phạm vi QA cô lập.** Chủ dự án xác nhận lần Run thực tế kết thúc:

```text
RESULT PASS required-gates migration-rollback=PASS database-retained-no-cleanup
```

Nguồn bằng chứng SQL: kết quả Run và xác nhận toàn bộ gate do người dùng cung cấp; đối chiếu control flow của `V15SqlServerQaTool` hiện tại. Dòng RESULT chỉ xuất hiện sau history/validate cuối, schema checks, bảo toàn legacy và service journey; `migration-rollback=PASS` chỉ đặt sau fault injection đủ proof và kiểm tra rollback. Lượt cập nhật tài liệu này **không chạy lại Run, Inspect hoặc Preflight, không kết nối SQL Server/import DPAPI**. Không có timestamp Run, transcript SQL đầy đủ hoặc SELECT sau Run mới để bổ sung; không tự tạo số liệu đó.

Database QA V15 đã dùng, có migration history và fixture; **không còn trống, không chạy lại fresh Run hoặc Preflight, không clean/repair/reset/drop/seed**. Giữ nguyên cả QA V14/V15 và ba database được bảo vệ. Không thay đổi ứng dụng cổng 8083.

## 1. Mục tiêu đã nghiệm thu qua helper

| Thuộc tính | Giá trị cố định trong helper đã Run PASS |
|---|---|
| Máy Windows/SQL Server | `DESKTOP-31GJQ3T` |
| Instance | `SQLEXPRESS` |
| Endpoint bắt buộc | `127.0.0.1:1433` |
| Database QA đã sử dụng | `UTEExpress_V15_QA_20261029` |
| SQL login riêng | `uteexpress_v15qa` |
| DPAPI riêng | `%TEMP%\uteexpress-v15-qa.credential.xml` |
| Phạm vi quyền | `public` ở server; `db_owner` chỉ tại database QA V15 |

Run PASS xác nhận guard identity/quyền/trống trước bước ghi đã vượt qua trên đúng target cố định. Database/login hiện đã tồn tại; không tái sử dụng cho một lần fresh Run khác hoặc thay credential. Không kiểm tra lại bằng JDBC trong lượt này.

Bốn database khác được bảo vệ: `UTEExpressDB`, `UTEExpress_Test`, `UTEExpress_Demo`, `UTEExpress_V14_QA_20261029_7dc87af7`. Database QA V14 đã có 42 bảng/dữ liệu nghiệm thu: không dùng lại cho V15. Lượt cập nhật này không đọc hoặc ghi bất kỳ database nào, kể cả QA V15.

## 2. Review migration nguyên bản

File: `src/main/resources/db/migration/V15__add_nationwide_shipping_pricing.sql`. Không sửa V15 hoặc V1–V14.

| Kiểm tra offline | Kết quả |
|---|---|
| Flyway checksum V15 | `347627752` — tính bằng `ChecksumCalculator` thực tế |
| SHA-256 V15 | `faa2cf1cb51b3407b0fe642640b5479e99ee3709e1e8b2b301b84695e1696367` |
| SQL Server parser Flyway | Hai batch; cả hai `canExecuteInTransaction=true` |
| Ranh giới batch | `GO` sau toàn bộ ADD COLUMN, trước hai CHECK snapshot |
| DML/backfill/seed | Không có |
| SQL runtime | PASS theo kết quả thực tế người dùng cung cấp; không cần sửa migration |

`CK_rate_rules_scope` giữ ba giá trị cũ `INTRA_PROVINCE`, `INTER_PROVINCE`, `INTER_REGION`, thêm bốn giá trị `SAME_REGION`, `NORTH_CENTRAL`, `CENTRAL_SOUTH`, `NORTH_SOUTH`. Có bảy giá trị SQL hợp lệ; năm nhóm giá toàn quốc gồm `INTRA_PROVINCE` và bốn nhóm mới. Không chuyển đổi ngầm giá legacy sang nhóm mới.

`pickup_fee_rules.branch_id` đổi đúng kiểu `BIGINT` từ NOT NULL thành NULL để biểu diễn phụ phí mặc định. Ba FK branch/service/pricing version giữ nguyên. Bốn index phụ thuộc được drop/recreate cùng tên, key, filter và tính UNIQUE:

- `IX_pickup_fee_rules_lookup(branch_id, service_id, is_active, effective_from, effective_to)`.
- `IX_pickup_rules_version(pricing_version_id, branch_id)`.
- `UX_pickup_legacy_start(branch_id, service_id, effective_from) WHERE pricing_version_id IS NULL`.
- `UX_pickup_rules_version_branch(pricing_version_id, branch_id) WHERE pricing_version_id IS NOT NULL`.

SQL Server coi hai tổ hợp key cùng NULL là trùng trong unique index: mỗi pricing version chỉ có một default `branch_id IS NULL`. Phép thử default commit/reconnect và duplicate bị từ chối đã PASS trên SQL Server trong Run được xác nhận; H2 là bằng chứng riêng. Xem [Microsoft: unique indexes](https://learn.microsoft.com/en-us/sql/relational-databases/indexes/create-unique-indexes?view=sql-server-ver17).

Mỗi bảng `shipment_quotes` và `shipments` có tám cột mới, tất cả nullable:

| Cột | SQL type |
|---|---|
| `rate_scope` | `VARCHAR(30)` |
| `origin_province_code`, `destination_province_code` | `VARCHAR(2)` |
| `base_shipping_fee`, `extra_weight_fee`, `priced_extra_price` | `DECIMAL(18,2)` |
| `priced_base_weight_kg`, `priced_step_weight_kg` | `DECIMAL(10,2)` |

Hai CHECK cho phép toàn bộ tám cột NULL để giữ nguyên đơn/báo giá cũ, hoặc đầy đủ tám cột với scope mới hợp lệ, mã tỉnh dài hai ký tự, phí không âm, trọng lượng dương và `shipping_fee = base_shipping_fee + extra_weight_fee`. CHECK không phải FK tới danh mục tỉnh; backend vẫn xác thực mã/tên theo `ProvinceCatalog`. Không bổ sung mặc định hoặc tính lại giá/địa chỉ cũ.

Rủi ro ngoài nghiệm thu: ALTER nullability và rebuild index có thể cần schema modification lock, scan/index build và transaction log. Suy luận từ [Microsoft: ALTER TABLE](https://learn.microsoft.com/en-us/sql/t-sql/statements/alter-table-transact-sql?view=sql-server-ver17); chưa đo lock/latency ở quy mô production. QA cô lập với fixture đã PASS, không đánh đồng với rollout trên dữ liệu đang dùng. Không thay checksum V1–V15 đã nghiệm thu; V15 nay đã SUCCESS nên không sửa source hoặc tự repair checksum.

## 3. Helper riêng và hàng rào an toàn

Entry point: `scripts/v15-sql-qa.ps1`, Java test CLI `com.ute.uteexpress.release.v15.V15SqlServerQaTool`. Không thay helper/script V14 đã nghiệm thu.

- `Credential`: secure prompt, DPAPI Windows user hiện tại, file TEMP ngoài Git với ACL user hiện tại/SYSTEM. Không kết nối SQL. Không sử dụng credential V14. Mặc định từ chối ghi đè; `-ReplaceCredential` chỉ dùng theo yêu cầu riêng khi đổi mật khẩu login V15.
- `Inspect`: SELECT metadata/history/counts, dùng được khi database đã có dữ liệu; không Flyway, SET, DDL hoặc DML.
- `Preflight`: SELECT identity/quyền và đếm **tất cả** user objects; khác 0 là từ chối. Không coi database có history/fixture là database mới.
- `Run`: yêu cầu tên `-ReadyDatabase` chính xác và `-AllowWrite`; kiểm tra source checksum/parser trước JDBC, preflight trống trước mọi ghi. Không hỗ trợ resume hoặc retry Run trên database đã dùng.
- Mỗi JDBC connection xác minh product SQL Server, machine, instance, TCP port, `ORIGINAL_LOGIN()`, JDBC catalog, `DB_NAME()`, VIEW DEFINITION, db_owner, không sysadmin/CONTROL SERVER và `HAS_DBACCESS=0` đối với cả bốn database được bảo vệ. Kết quả NULL hoặc khác kỳ vọng đều từ chối và đóng connection; không fallback.
- Endpoint, catalog và username cố định, không nhận JDBC URL/credential override. Không gọi admin CREATE DATABASE/LOGIN; không clean/repair/reset hoặc xóa dữ liệu QA. Không bật Boot, component scan toàn ứng dụng, HTTP, scheduler hoặc notification worker.
- Child Java chỉ nhận credential qua environment riêng, bỏ cấu hình DB/Spring/Java-options thừa hưởng; không truyền password trong command line. Wrapper chỉ hiện dòng PASS/SKIP/RESULT và diagnostic cấu trúc (stage, type, frame, SQLState, vendor code), không hiện exception message, SQL parameter, URL chứa credential hoặc framework log.
- Test helper ở `src/test`, giá fixture ở `src/test/resources/demo`, không thuộc production JAR. Context dùng `@TestConfiguration` được đăng ký tường minh, không bị component scan của ứng dụng tự nhận.

## 4. Quy trình đã hoàn thành — không chạy lại

Kế hoạch chuẩn bị trước đây yêu cầu kiểm tra hai tên chưa tồn tại bằng SSMS, tạo database/login riêng, secure prompt DPAPI, Inspect/Preflight khi còn trống rồi Run một lần sau phê duyệt. Lần Run nay đã được chủ dự án xác nhận PASS. Hướng dẫn tạo môi trường/fresh Run cũ không còn là bước tiếp theo cho database này.

Không chạy lại Credential, Preflight hoặc Run để cập nhật báo cáo; không tự lấy thêm SELECT, thay quyền, đổi password hay tạo database mới. Nếu cần điều tra sau này, phải có phạm vi đọc riêng; tuyệt đối không reset/clean/repair để tái dùng QA.

## 5. Kết quả SQL Server đã xác nhận

Các PASS dưới đây thuộc kết quả SQL thực tế do người dùng xác nhận, không được suy từ tests H2. Dòng RESULT cuối và control flow helper khớp toàn bộ gate bắt buộc; không có gate bắt buộc SKIP/BLOCKED trong kết quả này.

| Gate | Phép thử trong helper | Kết quả SQL |
|---|---|---|
| A: identity/empty/permissions | Machine/instance/port/login/catalog, bốn DB access bằng 0, database trống **trước Run** | PASS |
| B: baseline | V1–V14 nguyên bản; Flyway validate, history/checksum từng version; metadata V14 và pickup/rate | PASS |
| C: legacy | Transaction fixture tối thiểu, snapshot hash các cột cũ | PASS |
| D: V15 | Migration nguyên bản; checksum **347627752**, 16 cột, hai CHECK, scope, index/FK trusted/enabled | PASS |
| Default pickup | Default branch NULL commit/reconnect; duplicate version bị 2601/2627 đúng index | PASS |
| FK/CHECK/DECIMAL | FK 547, pickup dương, snapshot đầy đủ/thiếu/âm/sai sum/sai scope, overflow 8115, rollback | PASS |
| Legacy bảo toàn | Branch/hub, service/version, hai scope cũ, pickup, shipment, quote, payment không đổi; snapshot legacy giữ NULL | PASS |
| Migration rollback | Flyway binary pin + cùng connection/SPID/transaction/TRANCOUNT/XACT_STATE, autocommit=false; fault rồi kiểm tra DDL/index/history/legacy hồi phục | PASS, không SKIP |
| Business rollback | Confirm qua service trong transaction ngoài; shipment/event/quote consumption đều rollback | PASS |
| Hibernate | `ddl-auto=validate` toàn bộ entity V15, không override ORM V14 | PASS |
| E: pricing/service | 1.156 cặp × hai phương thức, trọng lượng, phụ phí/fallback, publish/snapshot/RBAC/coverage/tamper/concurrency | PASS |
| Final | Flyway validate/checksum/history V1–V15; metadata và legacy cuối | PASS |

Fixture legacy: một actor LOCKED không có role đăng nhập, BCrypt của chuỗi ngẫu nhiên không lưu plaintext; hub/branch tổng hợp; service/version PUBLISHED; rule INTER_PROVINCE/INTER_REGION có tiền lẻ; pickup gắn branch; một shipment chưa có method/snapshot; một quote cũ đã hết hạn; payment SUCCESS. Không dùng dữ liệu cá nhân thật. Snapshot dùng SHA-256 trong bộ nhớ, không xuất row/token/hash mật khẩu; chỉ so sánh các cột đã tồn tại trước V15.

Fixture nghiệp vụ sau V15: bốn actor tổng hợp với các role ứng dụng đã seed; mật khẩu ngẫu nhiên không biết/không dùng login HTTP; hai hub/hai chi nhánh với vùng phục vụ rõ ràng Đà Nẵng ↔ Hồ Chí Minh, không tạo 34 chi nhánh giả. Admin tạo/publish giá từ CSV **test-only** qua `AdminPricingService`, không có giá fallback trong production. Thời gian QA là Clock UTC điều khiển được, không đổi giờ hệ thống.

Service journey: 1.156 cặp tỉnh × hai phương thức chỉ estimate không ghi; năm biên trọng lượng và vượt giới hạn; HOME_PICKUP branch override/global fallback; BRANCH_DROPOFF pickup=0; tạo/confirm/idempotency/snapshot; quote owner và membership DB/RBAC; từ chối tạo quote ngoài coverage; controller binder từ chối client fees (MockMvc standalone, không mở HTTP); transaction rollback; hai thread confirm một quote sinh đúng một shipment/event; Admin retire/publish bản mới và xác minh đơn cũ/quote còn hạn giữ giá cũ, audit xuất hiện.

Giới hạn: phép thử binder không phải browser E2E hoặc toàn bộ HTTP CSRF/filter chain trên SQL Server; actor ứng dụng được thiết lập trong SecurityContext để kiểm tra service/method RBAC, không thay thế login/password browser. Run thực tế đã có proof và migration rollback PASS; nguyên tắc SKIP khi thiếu proof vẫn được giữ cho những phép thử tương lai. Upgrade ở đây là V14→V15 với fixture cô lập, không chứng minh dữ liệu/khóa/tải của database đang dùng. UI SQL/browser và rollout production còn nằm ngoài nghiệm thu offline này.

## 6. Kết quả offline

**Lượt kết sổ sau SQL PASS:** chạy lại 75/75 targeted ngày 29/10/2026, 0 failures/errors/skips. Không chạy full Maven hoặc browser lại vì chỉ docs/ignore thay đổi. Scan cuối 576 candidate text files không findings; path scan không credential/private artifacts trong repository; 6 scripts và 5 blocks Git plan AST, JAR isolation/15 migration source-identical, UTF-8/links/whitespace/manifest và `git diff --check` đều PASS. Các full Maven/package dưới đây là kết quả lượt chuẩn bị, được giữ nguyên nguồn thời điểm.

- PASS: PowerShell AST, guard Run/Inspect thiếu readiness từ chối **trước import credential**. Chính sách máy chặn chạy script trực tiếp; guard smoke dùng process PowerShell `-ExecutionPolicy Bypass`, không đổi policy hệ thống.
- PASS: targeted Maven 75 tests, 0 failures/errors/skips, Java 21, SQL opt-in false. Có 17 tests mới cho helper V15, phần còn lại regression V14/migration/nationwide pricing.
- PASS H2 rehearsal: service journey, 2.312 estimates, weight/fee/snapshot/publish/ownership/coverage/tamper/rollback/concurrency; context chỉ validate schema H2 đã tạo bởi test. Đây không phải chạy V15 SQL trên SQL Server.
- PASS: full Maven `mvnw.cmd -o -Duteexpress.sqlserver.validate=false test`, 1.616 tests: **1.615 passed, 0 failures, 0 errors, 1 SQL opt-in skipped**; thời gian 2 phút 42 giây. Log cục bộ `target/v15-full-tests.log`, targeted `target/v15-targeted-tests.log` (không đưa vào Git).
- PASS: `mvnw.cmd -o -DskipTests -Duteexpress.sqlserver.validate=false package`; build/repackage JAR thành công, không khởi động JAR. Kiểm tra 826 archive entries: không có helper V14/V15, demo entry point, CSV fixture, ORM override, DPAPI/script QA hoặc dependencies H2/Mockito/Spring test; có V15 migration production đúng vị trí. Log `target/v15-package-check.log`. Không thay `pom.xml`, cấu hình production hoặc ứng dụng cổng 8083.
- `git diff --check` PASS với cảnh báo LF→CRLF của một số file có sẵn từ trước. Không chỉnh chúng trong lượt này.
- Các gate SQL trước đây NOT RUN nay đã PASS theo xác nhận Run của chủ dự án ở mục 5; không chạy lại SQL trong lượt cập nhật tài liệu. Kết quả offline và SQL được ghi riêng, không cộng dồn thành số test Maven.

Các lỗi chỉ ở helper trong lúc phát triển đã sửa: import Role/Statement trùng tên, lựa chọn EntityManager bean, public H2 function alias, dùng pool riêng cho test H2 để tránh comparator của CHECK giữ session vật lý đã đóng, và số lượng shipment kỳ vọng cuối journey (năm đơn, không đếm đơn đã rollback). Không sửa production service/migration để làm helper PASS.

Cảnh báo test hiện có: H2 2.4.240 mới hơn bản Flyway 11.14.1 đã verify (2.3.232); Mockito/JDK dynamic agent; unchecked compilation ở test cũ. Không coi là bằng chứng SQL Server.

## 7. File trong lượt chuẩn bị

Tạo mới 17 file, sửa 0/xóa 0 file có sẵn:

- `docs/v15-sql-server-qa.md`.
- `scripts/v15-sql-qa.ps1`.
- Trong `src/test/java/com/ute/uteexpress/release/v15/`: `V15QaTarget.java`, `V15ReadOnlyInspection.java`, `V15QaDiagnostics.java`, `V15Sql.java`, `V15SourceReview.java`, `V15LegacyFixtures.java`, `V15SchemaChecks.java`, `V15ConstraintChecks.java`, `V15TransactionProbe.java`, `V15BusinessChecks.java`, `V15SqlServerQaTool.java`, `V15QaSafetyTests.java`, `V15HelperRegressionTests.java`, `V15CallbackLifecycleTests.java`, `V15BusinessHarnessTests.java`.

Đoạn file/count này là snapshot lịch sử của lượt chuẩn bị offline. Sau khi người dùng xác nhận SQL PASS, đã tạo [nghiệm thu Commit 25](commit25-final-qa.md), đồng bộ [release checklist](release-checklist.md) và lập [manifest/kế hoạch Git](commit24-25-git-plan.md). Không sửa source, helper, migration hoặc database trong lượt cập nhật. SQL accepted trong phạm vi QA; production-ready chưa được chứng nhận.

Đối chiếu SHA-256 với snapshot đầu lượt: **100/100 file worktree có sẵn giữ nguyên**. Git status cuối lượt: 65 tracked modified + 52 untracked = 117 file (100 có sẵn + 17 mới); staged 0, deleted 0; branch không đổi. Danh sách thay đổi có sẵn vẫn thuộc các lượt Commit 24/25 trước, không được gom nhầm thành 117 file do lượt chuẩn bị V15 này tạo/sửa.

**Bước tiếp theo:** review báo cáo và phê duyệt phương án Git trong [kế hoạch tách Commit 24/25](commit24-25-git-plan.md). Không có bước chạy lại SQL hoặc triển khai Commit 26.
