# Commit 25 — giá vận chuyển toàn quốc

**Kết sổ sau SQL Server QA V15 — PASS:** chủ dự án xác nhận `RESULT PASS required-gates migration-rollback=PASS database-retained-no-cleanup`, checksum V15 **347627752**. V1–V15/rollback/Hibernate/constraints/legacy/pricing/snapshot/RBAC/concurrency đạt trên QA riêng. Không chạy lại SQL hoặc thay app 8083 ở lượt kết sổ. Xem [nghiệm thu Commit 25](commit25-final-qa.md), [SQL V15](v15-sql-server-qa.md), [manifest và kế hoạch Git](commit24-25-git-plan.md). Các số liệu/file counts/BLOCKED/NOT RUN và hướng dẫn xử lý V14 bên dưới là snapshot lịch sử của giai đoạn triển khai, không phải việc cần chạy lại. QA V14/V15 đều đã dùng và phải được giữ nguyên; browser E2E SQL, restart SQL demo và production load chưa nghiệm thu.

**Ghi chú sau lượt Commit 25:** Báo cáo/manifest 95 file và kết quả 04:25 bên dưới là snapshot của lượt triển khai giá toàn quốc. Trong lượt điều tra helper SQL QA lúc 10:29–10:34 cùng ngày, credential đã hoạt động, Inspect chỉ SELECT xác nhận đúng QA và 0 objects/bảng/history/fixture; NPE callback đã sửa. Chưa chạy lại Run hoặc V14/V15 SQL migration. Xem [trạng thái mới, tests và file thay đổi của helper](v14-null-context-investigation.md); các dòng BLOCKED 18488 bên dưới là lịch sử.

Ngày 29/10/2026 (UTC+7), nhánh `feature/auto-intake-receipts`, chỉ thay đổi local. Không tạo nhánh/commit/push/merge. Không đọc/ghi dữ liệu ba DB được bảo vệ và không chạy V15 trên SQL Server.

## Kiến trúc được tái sử dụng

`QuoteService` là nơi duy nhất chọn bảng giá và phụ phí: dùng chung cho Guest, User, Admin preview và mô phỏng nháp. `ShippingChargeCalculator` chỉ tách công thức BigDecimal dùng chung, không có bảng giá/giá mặc định riêng. Tiếp tục dùng ShippingRateRule, PickupFeeRule, PricingVersion, service gate PESSIMISTIC_WRITE, owner-scoped quote lock, CSRF, RBAC DB và transaction/audit hiện có.

Luồng User: chọn tỉnh/thành và phương thức → tìm chi nhánh có vùng phục vụ → lấy quote riêng → xem phí chi tiết/hạn → xác nhận. Khi xác nhận, tính lại theo điều khoản snapshot đã giữ, đối chiếu tổng phí, quyền, dịch vụ, kích thước, coverage/hub và expiry. Không lấy giá từ request. Quote còn hạn giữ giá cũ khi bảng giá thay đổi; không âm thầm thay phí đã chấp nhận. Retry cùng quote vẫn trả cùng shipment.

## Danh mục và phân miền

Tên và mã hai chữ số được đối chiếu [Quyết định 19/2025/QĐ-TTg, phụ lục I](https://datafiles.chinhphu.vn/cpp/files/vbpq/2025/7/19ttg.signed.pdf). Source UTF-8 bất biến ở `catalog/vietnam-provinces-2025.csv`; không gọi API địa lý khi chạy ứng dụng. Có đúng 34 tỉnh, 6 thành phố. Việc chia NORTH/CENTRAL/SOUTH là **chính sách vận chuyển của đồ án**, không phải phân vùng hành chính được trích từ quyết định.

| Miền vận chuyển | Mã và tên |
|---|---|
| NORTH (15) | 01 Hà Nội; 04 Cao Bằng; 08 Tuyên Quang; 11 Điện Biên; 12 Lai Châu; 14 Sơn La; 15 Lào Cai; 19 Thái Nguyên; 20 Lạng Sơn; 22 Quảng Ninh; 24 Bắc Ninh; 25 Phú Thọ; 31 Hải Phòng; 33 Hưng Yên; 37 Ninh Bình |
| CENTRAL (11) | 38 Thanh Hóa; 40 Nghệ An; 42 Hà Tĩnh; 44 Quảng Trị; 46 Huế; 48 Đà Nẵng; 51 Quảng Ngãi; 52 Gia Lai; 56 Khánh Hòa; 66 Đắk Lắk; 68 Lâm Đồng |
| SOUTH (8) | 75 Đồng Nai; 79 Hồ Chí Minh; 80 Tây Ninh; 82 Đồng Tháp; 86 Vĩnh Long; 91 An Giang; 92 Cần Thơ; 96 Cà Mau |

Chỉ nhận mã, tên hiện hành và một số cách viết xác định rõ như `TP.HCM`, `TP. Đà Nẵng`; không đoán tỉnh từ huyện/xã hoặc tự ánh xạ Quảng Nam/Bình Dương/Hà Giang cũ. Địa chỉ của shipment/address book cũ giữ nguyên. User phải chọn tỉnh hiện hành khi tạo quote mới. Danh mục tỉnh không tự xác nhận khả năng phục vụ; coverage vẫn do BranchServiceArea quyết định. Không thay đổi mô hình huyện/xã lịch sử của ứng dụng trong phạm vi này.

## Năm nhóm và công thức

| Nhóm | Số cặp có hướng | Giá demo cơ bản |
|---|---:|---:|
| INTRA_PROVINCE | 34 | 20.000 VND |
| SAME_REGION | 376 | 30.000 VND |
| NORTH_CENTRAL | 330 | 40.000 VND |
| CENTRAL_SOUTH | 176 | 45.000 VND |
| NORTH_SOUTH | 240 | 60.000 VND |

Tổng 1.156 cặp. Mỗi cặp đúng một nhóm; không thiếu miền rồi mặc định cùng miền. Hai chiều hiện dùng chung nhóm. Snapshot giữ mã gửi/nhận có hướng để sau này thêm mức giá theo chiều mà không mất ý nghĩa lịch sử.

`shipping = basePrice + ceil(max(0, actualWeight − baseWeight) / extraWeightStep) × extraPricePerStep`.

Demo gồm 1 kg đầu, bước cân vượt 1 kg giá 5.000 VND; HOME_PICKUP mặc định 10.000 VND; BRANCH_DROPOFF không dùng pickup rule và không chịu phụ phí này. Ví dụ Trung ↔ Nam: 1 kg dropoff 45.000; 1,01–2 kg dropoff 50.000; 2,01 kg dropoff 55.000; home cộng 10.000. Các số là **giả định học thuật**, không phải giá thị trường.

Giá demo chỉ ở test fixture/resource, được lưu thành rule trong H2 trước khi tính. Controller/Service không hardcode mức phí. Giá, bước cân và giới hạn dịch vụ khác vẫn chỉnh qua Admin hiện có. Không quy đổi thể tích trước đây nên không thêm hệ số kg thể tích tùy tiện; vẫn kiểm tra đầy đủ giới hạn cân/dài/rộng/cao và độ chính xác DECIMAL. Không giá âm/0 che thiếu dữ liệu. BRANCH_DROPOFF phụ phí 0 là quy tắc không bán dịch vụ pickup, không phải fallback khi thiếu rule.

## Ước tính và khả năng phục vụ

- `/quote` mặc định ước tính toàn quốc: chỉ cần 2 tỉnh, dịch vụ, phương thức, trọng lượng/kích thước; HOME_PICKUP lấy phụ phí mặc định toàn quốc từ DB. Không cần giả branch, không tạo quote giữ giá/shipment.
- `/quote?nationwide=false` giữ chế độ có kiểm tra địa bàn/chi nhánh. Admin preview/nháp tiếp tục hỗ trợ.
- `/user/shipments/create` dùng 34 lựa chọn, tìm chi nhánh thực, sau đó quote/confirm như trước. Một tỉnh có công thức giá không đồng nghĩa doanh nghiệp nhận kiện tại đó.
- Phụ phí branch cụ thể ưu tiên; chỉ nếu không có rule branch mới dùng rule mặc định toàn quốc (`branch_id IS NULL`). Nếu rule branch chồng lấn, dừng; không dùng default để che lỗi. National estimate dùng default; quote có branch cụ thể có thể khác, User luôn xem giá chính xác trước xác nhận.
- H2 demo chỉ có địa bàn demo Đà Nẵng và Hồ Chí Minh và các tuyến thật trong fixture cũ; không sinh 34 chi nhánh hoặc tuyến giả. Có thể ước tính Hà Nội → Cà Mau nhưng không được tạo đơn trên địa bàn chưa cấu hình.

## Admin

Mở `/admin/services` và `/admin/pricing`. Giữ luồng tạo nháp, sửa quy tắc, bật/tắt trong nháp, kiểm tra xung đột, công bố, lập lịch ngừng. Dropdown có năm nhóm; phụ phí cho branch riêng hoặc “Toàn quốc (mặc định)”. Bản công bố không sửa trực tiếp; muốn đổi giá tạo phiên bản mới có khoảng hiệu lực phù hợp. Audit và timestamp phiên bản được giữ.

Lỗi JPA cũ được phát hiện bằng integration thật và sửa: khi tạo ShippingService/PricingVersion, phải dùng entity trả về từ `saveAndFlush` trước khi đọc ID/ghi audit. Với entity có `@Version` đã khởi tạo, Spring Data có thể gọi merge và trả bản managed khác. Test mock cũ không phát hiện điều này.

## V15 và tương thích dữ liệu

`V15__add_nationwide_shipping_pricing.sql`, hai batch theo parser Flyway SQL Server:

- Nới CHECK scope để chấp nhận 5 nhóm mới, giữ INTER_PROVINCE/INTER_REGION cũ. Không tự chuyển bảng giá generic cũ sang nhóm cụ thể; thiếu bảng giá mới phải báo lỗi rõ ràng.
- Cho `pickup_fee_rules.branch_id` NULL làm default toàn quốc, giữ FK khi có branch. Rebuild bốn index phụ thuộc với đúng tên/key/filter cũ. Unique `(pricing_version_id, branch_id)` vẫn ngăn hai default trong một bản trên SQL Server; semantics NULL khác H2 được nêu rõ và kiểm tra adapter. Tham khảo [Microsoft UNIQUE index](https://learn.microsoft.com/en-us/sql/relational-databases/indexes/create-unique-indexes?view=sql-server-ver17).
- Thêm 8 cột nullable vào mỗi bảng quote/shipment: nhóm, mã tỉnh đi/đến, phí cơ bản, phí vượt cân, cân cơ bản, bước cân, giá mỗi bước. CHECK cho phép toàn bộ NULL legacy hoặc snapshot đầy đủ, không âm và khớp shipping_fee. GO tách ADD khỏi CHECK.
- Không backfill/update/delete địa chỉ, tiền, rule hoặc payment. Không seed giá, tạo branch/user hay đổi lịch sử. FK/index khác giữ nguyên. Cần sao lưu và cửa sổ bảo trì khi kiểm thử nâng cấp vì rebuild index có thể giữ khóa; chưa kiểm chứng trên SQL Server thật.

Ứng dụng mới cần V15, nhưng **không chạy V15 trên bất kỳ SQL Server nào trong lượt này**. V14 QA riêng chỉ được target14. Launcher SQL demo hiện bị chặn trước JDBC ở các mode migrate/seed/run khi checkout có V15; không tự nâng phạm vi phê duyệt từ V14 lên V15. Inspect chỉ đọc vẫn giữ. Ứng dụng đang chạy 8083 không bị dừng hoặc restart.

## Kiểm thử và tiến độ

Targeted: **226/226 PASS**, gồm parser/mapping H2, official catalog/classification, 1.156 cặp × 2 phương thức qua QuoteService/JPA thật, Admin publish 5 nhóm/default pickup, snapshot, legacy, rollback, concurrent confirm, RBAC và guard V14. Đây là 226 test methods/invocations, không cộng 2.312 phép ước tính thành số test.

Kết quả thực thi:

| Kiểm tra | Kết quả |
|---|---|
| Maven `test -Duteexpress.sqlserver.validate=false` đầu | 1.587 total / 1.586 PASS / 1 SKIPPED |
| Maven `clean verify -Duteexpress.sqlserver.validate=false` cuối | **1.593 total / 1.592 PASS / 0 failures/errors / 1 SKIPPED**, BUILD SUCCESS, 04:22:17 UTC+7 |
| Surefire XML | Khớp tổng trên; skip duy nhất contextLoads opt-in SQL cũ |
| Chrome headless thật, H2 riêng | **399 PASS / 0 FAIL / 0 BLOCKED**, không Computer Use |
| JavaScript qua Node runtime, không npm | **12/12 PASS** (6 notifications + 3 operations + 3 reports) |
| Backend WebSocket regression | **17/17 PASS** trong tổng Maven; browser hai phiên/reconnect cũng PASS |
| Production JAR | PASS: có catalog/V15; không có H2, test classes/resources, fake SMTP, browser/QA tooling hoặc DPAPI credential |
| PowerShell helper AST / `git diff --check` | PASS; chỉ cảnh báo LF/CRLF sẵn có |
| SQL QA V14 / nguyên bản SQL Server V15 / persistence SQL Commit 25 | **BLOCKED / NOT RUN / NOT RUN**, không suy ra từ H2 |

Full logs ngoài Git: `%TEMP%/uteexpress-commit25-targeted-final.log`, `uteexpress-commit25-all-tests.log`, `uteexpress-commit25-clean-verify.log`, `uteexpress-commit25-browser-final.log`. JSON browser được lưu ở [browser-qa-results.json](browser-qa-results.json), được kiểm tra không chứa runtime password/OTP. Các lượt không cộng dồn số test/checkpoint.

Chrome lượt cuối **2026-10-28T21:25:19Z = 29/10/2026 04:25:19 UTC+7**, evidence ngoài Git: `%TEMP%/uteexpress-browser-evidence-35a0348169364a59b200b21798bf35c6`. Lượt cuối dùng bản build sau `clean verify`, gồm validation coverage có điều kiện mới nhất. Không JS/console.error, không HTTP 5xx, runtimeSecretCheck PASS.

SHA-256 V15 source: `FAA2CF1CB51B3407B0FE642640B5479E99EE3709E1E8B2B301B84695E1696367`. Đây là nhận dạng file đã review, không phải bằng chứng đã migrate SQL Server.

H2 upgrade adapter bỏ SET options và thay filtered indexes theo semantics được ghi ngay trong test; không phải thực thi nguyên bản SQL Server. Test Hibernate riêng đã parse thực `orm-v14-qa.xml` offline và xác minh đủ 41 entity, chỉ loại cột snapshot V15, giữ cột receipt V14; không có JDBC SQL Server trong phép thử này.

Lỗi ở các lượt đầu đã được xử lý và chạy lại: kỳ vọng scope generic cũ được đổi đúng chính sách 5 nhóm, whitelist projection thêm breakdown an toàn, JPA managed-result/audit đã sửa bằng regression thật. Validation public có điều kiện: national không đòi chi nhánh giả, coverage mode vẫn bắt buộc huyện/xã/branch; request thiếu bị trả lỗi form, không gây 500. Không disable/bỏ test thất bại.

Cảnh báo còn lại: Mockito/ByteBuddy dynamic-agent trên Java 21, unchecked fixture test cũ, explicit SQLServerDialect ở metadata test offline, repository metadata Maven riêng trả 401 nhưng dependencies cache đủ và build thành công. Không sửa pom/cấu hình production để che các cảnh báo này.

## Khi chủ dự án quay lại

1. Xử lý mật khẩu login V14 theo `commit24-final-qa.md`, rồi chạy Preflight/Run đúng QA V14 nếu đủ điều kiện. Không áp dụng V15 tại đó.
2. Review source/diff local Commit 24–25 và báo cáo test cuối; tự quyết định tách commit. Agent không stage/commit.
3. Chạy H2 riêng bằng `scripts/release-tools.ps1 -Mode Demo -Port 18083 -TestMail`; nhập mật khẩu riêng qua secure prompt. Không dùng lại session SQL 8083 để thử checkout mới. Account H2: `owner@example.test`, `admin@example.test`, `manager@example.test`, `pickup-courier@example.test`; mật khẩu do chủ dự án chọn khi launch, không lưu vào Git.
4. Trên H2 thử `/quote` với đủ năm nhóm/cân 0,5–1–1,01–2,01, hai phương thức; tạo đơn với Đà Nẵng/Hồ Chí Minh, “Khu vực demo”/“Phường demo”; xem breakdown, confirm và retry. User/shipper vào pricing Admin phải 403.
5. Đề xuất QA V15 sau: một DB mới khác hoàn toàn QA V14, login riêng và xác nhận mới, kiểm tra schema/data legacy, nguyên bản V15, NULL unique, FK, rollback, Hibernate validate. Chỉ lập kế hoạch; chưa tạo DB hoặc cấp quyền.

Không cần cloud/domain để demo một laptop. H2 có thể demo local nhưng dữ liệu mất khi dừng; SQL persistence của Commit 25 còn gate V15 chưa kiểm chứng. Không gọi production-ready hoặc hoàn tất SQL QA khi chưa có bằng chứng.

## File và trạng thái Git cuối lượt

Working tree gồm **65 file tracked sửa + 30 untracked = 95 file**, không staged hoặc xóa. Trong lượt hiện tại **tạo 17 file, sửa 42 file**; các thay đổi khác đã có từ Commit 24/Giai đoạn A và được giữ nguyên. Không đổi branch khỏi `feature/auto-intake-receipts`, không chạy add/commit/push/merge/reset/stash/clean. Không sửa pom hoặc application.properties. Manifest sau liệt kê toàn bộ working tree và đánh dấu nguồn thay đổi; không bao gồm target/TEMP bị Git ignore.

<!-- COMMIT25_FILE_MANIFEST -->

| Git | File | Trong lượt hiện tại |
|---|---|---|
| ?? | [docs/automatic-origin-receipts.md](../docs/automatic-origin-receipts.md) | Giữ nguyên từ Commit 24 / QA A |
| M | [docs/browser-qa-results.json](../docs/browser-qa-results.json) | Sửa |
| M | [docs/browser-qa.md](../docs/browser-qa.md) | Sửa |
| ?? | [docs/commit24-final-qa.md](../docs/commit24-final-qa.md) | Tạo mới |
| M | [docs/final-qa-report.md](../docs/final-qa-report.md) | Sửa |
| ?? | [docs/nationwide-shipping-pricing.md](../docs/nationwide-shipping-pricing.md) | Tạo mới |
| M | [docs/release-checklist.md](../docs/release-checklist.md) | Sửa |
| M | [docs/sql-demo-setup.md](../docs/sql-demo-setup.md) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [docs/v14-sql-server-qa.md](../docs/v14-sql-server-qa.md) | Sửa |
| ?? | [scripts/v14-sql-qa.ps1](../scripts/v14-sql-qa.ps1) | Sửa |
| M | [src/main/java/com/ute/uteexpress/controller/ManagerHandoffController.java](../src/main/java/com/ute/uteexpress/controller/ManagerHandoffController.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/controller/ManagerLogisticsController.java](../src/main/java/com/ute/uteexpress/controller/ManagerLogisticsController.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/controller/PublicQuoteController.java](../src/main/java/com/ute/uteexpress/controller/PublicQuoteController.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/controller/ShipperPickupController.java](../src/main/java/com/ute/uteexpress/controller/ShipperPickupController.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/controller/UserShipmentController.java](../src/main/java/com/ute/uteexpress/controller/UserShipmentController.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/dto/manager/ManagerForms.java](../src/main/java/com/ute/uteexpress/dto/manager/ManagerForms.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/dto/pricing/GuestQuoteForm.java](../src/main/java/com/ute/uteexpress/dto/pricing/GuestQuoteForm.java) | Sửa |
| ?? | [src/main/java/com/ute/uteexpress/dto/pricing/PriceBreakdown.java](../src/main/java/com/ute/uteexpress/dto/pricing/PriceBreakdown.java) | Tạo mới |
| M | [src/main/java/com/ute/uteexpress/dto/pricing/PricingCommands.java](../src/main/java/com/ute/uteexpress/dto/pricing/PricingCommands.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/dto/pricing/PricingForms.java](../src/main/java/com/ute/uteexpress/dto/pricing/PricingForms.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/dto/pricing/PublicQuoteView.java](../src/main/java/com/ute/uteexpress/dto/pricing/PublicQuoteView.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/dto/shipment/BranchIntakeCommand.java](../src/main/java/com/ute/uteexpress/dto/shipment/BranchIntakeCommand.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/dto/shipment/HandoffReviewCommand.java](../src/main/java/com/ute/uteexpress/dto/shipment/HandoffReviewCommand.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/main/java/com/ute/uteexpress/dto/shipment/OriginIntakeResult.java](../src/main/java/com/ute/uteexpress/dto/shipment/OriginIntakeResult.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/dto/shipment/ShipmentDetailView.java](../src/main/java/com/ute/uteexpress/dto/shipment/ShipmentDetailView.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/dto/shipment/ShipmentQuoteView.java](../src/main/java/com/ute/uteexpress/dto/shipment/ShipmentQuoteView.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/dto/shipper/PickupForms.java](../src/main/java/com/ute/uteexpress/dto/shipper/PickupForms.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/dto/shipper/PickupViews.java](../src/main/java/com/ute/uteexpress/dto/shipper/PickupViews.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/entity/PickupFeeRule.java](../src/main/java/com/ute/uteexpress/entity/PickupFeeRule.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/entity/Shipment.java](../src/main/java/com/ute/uteexpress/entity/Shipment.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/entity/ShipmentIntake.java](../src/main/java/com/ute/uteexpress/entity/ShipmentIntake.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/java/com/ute/uteexpress/entity/ShipmentQuote.java](../src/main/java/com/ute/uteexpress/entity/ShipmentQuote.java) | Sửa |
| ?? | [src/main/java/com/ute/uteexpress/entity/ShippingPriceSnapshot.java](../src/main/java/com/ute/uteexpress/entity/ShippingPriceSnapshot.java) | Tạo mới |
| M | [src/main/java/com/ute/uteexpress/entity/ShippingRateRule.java](../src/main/java/com/ute/uteexpress/entity/ShippingRateRule.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/enums/RateScopeType.java](../src/main/java/com/ute/uteexpress/enums/RateScopeType.java) | Sửa |
| ?? | [src/main/java/com/ute/uteexpress/enums/ShippingRegion.java](../src/main/java/com/ute/uteexpress/enums/ShippingRegion.java) | Tạo mới |
| M | [src/main/java/com/ute/uteexpress/repository/PickupFeeRuleRepository.java](../src/main/java/com/ute/uteexpress/repository/PickupFeeRuleRepository.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/service/AdminPricingService.java](../src/main/java/com/ute/uteexpress/service/AdminPricingService.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/service/ManagerHandoffService.java](../src/main/java/com/ute/uteexpress/service/ManagerHandoffService.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/main/java/com/ute/uteexpress/service/OriginReceiptCodes.java](../src/main/java/com/ute/uteexpress/service/OriginReceiptCodes.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/main/java/com/ute/uteexpress/service/ProvinceCatalog.java](../src/main/java/com/ute/uteexpress/service/ProvinceCatalog.java) | Tạo mới |
| M | [src/main/java/com/ute/uteexpress/service/QuoteService.java](../src/main/java/com/ute/uteexpress/service/QuoteService.java) | Sửa |
| M | [src/main/java/com/ute/uteexpress/service/ShipmentLifecycleService.java](../src/main/java/com/ute/uteexpress/service/ShipmentLifecycleService.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/main/java/com/ute/uteexpress/service/ShippingChargeCalculator.java](../src/main/java/com/ute/uteexpress/service/ShippingChargeCalculator.java) | Tạo mới |
| M | [src/main/java/com/ute/uteexpress/service/UserShipmentService.java](../src/main/java/com/ute/uteexpress/service/UserShipmentService.java) | Sửa |
| ?? | [src/main/resources/catalog/vietnam-provinces-2025.csv](../src/main/resources/catalog/vietnam-provinces-2025.csv) | Tạo mới |
| ?? | [src/main/resources/db/migration/V14__add_generated_origin_intake_receipts.sql](../src/main/resources/db/migration/V14__add_generated_origin_intake_receipts.sql) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/main/resources/db/migration/V15__add_nationwide_shipping_pricing.sql](../src/main/resources/db/migration/V15__add_nationwide_shipping_pricing.sql) | Tạo mới |
| M | [src/main/resources/templates/manager/detail.html](../src/main/resources/templates/manager/detail.html) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/resources/templates/manager/handoff-detail.html](../src/main/resources/templates/manager/handoff-detail.html) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/main/resources/templates/pricing/detail.html](../src/main/resources/templates/pricing/detail.html) | Sửa |
| M | [src/main/resources/templates/pricing/estimate.html](../src/main/resources/templates/pricing/estimate.html) | Sửa |
| M | [src/main/resources/templates/pricing/rules.html](../src/main/resources/templates/pricing/rules.html) | Sửa |
| M | [src/main/resources/templates/shipments/create.html](../src/main/resources/templates/shipments/create.html) | Sửa |
| M | [src/main/resources/templates/shipments/detail.html](../src/main/resources/templates/shipments/detail.html) | Sửa |
| M | [src/main/resources/templates/shipments/quote.html](../src/main/resources/templates/shipments/quote.html) | Sửa |
| M | [src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java](../src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/demo/SqlDemoApplication.java](../src/test/java/com/ute/uteexpress/demo/SqlDemoApplication.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/demo/SqlDemoSafetyTests.java](../src/test/java/com/ute/uteexpress/demo/SqlDemoSafetyTests.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java](../src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java) | Sửa |
| ?? | [src/test/java/com/ute/uteexpress/operations/BrowserNationwidePricingJourney.java](../src/test/java/com/ute/uteexpress/operations/BrowserNationwidePricingJourney.java) | Tạo mới |
| M | [src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java](../src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java](../src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/operations/CustomerJourneySupport.java](../src/test/java/com/ute/uteexpress/operations/CustomerJourneySupport.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/operations/CustomerShipmentJourneyTests.java](../src/test/java/com/ute/uteexpress/operations/CustomerShipmentJourneyTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java](../src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/operations/LogisticsConcurrencyIntegrationTests.java](../src/test/java/com/ute/uteexpress/operations/LogisticsConcurrencyIntegrationTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/operations/LogisticsGuardIntegrationTests.java](../src/test/java/com/ute/uteexpress/operations/LogisticsGuardIntegrationTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/operations/LogisticsJourneySupport.java](../src/test/java/com/ute/uteexpress/operations/LogisticsJourneySupport.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/operations/LogisticsMvcIntegrationTests.java](../src/test/java/com/ute/uteexpress/operations/LogisticsMvcIntegrationTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/operations/LogisticsRollbackIntegrationTests.java](../src/test/java/com/ute/uteexpress/operations/LogisticsRollbackIntegrationTests.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/test/java/com/ute/uteexpress/operations/NationwideDemoPricing.java](../src/test/java/com/ute/uteexpress/operations/NationwideDemoPricing.java) | Tạo mới |
| ?? | [src/test/java/com/ute/uteexpress/operations/NationwidePricingIntegrationTests.java](../src/test/java/com/ute/uteexpress/operations/NationwidePricingIntegrationTests.java) | Tạo mới |
| ?? | [src/test/java/com/ute/uteexpress/operations/OriginIntakeIntegrationTests.java](../src/test/java/com/ute/uteexpress/operations/OriginIntakeIntegrationTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/pricing/PricingManagementTests.java](../src/test/java/com/ute/uteexpress/pricing/PricingManagementTests.java) | Sửa |
| ?? | [src/test/java/com/ute/uteexpress/pricing/ProvinceCatalogTests.java](../src/test/java/com/ute/uteexpress/pricing/ProvinceCatalogTests.java) | Tạo mới |
| ?? | [src/test/java/com/ute/uteexpress/release/NationwidePricingMigrationTests.java](../src/test/java/com/ute/uteexpress/release/NationwidePricingMigrationTests.java) | Tạo mới |
| ?? | [src/test/java/com/ute/uteexpress/release/OriginIntakeMigrationTests.java](../src/test/java/com/ute/uteexpress/release/OriginIntakeMigrationTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java](../src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/test/java/com/ute/uteexpress/release/v14/V14MappingOverrideTests.java](../src/test/java/com/ute/uteexpress/release/v14/V14MappingOverrideTests.java) | Tạo mới |
| ?? | [src/test/java/com/ute/uteexpress/release/v14/V14QaFixtures.java](../src/test/java/com/ute/uteexpress/release/v14/V14QaFixtures.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java](../src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java) | Sửa |
| ?? | [src/test/java/com/ute/uteexpress/release/v14/V14QaTarget.java](../src/test/java/com/ute/uteexpress/release/v14/V14QaTarget.java) | Giữ nguyên từ Commit 24 / QA A |
| ?? | [src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java](../src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java) | Sửa |
| ?? | [src/test/java/com/ute/uteexpress/release/v14/V14TransactionProbe.java](../src/test/java/com/ute/uteexpress/release/v14/V14TransactionProbe.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/security/LogisticsServiceSecurityTests.java](../src/test/java/com/ute/uteexpress/security/LogisticsServiceSecurityTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/security/ManagerLogisticsControllerTests.java](../src/test/java/com/ute/uteexpress/security/ManagerLogisticsControllerTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/security/ManagerLogisticsFlowTests.java](../src/test/java/com/ute/uteexpress/security/ManagerLogisticsFlowTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/security/PricingFlowTests.java](../src/test/java/com/ute/uteexpress/security/PricingFlowTests.java) | Sửa |
| M | [src/test/java/com/ute/uteexpress/security/ShipperPickupFlowTests.java](../src/test/java/com/ute/uteexpress/security/ShipperPickupFlowTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/service/ManagerLifecycleTests.java](../src/test/java/com/ute/uteexpress/service/ManagerLifecycleTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/service/PickupLifecycleTests.java](../src/test/java/com/ute/uteexpress/service/PickupLifecycleTests.java) | Giữ nguyên từ Commit 24 / QA A |
| M | [src/test/java/com/ute/uteexpress/service/UserShipmentServiceTests.java](../src/test/java/com/ute/uteexpress/service/UserShipmentServiceTests.java) | Sửa |
| ?? | [src/test/resources/demo/nationwide-shipping-prices.csv](../src/test/resources/demo/nationwide-shipping-prices.csv) | Tạo mới |
| ?? | [src/test/resources/orm-v14-qa.xml](../src/test/resources/orm-v14-qa.xml) | Tạo mới |
