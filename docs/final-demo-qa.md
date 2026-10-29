# Final Demo — báo cáo triển khai và QA

Ngày: 2026-10-29. Branch: `feature/final-demo-data`, xuất phát từ main `0283ca891643fc397b71af62d2f20ea8c9d4497d` (merge Commit 26). Chưa stage/commit/push/merge.

## Kết luận hiện tại

**Final Stability mới nhất:** Chrome SQL chỉ đọc/xác thực **111 PASS / 0 FAIL / 0 BLOCKED**, Chrome H2 có ghi **634 PASS / 0 FAIL / 0 BLOCKED**. Maven verify **1.645 passed, 1 skipped** (1.646 tổng), JavaScript 21 PASS. Chi tiết lỗi, bản sửa, timing và manifest ở [final-stability-report.md](final-stability-report.md).

Đã sửa trang lỗi thân thiện, cookie FinalDemo riêng, hướng dẫn coverage từ DB, SQL ORDER BY trùng ở Manager tickets và CSRF deferred của form lớn. FinalDemo **đang chạy 18085 với worker disabled** sau restart riêng được duyệt; 8083/18084 không bị restart. Lệnh/tài khoản/kịch bản ở [runbook](final-demo-runbook.md).

Các phần bên dưới lưu bằng chứng SQL V1–V15/seed/DBCC và HTTP smoke 83 checks của các lượt trước. SQL vẫn giữ dữ liệu hiện có; trong stability chỉ đọc/xác thực, người dùng chọn ghi test trên H2. Không tuyên bố browser write E2E SQL hoặc production readiness.

## Nguyên nhân JDBC/DBCC và bản sửa

Ở lượt seed trước, transaction seed và kiểm tra network đã thành công; hậu kiểm gọi `Statement.executeQuery(DBCC...)` phát sinh SQLServerException code 0 / SQLState null. Database không rollback và không còn trống.

Đã kiểm tra bytecode driver thực tế **mssql-jdbc-13.2.1.jre11.jar**: khi `executeQuery()` không nhận ResultSet, nhánh `R_noResultset` gọi `SQLServerException.makeFromDriverError` với SQLState null. Sau bản sửa, cùng DBCC trên database được giữ nguyên trả về:

```text
PASS Flyway validate V1-V15
PASS Hibernate validate
PASS network 35 coverage sites 1225 branch pairs real-service scenarios and actor bindings
PASS SQL DBCC resultSets=0 updateCounts=1 violationRows=0
PASS SQL constraints and technical network validation
```

Đây xác nhận lỗi giao thức JDBC của cách gọi cũ: DBCC không trả ResultSet trong lần kiểm tra không có vi phạm. `updateCounts=1` là **số phần tử update-count đã đọc trong luồng kết quả JDBC**, không phải thông báo đã UPDATE một bản ghi nghiệp vụ. Thông báo exception đầy đủ của lượt cũ không được giữ lại; kết luận dựa trên bytecode driver và kết quả thực tế của cùng lệnh qua API phù hợp.

Bản sửa chỉ thuộc helper test-classpath:

- `FinalDemoConstraintCheck`: dùng `execute()`, đọc mọi ResultSet và update count, chỉ kết thúc khi `getMoreResults` trả false và `getUpdateCount() == -1`.
- Duyệt mọi dòng của mọi ResultSet; bất kỳ dòng nào cũng làm validation FAIL. Không in dữ liệu dòng/biểu thức constraint. Update count 0 không bị coi là đã hết kết quả.
- Không nuốt SQLException ở execute, đọc row hoặc kết quả tiếp theo. SQLWarning cũng khiến validation không được chấp nhận. Statement/ResultSet được đóng và đặt query timeout 60 giây.
- `FinalDemoApplication` gọi helper mới, báo số result sets/update counts/violation rows an toàn. Không thay đổi SQL DBCC, migration hay nghiệp vụ.
- 9 JDBC regression tests kiểm tra không ResultSet, ResultSet rỗng, nhiều kết quả xen update counts, vi phạm đầu/cuối, lỗi SQL muộn, warning/missing ResultSet và đóng tài nguyên. Thêm 3 FinalDemo H2 regression: tổng targeted **12 PASS**.

## Startup và HTTP smoke — 2026-10-29

Dùng runner tạm trong TEMP, không đưa vào repo/JAR: cùng UteexpressApplication và FinalDemoApplication.Configuration, loopback 18085, Flyway disabled, Hibernate validate, **notification worker disabled**. Runner xác minh cả notificationWorker và ticketNotificationWorker không có bean trước khi báo READY. Không chạy migration/seed, không xử lý outbox nghiệp vụ.

Lỗi script ở lượt trước đã được sửa theo phê duyệt: `$home` đổi thành `$homeResponse`, vì `$HOME` là biến hệ thống chỉ đọc và PowerShell không phân biệt hoa/thường. Đã kiểm tra parser và tên biến dành riêng trước khi thực thi. Không sửa source nghiệp vụ hoặc script launcher trong repository.

### Kết quả bốn vai trò

| Vai trò | Tài khoản synthetic đã đăng nhập | Các trang GET được phép đã kiểm tra (200) | Assertions |
|---|---|---|---:|
| Admin | final-admin@example.test | /admin, /admin/users | 20 |
| Manager | final-manager-b-79@example.test | /manager, /manager/shipments, /manager/trips | 21 |
| Shipper | final-shipper-b-79@example.test | /shipper, /shipper/pickups, /shipper/deliveries | 21 |
| User | final-user-01@example.test | /user/shipments, /user/shipments/create | 20 |

82 assertions theo vai trò + 1 assertion startup/worker = **83 PASS**, 0 FAIL, 0 BLOCKED. Mỗi vai trò có HttpClient/CookieContainer riêng; mật khẩu lấy từ DPAPI web credential, không đưa vào argument, source, báo cáo hoặc log.

Mỗi phiên đều thực thi:

- GET trang chủ và form đăng nhập: 200.
- POST /login thiếu CSRF: 403; đăng nhập đúng credential và CSRF: 302 về /account.
- JSESSIONID trước/sau đăng nhập khác nhau, không rỗng; token/cookie không được ghi log.
- GET /account: 200, đúng email synthetic và liên kết thao tác của vai trò. GET /login khi đã đăng nhập trả 302.
- GET các trang đúng vai trò: 200; thử ba endpoint gốc của vai trò khác đều 403 (12 kiểm tra truy cập chéo tổng cộng). Không tự gán quyền hoặc coi ADMIN kế thừa MANAGER/SHIPPER/USER.
- POST /logout thiếu CSRF: 403; session vẫn truy cập /account được 200.
- POST /logout với CSRF: 302 về /login?logout. Sau đó endpoint riêng và /account đều trả 401: session xác thực đã bị thu hồi.

Output kết thúc:

```text
SMOKE ROLE PASS ADMIN
SMOKE ROLE PASS MANAGER
SMOKE ROLE PASS SHIPPER
SMOKE ROLE PASS USER
SMOKE SUMMARY PASS assertions=83 roles=4 no-business-posts no-migrate no-seed
```

### Bảo toàn dữ liệu và phạm vi bằng chứng

Trước/sau smoke, helper SELECT-only qua FinalDemoTarget đối chiếu inventory đã sắp xếp: số bản ghi các bảng kiểm tra, nhóm role/branch type/route leg/shipment status, consumed quotes, phép kiểm phí, số mã biên nhận, metadata FK/CHECK/index và toàn bộ Flyway history đều giống nhau. Vẫn có 90 User, 20 Shipment, 20 quote đã tiêu thụ, 193 ShipmentEvent và 13 intake receipt. Đây là so sánh inventory đã nêu, không là checksum mọi byte hoặc mọi cột trong database.

Chỉ gửi GET và POST /login, /logout. Không gửi request tạo báo giá/vận đơn, đổi trạng thái, đánh dấu thông báo, điều phối, đổi giá hoặc sửa tài khoản. Không in HTML, cookie, CSRF hoặc credential vào log. Log assertions và inventory nằm TEMP ngoài Git.

Cleanup chỉ đóng process Java do runner tạo và đã xác nhận **18085 không còn listener**. Không dừng/restart 8083/18084. SMTP/OTP, JavaScript, responsive, WebSocket UI và hành trình logistics qua browser chưa được kiểm thử trong lượt HTTP này. RBAC mới kiểm tra mức vai trò, không thay thế bộ kiểm thử ownership/branch scope và IDOR đầy đủ.

### Chạy để kiểm thử thủ công

Database đã có V1–V15 và dữ liệu; **không chạy lại Migrate/Seed**. Mở terminal PowerShell tại project:

```powershell
cd D:\WebCode\uteexpress\uteexpress
$env:JAVA_HOME='D:\WebCode\Java\jdk-21'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Run -ApprovedDatabase UTEExpress_FinalDemo -Port 18085
```

Mở http://127.0.0.1:18085 trên Chrome. Đăng nhập bằng một tài khoản trong bảng trên và **mật khẩu website đã nhập trong secure prompt**. Danh sách địa chỉ coverage/tài khoản khác ở [final-demo-network.md](final-demo-network.md). Lệnh Run bình thường bật notification worker để xử lý hàng đợi; riêng runner smoke ở lượt này tắt worker. Run không tự migrate/seed và từ chối port đang bị chiếm. Ứng dụng chỉ chạy khi người dùng thực hiện lệnh trên; phiên smoke đã đóng server tạm.

## Phạm vi đã chốt

Người dùng phê duyệt mở đủ 35 chi nhánh: **35 LOCAL_BRANCH + 7 CENTRAL_HUB, 112 tuyến có hướng, 42 Manager, 35 Shipper**. Mỗi Employee gắn một User duy nhất và một điểm vận hành; ManagerBranch phải khớp điểm Employee. Không nới phân quyền để giảm số tài khoản.

Đề xuất 82 tuyến trước đó đã được thay thế. `TransportRouting.path()` hiện dùng chặng trực tiếp originHub → destinationHub; không tìm đường qua hub thứ ba. Vì vậy cần 42 tuyến LINEHAUL giữa 7 hub, cộng 70 tuyến COLLECTION/DISTRIBUTION. 42 Manager bảo đảm người tiếp nhận/vận hành thực tế tại cả 42 điểm; 35 Shipper phục vụ chi nhánh của mình.

## Số lượng thực tế sau seed (đã SELECT độc lập trên SQL Server)

| Dữ liệu | H2 thực tế | SQL Server FinalDemo |
|---|---:|---|
| Chi nhánh khách | 35 | 35 |
| Hub | 7 | 7 |
| Tuyến | 112 | 112 (35 COLLECTION + 35 DISTRIBUTION + 42 LINEHAUL) |
| Service areas | 70 | 70 |
| User tổng | 90 | 90 |
| Manager / Shipper / User / Admin | 42 / 35 / 12 / 1 | 42 / 35 / 12 / 1 (JOIN user_roles/roles) |
| Employee / ManagerBranch | 77 / 42 | 77 / 42 |
| Dịch vụ / PricingVersion | 1 / 1 | 1 / 1 |
| ShippingRateRule / PickupFeeRule | 5 / 1 | 5 / 1 |
| Vận đơn / quote đã xác nhận | 20 / 20 | 20 / 20 (consumed_at có giá trị) |
| DeliveryProof / ReturnReceipt / SupportTicket | 1 / 1 / 1 | 1 / 1 / 1 |

Các lượng trên là trạng thái seed ban đầu, trước khi regression tạo thêm User và đơn để kiểm tra khách mới. Bài kiểm thử H2 trước đó thêm 1 User (mô phỏng trạng thái sau xác minh), tạo 70 báo giá = 35 điểm × 2 phương thức và xác nhận thêm 2 đơn TP.HCM → Đà Nẵng. Không khẳng định đã kiểm thử đăng ký/OTP/browser trong môi trường SQL mới.

## Kiểm tra SELECT sau lỗi

- `shipment_events=193`, `shipment_intakes=13`, `transport_trips=32`, `trip_receipts=31`; có một scenario đang IN_TRANSIT nên không phải mọi chuyến đã có receipt.
- 13 system_receipt_code đều khác nhau; 0 biên nhận thiếu code/request_version, sai độ dài 45 hoặc không khớp receipt_reference.
- `UX_intakes_system_receipt_code`: unique=1, disabled=0, has_filter=1.
- 0 vận đơn sai tổng phí hoặc phụ phí theo HOME_PICKUP/BRANCH_DROPOFF; 0 vận đơn thiếu sự kiện.
- Shipment status thực tế: CREATED 2, CONFIRMED 1, PICKUP_ASSIGNED 1, PICKED_UP 2, AT_ORIGIN_BRANCH 2, AT_ORIGIN_HUB 1, IN_TRANSIT 1, AT_DESTINATION_HUB 1, AT_DESTINATION_BRANCH 2, OUT_FOR_DELIVERY 1, DELIVERED 1, DELIVERY_FAILED 2, RETURNING 1, RETURNED 1, CANCELLED 1.
- Flyway history giữ đủ V1–V15 SUCCESS; database không bị reset hoặc coi lại là trống sau hậu kiểm lỗi.

### Flyway schema history thực tế

| Version | Checksum | Success |
|---|---:|---|
| V1 | 189739357 | true |
| V2 | 1191373203 | true |
| V3 | -1720389847 | true |
| V4 | -1466061241 | true |
| V5 | 1925825129 | true |
| V6 | -1705174727 | true |
| V7 | 444247411 | true |
| V8 | 2036200466 | true |
| V9 | 61067824 | true |
| V10 | 236607534 | true |
| V11 | 2051557719 | true |
| V12 | -1057022707 | true |
| V13 | 1007916434 | true |
| V14 | 142205694 | true |
| V15 | 347627752 | true |

## Kết quả thực thi

| Gate | Kết quả | Bằng chứng/phạm vi |
|---|---|---|
| Geography dataset | PASS | 35 mã/tên phường khớp text Công báo Quyết định 19/2025/QĐ-TTg; 34 mã tỉnh khớp ProvinceCatalog |
| Targeted JDBC + H2 FinalDemoTests | PASS | 12 tests: 9 JDBC result-handling + 3 H2 seed/safety/geography; không kết nối SQL trong tests |
| Toàn bộ Maven offline verify | PASS | `mvnw.cmd -o -Duteexpress.sqlserver.validate=false verify`: 1.631 tests, 1.630 passed, 0 failures/errors, 1 skipped; BUILD SUCCESS, 2 phút 44 giây |
| SQL opt-in regression | SKIP | UteexpressApplicationTests chỉ bật bằng system property; không bật trong lượt này |
| PowerShell parser | PASS | final-demo.ps1 không có lỗi parser |
| Migration files | PASS | 15 Git blobs khớp HEAD; có xét chuyển đổi LF/CRLF của working tree; không sửa V1–V15, không tạo V16 |
| Whitespace | PASS | git diff --check + kiểm tra riêng các file untracked (Git diff không bao gồm chúng) |
| Secret/artifact scan | PASS trong phạm vi scan | Không thấy token/private-key/password literal theo các pattern kiểm tra; DPAPI/log/PDF/dependencies nằm TEMP, không thuộc manifest |
| Production JAR exclusion | PASS | Không có FinalDemo classes/resources, credential XML, script PS1, file log hoặc .env trong các entry JAR |
| Inspect SQL FinalDemo | PASS | Đúng máy DESKTOP-31GJQ3T, SQLEXPRESS, TCP 1433, catalog/DB_NAME UTEExpress_FinalDemo, login riêng, db_owner, không access database người dùng khác; 0 user objects trước migrate |
| Flyway V1–V15 FinalDemo | PASS | 15 migrations SUCCESS, Flyway validate PASS; checksum V14 142205694, V15 347627752 |
| Hibernate validate SQL FinalDemo | PASS | ddl-auto=validate trước seed, không schema update |
| SQL seed / persistence | PASS | Transaction đã commit; SELECT độc lập xác nhận counts, 20 consumed quotes, 193 events, 13 intake receipts |
| SQL network / nghiệp vụ seed | PASS trong phạm vi thực thi | 1.225 cặp branch path/coverage/actor bindings, các workflow service tạo/giao/thất bại/giao lại/hoàn/support; không phải browser E2E |
| SQL DBCC JDBC hậu kiểm | PASS | Mode Validate exit 0; resultSets=0, updateCounts=1, violationRows=0; không migrate/seed lại |
| FK/CHECK metadata | PASS trong phạm vi metadata | 0 disabled/untrusted FK; 0 disabled/untrusted CHECK; không thay thế DBCC |
| Startup 18085 | PASS | Server tạm khởi động với worker disabled, sau đó đóng riêng process này |
| Login/session/CSRF/RBAC/logout HTTP smoke | PASS | 83 assertions, đủ 4 vai trò, 0 FAIL/BLOCKED; không browser E2E |
| Inventory trước/sau HTTP smoke | PASS | Các counts/groups/history/constraint metadata kiểm tra đều khớp; không request ghi nghiệp vụ |

Các build helper trong lượt SQL này đều BUILD SUCCESS. Có warning HTTP 401 khi Maven đọc metadata GitHub package flyway-community-db-support; dependency đã có sẵn nên không chặn build, không phải lỗi login SQL hoặc HTTP website. Không đổi credential để xử lý warning này. Baseline lượt chuẩn bị là 1.622 tests; sau thêm 9 JDBC regression, verify mới đạt 1.631 tests như bảng. Maven có các cảnh báo/mock fault-injection dự kiến từ regression cũ; kết quả tổng hợp không có lỗi. Không bỏ test để đạt PASS. Lần phát triển ban đầu phát hiện thiếu lý do hủy trong fixture; đã bổ sung lý do qua DTO hiện có, không sửa logic nghiệp vụ.

## Tính nhất quán và chống ghi đè

- Mọi kết nối SQL qua FinalDemoTarget kiểm tra product, máy DESKTOP-31GJQ3T, SQLEXPRESS, TCP 1433, catalog + DB_NAME(), ORIGINAL_LOGIN(), db_owner và không có quyền truy cập database người dùng khác/sysadmin/dbcreator.
- Inspect dùng SELECT; migrate bắt buộc 0 user objects; không baseline, clean, repair, reset hoặc tự resume schema không rõ trạng thái. Flyway target 15.
- Seed có `sp_getapplock` session riêng và một Spring transaction SERIALIZABLE bao trùm dữ liệu tham chiếu + toàn bộ 20 workflow. H2 đã kiểm tra rollback toàn bộ User, Branch, Shipment, Trip, PricingVersion và Event về 0; đây không thay thế bằng chứng rollback SQL Server.
- Dấu FD-INITIAL chỉ commit cùng toàn bộ seed. Rerun yêu cầu namespace/scenario đầy đủ và là no-op; giữ hash mật khẩu, dữ liệu khách sửa, trạng thái và giá. Namespace chưa hoàn chỉnh thì dừng, không tự chữa/xóa.
- Các vận đơn đi qua QuoteService, ShipmentLifecycleService, TransportTripService, LastMileService và TicketService. Không set trực tiếp trạng thái để giả lập thành công. Proof/reference/note ghi rõ mô phỏng; hai cách tiếp nhận nguồn dùng C24 sinh mã biên nhận.
- Không gọi notification worker REQUIRES_NEW trong transaction seed; event/outbox được giữ để worker của Run xử lý sau commit.
- Giá giả định học thuật từ fixture CSV được lưu thành bảng giá DB, service báo giá thật tạo snapshot C25. Không tính tiền song song, không sửa giá đơn cũ.

## Vận đơn mẫu

Mỗi note có `FD-SCENARIO:<mã>`; User sở hữu được luân phiên từ final-user-01 đến final-user-12. Tất cả luồng vật lý mẫu dùng TP.HCM → Đà Nẵng, còn mạng lưới/coverage mở đầy đủ 35 điểm.

NEW_DROP, CONFIRMED_DROP, PICKUP_ASSIGNED, PICKED_UP, HANDOFF_PENDING, ORIGIN_INTAKE_DROP, ORIGIN_INTAKE_HOME, ORIGIN_HUB, LINEHAUL, DEST_HUB, DEST_BRANCH, DELIVERY_ASSIGNED, DELIVERY_RUNNING, DELIVERED, DELIVERY_FAILED, REDELIVERY, RETURNING, RETURNED, CANCELLED, SUPPORT.

Các nhãn scenario biểu thị cả tiến độ assignment/handoff; không bổ sung enum trạng thái mới. Ví dụ HANDOFF_PENDING vẫn giữ trạng thái PICKED_UP, DELIVERY_ASSIGNED vẫn ở chi nhánh đích theo nghiệp vụ hiện có.

## Coverage và hạn chế

Xem [danh sách 35 điểm, địa chỉ nhập form và tài khoản](final-demo-network.md). 34 tỉnh có địa điểm mô phỏng, nhưng chỉ 35 phường được mở coverage. Tất cả 1.225 cặp chi nhánh có chặng vận hành hợp lệ; 1.156 cặp tỉnh là phạm vi phân loại giá, không phải cam kết giao mọi địa chỉ.

Form/schema còn bắt buộc district theo mô hình cũ. Dataset dùng tên địa bàn cũ kèm nhãn rõ, không bịa một cấp hành chính hiện hành. Phải nhập đúng địa bàn/phường đã cấu hình rồi tìm chi nhánh phục vụ; không bỏ bước này. Chưa thay đổi UX/schema địa chỉ hoặc mở wildcard toàn tỉnh.

SMTP thật/chức năng đăng ký nhận OTP trong FinalDemo chưa được cấu hình hay kiểm thử. Tài khoản synthetic ACTIVE ban đầu phục vụ demo riêng; không tự kích hoạt đăng ký mới hoặc bỏ xác minh. Không đưa fake SMTP vào production JAR.

Đây là bộ dữ liệu đồ án một laptop, không là bằng chứng production readiness, năng lực vận tải/giá thị trường hoặc load test.

## Những file mới (17), không sửa file tracked

- docs/final-demo-setup.md
- docs/final-demo-network.md
- docs/final-demo-qa.md
- scripts/final-demo-create-database.sql
- scripts/final-demo.ps1
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoApplication.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoConstraintCheck.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoConstraintCheckTests.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoNetwork.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoSeed.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoTarget.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoTests.java
- src/test/java/com/ute/uteexpress/finaldemo/FinalDemoValidation.java
- src/test/resources/final-demo.properties
- src/test/resources/final-demo/network.psv
- src/test/resources/final-demo/prices.csv
- src/test/resources/final-demo/pickup-fees.csv

## Kết luận và công việc còn lại

Xem [báo cáo stability mới nhất](final-stability-report.md) để biết kết quả sau các bản sửa; đoạn HTTP smoke 83 checks bên trên là lịch sử và đã được thay bằng Chrome SQL 111 checks cho nghiệm thu đọc/xác thực. Server hiện chạy worker disabled, không còn ở trạng thái tắt sau smoke cũ.

Không chạy lại Migrate/Seed hoặc reset dữ liệu. Inventory mới trước/sau loạt đọc cuối: 90 users, 21 shipments/quotes, 202 events, 15 intakes; giữ nguyên dữ liệu khác mốc seed 20 đơn. Chưa stage/commit/push/merge. SQL write browser E2E không chạy theo lựa chọn người dùng, SMTP thật/LAN/Internet/production load vẫn ngoài nghiệm thu hiện tại.
