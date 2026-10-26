# Commit 20 — Manager, Shipper & Logistics Integration Testing

Ngày thực hiện: 26/10/2026, UTC+7. Phụ trách An.
Nhánh `feature/an-logistics-integration-tests` được tạo từ `main` sạch tại `a72c423`,
đã merge Commit 19 (`f12b55d`) và các Commit 01–18. Không stage/commit/push/merge/stash hoặc tạo PR.

## Baseline và phạm vi

Baseline thực chạy trước khi sửa: `.\mvnw.cmd clean test` → **1.222 test, 1.221 passed,
0 failure, 0 error, 1 skipped; BUILD SUCCESS; 1 phút 21 giây**. Không mất test cũ để khớp số tham chiếu.

Khảo sát các controller/service/repository/DTO, schema và regression liên quan đến Shipment,
ShipmentEvent/Assignment/Intake, PickupEvidence/OriginHandoff, TransportRoute/Trip/Item/Event,
TripReceipt/Discrepancy, DeliveryDispatch/Attempt/Proof/Recovery, RedeliveryDecision,
ReturnRequest/Receipt; LogisticsAccess/TransitionPolicy/TransportRouting, Manager/Shipper reads,
notification worker, Operations Dashboard và Admin Reports. Tái sử dụng kiến trúc hiện hữu.

Không sửa V1–V13, dependency, application.properties, Auth/SecurityConfig, DTO hoặc enum chung.
Không kết nối DB phát triển/production hoặc đọc credentials. Schema test là Hibernate create-drop trên H2,
được đối chiếu bởi CoreDatabaseMappingTests hiện hữu; không gọi đó là chạy Flyway T-SQL thành công.

## Bất biến nghiệp vụ được giữ

### Manager, assignment, pickup và intake

- Manager cần session ROLE_MANAGER, tài khoản ACTIVE và role DB còn hiệu lực, ManagerBranch đúng điểm ACTIVE.
  ADMIN/USER/SHIPPER không tự kế thừa quyền. Không dùng branch parameter/client actor để quyết định scope.
- Generic lifecycle chỉ cho CREATED → CONFIRMED và CREATED/CONFIRMED → CANCELLED có lý do, không active task.
  Không dùng generic transition để bỏ qua chứng từ pickup/intake/trip/delivery/return.
- Planning/confirm có quyền ManagerBranch; thao tác vật lý còn cần Employee ACTIVE đúng home branch,
  DISPATCHER/WAREHOUSE_STAFF. Không tự bổ sung điều kiện Employee cho mọi thao tác planning khi contract chưa yêu cầu.
- Pickup chỉ HOME_PICKUP đã CONFIRMED. Courier phải là User ACTIVE/DB ROLE_SHIPPER, Employee ACTIVE,
  COURIER_DRIVER đúng chi nhánh nguồn. Reassignment chỉ trước khi bắt đầu, đóng task cũ và giữ lịch sử.
- Assignment không phải pickup; SUCCESS cần physicallyCollected + reference, FAILED cần lý do và không đánh dấu đã lấy.
  Chưa có PickupAttempt entity riêng: PickupEvidence lưu các lần SUCCESS/FAILED theo policy hiện tại.
- Pickup SUCCESS chuyển PICKED_UP, currentBranch vẫn null. Shipper yêu cầu handoff PENDING; Manager/Employee khác
  người gửi bàn giao kiểm tra và thực nhận mới tạo ShipmentIntake, hoàn tất assignment và đặt custody ở nguồn.
  REJECTED không tạo receipt, không chuyển custody; có thể yêu cầu bàn giao lại với lịch sử riêng.
- BRANCH_DROPOFF nhận trực tiếp sau confirm bằng ShipmentIntake có xác nhận vật lý/reference, không pickup assignment.
  Không tiếp nhận trùng, tiếp nhận đơn hủy hoặc hủy tùy tiện sau intake.

### Transport và custody

Trip: **DRAFT → SEALED → DEPARTED → RECONCILING → CLOSED**. Không có trạng thái/nút cancel trip hiện hành.
Manifest tối đa 500 kiện, thêm theo batch tối đa 50; kiện phải có intake/custody đúng điểm, không assignment/chuyến
xung đột, route ACTIVE, hub snapshot khớp mạng lưới. Sau seal không sửa manifest. Depart không phải parcel receipt.

Arrival chỉ ghi nhận xe/chuyến đến và mở đối soát. Mỗi TripReceipt phải đúng item, điểm nhận và nhân viên;
chỉ receipt mới chuyển currentBranch, status ở điểm đến và nhả reservation. Manager điểm đi không nhận thay điểm đến;
operator/Employee xuất và nhận phải độc lập. Không tự nhận đủ manifest khi chỉ có trip DEPARTED/CLOSED.

Forward: Branch A → Hub A → Hub B → Branch B. Một Shipment tồn tại xuyên suốt ba chặng, không nhân bản đơn.
MISSING/DAMAGED/REJECTED giữ kiện IN_TRANSIT, custody null và reservation chưa nhả cho đến khi kiểm nhận thực tế.
UNEXPECTED không gắn item; có thao tác resolve riêng. Không close khi còn kiện thiếu receipt hoặc discrepancy OPEN.

### Giao cuối, giao lại và hoàn

Manager đích chỉ phân công sau receipt hợp lệ tại Branch B. PREPARED handoff vẫn do branch giữ; Shipper accept
mới chuyển custody ra khỏi branch, start mới OUT_FOR_DELIVERY. Complete ghi SUCCESS attempt + DeliveryProof,
đóng assignment/dispatch và DELIVERED cùng transaction. Gửi thiếu xác nhận/proof hoặc gửi lặp không tạo thành công giả.

FAILED có code/reason và attempt number tăng đúng, vẫn có Shipper chịu trách nhiệm giữ kiện. Redelivery cùng courier
có decision riêng; đổi assignment cần Shipper yêu cầu recovery và branch độc lập thực nhận. Lịch sử attempts không mất.

ReturnRequest không phải RETURNED. Khi còn trên tay Shipper: REQUESTED/RECALL_REQUIRED, chỉ thực nhận recovery mới
READY_FOR_TRANSPORT. Chiều hoàn phải có ba route ACTIVE cấu hình riêng: Branch B → Hub B → Hub A → Branch A.
Reverse manifest giữ ReturnRequest, depart đổi stage IN_TRANSIT, receipt từng chặng mới cho đi tiếp; Shipment vẫn RETURNING.
Về nguồn là AT_ORIGIN, returnedAt chưa có. Chỉ final sender receipt hợp lệ từ Manager/Employee nguồn mới COMPLETED/RETURNED,
returnedAt được ghi và currentBranch null. Không có luồng hoàn thông thường sau DELIVERED hoặc giao tiếp sau RETURNED.

## Fixture và hạ tầng

`LogisticsJourneyConfig` import CustomerJourneyConfig (Commit 19), OperationsJdbcConfig (Commit 16),
AdminReportingJdbcConfig (Commit 18) và các logistics read service thật. Không repository hoặc business-service mock.
H2 memory random ở MSSQLServer mode + Hikari/JPA/transaction manager; tất cả 41 entity được tạo/kiểm tra bởi Hibernate.
Worker poll được gọi thủ công, không scheduler chạy nền. Broker là channel trong tiến trình, không socket network mới.

`LogisticsJourneySupport` tái sử dụng fixture User/price/coverage và reset database cho từng test, mở rộng:

- USER_SENDER/OTHER, Manager nguồn/đích/ngoài phạm vi, hai Manager hub độc lập, Admin;
  courier pickup/thay thế/delivery/ngoài phạm vi và tài khoản/Employee không hoạt động.
- Branch A/B/C, Hub A/B, liên kết parent hub và sáu TransportRoute đi/hoàn. ID lấy từ persistence thật, không ID hardcode.
- Báo giá PUBLISHED và pickup fee của fixture Commit 19. Giá chỉ là synthetic test data trong H2, không seed production.
- Clock UTC cố định. Không thêm Thread.sleep. Latch/executor có timeout, clear SecurityContext mỗi thread và đóng executor.

Shipment được tạo bằng QuoteService.quote/confirm; mọi status, evidence, receipt, dispatch, return thành công tiếp theo
đều qua business service. Không update ShipmentStatus trực tiếp để giả lập journey. Mỗi helper assert status/custody ở
ranh giới depart/arrival/receipt/handoff, giúp xác định chặng lỗi thay vì chỉ kiểm tra trạng thái cuối.

## Ma trận journey ưu tiên

Mọi dòng bên dưới chạy trên H2/JPA thật. “Hoàn chỉnh” ở đây là **service-level**, không browser/network E2E.

| Journey / tiền điều kiện | Action/service thật | Kết quả, chứng từ, custody, history và notification | Scope / lớp test | Kết quả |
|---|---|---|---|---|
| 1 HOME_PICKUP, giá/vùng phục vụ hợp lệ | QuoteService → ShipmentLifecycleService confirm/assign/collect/request/review | PICKUP_ASSIGNED không phải đã lấy; evidence SUCCESS; intake chỉ sau handoff ACCEPTED; nguồn giữ kiện; actor thật; worker xử lý event theo allowlist | User, Manager nguồn, pickup courier, receiver độc lập; FullJourney + Guard + MVC | Đạt |
| 2 BRANCH_DROPOFF, chưa nhận vật lý | quote/confirm → receiveDropoff | Một intake, AT_ORIGIN_BRANCH, custody nguồn; không pickup task; phí/owner giữ nguyên | Manager/Employee nguồn; FullJourney + Guard | Đạt |
| 3 Forward ba chặng, có intake | TransportTripService create/addItems/seal/depart/arrive/receive/close | Ba trip CLOSED + ba receipt đúng item; arrival không đổi custody; cuối AT_DESTINATION_BRANCH, một Shipment | Manager độc lập ở bốn điểm; FullJourney + candidate regression | Đạt |
| 4 Last-mile sau receipt đích | LastMileService assign/prepare/accept/start/complete | Một SUCCESS attempt/proof, assignment COMPLETED, DELIVERED, branch custody null; User nhận notice, không gửi User khác | Manager đích + đúng delivery courier; FullJourney | Đạt |
| 5 Failure → redelivery | fail → recovery/request/review → redelivery/new assignment → prepare/accept/start/complete; thêm same-courier scenario | FAILED #1 giữ nguyên, SUCCESS #2, một proof; branch receipt recovery trước task mới; Dashboard/Report phân biệt attempts và shipments | Đích/shipper; FullJourney | Đạt |
| 6 Failure → reverse ba chặng → sender | initiateReturn → recovery → TransportTripService ba chặng đảo → returnToSender | REQUESTED không phải RETURNED; đủ sáu receipt đi/hoàn, AT_ORIGIN chưa returnedAt; final receipt mới RETURNED/COMPLETED; snapshot không đổi | Manager đích/hub/nguồn + courier; FullJourney | Đạt |
| 7 Manifest 3 chỉ nhận 2 | receive hai kiện → MISSING → close bị chặn → physical receive có resolve → close | Kiện thứ ba vẫn IN_TRANSIT/custody null; issue OPEN hiển thị Dashboard/Report; receipt thật mới resolve/nhả giữ chỗ; Guest không thấy điều tra | Manager điểm nhận; FullJourney + Guard | Đạt |
| 8 Cross-branch/role/ownership | Manager ngoài scope, courier sai task, forged ID/branch/actor/fields | 401/403/404 theo contract; không event/evidence/assignment trái quyền; enum invalid/CSRF/XSS/filter an toàn | Guard + MVC | Đạt |
| 9 Concurrent physical receipt | Hai transaction receive cùng trip/item/version | Một thành công; một receipt/event hợp lệ; custody chỉ một điểm; không notification trước worker | Concurrency | Đạt |
| 10 Concurrent delivery/return | complete SUCCESS đối đầu initiateReturn cùng shipment/version | Một winner; DELIVERED có proof và không ReturnRequest, hoặc RETURNING không proof; không RETURNED giả | Concurrency | Đạt |

Hai method parameterized HOME_PICKUP/BRANCH_DROPOFF nối journey 1/2 + 3 + 4 qua toàn bộ service logistics thật.
Journey hoàn nối forward + failure/recovery + reverse + final sender receipt, không chèn trạng thái kết quả trong DB.

## Authorization, transaction và concurrency

Guard tests kiểm tra role DB bị thu hồi, User LOCKED, ManagerBranch bị thu hồi, courier sai branch/position,
Employee INACTIVE, reassign/history, evidence thiếu dữ liệu, handoff bị từ chối, cancel quá muộn, skip hub,
depart trước seal, sửa manifest sau seal, delivery trước destination receipt, terminal state và ba loại discrepancy item.
UserStatus không có INACTIVE: dùng LOCKED/UNVERIFIED theo enum hiện hữu; không thêm enum mới.

MVC dùng filter, method security, controller, DTO binding và Thymeleaf thật trên H2: anonymous/role isolation,
CSRF thiếu/sai, confirm replay flash error, branch tampering, mass assignment, pickup/handoff actors,
enum invalid, task ownership, XSS escaping, filter SQL-like input, invalid ID, role revoke và GET không ghi dữ liệu.
Login/session và WebSocket dùng regression sẵn có, không viết Auth thứ hai.

**22 race scenarios mới**, mỗi bên có SecurityContext riêng và transaction service thật, đồng bộ start bằng latch,
timeout hữu hạn. Không bắt mọi RuntimeException để coi là “loser”; chỉ chấp nhận conflict/domain/permission/404 đã xác định.
Đọc lại status/version/custody, chứng từ, reservation và event sau race; chấp nhận một trong hai winner hợp lệ.

| Nhóm | Các cặp đối đầu được chạy trực tiếp |
|---|---|
| Nguồn (7) | confirm/confirm, confirm/cancel, assign/assign, reassign/collect, collect/collect, collect/cancel, intake/intake |
| Trip (7) | hai active manifest tranh một kiện, seal/add manifest, depart/depart, receive/receive, receive/discrepancy, close/close, resolveUnexpected/close |
| Delivery/return (8) | accept/accept, success/success, success/failed, success/return, redelivery/redelivery, return/return, final sender receipt/final receipt, final receipt/delivery assign |

Không tuyên bố đã phủ mọi tổ hợp của concurrency matrix yêu cầu: chưa có race riêng reverse-manifest/misdispatch,
hai courier khác identity cùng accept, hai resolve discrepancy, hoặc hai điểm custody khác nhau cùng dispatch.
Các guard/forward/reverse/race reservation liên quan đã kiểm tra từng bất biến, nhưng không thay thế các tổ hợp race đó.
Depart/cancel-trip không tồn tại trong state machine nên không tự thêm thao tác cancel.
Collect/branch intake phải đi qua handoff ở các giai đoạn khác nhau; test không dựng trạng thái bất khả thi để ép race.

**11 fault-injection scenarios mới** thêm CHECK tạm trên H2 trong try/finally, rồi so sánh snapshot DB trước/sau:
pickup proof, HOME intake sau update handoff, manifest, departure event sau cập nhật cả manifest,
physical receipt, discrepancy sau update item, delivery proof sau tạo attempt, delivery event sau tạo proof,
return request, final sender receipt và RETURNED event sau ghi receipt.
Rollback phải khôi phục status/version/custody, attempts/assignments/handoffs/return stage/trip version,
mọi chứng từ/history và notification. Gỡ constraint rồi retry cùng command thành công; không nới constraint production.

## Hai bug production được tái hiện và sửa

### LOG-20-01 — Authorization probe làm transaction rollback-only

- Tái hiện: Manager Hub A chỉ có scope Hub A nhận chuyến từ Branch A bằng `TransportTripService.arrive`.
- Expected: quyền điểm nhận hợp lệ, ghi ARRIVED/RECONCILING và commit; chưa có parcel receipt/custody.
- Actual trước sửa: `UnexpectedRollbackException`; cả sáu ca journey ban đầu lỗi khi arrive.
- Root cause: `locked()` thử quyền origin rồi bắt AccessDeniedException và thử destination. Authorization service
  read-only đang tham gia transaction bên ngoài; Spring đánh dấu rollback-only ở lần denial đầu tiên dù caller bắt exception.
  Các read service probe capability ở hai đầu cũng có cùng rủi ro.
- Sửa tối thiểu: `LogisticsAccessService` read-only thêm `noRollbackFor = AccessDeniedException.class`.
  Không đổi role/scope/Employee checks, không bỏ lock/version, không nuốt denial hoặc đổi SecurityConfig.
- Bằng chứng: journey arrival/receipt thực, trip detail cả hai đầu, source Manager xem return detail, planning read
  với inactive physical employee đều chạy. Negative scope tests vẫn chặn. Test riêng ghi/flush trong outer transaction
  rồi để denial thoát ra chứng minh **outer write transaction vẫn rollback**, không để lại partial write.

### LOG-20-02 — Candidate query không bind được enum TransportLeg trong Hibernate 7

- Tái hiện: Manager mở trip detail DRAFT; `findCandidates` dùng các so sánh `:leg = enum literal` độc lập.
- Expected: hiển thị kiện đủ điều kiện/empty state với đúng leg, không HTTP 500.
- Actual: `Could not determine ValueMapping for SqmParameter: SqmNamedParameter(leg)` trên Hibernate 7 thực.
- Root cause: tham số enum không liên kết cột đã có JDBC mapping; mock repository/offline HQL parsing chưa bắt lỗi runtime này.
- Sửa tối thiểu: thêm EXISTS tới route cấu hình theo origin/destination và `route.leg = :leg` vào hai predicate
  forward/return trong TransportTripItemRepository. Cột TransportRoute.leg cung cấp mapping VARCHAR.
  Trip vốn bắt buộc có route FK với cặp điểm duy nhất; service chỉ dùng query theo route của trip.
  Không đổi chữ ký repository/API hoặc schema. Query trực tiếp không có route cấu hình giờ trả rỗng, phù hợp phạm vi lập chuyến.
- Bằng chứng: query forward/return candidate chạy và chọn đúng Shipment ở **cả sáu chặng**, sau đó vận chuyển/receipt thật;
  trang trip detail MVC cả origin và destination render thành công.

Không sửa business rule của Auth/Admin/Pricing/User/Support. Chưa có lỗi schema SQL Server nào được chứng minh;
không tạo V14. Lỗi fixture ban đầu: thử xem delivery detail ở nguồn trước khi có ReturnRequest bị 404 đúng contract;
đã sửa precondition thành return hợp lệ, giữ nguyên assertion quyền và production policy.

## Dashboard, Reports và Notifications

Query/service thật cùng dữ liệu logistics từ journey, không mock nguồn số liệu:

- Ba forward trips là ba lượt vận chuyển nhưng một unique shipment; reverse thành sáu lượt vẫn một unique shipment.
- DELIVERED chỉ được đếm sau proof; failed attempt count và failed shipment count được kiểm tra riêng.
- ReturnRequest/AT_ORIGIN không được đếm thành completed return; ReturnReceipt cuối mới vào RETURNED report.
- Missing parcel không có receipt/custody điểm nhận; discrepancy OPEN hiển thị và biến mất khỏi open queue sau receipt resolve.
- Không tính fee snapshot thành doanh thu hoặc thay đổi giá khi confirm/intake/transport/delivery/return.
- Worker hiện hữu xử lý các event đã commit, fan-out đúng role/branch/owner theo allowlist; retry không tăng số notice;
  USER_OTHER không nhận được notice. Không tự gửi notification cho mọi event.
- GET trip detail không tạo ShipmentEvent/TripEvent/notification. Read-only Dashboard/Reports và broker outage/retry/offline
  được giữ bởi regression Commit 15–19, gồm JDBC customer journeys và embedded WebSocket tests.

## Kết quả build và mức bằng chứng

Full `.\mvnw.cmd clean test` đã chạy: **1.309 test, 1.308 passed, 0 failure, 0 error,
1 skipped; BUILD SUCCESS; 2 phút 36 giây**. XML Surefire xác nhận 1.222 test cũ (1 skipped)
+ 87 test mới (0 skipped), không test cũ bị sửa/xóa. 17 WebSocket/STOMP tests tiếp tục đạt.
Các nhóm mới:

| Lớp | Số ca đạt | Mức tích hợp |
|---|---:|---|
| LogisticsFullJourneyTests | 6 | Service-level xuyên module, H2/JPA thật, Dashboard/Report/worker thật |
| LogisticsGuardIntegrationTests | 28 | Authorization/domain guard/JPA thật, hai bug regression |
| LogisticsConcurrencyIntegrationTests | 22 | Hai thread/transaction thật, H2 locks/version và DB assertions |
| LogisticsRollbackIntegrationTests | 11 | Constraint failure + transaction rollback + retry thật |
| LogisticsMvcIntegrationTests | 20 | MockMvc/filter/Thymeleaf + business services/repositories H2 thật |
| Tổng mới | 87 | Không logistics/repository mock |

JavaScript đã chạy: Notifications 6, Operations 3, Reports 3 — **12/12 checks đạt**.
`git diff --check` exit 0; kiểm tra UTF-8/trailing whitespace/newline riêng trên cả file mới chưa track cũng đạt.
| Lệnh cuối | Kết quả thực chạy |
|---|---|
| `.\mvnw.cmd verify` | BUILD SUCCESS; 1.309 test / 1.308 passed / 0 failure / 0 error / 1 skipped; 2 phút 27 giây; JAR package/repackage thành công |
| `.\mvnw.cmd -DskipTests compile` | BUILD SUCCESS; 4,069 giây |
| `git diff --check` | Exit 0; kiểm tra riêng UTF-8/newline/whitespace file mới và sửa cũng đạt |
| SQL Server / Flyway / Hibernate validate | Chưa chạy; test opt-in cũ là skipped duy nhất |

XML Surefire sau verify xác nhận đúng 87 test mới và đủ 1.222 test cũ; test cũ thay đổi/xóa: 0.
Một lần gọi công cụ Maven MVC gặp timeout ở bước phê duyệt trước khi process được khởi động;
retry được cho phép đã chạy thành công. Đây không phải test bị skip hoặc lỗi assertion bị bỏ qua.

Unit tests cũ vẫn có mock; các MVC Boot TestApplication trước đây có repository mock. 17 WebSocket/STOMP tests
dùng embedded Tomcat/native network thật nhưng User/Role/Notification persistence mock. Broker trong journey mới
là channel local, không chứng minh STOMP qua network cùng JDBC. Không gọi sự kết hợp đó là network end-to-end hoàn chỉnh.
JS checks dùng Node vm/DOM/socket simulation, không phải graphical browser.

## SQL Server, Flyway và giới hạn

Máy có docker/sqlcmd executable, nhưng repository không có Testcontainers SQL Server dependency/profile hoặc
connection test instance được cấp quyền để chạy sạch. Không coi việc có executable là được phép tạo/xóa database.
Không lấy credentials trong application.properties, không tự kết nối UTEExpressDB. Test validate opt-in cũ dùng
Windows integrated authentication, không phù hợp SQL Authentication đã được xác nhận; không bật hoặc đổi cấu hình.

**Chưa chạy SQL Server JDBC, fresh migration V1–V13, upgrade migration, Hibernate validate hoặc SQL Server concurrency.**
Chưa xác nhận checksum, filtered index/unique, isolation/deadlock/collation/precision hoặc execution plan trên SQL Server.
H2 CHECK/FK/unique/JPA tests không thay thế việc này. Các constraint/index được mapping/schema regression kiểm tra ở mức tĩnh,
không tuyên bố migration đã thực thi. Không chỉnh migration cũ để làm pass.

Warnings kế thừa cần phân biệt lỗi test: Flyway Community metadata GitHub HTTP 401 nhưng Maven dùng dependency đã có;
Mockito/ByteBuddy dynamic agent Java 21; explicit SQLServerDialect ở offline mapping test; unchecked operations ở test LastMile cũ.
Các H2 constraint error/STOMP denial/worker deferred warning do negative/fault-injection assertions là có chủ đích.

## Cách chạy và tái hiện lỗi

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests compile
git diff --check
.\mvnw.cmd '-Dtest=LogisticsFullJourneyTests,LogisticsGuardIntegrationTests,LogisticsConcurrencyIntegrationTests,LogisticsRollbackIntegrationTests,LogisticsMvcIntegrationTests' test
node src/test/js/notifications.test.mjs
node src/test/js/operations.test.mjs
node src/test/js/reports.test.mjs
```

Node CLI không trên PATH trong phiên này; cùng ba module/assertion được chạy bằng Node runtime công cụ.
Không npm install. Maven pom không có Failsafe/profile integration riêng: các `*Tests` này chạy trong Surefire,
`verify` chạy lại test rồi package/repackage. SQL Server opt-in không tự chạy trong verify.

LOG-20-01: chạy `LogisticsFullJourneyTests` trên source trước sửa để thấy arrive rollback-only;
LOG-20-02: chạy `managerReadsCanProbeOtherEndAndNonPhysicalCapabilitiesWithoutRollbackOnly` trước query fix,
hoặc regression candidate sáu chặng. Không revert/change Git tự động để tái hiện.
Lỗi production có failing test thật trước sửa; không xóa/disable test hoặc sửa expected assertion để che bug.

## Kiểm thử thủ công và bàn giao Commit 21–22

Hướng dẫn sau chưa được thao tác trên trình duyệt thật trong phiên này. Chỉ thực hiện với fixture được vận hành cấp phép
trong môi trường test, có route/coverage/giá/nhân sự hợp lệ; không seed dữ liệu production tùy tiện:

1. USER tạo HOME_PICKUP và BRANCH_DROPOFF; ghi tracking, quote và snapshot phí.
2. Manager nguồn confirm; HOME phân công, đúng Shipper ghi evidence/request handoff, Manager độc lập accept.
   Kiểm tra trước accept không có custody nguồn. Dropoff chỉ nhận khi thực sự mang kiện tới quầy.
3. Tại `/manager/trips`, tạo/lập manifest/seal/depart theo ba chặng. Mỗi điểm nhận dùng tài khoản riêng:
   arrive chưa nhận kiện, receipt từng item rồi close. Thử thiếu một kiện để thấy close bị chặn/discrepancy.
4. Manager đích vào `/manager/deliveries`, phân công/prepare. Shipper nhận/start/complete có proof hoặc fail có lý do.
   Kiểm tra `/shipper/deliveries`, quyền task, dashboard/proof/attempt và customer notification.
5. Với failure, thực hiện recovery trước khi đổi assignment hoặc cho same-courier redelivery đúng policy.
6. Với return, xác nhận recall/recovery, chạy ba tuyến hoàn có cấu hình; về nguồn vẫn RETURNING, final sender receipt
   mới RETURNED. Đối chiếu Admin Reports lượt trip với unique shipment, pending/completed returns.
7. Dùng tài khoản khác branch/role/shipper thử xem/ghi các ID tương ứng, thiếu CSRF, gửi lại request/stale version;
   không được thay custody/status/evidence trái quyền. Kiểm tra internal note chỉ ở phạm vi nhiệm vụ được phép.
8. Kiểm tra nhiều tab/offline/reconnect/logout/role revoke và UI mobile/accessibility bằng trình duyệt thật.

Bàn giao: cấp SQL Server test riêng để chạy fresh/upgrade migration và validate/concurrency; review hai sửa lỗi nhỏ
với nhóm trước merge. Chạy network + persistence JDBC cùng lúc nếu cần E2E, kiểm thử quy mô dữ liệu/benchmark/SQL plans,
và các race biến thể chưa có trực tiếp trong ma trận. Không cam kết exactly-once/client ACK hoặc distributed abuse protection;
broker/limiter theo instance và deliveredAt chỉ local-channel acceptance theo kiến trúc hiện hữu.

## File bàn giao

8 file mới, 2 file production sửa, 0 file xóa. Không sửa test cũ hoặc cấu hình/schema.
Generated log/JAR/XML không đưa vào Git. Log phiên ở TEMP với tiền tố `uteexpress-commit20-`,
XML kết quả mới nhất trong `target/surefire-reports/`.

Mới; cũng là toàn bộ danh sách `git ls-files --others --exclude-standard`:

```text
docs/logistics-integration-testing.md
src/test/java/com/ute/uteexpress/operations/LogisticsConcurrencyIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/LogisticsFullJourneyTests.java
src/test/java/com/ute/uteexpress/operations/LogisticsGuardIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/LogisticsJourneyConfig.java
src/test/java/com/ute/uteexpress/operations/LogisticsJourneySupport.java
src/test/java/com/ute/uteexpress/operations/LogisticsMvcIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/LogisticsRollbackIntegrationTests.java
```

Sửa:

```text
src/main/java/com/ute/uteexpress/repository/TransportTripItemRepository.java
src/main/java/com/ute/uteexpress/service/LogisticsAccessService.java
```

Index trống; không stage/commit/push/merge/stash hoặc tạo PR. Nhánh cuối vẫn là nhánh yêu cầu.
