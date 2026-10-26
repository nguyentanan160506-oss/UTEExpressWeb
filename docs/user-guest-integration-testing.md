# Commit 19 — Kiểm thử tích hợp User, Guest, hỗ trợ và thông báo

Ngày kiểm thử: 25–26/10/2026 (UTC+7). Phụ trách Tân, nhánh `feature/tan-user-integration-tests`.
Điểm bắt đầu là `main` tại `8d17b8c`, đã merge Commit 18 (`2a847aa`) và Commit 17 (`9ee9612`).
Working tree sạch trước khi tạo nhánh. Không stage, commit, push, merge, stash hoặc sửa lịch sử Git.

## Mục tiêu và phạm vi khảo sát

Ổn định các ranh giới Auth/Account → Quote → Shipment, User/Guest ownership, Customer Support,
định tuyến ticket và notification outbox. Tái sử dụng kiến trúc hiện có, không viết lại module.
Đã đối chiếu SecurityConfig/DatabaseUserDetailsService, Entity/Repository/DTO/form/controller,
QuoteService/BranchNetworkService/UserShipmentService, PublicTrackingService/policy,
TicketService/CustomerSupportAccess/read service/throttle/state policy, notification processors,
workers/delivery/API và bộ kiểm thử WebSocket. Luồng logistics chỉ kiểm tra ở ranh giới với khách hàng.

Không thay production source, dependency, cấu hình hoặc schema; V1–V13 giữ nguyên.
Không tạo migration, seed dữ liệu, tài khoản thật hoặc truy cập dữ liệu khách hàng.

## Baseline và kết quả

Baseline thực chạy trước khi thêm test:

| Lệnh | Kết quả |
|---|---|
| `.\mvnw.cmd clean test` | 1.146 test; 1.145 passed; 0 failure; 0 error; 1 skipped; BUILD SUCCESS; 2 phút 01 giây |
| `.\mvnw.cmd -DskipTests compile` | BUILD SUCCESS; 3,873 giây |

Baseline có đủ 17 ca WebSocket/STOMP trên embedded Tomcat và các nhóm JDBC Operations,
Customer Support, Admin Reports/Audit. Test opt-in SQL Server là test skipped duy nhất.
Full `clean test` sau bổ sung: **1.222 test, 1.221 passed, 0 failure, 0 error, 1 skipped**;
BUILD SUCCESS, 2 phút 17 giây. Phân tách bằng XML Surefire: 1.146 test cũ (1 skipped) + 76 test mới
(không skipped), không mất test cũ. 17 test WebSocket/STOMP tiếp tục đạt.

| Nhóm mới | Số test | Persistence |
|---|---:|---|
| CustomerAccessIntegrationTests | 21 | MVC/security/session/BCrypt + H2 JDBC |
| CustomerPricingIntegrationTests | 23 | Service/JPA/H2 JDBC, gồm xác nhận đồng thời |
| CustomerShipmentJourneyTests | 27 | Service/lifecycle/JPA/H2 JDBC |
| CustomerSupportJourneyTests | 4 | Service/worker/JPA/H2 JDBC + broker in-process |
| CustomerThrottleIntegrationTests | 1 | TicketService/transaction/H2 JDBC + Clock |
| Tổng | 76 | Không repository mock trong các nhóm mới |

JavaScript: Notifications **6**, Operations **3**, Reports **3** checks đều đạt (12/12).

| Kiểm tra cuối | Kết quả |
|---|---|
| `.\mvnw.cmd verify` | BUILD SUCCESS; 1.222 test / 1.221 passed / 0 failure / 0 error / 1 skipped; 2 phút 02 giây; JAR đã package/repackage |
| `.\mvnw.cmd -DskipTests compile` | BUILD SUCCESS; 4,797 giây |
| `git diff --check` | Exit 0 |
| File untracked | Kiểm tra riêng UTF-8, newline cuối và trailing whitespace trên đủ 8 file, đều đạt |
| SQL Server / Hibernate validate | Chưa chạy; test opt-in cũ skipped |

Hai assertion privacy được chỉnh sau lần clean test để kiểm tra chuỗi số tiền có phần thập phân,
tránh vô tình trùng dãy số ngẫu nhiên trong tracking hex. `verify` đã compile và chạy lại toàn bộ test
với chỉnh sửa cuối này, giữ nguyên 1.222 test. Không đổi expected behavior hoặc bỏ ca lỗi.
Log lệnh nằm trong thư mục TEMP của phiên với tiền tố `uteexpress-commit19-`;
báo cáo XML mới nhất nằm ở `target/surefire-reports/` (không đưa generated artifacts vào Git).

Warning baseline có sẵn: Maven không tải được metadata Flyway Community từ GitHub Packages (HTTP 401,
không làm build thất bại); Mockito/ByteBuddy dynamic Java agent trên Java 21; explicit SQLServerDialect
trong offline mapping test; unchecked operations ở test LastMile cũ. Các log constraint violation của H2
và STOMP access denied là fault injection/negative assertions có chủ đích, không phải lỗi bị bỏ qua.

## Ma trận kiểm thử

Các lớp mới nằm trong `src/test/java/com/ute/uteexpress/operations/`.
Ma trận bao gồm regression cũ để tránh viết lại hàng trăm assertion đã có.

| Nhóm | Test mới và bằng chứng | Regression hiện có tiếp tục chạy |
|---|---|---|
| Auth/Account | `CustomerAccessIntegrationTests`: BCrypt thật với User/Role từ H2; session fixation, giữ session, logout, LOCKED/UNVERIFIED, session không hợp lệ, 401/403/404, CSRF | `AuthenticationTests`, `DatabaseUserDetailsServiceTests`, `SecurityConfigTests`, registration/OTP tests |
| Role isolation | Đăng nhập USER/MANAGER/SHIPPER/ADMIN từ DB; không tự kế thừa USER; tài khoản bị khóa dù giữ session bị chặn ở shipment/support/notification API | Service security tests của User, Quote, Logistics, Admin |
| Tạo vận đơn | MVC thật quote → review → confirm; forged sender/status/fee/event/actor bị binder từ chối; hai POST confirm trả cùng resource | `UserShipmentControllerTests`, `UserShipmentFlowTests`, `ShipmentValidationTests` |
| Validation | `CustomerPricingIntegrationTests`: địa chỉ gửi/nhận, độ dài tên, trọng lượng/kích thước, service không tồn tại/ngừng hoạt động, branch ngoài vùng phục vụ, thiếu pickup rule | `QuoteServiceTests`, `ShipmentValidationTests`, `BranchNetworkServiceTests`, pricing flow tests |
| Ownership | `CustomerShipmentJourneyTests`: hai User tạo hai shipment độc lập; detail/search/filter/paging/service kiểm tra owner; literal `%`, `_`, `[`, backslash không thành wildcard | `UserShipmentServiceTests`, `UserShipmentServiceSecurityTests`, customer MVC/JDBC tests |
| Guest tracking | Mọi ShipmentStatus hiện có; trim/case hợp lệ; mã sai định dạng/ký tự/độ dài; DTO không chứa PII, tiền, nội dung riêng; không ghi event/notification | `PublicTrackingControllerTests`, `PublicTrackingServiceTests`, `TrackingRateLimiterTests` |
| Guest quote | POST ẩn danh/CSRF/MVC/JDBC thật; User và Guest cùng giá database; không lưu shipment, quote riêng, event hoặc notification; lỗi thiếu giá không lộ SQL/stack trace | `PricingFlowTests`, `PricingManagementTests`, `QuoteServiceTests` |
| Phiên bản giá | PUBLISHED, DRAFT, future, expired, retired, inactive, missing; snapshot không đổi sau sửa giá; quote đúng owner/hạn; branch ngừng phục vụ sau quote bị từ chối | `PricingTransactionTests`, `PricingServiceSecurityTests`, `QuoteServiceSecurityTests` |
| Transaction | CHECK constraint H2 được thêm tạm để buộc INSERT event thất bại; rollback shipment/quote consumption, rollback Manager transition; retry cùng quote thành công | Ticket/audit/notification/last-mile concurrency và transaction tests |
| Idempotency | Hai thread xác nhận cùng quote đồng thời, một shipment/một creation event; confirm/reopen/message replay | `CustomerSupportConcurrencyTests`, `CustomerSupportJdbcTests`, quote tests |
| Support journey | `CustomerSupportJourneyTests`: shipment thật → ticket route DB tới chi nhánh đích → dashboard → Manager assign/process/note/reply/resolve/close → customer reopen request → Manager reopen | `CustomerSupportMvcTests`, `CustomerSupportJdbcTests`, Operations tests |
| PUBLIC/INTERNAL | Read DTO chỉ chứa PUBLIC/resolution; notification không có nội dung điều tra/closing note; broker chỉ phát map REFRESH; khách khác không được đọc/đánh dấu | Customer MVC XSS/forged actor/kind/ID tests; không tồn tại public ticket REST API |
| Throttle | `CustomerThrottleIntegrationTests`: quota trong TicketService thật, rollback khi 429, replay vẫn hợp lệ, User B độc lập, Manager không bị quota khách, Clock reset | 4 `CustomerSupportThrottleTests`: quota/Retry-After, Clock, giới hạn registry/config |
| Notification persistence | Worker thật, JDBC thật; lỗi DB → retry theo Clock → persist/dedup; offline đọc lại API/service; không notification đệ quy | `ShipmentNotificationTests`, `NotificationTransactionTests`, customer JDBC/concurrency tests |
| Broker lỗi | Delivery lỗi trên in-process channel không rollback ticket/reply/notification đã commit; retry local chỉ phát REFRESH, không phát lại sau deliveredAt | Notification retry tests và JavaScript reconnect checks |
| Network WebSocket | Không viết server thứ hai; chạy lại nguyên 17 ca embedded Tomcat/STOMP | `NotificationWebSocketTests`: anonymous/origin/CONNECT CSRF, queue người khác, SEND giả, logout/session expiry/revoke/reconnect/ticket link |
| Read-only | Guest tracking/quote không tạo business state; báo cáo/dashboard không phát thông báo giả theo regression | `AdminReportingJdbcTests`, `AdminReportingMvcTests`, Operations read-only tests |

## Fixture và các luồng đã kiểm chứng

`CustomerJourneyConfig` import các service/repository thật và `OperationsJdbcConfig` dùng random H2 memory
ở MSSQLServer mode, Hikari pool, Hibernate create-drop. Không Flyway hay SQL Server trong context này.
`CustomerJourneySupport` tạo lại dữ liệu cho từng test: USER_A/USER_B độc lập, Manager nguồn/đích/ngoài phạm vi,
Admin, Shipper, hai local branch + hub, vùng phục vụ cho hai phương thức, phiên bản PUBLISHED,
rule nội tỉnh/liên tỉnh và phụ phí HOME_PICKUP. Giá fixture chỉ tồn tại trong test, không phải giá mặc định production.
Clock UTC được điều khiển; không dùng Thread.sleep mới. Login test tạo chuỗi ngẫu nhiên trong bộ nhớ rồi BCrypt,
không lấy credentials từ cấu hình máy và không ghi chúng ra log.

1. **Báo giá → shipment → xác nhận**: quote/confirm service và controller thật; sender/actor từ authentication,
   trạng thái CREATED do server đặt, currentBranch null trước khi thực nhận; chống client sửa tiền/trạng thái.
2. **Giao thành công tại cùng chi nhánh**: User tạo đơn, Manager confirm, nhận dropoff, phân loại tại chỗ qua
   TransportTripService, phân công/chuẩn bị, Shipper accept/start/complete, User đọc DELIVERED và notice.
   Không sửa status trực tiếp, không tạo chuyến hoặc biên nhận giả. Từ chối completion thiếu xác nhận trước khi
   ghi proof/attempt; completion hợp lệ có proof, snapshot phí giữ nguyên.
3. **Support**: route source → destination được cấu hình rõ trong DB test, không tự fallback source.
   Manager nguồn nhận 404, Manager đích thấy ticket trong dashboard và xử lý; customer thấy PUBLIC reply/
   resolution. Reopen-request có actor thật, idempotent, giữ CLOSED cho đến Manager thực sự reopen.
4. **Outbox**: gọi `poll()` của worker hiện có thủ công, không bật scheduler. Lỗi constraint notification
   không ACK event hoặc để lại notice một phần; thời gian retry tăng bằng Clock rồi xử lý thành công/dedup.
5. **Broker outage**: ngắt bằng MessageHandler lỗi có kiểm soát, rồi tháo handler trong finally. Dữ liệu ticket
   và notice vẫn đọc được; delivery thử lại qua channel thật trong tiến trình.

Các fixture tracking đủ mọi trạng thái/legacy RETURNED là **read-model fixture**, có gán status trong setup;
không đại diện cho việc đã chạy toàn bộ tuyến physical inter-hub, failed delivery, redelivery và return của An.
Luồng service thành công ở mục 2 chỉ dùng transition nghiệp vụ thật cho tuyến cùng chi nhánh.

## Chính sách và những trường không tồn tại

- UserStatus hiện chỉ có ACTIVE, LOCKED, UNVERIFIED; “INACTIVE” trong yêu cầu được kiểm tra bằng hai trạng thái
  không ACTIVE này, không bổ sung enum mới. Dịch vụ có cờ active, **giá** mới có DRAFT/PUBLISHED.
- Không có ROLE_VENDOR; không có role hierarchy tự cho ADMIN/SHIPPER quyền USER.
- HTML protected route chưa login trả 401 theo SecurityErrorHandlers hiện tại, không ép thành redirect.
  Login thành công redirect `/account`, thất bại HTML `/login?error`; JSON lỗi là problem 401/403.
- Chưa có endpoint sửa/hủy vận đơn cho User, nên không tạo mới để “đủ test”; SecurityConfig deny các route
  ngoài allowlist và lifecycle write method vẫn chỉ MANAGER/SHIPPER. Không có REST detail shipment trả full entity.
- Guest tracking chỉ tìm bằng tracking number `UTE` + 32 hex. Allowlist event hiện chỉ có tuple tạo đơn đã duyệt;
  trạng thái tổng quan cập nhật cho mọi enum hiện hành. Không mở mặc định assignment/discrepancy/proof/ticket events.
- Guest estimate không thu contacts/COD/declared value, không giữ quote riêng và không ghi nhận khoản đã thu.
  User quote và Guest dùng cùng QuoteService; nội tỉnh/liên tỉnh tính theo tên tỉnh chuẩn, chưa dùng INTER_REGION.
- Insurance chưa có sản phẩm/rule được bán trong flow này; insuranceFee = 0 theo contract hiện hữu.
- V12 có UNIQUE một route/origin, không có priority routing nhiều rule để kiểm thử. Thiếu/inactive/unrelated
  route bị từ chối trong regression; không seed cấu hình tự động và không đổi routing architecture.
- Ticket state machine giữ nguyên; REOPEN_REQUESTED là event/yêu cầu, REOPENED là trạng thái do Manager chuyển.
  Customer không được sửa status, assignedManager, priority hoặc kind INTERNAL.
- Throttle **chỉ bảo vệ trong từng instance**, không Redis và không cam kết chống abuse phân tán.

## Mức tích hợp và giới hạn bằng chứng

| Loại kiểm thử | Persistence | HTTP/broker | Có thể kết luận |
|---|---|---|---|
| Unit/policy cũ | Mock hoặc object đơn | Không network | Validation/policy, không chứng minh JDBC |
| MockMvc cũ dùng TestApplication | Repository mock | Security/MVC/Thymeleaf thật | Contract/security ở biên HTTP, không phải persistence thật |
| MVC mới Commit 19 | H2/JPA/repository/service/BCrypt thật | Mock servlet request/session | Tích hợp MVC + persistence; chưa phải trình duyệt/network E2E |
| Service journeys mới | H2/JPA/transaction/locks thật | Broker in-process; workers poll thật thủ công | Luồng liên module, rollback, quota, dedup thực trong H2 |
| 17 WebSocket tests hiện hữu | User/Role/Notification repository mock | Embedded Tomcat + WebSocket/STOMP network thật | Transport/origin/CSRF/session/queue isolation; chưa chứng minh JDBC qua network |
| JS checks | API/socket/DOM simulation | Node vm | Reconcile/reconnect/safe text/loading behavior; chưa phải graphical browser |
| SQL Server | Chưa chạy | Chưa chạy | Chưa xác nhận Flyway V1–V13, validate, index/query/locks trên SQL Server |

Không gọi tổ hợp hai nhóm H2 và WebSocket mock là full end-to-end. Không có test SQL Server được cấp quyền
trong môi trường này; không dùng UTEExpressDB phát triển/production. Test opt-in cũ dùng integrated authentication
trong khi môi trường thực dùng SQL Authentication, nên không bật hoặc sửa connection settings.

## Phát hiện, sửa lỗi và vấn đề bàn giao

Chưa phát hiện bug production được chứng minh trong phạm vi các ca mới đã chạy. Kiểm tra nghi vấn khác biệt
whitespace giữa Guest/User cho thấy setter ShipmentCreateForm đã strip tên tỉnh, test so sánh giá đạt;
không sửa QuoteService khi chưa có lỗi. Các lần chạy test ban đầu có lỗi fixture (thiếu updatedBy của
PricingVersion, retirement không đúng constraint); đã sửa fixture theo schema, không nới assertion/constraint production.

Không có bug fix production, không thay API/DTO/enum chung và không sửa business policy của An/Thuận.
Không có failing test bị xóa hoặc bỏ qua để đạt build. Test validate opt-in skipped là có sẵn từ baseline.

Các vấn đề chưa xác minh và bàn giao Commit 20–21:

- An/Commit 20: chạy tuyến multi-leg, discrepancy/failed attempt/redelivery/return đầy đủ qua nghiệp vụ thật,
  đối chiếu proof/custody với User visibility; các regression cũ vẫn là bằng chứng theo mức mock/JDBC của từng lớp.
- Thuận/nhóm/Commit 21: cấp database SQL Server test riêng, chạy Flyway V1–V13 từ sạch và Hibernate validate,
  kiểm tra filtered unique/index/lock/UTC/collation và concurrency bằng SQL Server. Không sửa migration đã merge;
  nếu có lỗi, thống nhất migration sửa tiếp theo trước khi triển khai.
- Kiểm tra trình duyệt thật responsive/accessibility, nhiều tab/reconnect và quy mô dữ liệu lớn. Chưa benchmark.
- Review quyền hiển thị lịch sử User: DTO chủ sở hữu hiện theo contract chứa title/description/location của
  ShipmentEvent; không đồng nhất với DTO/allowlist Guest. Chưa thay contract timeline hoặc tự phân loại lại
  ghi chú logistics. Nhóm cần duyệt chính sách nếu muốn lọc thêm nội dung vận hành đối với chính chủ vận đơn.
- Guest timeline bảo thủ chỉ có sự kiện tạo đơn; cần duyệt allowlist mốc công khai mới trước khi mở rộng.
- Cấu hình routing/coverage/giá thật phải do vận hành cấp hợp lệ; không tạo dữ liệu giả để UI thành công.
- Production readiness: limiter theo instance, broker đơn node, deliveredAt là local-channel acceptance,
  không client ACK/exactly-once. H2 MSSQL mode không chứng minh mọi semantics SQL Server.

## Cách chạy và tái hiện

Tại repository, Java 21, dùng wrapper đúng tên `mvnw.cmd`:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests compile
git diff --check
.\mvnw.cmd '-Dtest=CustomerAccessIntegrationTests,CustomerPricingIntegrationTests,CustomerShipmentJourneyTests,CustomerSupportJourneyTests,CustomerThrottleIntegrationTests' test
node src/test/js/notifications.test.mjs
node src/test/js/operations.test.mjs
node src/test/js/reports.test.mjs
```

JS dùng Node built-in modules, không cần npm install. Trong phiên này Node CLI không có trên PATH;
ba file được thực thi qua Node runtime của công cụ, cùng module/assertion. Không tự thêm infrastructure.
Maven pom hiện không cấu hình Failsafe hoặc profile integration riêng: các lớp `*Tests` chạy trong Surefire
ở pha test, bao gồm H2 và embedded Tomcat. `verify` chạy lại chúng rồi package/verify; không ngầm bao gồm
SQL Server opt-in hoặc một integration suite ngoài Maven chưa được cấu hình.

Tái hiện rollback bằng các method `eventDatabaseFailureRollsBackShipmentAndConsumptionAndSameQuoteCanRetry`,
`managerEventFailureRollsBackStatusAndLeavesUserViewAndNotificationsConsistent`,
`ticketOutboxDatabaseFailureRetriesAtControlledClockWithoutDuplicatesOrLostEvents`.
Mọi constraint tạm chỉ trên H2 và được gỡ trong finally; tuyệt đối không chạy SQL đó trên DB thật.

Kiểm thử thủ công trên môi trường test có dữ liệu được cấp phép:

1. Guest mở `/tracking`, thử mã hợp lệ/sai/không tồn tại; chỉ thấy summary/timeline công khai, không PII/giá.
2. Guest mở `/quote`, chọn vùng/branch/service hợp lệ và xem giá; thiếu giá phải báo lỗi, không tạo shipment.
3. Đăng nhập USER_A, chọn HOME_PICKUP hoặc BRANCH_DROPOFF, xem giá/hạn trước khi confirm; gửi hai lần cùng
   quote vẫn mở cùng vận đơn. USER_B không mở detail/quote/ticket/notice của A bằng thay ID/token.
4. Manager được cấp branch xác nhận/tiếp nhận theo đúng nghiệp vụ; USER_A xem trạng thái và giá snapshot.
5. USER_A tạo ticket từ shipment; Manager đúng route trả lời PUBLIC, thêm INTERNAL note, resolve/close.
   Customer không thấy INTERNAL; reopen-request chưa tự đổi CLOSED; Manager xem xét rồi reopen.
6. Mở notification ở hai tab, đóng/kết nối lại socket, kiểm tra badge/API reconciliation; thử logout/khóa
   tài khoản phải mất quyền phù hợp. Không chỉnh request để vô hiệu CSRF hoặc mở subscription trái phép.

Các bước trình duyệt trên là **hướng dẫn**, chưa được thao tác UI bằng trình duyệt thật trong phiên này.

## File bàn giao

8 file mới, 0 file tracked sửa, 0 file xóa. Toàn bộ 8 file mới cũng là danh sách untracked,
được xác nhận bằng `git ls-files --others --exclude-standard`; index trống.

```text
docs/user-guest-integration-testing.md
src/test/java/com/ute/uteexpress/operations/CustomerAccessIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/CustomerJourneyConfig.java
src/test/java/com/ute/uteexpress/operations/CustomerJourneySupport.java
src/test/java/com/ute/uteexpress/operations/CustomerPricingIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/CustomerShipmentJourneyTests.java
src/test/java/com/ute/uteexpress/operations/CustomerSupportJourneyTests.java
src/test/java/com/ute/uteexpress/operations/CustomerThrottleIntegrationTests.java
```
