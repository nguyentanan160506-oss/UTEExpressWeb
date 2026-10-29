# Commit 24 — SQL Server V14 QA độc lập

## Kết quả SQL Server thật ngày 29/10/2026 — PASS

Người dùng đã phê duyệt lần Run mới sau sửa helper. Đã thực hiện đúng thứ tự **Inspect chỉ đọc → Preflight chỉ đọc → Run một lần → Inspect chỉ đọc sau cùng**. Không chạy lại Run sau đó, không sửa source để làm migration PASS.

| Thuộc tính trước ghi | Kết quả |
|---|---|
| Máy / instance / TCP port | DESKTOP-31GJQ3T / SQLEXPRESS / 1433 |
| JDBC catalog và DB_NAME | Đều UTEExpress_V14_QA_20261029_7dc87af7 |
| ORIGINAL_LOGIN | uteexpress_v14qa_7dc87af7 |
| Quyền | db_owner + VIEW DEFINITION/CREATE TABLE/ALTER dbo đúng QA; không sysadmin; HAS_DBACCESS của UTEExpressDB, UTEExpress_Test, UTEExpress_Demo đều 0 |
| Trạng thái | 0 user objects, 0 bảng, không history/fixture; Inspect và Preflight PASS |

Run kết thúc khoảng **10:45:48 UTC+7**, exit 0:

```text
PASS flyway-v1-v13
PASS reviewed-flyway-binary-11.14.1
PASS callback-v13-same-session-transaction
PASS legacy-fixtures-committed
EXPECTED_FAIL afterEachMigrate-v14-rollback-probe
PASS flyway-v14-transaction-rollback
PASS flyway-v14-checksum-index-check-legacy
PASS valid-generated-receipt-commit-reconnect
PASS unique-and-business-transaction-rollback
PASS check-reference-mismatch
PASS check-missing-version
PASS check-missing-code
PASS check-negative-version
PASS check-code-length
PASS hibernate-schema-validate
RESULT PASS required-checks migration-rollback=PASS database-retained-no-cleanup
```

Flyway validate chạy sau V1–V13, sau V14 và sau các kiểm tra/ORM; đều PASS. Fault injection không bị SKIP: pin Flyway đúng bản review, callback V13 có cùng connection/SPID/transaction và active transaction, V14 kiểm tra lại proof/metadata trong cùng connection trước khi ném ExpectedRollback. Sau expected failure, connection mới xác minh không cột/index/CHECK/history V14; V1–V13 và legacy vẫn nguyên vẹn. Không có lỗi ngoài dự kiến hoặc cần repair.

### History sau kiểm thử — SELECT thực tế

| Version | Checksum | Success |
|---:|---:|---|
| 1 | 189739357 | true |
| 2 | 1191373203 | true |
| 3 | -1720389847 | true |
| 4 | -1466061241 | true |
| 5 | 1925825129 | true |
| 6 | -1705174727 | true |
| 7 | 444247411 | true |
| 8 | 2036200466 | true |
| 9 | 61067824 | true |
| 10 | 236607534 | true |
| 11 | 2051557719 | true |
| 12 | -1057022707 | true |
| 13 | 1007916434 | true |
| 14 | **142205694** | **true** |

Đủ 14 rows, không failed row/V15. Checksum V14 thành công khớp source qua Flyway validate. Source V1–V15 giữ nguyên trong lượt này.

### DDL, dữ liệu và rollback

- `system_receipt_code`: VARCHAR(45) nullable; `request_version`: BIGINT nullable — PASS.
- `UX_intakes_system_receipt_code`: unique/enabled, IGNORE_DUP_KEY=false, đúng key và filter `system_receipt_code IS NOT NULL` — PASS. Receipt non-null hợp lệ commit/đọc lại; trùng mã bị từ chối với đúng tên index, SQL code thuộc 2601/2627.
- `CK_intakes_system_receipt`: enabled/trusted; dữ liệu sai reference, NULL version, NULL code, version âm hoặc code sai độ dài đều bị từ chối SQL 547 và rollback — PASS.
- Hai dòng legacy có cùng reference vẫn tồn tại, hai cột mới NULL, toàn bộ giá trị đã chụp trước nâng cấp không thay đổi sau fault/commit — PASS.
- Business transaction cố UPDATE shipment + INSERT trùng mã; sau rollback status vẫn CONFIRMED/current_branch NULL, intake count không tăng — PASS.
- Hibernate `ddl-auto=validate` trên SQL Server thật: PASS với baseline V14, không schema update/Spring Boot/web startup. ORM test-only loại đúng hai embedding V15 để không kiểm tra nhầm schema V15.

### Database được giữ sau kiểm thử

**42 bảng**: 41 bảng nghiệp vụ + schema history. Rows có dữ liệu: roles=4; users=1 (LOCKED, user_roles=0, hash ngẫu nhiên); branches=1; employees=1; shipping_services=1; shipments=4; shipment_intakes=3 (2 legacy + 1 generated); flyway_schema_history=14. Các bảng nghiệp vụ khác 0. Fixture SQL này không phải dữ liệu demo hoặc một workflow giao hàng hoàn chỉnh.

Metadata sau cùng: V14 columns/index/CHECK = 2/1/1; V15 rate_scope columns ở quote/shipment = 0. **DB đã có schema và dữ liệu, không còn trống; không chạy lại fresh Run/clean/repair/reset/drop.** Giữ nguyên để chủ dự án xem bằng SSMS hoặc `-Mode Inspect` chỉ SELECT.

### Bằng chứng và kết luận

Log ngoài repository: `%TEMP%/uteexpress-v14-approved-inspect-before.log`, `uteexpress-v14-approved-preflight.log`, `uteexpress-v14-approved-run-20261029-1044.log`, `uteexpress-v14-approved-inspect-after.log`. Wrapper chỉ lưu output có cấu trúc, không credential/password hoặc raw provider messages. Không thay credential.

**Không có gate bắt buộc SKIP/BLOCKED. Commit 24 đạt nghiệm thu SQL Server trong phạm vi được duyệt.** Không chạy/kiểm chứng V15, không chứng nhận full app Commit 25, SQL service concurrency hoặc production load. Không thay đổi ba DB bảo vệ/8083/Git branch/index. Chi tiết [báo cáo Commit 24](commit24-final-qa.md).

---

## Lịch sử chuẩn bị và xử lý blocker — không phải trạng thái hiện tại

**Lịch sử 29/10/2026, 10:29 UTC+7:** Credential đã hoạt động; mode Inspect chỉ SELECT PASS và khi đó có 0 user objects/bảng/history/fixture. NPE sau lần Run người dùng báo được tái hiện và sửa tại callback `supports(event, null)`, không phải SQL V13. Giai đoạn đó chưa chạy lại Run hoặc ghi SQL. Xem [báo cáo điều tra NPE](v14-null-context-investigation.md). Các blocker dưới đây đã được xử lý trước lần nghiệm thu PASS ở đầu tài liệu.

**Cập nhật 29/10/2026:** Đã thử Preflight chỉ đọc trong lượt Commit 25 và dừng với `18488 / S0001`; login đang yêu cầu đổi mật khẩu lần đầu theo bằng chứng SSMS người dùng cung cấp. Chưa migrate/fixture. Xem [báo cáo hiện tại và cách thay DPAPI an toàn](commit24-final-qa.md). Các mục “Giai đoạn A” dưới đây ghi nhận lần chuẩn bị helper trước đó, không phải trạng thái SQL PASS hiện tại. Checkout có V15 nhưng QA này luôn target V14; ORM override test-only loại đúng hai embedding V15 để validate baseline V14, không tuyên bố ứng dụng mới tương thích schema cũ.

## Trạng thái Giai đoạn A

Helper đã được chuẩn bị để review và kiểm thử offline. **Chưa import credential, chưa mở JDBC hoặc chạy bất kỳ câu SQL nào trên SQL Server trong giai đoạn A.** Chưa được coi là V14 SQL Server PASS. Đợi người dùng xác nhận database/login mới đã sẵn sàng trước Giai đoạn B.

Phạm vi cố định đã được phê duyệt:

| Thuộc tính | Giá trị |
|---|---|
| Server được phép | Máy Windows hiện tại, instance SQLEXPRESS, TCP 127.0.0.1:1433 |
| Database QA | `UTEExpress_V14_QA_20261029_7dc87af7` |
| SQL login riêng | `uteexpress_v14qa_7dc87af7` |
| DPAPI file riêng | `%TEMP%\uteexpress-v14-qa-7dc87af7.credential.xml` |
| Database cấm sửa | UTEExpressDB, UTEExpress_Test, UTEExpress_Demo |

Không sửa cấu hình Spring production, guard/tool QA cũ hoặc V1–V14. Helper không gọi Spring Boot, không bật worker, không mở cổng HTTP và không dừng ứng dụng 8083. Toàn bộ Java helper ở test source, không đóng gói trong production JAR.

## 1. Tạo database mới bằng SSMS — người dùng thực hiện riêng

Kết nối SSMS bằng **`tcp:127.0.0.1,1433`**, dùng Windows Authentication với tài khoản quản trị đã có. Kết nối TCP này giúp kiểm tra endpoint thực tế thay vì mặc định tên instance và port là một. Nếu không kết nối đúng endpoint, dừng; không đổi sang database đang sử dụng.

Trước tiên, mở query chỉ đọc dưới đây để đối chiếu machine_name với tên laptop (có thể xem `$env:COMPUTERNAME` trong PowerShell), instance và port. Nếu không đúng, dừng, không chạy khối tạo DB.

```sql
SELECT SERVERPROPERTY('MachineName') AS machine_name,
       SERVERPROPERTY('InstanceName') AS instance_name,
       CONNECTIONPROPERTY('local_tcp_port') AS tcp_port;
```

Sau khi kết quả trên đúng, mở query mới và chạy toàn bộ khối dưới đây. Khối này **chưa được agent thực thi**. Nếu tên DB hoặc login đã tồn tại, dừng, không chạy phần còn lại, không xóa hoặc tái sử dụng đối tượng đó. Không bôi riêng dòng CREATE để bỏ qua các guard.

```sql
USE [master];
GO
SET IMPLICIT_TRANSACTIONS OFF;

SELECT
    SERVERPROPERTY('ServerName') AS server_name,
    SERVERPROPERTY('MachineName') AS machine_name,
    SERVERPROPERTY('InstanceName') AS instance_name,
    CONNECTIONPROPERTY('local_tcp_port') AS tcp_port,
    DB_ID(N'UTEExpress_V14_QA_20261029_7dc87af7') AS existing_database_id,
    SUSER_ID(N'uteexpress_v14qa_7dc87af7') AS existing_login_id;

IF @@TRANCOUNT <> 0
    THROW 51001, N'Dang co transaction; dung tao database.', 1;
IF COALESCE(CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')), N'') <> N'SQLEXPRESS'
    THROW 51002, N'Sai instance; dung.', 1;
IF ISNULL(CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')), 0) <> 1433
    THROW 51003, N'Sai TCP port; dung.', 1;
IF DB_ID(N'UTEExpress_V14_QA_20261029_7dc87af7') IS NOT NULL
    THROW 51004, N'Ten database da ton tai; khong tai su dung.', 1;
IF SUSER_ID(N'uteexpress_v14qa_7dc87af7') IS NOT NULL
    THROW 51005, N'Ten login da ton tai; khong tai su dung.', 1;

CREATE DATABASE [UTEExpress_V14_QA_20261029_7dc87af7];
```

Xác nhận machine_name đúng laptop của bạn. Nếu DB/login có sẵn trước bước tạo, báo lại thay vì tiếp tục. Sau CREATE thành công, không chạy lại khối này.

## 2. Tạo login bằng SSMS — không ghi password vào SQL

Security → Logins → New Login:

1. Login name đúng `uteexpress_v14qa_7dc87af7`, chọn SQL Server Authentication.
2. Nhập mật khẩu mới riêng trong giao diện SSMS, giữ password policy. Không dùng sa hoặc mật khẩu demo/QA cũ; không gửi mật khẩu vào chat.
3. Default database: database QA mới. Server Roles chỉ có public; không cấp sysadmin.
4. User Mapping: **chỉ** database QA mới; cấp db_owner tại database này. Không ánh xạ vào ba DB được bảo vệ. Quyền rộng db_owner chỉ dành DB QA rỗng cô lập để thử DDL.
5. Không thay quyền login khác, không đổi cấu hình SQL Server hoặc ứng dụng đang chạy.

Mới tạo DB bằng SSMS là trạng thái được mong đợi sau bước 1; helper sẽ đòi database đó còn trống. Nó không thể tự chứng minh tên chưa tồn tại trước thời điểm bạn tạo, vì vậy cần guard SSMS và xác nhận của chủ sở hữu. Sau bất kỳ lần Run đã tạo object, helper không cho tái sử dụng/resume database.

## 3. Tạo credential DPAPI riêng — không kết nối SQL

PowerShell tại repository, JDK 21 được cấu hình JAVA_HOME:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\v14-sql-qa.ps1 -Mode Credential
```

Nhập credential SQL mới tại Get-Credential. File DPAPI chỉ giải mã được bởi tài khoản Windows hiện tại; ACL giới hạn tài khoản đó và SYSTEM, ngoài Git. Script không in credential, không ghi password vào command line/history/log; nếu file đã có thì dừng, không tự ghi đè. Không dùng file credential SQL demo/QA cũ.

Sau khi xong, xác nhận trong chat: DB đã được tạo mới sau guard không tồn tại, login/mapping đúng, credential riêng đã tạo, môi trường sẵn sàng. Chỉ gửi tên instance/database/login và trạng thái, không gửi password/XML.

## 4. Các lệnh Giai đoạn B — chưa thực thi

**Chỉ sau xác nhận sẵn sàng của người dùng**, agent sẽ chạy Preflight trước:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\v14-sql-qa.ps1 -Mode Preflight -ReadyDatabase UTEExpress_V14_QA_20261029_7dc87af7
```

Preflight chỉ SELECT: product SQL Server, JDBC catalog và DB_NAME, machine/instance/port, ORIGINAL_LOGIN, VIEW DEFINITION, db_owner, CREATE TABLE và ALTER dbo; sysadmin phải false, HAS_DBACCESS của ba database được bảo vệ phải 0. Chỉ chấp nhận 0 user objects. Thiếu dữ liệu xác minh hoặc sai bất kỳ mục nào đều dừng. Nếu chưa xác nhận readiness, script dừng trước cả import DPAPI/Maven/JDBC.

Sau preflight PASS và trong phạm vi ghi đã được phê duyệt:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\v14-sql-qa.ps1 -Mode Run -ReadyDatabase UTEExpress_V14_QA_20261029_7dc87af7 -AllowWrite
```

Run kiểm tra lại preflight, rồi:

1. Flyway target13, validate history/checksum V1–V13.
2. Tạo fixture tối thiểu trong một transaction: 1 User LOCKED không có role, password hash ngẫu nhiên; 1 branch, 1 Employee, 1 service, 4 shipment và 2 intake legacy có cùng reference. Không gọi demo seed hoặc tạo tài khoản Admin đăng nhập được.
3. Nếu đủ bằng chứng transaction: fault injection V14 như mục 5; phải rollback hết cột/index/CHECK/history14 và giữ chính xác legacy rows. Nếu rollback bất thường, dừng ngay, không tiếp tục migrate.
4. Chạy V14 nguyên bản không callback gây lỗi; kiểm tra history/checksum, cột, filtered unique index, CHECK enabled/trusted và legacy không đổi.
5. Commit một receipt hợp lệ, đọc lại bằng kết nối mới; thử trùng mã (2601/2627), CHECK sai code/reference/NULL/version âm/độ dài (547). Mỗi test âm rollback và đối chiếu dữ liệu.
6. Test business rollback: UPDATE một shipment fixture và INSERT trùng mã trong cùng transaction; sau lỗi phải giữ trạng thái và số receipt ban đầu.
7. Flyway validate và Hibernate schema validate qua JPA factory riêng, không Spring Boot/worker/web server.

Mỗi connection đều kiểm tra lại target/login/port trước khi chuyển cho Flyway/Hibernate/DML. Test CHECK dùng code chưa sử dụng để không nhầm lỗi UNIQUE với CHECK. Không tự retry trên DB khác; không clean/repair/reset/drop. Kết thúc giữ toàn bộ database và fixture cho người dùng xem. Có thể có khoảng trống identity sau rollback, đó không phải receipt đã commit.

## 5. Xác minh transaction trước fault injection

Dependency thực tế được kiểm tra offline: **flyway-core-11.14.1.jar**.

SHA-256 binary được pin trong helper:
`45148edbcb55016418ff1ef7ecca3331b55f5216b3af4bd2db40063904db3715`.

Đã đọc source JAR cùng phiên bản trong Maven cache:

- `DbMigrate.java:282`: ExecutionTemplate bọc doMigrateGroup bằng connection migration.
- `DbMigrate.java:77,348–349`: connectionUserObjects lấy từ database.getMigrationConnection(), executor nhận JDBC connection của chính đối tượng đó.
- `DbMigrate.java:380,391,407`: BEFORE_EACH_MIGRATE → executor migration → AFTER_EACH_MIGRATE trong group.
- `DefaultCallbackExecutor.java:100–102`: callback nhận migration connection.

Đây là chứng cứ **offline**, chưa phải SQL Server PASS. Trên SQL Server thật helper còn phải:

1. V13 dùng callback quan sát không gây lỗi: cùng đối tượng JDBC connection, @@SPID, CURRENT_TRANSACTION_ID(), @@TRANCOUNT; autoCommit=false, transaction active và XACT_STATE=1 ở trước/sau migration.
2. Binary runtime phải khớp bản đã review.
3. V14 tiếp tục đo lại trước/sau; metadata V14 phải tồn tại trong chính connection đó.
4. Chỉ khi tất cả đúng, callback afterEachMigrate V14 mới ném lỗi riêng có chủ đích trước commit.
5. Bắt đúng lỗi riêng đó, rồi dùng kết nối mới xác minh rollback. Bất kỳ lỗi migration/permission/rollback khác đều là FAIL và dừng.

Nếu thiếu proof hoặc dependency thay đổi, không cố gây lỗi; kết quả migration rollback phải **SKIP**, dù các kiểm tra khác có thể PASS. Dòng RESULT nêu riêng migration-rollback=PASS/SKIP, không biến phép thử chưa chạy thành PASS.

Nguồn tham chiếu: [Redgate callback trước commit](https://www.red-gate.com/hub/product-learning/flyway/try-before-you-commit-in-flyway/), [CURRENT_TRANSACTION_ID trên SQL Server 2016+](https://learn.microsoft.com/en-us/sql/t-sql/functions/current-transaction-id-transact-sql?view=sql-server-2017). Không dùng H2 để kết luận cơ chế transaction SQL Server tương đương.

## 6. Kết quả A và gate C

Giai đoạn A đã chạy `mvnw.cmd test -Dtest=V14QaSafetyTests,OriginIntakeMigrationTests,ReleaseSafetyTests -Duteexpress.sqlserver.validate=false`: **86/86 PASS, 0 failure/error/skip**, kết thúc 29/10/2026 03:28:08 UTC+7. Trong đó 27 safety test mới, 57 regression guard cũ, 2 parser/upgrade H2 test. Một lỗi import trùng tên Statement trong test mới đã được sửa trước lần chạy cuối. Log ở `%TEMP%\uteexpress-v14-qa-stage-a-tests.log`.

PowerShell AST syntax PASS. Thử gọi Preflight **không có ReadyDatabase** đã bị guard từ chối trước import credential/Maven/JDBC; đây là kiểm tra offline đường chặn, không phải Giai đoạn B. Không chạy Preflight thật, Run hoặc Credential trong lượt agent này.

Helper không được phát hiện như một test tích hợp mặc định; JUnit chỉ có V14QaSafetyTests dùng mock, không import credential hoặc gọi DriverManager thật. Những kết quả offline này không thay cho SQL Server thực tế.

File mới riêng Giai đoạn A (không sửa các file Commit 24 đang có):

- `scripts/v14-sql-qa.ps1`.
- `src/test/java/com/ute/uteexpress/release/v14/V14QaTarget.java`.
- `src/test/java/com/ute/uteexpress/release/v14/V14TransactionProbe.java`.
- `src/test/java/com/ute/uteexpress/release/v14/V14QaFixtures.java`.
- `src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java`.
- `src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java`.
- `docs/v14-sql-server-qa.md`.

| Gate SQL Server | Trạng thái Giai đoạn A |
|---|---|
| Database/login sẵn sàng | Chờ người dùng xác nhận |
| JDBC identity/port/permission/freshness | NOT RUN |
| Flyway V1–V14 | NOT RUN |
| Legacy duplicates/UNIQUE/CHECK | NOT RUN |
| Callback cùng transaction trên SQL Server | NOT RUN |
| Fault injection rollback | NOT RUN |
| Business rollback/Flyway/Hibernate validate | NOT RUN |

Mọi status SQL sẽ được cập nhật từ kết quả thực thi B/C. Không sử dụng kết quả H2 của Commit 24 để điền các gate trên.
