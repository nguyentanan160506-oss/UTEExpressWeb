# FINAL STABILITY & BROWSER E2E QA — UTEExpressWeb

Ngày 2026-10-29. Branch `feature/final-demo-data`, HEAD `0283ca891643fc397b71af62d2f20ea8c9d4497d`. Chưa stage/commit/push/merge. Báo cáo này tiếp nối FinalDemo SQL validation và HTTP smoke trước đó, không thay thế bằng chứng lịch sử.

## Kết luận theo phạm vi

**Các gate đã thực thi sau sửa đều PASS:** Chrome SQL chỉ đọc/xác thực 111 checks; Chrome H2 có ghi 634 checks; Maven verify 1.646 tests (1.645 passed, 1 skipped); JavaScript 21 checks. Không có JS exception hoặc HTTP 5xx trong hai lượt Chrome cuối.

Người dùng chọn **chỉ ghi test trên H2, giữ SQL chế độ đọc**. Vì vậy chưa chạy hành trình browser có ghi từ tạo đơn đến DELIVERED/RETURNED trên SQL FinalDemo; không gộp kết quả H2 thành SQL E2E. Đây là nghiệm thu ổn định trong phạm vi đã duyệt, không chứng nhận hoàn toàn không lỗi hoặc production readiness.

FinalDemo đang chạy tại http://127.0.0.1:18085 với **notification worker disabled**. Đã được người dùng cho phép restart riêng server này; không dừng/restart 8083/18084. PID 8083 giữ nguyên 5572 ở các lần kiểm tra trước/sau; 18084 không có listener trong các snapshot. PID FinalDemo cuối kiểm tra: 7932, launcher nền 36724. Không khởi tạo server trùng cổng.

## Lỗi/rủi ro, nguyên nhân và bản sửa

| ID | Bằng chứng trước sửa | Nguyên nhân / thay đổi | Kết quả sau sửa |
|---|---|---|---|
| ST-01 | Chrome SQL baseline 403/404 còn Whitelabel | Thiếu templates/error. Thêm 401/403/404/500 và fallback 4xx/5xx, fragment chung; chỉ thông tin cố định, không render exception/path/message/trace. Giữ nguyên status và API problem JSON | Embedded H2 kiểm tra 400/401/403/404/500; Chrome SQL 401/403/404 thân thiện PASS |
| ST-02 | Một Chrome Profile chia sẻ phiên; FinalDemo dùng JSESSIONID như app localhost khác | Không cố tạo session riêng mỗi tab. Giải thích Profile/context độc lập; đặt cookie UTEEXPRESS_FINALDEMO_SESSION chỉ trong profile FinalDemo | HTTP H2 cookie/session rotation + marker của app khác PASS; Chrome SQL thêm JSESSIONID giả lập trong context QA vẫn giữ tài khoản FinalDemo; nhiều context không đổi tài khoản nhau |
| ST-03 | Form không có danh mục 35 phường, ban đầu dropdown trống; nhập Núi Thành ngoài coverage | Thêm danh mục geography ACTIVE từ DB, tối đa 200 dòng, chỉ DTO địa bàn/chi nhánh/phương thức, không PII. Fetch to-one branch/hub trong một query; giữ bước tìm chi nhánh và toàn bộ binding/CSRF. Bổ sung thông báo, không thêm coverage hoặc đoán chi nhánh | Chrome SQL HOME_PICKUP/BRANCH_DROPOFF có lựa chọn hợp lệ; Núi Thành bị từ chối rõ; bảng mở ở 1366/768/390 không tràn trang |
| ST-04 | /manager/tickets HTTP 503; service list PASS nhưng candidates SQL error 169 / S0001 | page(sort=id) tạo ORDER BY id DESC, id DESC. Loại tie-breaker trùng khi đã sort id, giữ createdAt/updatedAt + id cho các trường khác; không đổi WHERE hoặc scope | SQL read-only list/candidates đều PASS; Chrome /manager/tickets 200; 3 unit regressions giữ branch scope và thứ tự ổn định |
| ST-05 | Guest /quote trên SQL bị cắt HTTP body dù H2 demo nhỏ PASS | 70 dòng coverage vượt buffer HTML; CSRF deferred cần tạo session sau khi response committed. Tái hiện thật trên H2 70 dòng: IllegalStateException “Cannot create a session after the response has been committed” | PublicQuoteController materialize CSRF token ở @ModelAttribute trước render; không tắt/bỏ qua CSRF. Regression anonymous GET đầu với body >20KB PASS; Chrome SQL /quote và POST estimate PASS |

Một lần browser/regression đầu dừng vì thông báo coverage được đổi cách diễn đạt. Đã giữ nguyên thông báo gốc và nối thêm hướng dẫn, không bỏ hay làm yếu assertion nghiệp vụ. Một lỗi test harness 500 probe ban đầu map nhầm vào path biến id; đã thay bằng servlet test-only trong context H2, không thêm probe vào ứng dụng SQL/JAR production.

## Môi trường, startup và an toàn

- Java thực thi: Java 21 tại D:\WebCode\Java\jdk-21. SQL target guard mỗi kết nối: DESKTOP-31GJQ3T / SQLEXPRESS / TCP 1433 / catalog và DB_NAME UTEExpress_FinalDemo / dedicated login uteexpress_finaldemo / db_owner / không access database người dùng khác.
- Khi bắt đầu 18085 đã chạy, PID 34456, đúng launcher FinalDemo. Đã lấy Chrome baseline trên server đó trước sửa và không tự ý tắt. Chỉ restart sau câu trả lời phê duyệt của người dùng; lần reload tiếp theo chỉ đóng server QA vừa tạo để áp dụng fix ST-05.
- Các startup cuối đều Flyway **validate** V1–V15 PASS và Hibernate ddl-auto=validate PASS; không gọi Migrate/Seed, clean/repair/reset/drop. Không sửa V1–V15, checksum V14 142205694 và V15 347627752 giữ nguyên.
- Worker nền được tắt bằng tùy chọn launcher mới `-DisableNotificationWorker`; Run mặc định vẫn bật worker. Tùy chọn không biến cả ứng dụng thành read-only: observer Chrome mới chặn mọi request ghi nghiệp vụ ở cấp Fetch/CDP.
- Warning Maven metadata GitHub flyway-community-db-support HTTP 401 không chặn BUILD SUCCESS, không phải lỗi login SQL hoặc HTTP 401 website. Warning Mockito dynamic agent/unchecked tests và lỗi constraint injection dự kiến trong regression không bị tính là product FAIL.
- Launcher cũ chỉ hiển thị thông điệp trạng thái đã lọc, không giữ toàn bộ stack trace server lịch sử. Lỗi ticket được chẩn đoán lại qua services SQL chỉ đọc; lỗi streaming tái hiện qua server H2 với security thật. Không giả định không có exception chỉ vì log cũ không lưu.
- Backup/restore script đã chuẩn bị trong kế hoạch nhưng **không chạy**, vì người dùng chọn không ghi test SQL. Không tăng quyền login hoặc tạo thêm database. COPY_ONLY/VERIFYONLY và restore rehearsal chưa được chứng minh; không tự restore đè FinalDemo.

## Browser Chrome trên SQL FinalDemo — 111 PASS

Chrome headless riêng, profile TEMP riêng, CDP loopback 19225; browser contexts độc lập: Guest, User, Manager nguồn, Shipper nguồn, Shipper đích, Admin. Không sử dụng Chrome Profile cá nhân hoặc Computer Use.

Phạm vi thực thi:

- Guest: home, tracking, quote, login/register GET; từ chối private ID 401; trang 404/403 thân thiện.
- Đăng nhập DB/BCrypt, giữ session, logout có CSRF; thiếu CSRF logout bị 403. Kiểm tra role chéo 403 hợp lệ và không mặc định ADMIN kế thừa quyền khác.
- Context User vẫn là User sau khi Manager/Admin/Shipper đăng nhập ở các context khác. Cookie mang tên riêng; cookie JSESSIONID giả lập của app khác trong context QA không thay phiên FinalDemo. Không sửa cookie của Chrome cá nhân hoặc gửi request sang 8083/18084.
- User history/create form, detail trong phạm vi, support, notifications đọc; Manager dashboard/shipments/handoffs/trips/deliveries/tickets; Shipper pickup/delivery lists và own detail; Admin dashboard/users/services/reports/audit.
- HOME_PICKUP/BRANCH_DROPOFF: POST branch lookup đọc, có cả nguồn/đích theo dữ liệu thật. Thử ward Núi Thành không được cấu hình, dropdown không có fallback và có lỗi rõ ràng.
- Public estimate POST /quote: lấy service/rates DB hiện hành, kiểm tra shipping/pickup/insurance/total sum; HOME có pickup >0, dropoff pickup=0; không hardcode số tiền. HTML weight 0 không hợp lệ. Không tạo quote giữ giá hoặc xác nhận shipment trên SQL.
- Viewport home và bảng coverage mở rộng: 1366/768/390; table scroll nằm trong wrapper, không tràn toàn trang.
- Runtime JS errors=0, HTTP 5xx=0, unexpected write requests=0. Observer chỉ cho GET/HEAD và POST /login, /logout, /quote, /quote/branches, /user/shipments/branches; POST create/confirm/lock/register/mark-read và port khác bị chặn, có unit safety tests.

## Chrome E2E có ghi — chỉ H2 — 634 PASS

Lệnh: `scripts/browser-qa.ps1 -UiPolish -FinalStability`. Dùng H2 memory UUID mới tại 18082, fake SMTP nội bộ test-only và Chrome TEMP riêng/CDP 19222. Không lấy SQL credential, không chạy SQL Flyway. Report mới lưu TEMP thay vì ghi đè báo cáo Commit 22/26 trong docs.

Đã thực thi browser forms/CSRF thật cho:

- Register → nhận OTP qua SMTP giả lập thật → sai OTP/UNVERIFIED bị từ chối → verify đúng → login, chặn replay OTP; User mới tìm branch, báo giá và tạo đơn.
- HOME_PICKUP và BRANCH_DROPOFF, báo giá/snapshot, xác nhận lặp không tạo hai đơn, ownership/IDOR và private/public tracking.
- Manager xác nhận/phân công, Shipper pickup và yêu cầu bàn giao, Manager độc lập thực nhận nguồn và biên nhận RCV.
- Manifest nhiều kiện, seal/depart/arrive/receive/close qua COLLECTION/LINEHAUL/DISTRIBUTION, hub và chi nhánh đích.
- Last-mile delivery có proof, delivery failure, giao lại, recovery về chi nhánh, các chuyến ngược và RETURNED chỉ sau sender receipt; timeline/chứng từ đã kiểm tra.
- Customer support, nội bộ không lộ khách, notifications/WebSocket hai phiên không reload, reconnect/fallback và quyền nhận.
- Admin account lock/unlock, service/pricing version, preview/publish/retire, reports/CSV/audit trong phạm vi runner hiện có; không dựng UI quản trị branch/employee chưa có Controller.
- UI/keyboard/focus/menu/overflow tại 1440/768/390/320 cho nhiều trang vai trò; JS errors=0 và server 5xx=0.

## Regression và hiệu năng

| Gate | Kết quả |
|---|---|
| Maven offline verify sau ST-05 | 1.646 tests: 1.645 passed, 1 skipped (legacy SQL opt-in), 0 errors/failures; 3 phút 34 giây |
| FriendlyErrorPagesTests | 10 PASS: HTML status, API JSON, cookie/session, anonymous 70-row streaming |
| TicketReadOrderingTests | 3 PASS: id duy nhất, scope bị từ chối, tie-breaker createdAt/id |
| Browser safety tests mới | 2 PASS: allowlist read/auth và chặn mutations/port khác |
| FinalDemo H2 seed/coverage | PASS; 70 area options, inactive branch loại đúng, 1 prepared query |
| JavaScript | 21 PASS: notifications 6, operations 3, reports 3, UI 9; chạy Node runtime có sẵn, không npm dependencies mới |
| SQL service diagnostic | Ticket list + candidates PASS sau fix; trước fix candidates 169/S0001 |
| Whitespace / secret / artifact | PASS: diff check + mọi file mới; scan pattern và so khớp trực tiếp hai credential DPAPI hiện hành (không in giá trị); không artifact/secret trong manifest; JAR không có test tooling/H2/fake SMTP |

SQL Chrome baseline trước sửa: 59 measurements, median TTFB 159ms, max 680ms. Lượt sau sửa khi Maven + H2 E2E cùng chạy có median 234ms, p95 1.212ms, max 2.305ms; không coi số này là benchmark độc lập hoặc tự suy ra cần cache/pool mới.

Lượt cuối không còn job QA nặng đồng thời: **76 measurements, median TTFB 134ms, p95 347ms, max 712ms** (p95 nearest-rank). Các mẫu cold/warm ít, có cache/JIT và app 8083 vẫn chạy; không so trực tiếp để tuyên bố cải thiện theo phần trăm hoặc production load.

| Trang | Số mẫu | Median TTFB (ms) | Max (ms) |
|---|---:|---:|---:|
| /quote | 4 | 329,5 | 347 |
| /user/shipments | 3 | 320 | 336 |
| /user/shipments/create | 3 | 392 | 583 |
| /manager/shipments | 5 | 136 | 322 |
| /manager/tickets | 2 | 249,5 | 275 |
| /admin/reports | 2 | 459,5 | 712 |
| /admin/users | 4 | 73,5 | 229 |

Đo Hibernate Statistics read-only sau warm-up trên SQL (không gồm các SELECT identity guard ngoài ORM):

| Read service | Page size 1 → 20 | Prepared statements |
|---|---|---|
| User history | 1 → 3 rows thực tế | 4 → 3 (trang ngắn bỏ count query) |
| Manager shipments | 1 → 20 rows | 5 → 5 |
| Admin users | 1 → 20 rows | 5 → 5 |
| Coverage | 70 rows | 1 |

Không thấy query tăng theo số hàng trong các phép đo này. Không kết luận mọi endpoint đều không N+1. Không thay pool/cache/index/kiến trúc hoặc giảm kiểm tra quyền để lấy số đẹp.

## Dữ liệu được giữ nguyên

Trước và sau lượt Chrome SQL cuối, inventory SELECT-only giống nhau: 90 users, 21 shipments, 21 quotes, 202 events, 15 intakes cùng các nhóm role/status, giá/số mã biên nhận, metadata constraints/index và V1–V15 history. DB hiện có thêm dữ liệu so với mốc seed 20 đơn lịch sử; không đoán ai tạo và không reset về 20. Chỉ so các mục inventory, không tuyên bố hash toàn bộ byte/cột database.

Không POST tạo đơn, đổi trạng thái/giá/tài khoản hoặc mark notification trên SQL. Các thao tác ghi vào reference/fixture/luồng nghiệp vụ chỉ ở H2 mới. Không backup/restore, không Migrate/Seed, clean/repair/reset/drop, không đổi DPAPI/password hoặc Git identity. Runtime snapshots/logs/screenshots/Chrome profiles ở TEMP ngoài Git.

## Bằng chứng và bàn giao

- SQL baseline: `%TEMP%/uteexpress-stability-sql-read-035b24458d804a11a401f114fe37900f/sql-browser-read.json` (74 PASS, 6 FAIL; gồm 3 assertion UX + 2 ticket 503 + tổng hợp 5xx).
- SQL cuối: `%TEMP%/uteexpress-stability-sql-read-c235b716b42a4c5ba7fd4d5301d34dce/sql-browser-read.json` (111/0/0), screenshots home/coverage ở desktop/tablet/mobile.
- H2 cuối: `%TEMP%/uteexpress-browser-evidence-c3d534448ea44372883b26b316522f20/h2-final-stability.json` (634/0/0), runtimeSecretCheck PASS.
- Maven cuối: `%TEMP%/uteexpress-stability-postcsrf-verify.log`.
- Runtime FinalDemo: đường dẫn trong `%TEMP%/uteexpress-finaldemo-stability-runtime-path.txt`; stdout launcher chỉ các trạng thái đã lọc, không raw request/bind/credential.
- [Lệnh chạy, tài khoản, địa chỉ và kịch bản User → Manager → Shipper → DELIVERED](final-demo-runbook.md).
- [Danh mục 35 địa bàn / 42 điểm / 112 tuyến](final-demo-network.md).

Đủ bằng chứng để người dùng tiếp tục kiểm thử/chốt demo nội bộ một laptop trong phạm vi trên. SQL browser write E2E đã chủ động để lại theo lựa chọn người dùng; SMTP/OTP thực SQL, tải production, LAN/Internet và UI CRUD chi nhánh/nhân viên chưa có source không được tuyên bố PASS. Không khẳng định hệ thống không còn lỗi.

## Manifest Git cần review

Bộ 17 file FinalDemo đã untracked trước giai đoạn này được giữ lại, một số helper/docs được cập nhật. Danh sách dưới đây là toàn bộ working tree hiện tại, gồm cả nền FinalDemo chưa commit; không tự stage.

Tổng 46 file: 12 modified, 34 untracked; không file xóa, index chưa stage.

- ` M` .gitignore
- ` M` scripts/browser-qa.ps1
- ` M` src/main/java/com/ute/uteexpress/controller/PublicQuoteController.java
- ` M` src/main/java/com/ute/uteexpress/controller/UserShipmentController.java
- ` M` src/main/java/com/ute/uteexpress/repository/BranchServiceAreaRepository.java
- ` M` src/main/java/com/ute/uteexpress/service/BranchNetworkService.java
- ` M` src/main/java/com/ute/uteexpress/service/QuoteService.java
- ` M` src/main/java/com/ute/uteexpress/service/operations/TicketReadService.java
- ` M` src/main/resources/templates/auth/login.html
- ` M` src/main/resources/templates/pricing/estimate.html
- ` M` src/main/resources/templates/shipments/create.html
- ` M` src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java
- `??` docs/final-demo-network.md
- `??` docs/final-demo-qa.md
- `??` docs/final-demo-runbook.md
- `??` docs/final-demo-setup.md
- `??` docs/final-stability-plan.md
- `??` docs/final-stability-report.md
- `??` scripts/final-demo-browser-read.ps1
- `??` scripts/final-demo-create-database.sql
- `??` scripts/final-demo-stability-backup.sql
- `??` scripts/final-demo.ps1
- `??` src/main/resources/templates/error/401.html
- `??` src/main/resources/templates/error/403.html
- `??` src/main/resources/templates/error/404.html
- `??` src/main/resources/templates/error/4xx.html
- `??` src/main/resources/templates/error/500.html
- `??` src/main/resources/templates/error/5xx.html
- `??` src/main/resources/templates/error/fragments.html
- `??` src/main/resources/templates/shipments/coverage.html
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoApplication.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoBrowserReadTool.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoBrowserSafetyTests.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoConstraintCheck.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoConstraintCheckTests.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoNetwork.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoSeed.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoTarget.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoTests.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FinalDemoValidation.java
- `??` src/test/java/com/ute/uteexpress/finaldemo/FriendlyErrorPagesTests.java
- `??` src/test/java/com/ute/uteexpress/operations/TicketReadOrderingTests.java
- `??` src/test/resources/final-demo.properties
- `??` src/test/resources/final-demo/network.psv
- `??` src/test/resources/final-demo/pickup-fees.csv
- `??` src/test/resources/final-demo/prices.csv

Scan chốt PASS: tất cả file thay đổi được kiểm whitespace/secret/artifact, hai credential hiện hành không xuất hiện trong nội dung file; JAR không có FinalDemo/LocalDemo/fake SMTP/H2 test tooling; 15 migration Git blobs không đổi (có xét LF/CRLF). Git có warning LF sẽ chuẩn hóa CRLF cho .gitignore theo cấu hình sẵn có, không đổi Git config. Không phát hiện credential/log/backup/database file trong manifest. Các pattern ban đầu bắt nhầm chuỗi ghép URL test và gán biến password trong helper; đã phân biệt biểu thức sinh động với literal, không bỏ qua file kiểm tra.
