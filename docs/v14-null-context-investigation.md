# Commit 24 — điều tra NPE của helper SQL QA

Ngày 29/10/2026, nhánh `feature/auto-intake-receipts`. Chỉ sửa helper/test và tài liệu; SQL Server trong lượt này **chỉ SELECT**. Không chạy lại Run, migration, validate bằng Flyway trên SQL Server, fixture, seed, clean/repair/reset/drop hoặc tạo database. Không đổi credential, production config, V1–V15 hoặc tiến trình 8083.

## Nguyên nhân đã xác minh

Lỗi ở `V14TransactionProbe.supports(Event, Context)`, trước sửa tại dòng 39:

```java
return context.getMigrationInfo() != null && ...;
```

Flyway **11.14.1 thực tế**, binary pin đã được regression xác nhận, gọi callback với Context NULL. Source JAR cùng phiên bản trong Maven cache:

- `org/flywaydb/core/FlywayExecutor.java:352`: `effectiveCallbacks.stream().anyMatch(x -> x.supports(CREATE_SCHEMA, null))` khi chuẩn bị callbacks; chỗ gọi prepareCallbacks ở dòng 218.
- `DefaultCallbackExecutor.java:87` cũng gọi `callback.supports(event, null)` khi thăm dò migrate/undo event.

Helper cũ đánh giá `context.getMigrationInfo()` trước cả điều kiện loại event. Vì vậy, dù callback chỉ quan tâm V13, nó vẫn NPE khi Flyway dò CREATE_SCHEMA. Đây là **lỗi helper, không phải SQL migration V13**. Nhãn `stage=v13` cũ bao trùm cấu hình, migrate, validate/history và metadata nên không chỉ ra câu SQL nào.

Đã tái hiện trước khi vá bằng chính Flyway 11.14.1 trên H2 tách biệt: một regression thất bại với NPE `context is null`, stack có `V14TransactionProbe.supports(V14TransactionProbe.java:39)` → `FlywayExecutor.prepareCallbacks(FlywayExecutor.java:352)` → `Flyway.migrate`. Log `%TEMP%/uteexpress-v14-npe-red.log`. Đây là stack tái hiện offline, **không phải stack lịch sử SQL Server đã được lưu đầy đủ**; stdout Run người dùng cung cấp chỉ có type/stage.

Flyway đi qua khởi tạo/kết nối/chuẩn bị callback trước thực thi migration. DataSource không trả NULL trong lần kiểm tra thực; guarded identity/port/login/permissions vẫn PASS. Phép kiểm tra transaction V13 và metadata V14 chưa đạt tới trong lỗi này; không có bằng chứng transaction/DDL migration SQL Server lỗi.

## Bản sửa

```java
if ((event != BEFORE_EACH_MIGRATE && event != AFTER_EACH_MIGRATE)
        || context == null) return false;
var migration = context.getMigrationInfo();
return migration != null && migration.getVersion() != null
    && version.equals(migration.getVersion().getVersion());
```

Giữ nguyên điều kiện proof cùng JDBC connection/SPID/transaction/autocommit, pin Flyway và metadata trước fault injection. Thiếu transaction proof vẫn SKIP, không ép gây lỗi. Không sửa migration để vượt callback.

Helper chia stage thành `v13-configure`, `v13-migrate`, `v13-history`, `v13-absence-v14`. Diagnostics chỉ in class/cause, file/method/line và SQLState/error code (kể cả nested SQLException), giới hạn số cause/frame và lọc định dạng. Không in exception message, stack trace nguyên văn, JDBC URL, settings, mật khẩu hoặc credential. Script chỉ cho qua các dòng DIAG đúng allowlist. History version và filtered-index definition NULL nay bị từ chối có kiểm soát thay vì sinh NPE thứ cấp.

## Kết quả SQL chỉ đọc thực tế

Đã chạy **chỉ**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\v14-sql-qa.ps1 -Mode Inspect -ReadyDatabase UTEExpress_V14_QA_20261029_7dc87af7
```

`Inspect` là mode mới chỉ SELECT, không đòi database trống, không gọi Flyway/preflight migration, không bật session write options và không thể gọi `enableWrites`. Vì thế có thể kiểm tra cả database đã chứa schema/history mà không che dấu trạng thái sử dụng. Guard identity hiện có vẫn kiểm tra trên từng kết nối trước khi giao cho inspector. Không có fallback hoặc connection override.

Quan sát khoảng **10:29 ngày 29/10/2026 UTC+7**, log `%TEMP%/uteexpress-v14-npe-sql-inspect.log`:

| Thuộc tính | Kết quả thực tế |
|---|---|
| SQL Server machine | DESKTOP-31GJQ3T |
| Instance / TCP port | SQLEXPRESS / 1433 |
| JDBC catalog | UTEExpress_V14_QA_20261029_7dc87af7 |
| DB_NAME() | UTEExpress_V14_QA_20261029_7dc87af7 |
| ORIGINAL_LOGIN() | uteexpress_v14qa_7dc87af7 |
| Permission/isolation guard | PASS: db_owner/metadata đúng QA, không sysadmin, không có access ba DB được bảo vệ theo guard hiện có |
| User objects / user tables | **0 / 0** |
| dbo.flyway_schema_history | **Không tồn tại**, không có history V1–V13/V14/V15 |
| Bảng nghiệp vụ / seed V2 | Chưa có bảng, chưa có dữ liệu seed |
| Fixture markers users/branches/employees/services/intakes | 0; các bảng tương ứng chưa tồn tại |
| V14 columns / filtered index / CHECK | 0 / 0 / 0 |
| V15 rate_scope columns ở quote/shipment | 0 |

Không có V1–V13 chạy thành công một phần hay toàn bộ trong trạng thái hiện tại, không có dấu vết fixture. Không có schema/DDL migration đã commit để rollback/reset. Đây là **kết quả kiểm tra hiện trạng**, không phải giả định dựa trên Preflight cũ. Database vẫn đã từng được thử Run; không gọi nó là database chưa từng được sử dụng.

## Regression

- RED trước sửa: 1 test callback lifecycle thất bại đúng NPE, không SQL Server.
- Sau sửa: **95/95 PASS**, 0 failure/error/skip: real Flyway lifecycle H2, callback null/missing version, modes/identity/freshness, read-only inspector với DB rỗng và DB có history/data mô phỏng, diagnostics không lộ secret, transaction-proof gate, metadata override, parser/upgrade H2 và các release guards.
- Real Flyway lifecycle test dùng **migration V13 tối thiểu riêng trong TEMP/H2**, không phải thực thi production V1–V13. H2 không chứng minh transaction proof hoặc filtered index SQL Server; test xác nhận không inject khi không có proof SQL Server.
- Full `mvnw.cmd test -Duteexpress.sqlserver.validate=false`: **1.599 total / 1.598 PASS / 0 failure/error / 1 SQL opt-in SKIPPED**, BUILD SUCCESS, kết thúc **29/10/2026 10:34:23 UTC+7**, 2 phút 53 giây. Surefire XML được đối chiếu với tổng này. Log `%TEMP%/uteexpress-v14-npe-full-tests.log`.
- PowerShell AST syntax và `git diff --check`: PASS, chỉ cảnh báo LF/CRLF sẵn có. Không chạy lại browser vì không đổi production/UI code; 399 checkpoint browser trước đó là kết quả lịch sử, không phải lượt SQL QA này.

## Database hiện tại có thể tiếp tục không?

**Về trạng thái kỹ thuật: có**, vì quan sát hiện tại là 0 user objects, chưa có schema history hoặc dữ liệu cần bảo toàn. Không cần clean/repair/drop/reset hoặc database mới. Tuy nhiên, lượt này chỉ được đọc và DB đã từng được thử Run, nên **chưa tự chạy lại**; cần chủ dự án phê duyệt bước ghi tiếp theo cho đúng DB này.

Kế hoạch sau phê duyệt:

1. Inspect lại target/permissions/objects và đối chiếu vẫn đúng hiện trạng. Nếu xuất hiện object/history/fixture mới hoặc sai identity, dừng; không dùng fresh Run để tiếp tục một schema có sẵn.
2. Chạy Preflight mới ngay trước bước ghi. Chỉ khi PASS và đã có phê duyệt cho lần Run mới mới chạy helper đã vá, target V13 trước trong luồng hiện có. Các stage mới giúp xác định chính xác điểm lỗi; nếu lỗi không được dự kiến thì dừng và giữ DB.
3. Sau V1–V13 SUCCESS, kiểm tra history/checksum và metadata; tạo fixture legacy tối thiểu theo phạm vi QA đã thống nhất. V14 rollback fault injection chỉ khi pin binary và proof SQL Server cùng transaction đều đạt; thiếu proof thì SKIP riêng, không giả định tương đương H2.
4. V14 nguyên bản, filtered UNIQUE/CHECK, legacy, business rollback, Flyway/Hibernate validate đúng baseline V14. Cần bổ sung/thực thi concurrency SQL Server trong kế hoạch nghiệm thu tiếp theo; chưa được đánh dấu đã kiểm chứng bởi regression H2.
5. Giữ DB cho người dùng kiểm tra. Không V15, không chuyển sang UTEExpressDB/UTEExpress_Test/UTEExpress_Demo, không tự tạo DB mới. Mapping QA test-only vẫn loại đúng hai embedding V15, không chứng nhận ứng dụng Commit 25 chạy trên schema V14.

Không thực hiện lệnh ghi bước 2–4 trong lượt điều tra này. V14 SQL Server acceptance vẫn **PENDING APPROVAL / NOT RUN**, dù lỗi helper đã được sửa và SELECT đã PASS.

## File mới/sửa trong lượt điều tra

**5 file mới, 10 file sửa, 0 xóa.** Đây là delta so với working tree Commit 24–25 đang có trước lượt này, không coi toàn bộ file untracked kế thừa là mới tạo hôm nay.

File mới:

- [V14CallbackLifecycleTests.java](../src/test/java/com/ute/uteexpress/release/v14/V14CallbackLifecycleTests.java): regression Flyway thật/H2, RED trước vá.
- [V14HelperRegressionTests.java](../src/test/java/com/ute/uteexpress/release/v14/V14HelperRegressionTests.java): callback metadata NULL, inspect read-only/nonempty DB, diagnostics không lộ secret và wrapper guard.
- [V14QaDiagnostics.java](../src/test/java/com/ute/uteexpress/release/v14/V14QaDiagnostics.java): chẩn đoán có cấu trúc, không exception message/credential.
- [V14ReadOnlyInspection.java](../src/test/java/com/ute/uteexpress/release/v14/V14ReadOnlyInspection.java): SELECT identity, tables/row counts, history, fixture markers và V14/V15 metadata.
- Báo cáo `docs/v14-null-context-investigation.md` này.

File sửa:

- [scripts/v14-sql-qa.ps1](../scripts/v14-sql-qa.ps1): mode Inspect và bộ lọc DIAG an toàn; không thay credential đang hoạt động.
- [V14TransactionProbe.java](../src/test/java/com/ute/uteexpress/release/v14/V14TransactionProbe.java): kiểm tra event/Context NULL trước dereference.
- [V14SqlServerQaTool.java](../src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java): nhánh Inspect độc lập, stage chi tiết, diagnostics và guard NULL metadata/history.
- [V14QaTarget.java](../src/test/java/com/ute/uteexpress/release/v14/V14QaTarget.java): cho phép Inspect chỉ đọc, vẫn cấm enableWrites ở mọi mode ngoài Run.
- [V14QaSafetyTests.java](../src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java): chuyển Inspect từ mode cấm sang mode read-only được kiểm thử riêng; các mode clean/repair/drop/reset vẫn bị cấm.
- [docs/v14-sql-server-qa.md](v14-sql-server-qa.md), [docs/commit24-final-qa.md](commit24-final-qa.md): cập nhật trạng thái và đánh dấu hướng đổi password cũ đã hoàn tất.
- [docs/final-qa-report.md](final-qa-report.md), [docs/release-checklist.md](release-checklist.md): JDBC read PASS nhưng acceptance SQL vẫn pending, cập nhật regression.
- [docs/nationwide-shipping-pricing.md](nationwide-shipping-pricing.md): đánh dấu manifest/18488 của lượt Commit 25 trước là snapshot lịch sử, dẫn tới báo cáo helper mới.

Không sửa production Java/templates, pom, application.properties hoặc bất kỳ migration V1–V15. Không stage, add, commit, push, merge, switch hoặc reset Git. Database QA và credential được giữ nguyên.

Git cuối lượt vẫn `feature/auto-intake-receipts`: **65 tracked modified + 35 untracked = 100 file**, bao gồm toàn bộ thay đổi Commit 24–25 đã kế thừa; 0 staged, 0 xóa. Delta của riêng lượt sửa helper là 15 file ở trên. Đã đối chiếu hash các thay đổi main-source/V14/V15 có sẵn với snapshot đầu lượt và kiểm tra không có diff mới ở migration lịch sử, pom/config production. Không in hoặc đưa file DPAPI vào repository.
