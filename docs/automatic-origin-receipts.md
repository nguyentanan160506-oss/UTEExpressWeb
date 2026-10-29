# Commit 24 — Biên nhận tiếp nhận nguồn tự sinh

Nhánh `feature/auto-intake-receipts`, base `785d20a` (đã chứa Commit 23 `67f8e7e`). Chỉ thay luồng tiếp nhận nguồn; không đổi trip/hub receipt, seal/depart references, pricing, trạng thái vận chuyển hoặc quyền của các role.

## Kiến trúc và dữ liệu

BRANCH_DROPOFF: `/manager/shipments/{id}/intake` → `ShipmentLifecycleService.receiveDropoff`.
HOME_PICKUP: `/manager/handoffs/{id}/accept` → `ShipmentLifecycleService.reviewOriginHandoff`, sau bằng chứng pickup và bàn giao độc lập.

Cả hai vẫn tạo đúng một `ShipmentIntake`, cập nhật custody nguồn và ghi `ShipmentEvent` trong transaction cũ. Không thêm hệ thống/bảng biên nhận khác. Không thay cơ chế audit cũ; hai luồng này ghi biên nhận và shipment event, không tự thêm AuditLog trùng.

`OriginReceiptCodes` sinh `RCV-yyyyMMdd-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX`: ngày UTC của thời điểm nhận và UUID v4 bỏ dấu gạch, viết hoa, 45 ký tự. Client không có trường mã trong command/form; gửi `receiptReference` hoặc trường ngoài allowlist bị từ chối HTTP 400. Branch, actor, Employee lấy từ dữ liệu đã kiểm tra quyền, không lấy từ body.

Mã sinh chỉ trong POST tiếp nhận hợp lệ. GET không sinh hoặc thay mã. Kết quả `OriginIntakeResult` được trả qua transaction proxy; controller chỉ đặt flash notice có mã sau khi transaction thành công. UNIQUE/ghi event hoặc commit lỗi thì rollback và trả thông báo lỗi chung, không thông báo nhận thành công.

## V14 — nâng cấp không sửa dữ liệu cũ

`V14__add_generated_origin_intake_receipts.sql` thêm:

- `system_receipt_code VARCHAR(45) NULL`: mã hệ thống mới.
- `request_version BIGINT NULL`: version vận đơn ngay trước lần tiếp nhận, dùng đối chiếu retry.
- CHECK yêu cầu hai cột cùng NULL (legacy), hoặc cùng có giá trị hợp lệ và mã hệ thống khớp `receipt_reference`.
- Unique filtered index `UX_intakes_system_receipt_code WHERE system_receipt_code IS NOT NULL`.

Không backfill, UPDATE, DELETE hoặc thay mã cũ. Các mã thủ công trong `receipt_reference` trước đây có thể trùng và tiếp tục được hiển thị như cũ. Mã hệ thống mới là khóa duy nhất trong namespace `system_receipt_code`; mã thủ công legacy không được hồi tố biến thành định danh duy nhất. Với receipt mới, cùng mã được ghi vào `receipt_reference` để giữ các màn hình/consumer hiện có, CHECK ngăn hai giá trị lệch nhau.

Không đặt UNIQUE lên toàn bộ `receipt_reference`, vì dữ liệu cũ có thể trùng. Không đặt JPA unfiltered UNIQUE cho cột nullable vì nó không biểu diễn đúng filtered index SQL Server. Flyway quản lý index production; UNIQUE `shipment_id` cũ vẫn giữ để ngăn hai intake cho cùng vận đơn.

V14 đặt đủ SET options cho filtered index; GO tách ADD COLUMN khỏi CHECK/index để tránh SQL error 207. V1–V13 không sửa. Parser Flyway SQL Server kiểm tra các batch có thể chạy trong transaction.

**Chưa áp dụng V14 hoặc kiểm thử trên SQL Server thật.** Không migrate/seed/connect bất kỳ DB SQL Server nào trong lượt triển khai này. Bản mapping mới cần V14 trước khi khởi động bằng database thật. SQL demo launcher được cập nhật yêu cầu V1–V14 để từ chối schema cũ; không tự migrate ở Run. Migrate mode vốn chỉ dành database mới trống, không phải công cụ nâng cấp database hiện có. Muốn áp dụng V14 lên demo đang chứa dữ liệu phải có phê duyệt/quy trình riêng; không dùng clean/repair/reset/seed lại để nâng cấp. Không dừng tiến trình tại 8083 trong lượt này.

## Idempotency và kiểm tra trạng thái

Mỗi POST khóa vận đơn `PESSIMISTIC_WRITE`. Trước mọi nhánh replay đều kiểm tra ROLE_MANAGER hiện tại trong DB, User ACTIVE, ManagerBranch và Employee ACTIVE đúng chi nhánh/vị trí. HOME_PICKUP giữ kiểm tra người nhận độc lập với người giao, đúng assignment/evidence/handoff.

Chỉ trả receipt đã lưu khi **tất cả** khớp:

- Receipt do hệ thống mới tạo, đúng vận đơn, phương thức và chi nhánh nguồn.
- Cùng actor và Employee đã thực nhận; xác nhận thực nhận vẫn được gửi.
- Version request khớp version gốc đã lưu; version hiện tại đúng gốc + 1.
- Ghi chú đã chuẩn hóa khớp request ban đầu.
- Vận đơn vẫn `AT_ORIGIN_BRANCH`, currentBranch vẫn nguồn, không còn assignment xung đột.
- HOME_PICKUP: handoff đúng đã ACCEPTED và pickup assignment đã COMPLETED, cùng bằng chứng và người xử lý.

Hai request giống nhau đồng thời: một request ghi; request chờ khóa trả cùng mã sau commit, không ghi thêm event/intake. Request actor khác, payload/version khác, quyền bị mất, handoff sai hoặc kiện đã xuất chuyến đều bị từ chối; không dùng replay để che sai trạng thái. Biên nhận legacy thiếu metadata không được tự suy đoán là retry hợp lệ; vẫn xem được qua GET.

UUID giảm xác suất trùng; **database unique index mới là chốt bảo đảm** cho mã mới. Không chỉ dùng SELECT kiểm tra trùng. Nếu ép trùng, UNIQUE từ chối và toàn bộ transaction thất bại; không đổi trạng thái hoặc ghi event thành công. Không tự retry transaction tại chỗ sau khi database báo lỗi.

## Kiểm thử

Kết quả thực thi ngày 29/10/2026 (UTC+7):

| Nhóm | Kết quả |
|---|---|
| Controller/service tiếp nhận | 91/91 PASS |
| Origin intake, migration, logistics MVC/concurrency/rollback | 69/69 PASS; trong đó 14 test intake mới và 2 test V14 mới |
| Mapping Hibernate tích lũy V1–V14 và Manager MVC flow | 17/17 PASS |
| Toàn bộ Maven `test -Duteexpress.sqlserver.validate=false` | 1.509 total, 1.508 passed, 0 failures/errors, 1 skipped; BUILD SUCCESS 02:38:00 |
| Chrome headless/H2 | 365 PASS / 0 FAIL / 0 BLOCKED; 0 JS/console.error, 0 HTTP 5xx, runtime secret guard PASS |
| `git diff --check` | PASS; chỉ cảnh báo LF/CRLF |
| SQL Server thực thi V14/locking/upgrade thật | NOT RUN theo giới hạn của yêu cầu |

Các nhóm targeted có phần trùng nhau; không cộng để tính tổng. Một test skip là SQL validation opt-in cũ, cố ý tắt để không truy cập SQL Server. Toàn bộ test dùng mock/H2 độc lập; H2 không chứng minh locking/DDL SQL Server thật.

Lượt full test đầu phát hiện schema fixture mới đọc V1–V13 và MVC flow còn gửi mã thủ công; đã cập nhật lên V14 và chạy lại toàn bộ đạt số liệu cuối trên. Các lỗi khác biệt NULL của H2 đã được xử lý bằng fixture đúng semantics, không bỏ UNIQUE của migration hoặc bỏ test thất bại.

Logs: `%TEMP%/uteexpress-commit24-targeted.log`, `uteexpress-commit24-integration.log`, `uteexpress-commit24-mapping.log`, `uteexpress-commit24-all-tests.log`, `uteexpress-commit24-browser.log`. Evidence Chrome: `%TEMP%/uteexpress-browser-evidence-c577c9967ad14303824d9bb0c1879b55`. Browser timestamp 2026-10-28T19:38:08Z = 29/10/2026 02:38:08 UTC+7. 365 là checkpoint, không phải 365 hành trình độc lập.

H2 MSSQL mode mặc định không tương đương filtered unique index; fixture mới dùng `CREATE UNIQUE NULLS DISTINCT INDEX` để cho phép nhiều legacy NULL, vẫn chặn trùng mã non-null. Test migration chạy SQL Server parser thật, rồi thực thi DDL tương đương trên H2: bỏ SET options riêng SQL Server và chuyển predicate filtered index sang NULLS DISTINCT. Đây là dialect adapter được nêu rõ, không tuyên bố đã chạy V14 trên SQL Server. Tham khảo [H2 grammar](https://h2database.com/html/grammar.html#nulls_distinct), [H2 CREATE INDEX](https://h2database.com/html/commands.html#create_index).

## Thử bằng giao diện trên H2 riêng

1. Dùng H2 demo mới riêng tại cổng 18083 bằng lệnh bên dưới; không dùng phiên SQL Server tại 8083 cho bản mapping mới khi chưa áp dụng V14 được duyệt. Nhập password demo qua secure prompt, không ghi literal. H2 dùng các account `owner@example.test`, `manager@example.test` (Đà Nẵng), `pickup-courier@example.test`; không phải prefix demo23 của SQL. H2 mất dữ liệu khi dừng.
2. BRANCH_DROPOFF: tạo/xác nhận đơn, Manager đúng nguồn mở chi tiết. Form không còn nhập mã; vẫn cần tick thực nhận và ghi chú tùy chọn. Submit: notice có `RCV-...`, phần “Biên nhận nguồn đã lưu” và timeline có cùng mã.
3. HOME_PICKUP: courier lấy thành công và gửi handoff; Manager độc lập đúng nguồn mở bàn giao, tick thực nhận. Notice, trang bàn giao và chi tiết vận đơn có cùng mã tự sinh.
4. Tải lại GET: mã không đổi, không có receipt/event mới. Gửi lại chính POST/version/ghi chú ban đầu khi chưa có bước nghiệp vụ khác: trả mã cũ. POST với version mới hoặc sau xuất chuyến: báo lỗi, không nhận lại.
5. Thử Manager khác chi nhánh, USER/Guest, thiếu CSRF, chưa xác nhận vật lý hoặc mã client giả: bị từ chối, không có intake.
6. Xem biên nhận cũ: mã thủ công giữ nguyên. Chứng từ trip/hub/seal/depart vẫn nhập và xử lý theo nghiệp vụ cũ.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 18083 -TestMail
```

Mở http://127.0.0.1:18083, dùng địa chỉ mô phỏng Đà Nẵng / Khu vực demo / Phường demo → Hồ Chí Minh / Khu vực demo / Phường demo để tạo đơn mới. Không dùng dữ liệu cá nhân thật.

Commit đề xuất: `feat(logistics): generate idempotent origin intake receipts`.

## Danh sách file

Working tree: 34 modified, 6 untracked, 0 deleted, 0 staged. Phần lớn file test/fixture chỉ cập nhật constructor command bỏ mã client. `ShipperPickupController` chỉ thêm helper notice có kết quả để dùng cho origin handoff; nghiệp vụ shipper khác giữ nguyên. `SqlDemoApplication` cập nhật guard yêu cầu schema14, không được chạy trong lượt này. Không đổi pom, application.properties hoặc V1–V13.

### File sửa

- `docs/browser-qa-results.json`
- `docs/browser-qa.md`
- `docs/sql-demo-setup.md`
- `src/main/java/com/ute/uteexpress/controller/ManagerHandoffController.java`
- `src/main/java/com/ute/uteexpress/controller/ManagerLogisticsController.java`
- `src/main/java/com/ute/uteexpress/controller/ShipperPickupController.java`
- `src/main/java/com/ute/uteexpress/dto/manager/ManagerForms.java`
- `src/main/java/com/ute/uteexpress/dto/shipment/BranchIntakeCommand.java`
- `src/main/java/com/ute/uteexpress/dto/shipment/HandoffReviewCommand.java`
- `src/main/java/com/ute/uteexpress/dto/shipper/PickupForms.java`
- `src/main/java/com/ute/uteexpress/dto/shipper/PickupViews.java`
- `src/main/java/com/ute/uteexpress/entity/ShipmentIntake.java`
- `src/main/java/com/ute/uteexpress/service/ManagerHandoffService.java`
- `src/main/java/com/ute/uteexpress/service/ShipmentLifecycleService.java`
- `src/main/resources/templates/manager/detail.html`
- `src/main/resources/templates/manager/handoff-detail.html`
- `src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java`
- `src/test/java/com/ute/uteexpress/demo/SqlDemoApplication.java`
- `src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java`
- `src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java`
- `src/test/java/com/ute/uteexpress/operations/CustomerShipmentJourneyTests.java`
- `src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java`
- `src/test/java/com/ute/uteexpress/operations/LogisticsConcurrencyIntegrationTests.java`
- `src/test/java/com/ute/uteexpress/operations/LogisticsGuardIntegrationTests.java`
- `src/test/java/com/ute/uteexpress/operations/LogisticsJourneySupport.java`
- `src/test/java/com/ute/uteexpress/operations/LogisticsMvcIntegrationTests.java`
- `src/test/java/com/ute/uteexpress/operations/LogisticsRollbackIntegrationTests.java`
- `src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java`
- `src/test/java/com/ute/uteexpress/security/LogisticsServiceSecurityTests.java`
- `src/test/java/com/ute/uteexpress/security/ManagerLogisticsControllerTests.java`
- `src/test/java/com/ute/uteexpress/security/ManagerLogisticsFlowTests.java`
- `src/test/java/com/ute/uteexpress/security/ShipperPickupFlowTests.java`
- `src/test/java/com/ute/uteexpress/service/ManagerLifecycleTests.java`
- `src/test/java/com/ute/uteexpress/service/PickupLifecycleTests.java`

### File mới

- `docs/automatic-origin-receipts.md`
- `src/main/java/com/ute/uteexpress/dto/shipment/OriginIntakeResult.java`
- `src/main/java/com/ute/uteexpress/service/OriginReceiptCodes.java`
- `src/main/resources/db/migration/V14__add_generated_origin_intake_receipts.sql`
- `src/test/java/com/ute/uteexpress/operations/OriginIntakeIntegrationTests.java`
- `src/test/java/com/ute/uteexpress/release/OriginIntakeMigrationTests.java`
