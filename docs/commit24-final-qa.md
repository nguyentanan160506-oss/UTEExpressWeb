# Commit 24 — báo cáo nghiệm thu tiếp nhận nguồn

## Nghiệm thu SQL Server thực — PASS ngày 29/10/2026

**Commit 24 đạt gate SQL Server trong phạm vi QA V14 đã được phê duyệt.** Run đúng một lần, kết thúc khoảng **10:45:48 UTC+7**, exit code 0:

```text
RESULT PASS required-checks migration-rollback=PASS database-retained-no-cleanup
```

Không có lỗi ngoài dự kiến, không có gate bắt buộc SKIP/BLOCKED. `EXPECTED_FAIL afterEachMigrate-v14-rollback-probe` là phép thử rollback được phê duyệt và có proof, không phải lỗi migration còn tồn tại.

| Gate | Kết quả thực tế |
|---|---|
| Inspect trước ghi | PASS lúc khoảng 10:43:50: DESKTOP-31GJQ3T / SQLEXPRESS / TCP 1433; catalog và DB_NAME đúng UTEExpress_V14_QA_20261029_7dc87af7; login uteexpress_v14qa_7dc87af7 |
| Quyền và database trống trước Run | PASS: guard kiểm tra mỗi kết nối, db_owner/metadata chỉ QA, không sysadmin, HAS_DBACCESS của ba DB bảo vệ đều 0; 0 user objects/bảng/history/fixture |
| Preflight riêng trước Run | PASS khoảng 10:44:24; Run tự kiểm tra lại Preflight trước bật bước ghi |
| Flyway V1–V13 | PASS, đủ 13 migration SUCCESS và validate history/checksum; không sửa source migration |
| Fixture legacy | PASS: hai receipt_reference cũ trùng nhau, metadata hệ thống NULL; dữ liệu được chụp trước nâng cấp và so sánh nguyên vẹn sau rollback/migrate |
| Proof transaction và fault injection | PASS: Flyway 11.14.1 đúng SHA-256 đã review; V13 cùng JDBC connection/SPID/transaction, transaction active và autoCommit=false; V14 đo lại cùng điều kiện và metadata tồn tại trong connection đó trước khi ném lỗi riêng |
| Rollback migration V14 | PASS: sau expected failure, không còn hai cột/index/CHECK/history V14, V1–V13 vẫn hợp lệ, legacy không đổi; không clean/repair |
| V14 nguyên bản commit | PASS; history SUCCESS, checksum **142205694** khớp source, hai cột VARCHAR(45)/BIGINT nullable, filtered UNIQUE và CHECK đúng metadata |
| UNIQUE | PASS: receipt hợp lệ commit và đọc lại bằng connection mới; mã non-null trùng bị từ chối đúng index UX_intakes_system_receipt_code với mã lỗi thuộc 2601/2627 |
| CHECK | PASS, lỗi 547 đúng CK_intakes_system_receipt cho reference lệch, thiếu version, thiếu code, version âm và độ dài sai; mỗi request âm rollback |
| Transaction rollback nghiệp vụ | PASS: cập nhật trạng thái/custody fixture và insert mã trùng trong cùng transaction; sau rollback shipment vẫn CONFIRMED/current_branch NULL, số intake giữ nguyên |
| Flyway validate sau cùng | PASS, history V1–V14 SUCCESS; không có failed row |
| Hibernate ddl-auto=validate | PASS trên SQL Server thật với mapping baseline V14; không Spring Boot/web server/schema update |

### Trạng thái database sau kiểm thử

Inspect sau Run chỉ SELECT xác nhận **42 bảng** (41 bảng nghiệp vụ + flyway_schema_history), **14 history rows SUCCESS**, V14 columns/index/CHECK = **2/1/1**. Không có history V15 và số cột rate_scope V15 ở quote/shipment = 0.

Giữ lại fixture tối thiểu: 1 User LOCKED không có role và không có mật khẩu sử dụng được đã công bố, 1 branch, 1 Employee, 1 service, 4 shipments, 3 intakes (2 legacy trùng reference + 1 generated receipt hợp lệ); roles có 4 dòng từ migrations. Các insert sai đã rollback. Đây là fixture kiểm tra FK/DDL/transaction, không phải dữ liệu mô phỏng một hành trình vận chuyển hoặc demo seed.

**Database không còn trống và đã được sử dụng. Không chạy lại fresh Run/Preflight như cho database mới; không clean/repair/drop/reset.** Được giữ nguyên để chủ dự án kiểm tra, có thể dùng mode Inspect chỉ SELECT.

### Bằng chứng và giới hạn

Logs ngoài Git, không chứa credential/password/raw provider messages:

- `%TEMP%/uteexpress-v14-approved-inspect-before.log`.
- `%TEMP%/uteexpress-v14-approved-preflight.log`.
- `%TEMP%/uteexpress-v14-approved-run-20261029-1044.log`.
- `%TEMP%/uteexpress-v14-approved-inspect-after.log`.

Chi tiết checksum từng migration và quá trình kiểm thử xem [SQL Server validation V14](v14-sql-server-qa.md). `git diff --check` được chạy sau cập nhật tài liệu. Không chạy lại Maven/browser trong lượt chỉ kiểm thử SQL này vì không sửa source; lượt helper gần nhất vẫn có 95/95 targeted và 1.598 PASS/1 SQL opt-in SKIPPED. Không cộng kết quả cũ thành test SQL mới.

Hibernate dùng test-only `orm-v14-qa.xml`, chỉ loại hai embedding giá V15 ở Shipment/ShipmentQuote, giữ mapping receipt V14 và toàn bộ entity nền. Kết quả chứng nhận **baseline V14**, không chứng nhận ứng dụng Commit 25 chạy trên schema V14. Không chạy V15; SQL concurrency qua hai request/service thực, full browser trên SQL Server hoặc production load không nằm trong lượt được phê duyệt này và chưa được chứng nhận. Những giới hạn đó không làm sai kết quả 10 mục QA V14 đã yêu cầu; không tuyên bố production-ready hoặc SQL Commit 25 PASS.

Lượt này chỉ sửa tài liệu QA, không sửa helper/migration V1–V15, mật khẩu/DPAPI, pom hoặc production config. Không thay đổi UTEExpressDB/UTEExpress_Test/UTEExpress_Demo, không tạo DB mới, không dừng/restart 8083 hoặc thao tác Git làm thay đổi branch/index.

---

## Lịch sử trước lần Run được phê duyệt — đã được thay thế bởi kết quả trên

**Lịch sử 29/10/2026, trước 10:34:** Lỗi mật khẩu 18488 đã được người dùng xử lý; SELECT Inspect thực tế PASS đúng QA, xác nhận 0 user objects/bảng/history/fixture. Lỗi `stage=v13 NullPointerException` do helper không xử lý Context NULL khi Flyway dò callback, đã có RED→GREEN regression và bản vá. Khi đó chưa chạy lại migration hoặc ghi SQL, gate còn PENDING APPROVAL / NOT RUN. [Báo cáo điều tra NPE](v14-null-context-investigation.md) ghi lại bằng chứng của giai đoạn này.

Nhánh local: `feature/auto-intake-receipts`. Báo cáo cập nhật ngày 29/10/2026 (UTC+7), tiếp nối [thiết kế biên nhận](automatic-origin-receipts.md) và [SQL QA V14](v14-sql-server-qa.md). Không commit/merge, không dừng cổng 8083.

## Đã hoàn thành

- Giữ nguyên luồng tự sinh receipt cho BRANCH_DROPOFF và HOME_PICKUP, kiểm tra quyền trước replay, khóa vận đơn và unique filtered index V14. Không sửa V1–V14 trong lượt này.
- Giữ lại toàn bộ thay đổi Commit 24 có sẵn; không reset/xóa source.
- Chạy lại preflight JDBC chỉ đọc bằng helper và DPAPI riêng đã được phê duyệt. Kết quả **BLOCKED**: `SQLState=S0001`, `code=18488`, tại `stage=preflight`. Không chạy migration/fixture/fault injection sau lỗi này. Log đã loại secret: `%TEMP%/uteexpress-commit25-v14-preflight.log`.
- Người dùng đã cung cấp bằng chứng SSMS `IsMustChange=1`; lỗi runtime hiện tại phù hợp với yêu cầu đổi mật khẩu lần đầu. Vì JDBC chưa xác thực thành công, không tuyên bố đã tự kiểm chứng identity, freshness hoặc quyền tại thời điểm này.
- Bổ sung `-ReplaceCredential` cho helper: secure prompt, xuất DPAPI candidate ở TEMP, ACL riêng, import kiểm tra candidate, `File.Replace` nguyên tử cùng volume và giữ bản credential mã hóa cũ. Không xóa bản đang dùng trước khi có bản thay thế. Agent chưa gọi secure prompt hoặc thay credential thực.
- Hỗ trợ tool V14 tiếp tục kiểm tra baseline V14 khi checkout có V15: test-only `orm-v14-qa.xml` chỉ loại hai embedding giá được thêm ở V15. Không migrate V15 trên QA V14. Đây không phải chứng nhận ứng dụng Commit 25 chạy trên schema V14.

## Gate SQL Server trước khi được phê duyệt Run — lịch sử

| Hạng mục | Trạng thái |
|---|---|
| JDBC identity/port/login/catalog/freshness | BLOCKED — 18488 |
| Flyway V1–V14 trên DB QA riêng | BLOCKED — chưa chạy |
| Legacy receipt trùng; filtered UNIQUE; CHECK | BLOCKED — chưa chạy trên SQL Server |
| Callback cùng transaction; migration rollback | BLOCKED — chưa chạy; vẫn đòi proof runtime trước fault injection |
| Business rollback/concurrent receipt trên SQL Server | BLOCKED — chưa chạy |
| Flyway/Hibernate validate SQL Server | BLOCKED — chưa chạy |

Không dùng kết quả H2 để điền PASS cho các gate này. Kết quả trước lượt này: Maven 1.508 PASS/1 SKIPPED và Chrome H2 365 checkpoint PASS, là bằng chứng lịch sử đã lưu, không phải phép đo mới của SQL Server.

## Hướng xử lý 18488 trước đây — đã hoàn tất, chỉ giữ làm lịch sử

Người dùng đã đổi mật khẩu và cập nhật DPAPI; Inspect hiện đã đăng nhập thành công. **Không cần thực hiện lại các bước đổi password/credential bên dưới để sửa NPE callback.** Bước tiếp theo hiện tại chỉ là xem/phê duyệt kế hoạch migration trong báo cáo điều tra mới.

Trong SSMS, dùng tài khoản quản trị Windows đã có, kết nối đúng `tcp:127.0.0.1,1433`, xác minh máy/instance SQLEXPRESS. Mở **Security → Logins → uteexpress_v14qa_7dc87af7 → Properties → General**: đặt mật khẩu mới riêng trong giao diện, bỏ tùy chọn bắt buộc đổi mật khẩu lần đầu, giữ password policy và quyền/mapping hiện có chỉ ở QA. Không đổi login ứng dụng hoặc sa, không sửa DB khác, không gửi mật khẩu vào chat.

Sau đó tự chạy secure prompt:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\v14-sql-qa.ps1 -Mode Credential -ReplaceCredential
```

Helper chỉ thay file DPAPI ngoài Git, không kết nối SQL. Bản cũ được giữ dưới tên backup ngẫu nhiên trong TEMP. Đây không phải xác nhận mật khẩu đã đăng nhập được; bước Preflight riêng vẫn bắt buộc.

Khi quay lại, báo đã đổi password/`IsMustChange=0` và credential được cập nhật, không gửi secret. Chạy Preflight theo tài liệu V14 trước Run; nếu database có object hoặc sai identity thì dừng, không tái sử dụng/reset. DB QA được giữ nguyên cho người dùng kiểm tra. V15 chưa được phê duyệt trên DB này.

## Kiểm thử local của lượt hiện tại

Targeted pricing/mapping/V14 guard: **226/226 PASS**, 0 failures/errors/skips. Lượt `mvnw.cmd test` đầu: 1.587 total / 1.586 PASS / 1 SQL opt-in SKIPPED. Sau các regression cuối, **`mvnw.cmd clean verify -Duteexpress.sqlserver.validate=false`: 1.593 total / 1.592 PASS / 1 SKIPPED / 0 failure/error**, kết thúc 29/10/2026 04:22:17 UTC+7, BUILD SUCCESS; Surefire XML khớp. Skip duy nhất là contextLoads SQL Server opt-in cũ, không phải bỏ qua test để che lỗi.

Chrome headless H2: **399 PASS / 0 FAIL / 0 BLOCKED** ở lượt sau tích hợp giá toàn quốc; bao gồm cả hai biên nhận nguồn, replay, logistics giao thành công/hoàn hàng, OTP và WebSocket. JavaScript 12/12 và WebSocket backend 17/17 đạt; backend WebSocket nằm trong tổng Maven, không cộng lại. PowerShell AST, `git diff --check` và kiểm tra JAR không chứa test tooling/SMTP/H2/credential đều PASS. Xem [báo cáo Commit 25](nationwide-shipping-pricing.md) và JSON browser cho bằng chứng lượt cuối.

## Kết luận trước lần Run SQL Server — lịch sử

Commit 24 chưa thể được gọi là SQL Server verified hoặc hoàn tất gate SQL. Phần nghiệp vụ/H2 đã có bằng chứng; phần SQL bị chặn bởi đổi mật khẩu tương tác, không phải lý do thay đổi migration hay mở rộng quyền. Commit 25 được triển khai độc lập trên mock/H2.

## File liên quan trực tiếp đến phần SQL QA trong lượt này

- Sửa `scripts/v14-sql-qa.ps1`: thay DPAPI an toàn, chưa thực thi prompt.
- Sửa `src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java`: ORM override đúng phạm vi V14.
- Sửa `src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java`: guard cho cơ chế thay file và override.
- Tạo `src/test/java/com/ute/uteexpress/release/v14/V14MappingOverrideTests.java` và `src/test/resources/orm-v14-qa.xml`: metadata proof offline, không JDBC.
- Sửa `docs/v14-sql-server-qa.md` và tạo báo cáo này. Không sửa V14 hoặc core receipt services đang có.

Toàn bộ file mới/sửa của working tree hai commit, trạng thái Git và phân biệt thay đổi kế thừa xem [manifest Commit 25](nationwide-shipping-pricing.md#file-và-trạng-thái-git-cuối-lượt). Không có file xóa/staged; chưa commit.
