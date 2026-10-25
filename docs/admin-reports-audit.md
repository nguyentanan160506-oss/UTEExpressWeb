# Commit 18 — Admin Reports, CSV Exports & System Audit

Phụ trách: Thuận. Nhánh: `feature/thuan-reports-audit`.

Đầu phiên working tree sạch trên main `3881d16` (Commit 16); Commit 17 đã được lưu tại `9ee9612` trên `feature/tan-customer-support`, chưa nằm trên main local. Nhánh Commit 18 được tạo trực tiếp từ Commit 17, không merge/cherry-pick/stash hoặc sửa lịch sử. Baseline chạy thực tế: **1031 tests, 1030 passed, 0 failures, 0 errors, 1 skipped**. Không cấu hình lại Git identity vì không có thao tác commit.

Không sửa V1–V12, dependency, DB/SMTP configuration hoặc dữ liệu SQL Server thật. Không thay Shipment/Trip/Delivery/Returns/Ticket transitions, không tính lại fee snapshot. Code audit mới chỉ bổ sung persistence vào transaction quản trị User hiện hữu. Các thay đổi Entity chỉ là annotation index.

## 1. Khảo sát và lựa chọn kiến trúc

- Admin trước đây có dashboard đếm User ACTIVE/LOCKED/UNVERIFIED, quản lý lock/unlock/roles và service/pricing catalog. Không có endpoint reporting/audit toàn hệ thống. Schema User **không có INACTIVE**; tài khoản bị khóa là LOCKED.
- AuditLog chung có từ V1, actor FK nullable, actorUsername, action, resourceType/resourceId, IP, old/new NVARCHAR(MAX), createdAt UTC. Trước Commit 18 chỉ AdminPricingService ghi: service create/edit/enable/disable; draft/rules/period; publish/retirement/legacy retirement. Không sao chép lại các audit đó.
- ShipmentEvent đã ghi các thay đổi/assignment/pickup/handoff/delivery/return. TransportTripEvent ghi create/manifest/seal/depart/arrive/receipt/discrepancy/close. SupportTicketEvent ghi intake/assignment/classification/state/resolution/customer reply/reopen request. Các bảng chứng từ chuyên biệt lưu delivery attempt/proof, receipt và nhân sự thực hiện.
- Đây là **business histories** với mức metadata khác nhau, không đồng nhất với security/administrative audit. Actor thiếu trong legacy không được suy từ người xử lý hiện tại. Không lấy role hiện tại làm role tại thời điểm xảy ra sự kiện.
- Chưa có service/API tạo/chỉnh ManagerBranch hoặc Admin tạo tài khoản trong source hiện hành; không dựng hook cho nghiệp vụ chưa tồn tại. Registration không được biến thành Admin-create. Không backfill audit giả.
- Có ShipmentPayment và fee snapshot trong schema, nhưng chưa có luồng writer/đối soát tài chính đầy đủ để khẳng định doanh thu, tiền mặt/COD đã thu hoặc lợi nhuận. Commit này **không triển khai financial report**, không đọc lại bảng giá để tính phí cũ.

Thành phần mới: AdminReportsController, AdminAuditController, error advice; ReportViews/AuditViews/ReportLabels; AdminReportQueries/AdminAuditQueries và OperationalReportRules; AdminReportingAccess, ReportPolicy, AdminReportService, AdminAuditService, ReportCsvService. Không có Entity/reporting table/audit store thứ hai. OperationsDashboardQueries chỉ chuyển predicate proof chung sang OperationalReportRules, không đổi công thức hoặc scope Manager.

## 2. Routes và quyền

| Method | Route | Chức năng |
|---|---|---|
| GET | `/admin/reports` | Báo cáo chọn nhóm, KPI, trend/breakdown, detail DB pagination |
| GET | `/admin/reports/export` | CSV DETAILS hoặc SUMMARY với cùng bộ lọc |
| GET | `/admin/audit` | Unified audit read model, filter + DB pagination |

Tái sử dụng SecurityConfig `/admin/**` và nav Admin. Không nới endpoint Guest/Manager/User/Shipper. Không tạo JSON reporting/audit API, audit export hoặc endpoint POST/edit/delete lịch sử. Admin không tự có quyền `/manager/operations` hay `/manager/tickets`; chi tiết dữ liệu cần thiết được cung cấp bằng projection read-only tại trang Admin.

AdminReportingAccess kiểm tra authority ROLE_ADMIN từ authenticated SecurityContext, email Principal → User thật, refresh account, cần ACTIVE và DB ROLE_ADMIN. Mỗi query chứa GATE EXISTS kiểm tra lại Admin ID/ACTIVE/role trong database; ID này do service lấy từ Principal, tách khỏi actorId dùng để lọc audit. Service kiểm tra lại quyền trước trả dashboard/CSV/audit, không chỉ ẩn UI. LOCKED/UNVERIFIED hoặc role bị thu hồi không giữ quyền qua session cũ.

Admin hiện có quyền reporting toàn hệ thống, chưa có permission chi tiết theo trường. Các projection chủ động bỏ PII dù Admin có thể quản lý tài khoản ở module khác. Branch/service parameters chỉ chọn dữ liệu trong quyền Admin đã xác thực, không cấp thêm quyền Manager. Branch hoặc service đã ngừng hoạt động vẫn có thể lọc nếu ID tồn tại để xem lịch sử.

## 3. Bộ lọc thời gian và pagination

Mọi ngày là **UTC**, không ngầm diễn giải theo giờ Việt Nam. Người dùng chọn hai ngày bao gồm hai đầu; chuyển thành `[from.atStartOfDay(), to.plusDays(1).atStartOfDay())`. Ví dụ 01–31 gồm đầy đủ ngày31. Không lấy `23:59:59` làm mốc kết thúc.

Preset TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH dùng Clock injected. CUSTOM cần đủ hai ngày. From <= to, không ngày tương lai, tối đa93 ngày gồm hai đầu; miền năm tránh tràn DATETIME2/plusDays. Status phải thuộc allowlist riêng của dataset. Branch/service IDs phải dương và tồn tại; không cho injection qua enum/status/source/entity type hoặc sort.

Page0..100000, size1..50 (mặc định20). Detail ORDER BY identity DESC, ổn định; audit ORDER BY occurred timestamp DESC, source ASC, source ID DESC. Không nhận SQL/sort fragment từ client; tham số sort không được dùng. Option directories Branch/ShippingService đọc projection50 dòng/trang. Không findAll rồi aggregate entity trong Java.

Các **snapshot hiện tại** cố ý không cắt theo ngày tạo/ghi nhận để không bỏ công việc cũ. Ngày được validation và hiển thị rõ nhưng không dùng để loại pending rows ở các dataset snapshot. Query có timeout10s, read service transaction timeout30s; count/group tại DB, detail có limit. Đây không phải lời hứa workload nhỏ với mọi database; cần execution plan/benchmark thực tế.

## 4. Báo cáo và cơ sở đếm

| Dataset | Mốc / loại dữ liệu | Chi nhánh khi lọc | Đơn vị |
|---|---|---|---|
| SHIPMENTS | Shipment.createdAt trong kỳ, trạng thái hiện tại của cohort | origin | vận đơn |
| DELIVERIES | attemptTime trong kỳ, chỉ attempt hợp lệ | destination | lượt giao + số shipment DISTINCT riêng |
| DELIVERED | DeliveryProof.deliveredAt trong kỳ, proof hợp lệ, Shipment DELIVERED | destination | vận đơn có proof |
| RETURN_REQUESTS | ReturnRequest.requestedAt trong kỳ | destination xử lý | yêu cầu, shipment DISTINCT riêng |
| RETURN_STARTED | ShipmentEvent thực vào RETURNING từ trạng thái khác, occurredAt trong kỳ, có fromStatus | event branch | sự kiện và shipment DISTINCT riêng |
| RETURNED | ReturnReceipt.receivedAt trong kỳ, COMPLETED + RETURNED + receipt tại nguồn | receipt/source | shipment thực trả |
| PENDING_RETURNS | stage chưa COMPLETED, snapshot hiện tại | destination | yêu cầu và shipment DISTINCT riêng |
| TRIPS | TransportTrip.createdAt trong kỳ, trạng thái hiện tại của cohort | origin | chuyến |
| TRIP_RECEIPTS | receipt.receivedAt trong kỳ, branch=trip.destination, thời gian >= departedAt thật | receiving branch | lượt nhận kiện/chặng và shipment DISTINCT riêng |
| DISCREPANCIES | recordedAt trong kỳ, trạng thái xử lý hiện tại | trip.destination | sai lệch |
| OPEN_DISCREPANCIES | OPEN snapshot hiện tại, gồm sai lệch cũ | trip.destination | sai lệch |
| TICKETS | createdAt trong kỳ, trạng thái/source/category/priority hiện tại của cohort | handlingBranch | ticket |
| TICKET_EVENTS | occurredAt trong kỳ, allowlist RESOLVED/CLOSED/REOPENED/REOPEN_REQUESTED | event.branch | sự kiện xử lý ticket |
| OPEN_TICKETS | status chưa RESOLVED/CLOSED, snapshot hiện tại | handlingBranch | ticket |
| BRANCHES | Các cột có cơ sở riêng ở phần dưới | đúng Branch được chọn | branch và từng metric riêng |

Legacy ticket chưa có handlingBranch vẫn nằm trong báo cáo Admin toàn hệ thống với nhãn chưa xác định; branch filter cụ thể loại các bản ghi không xác định scope. Không backfill hoặc cấp thêm customer visibility. TICKET_EVENTS không cần chọn nội dung PUBLIC/INTERNAL để tính thống kê.

Filter service với TRIPS chọn những chuyến có ít nhất một parcel của service đó; các metric lượt vận chuyển/shipment DISTINCT chỉ đếm parcel khớp service. Kiểm tra hoàn tất manifest vẫn xét **toàn chuyến**, vì đây là thuộc tính chuyến. Discrepancy có item xác định mới được lọc theo service của item; UNEXPECTED/itemNULL không được gán service từ những kiện khác trên chuyến. Không service filter thì giữ các sai lệch chưa xác định này.

## 5. Công thức Delivery và Returns

DELIVERIES tái sử dụng đúng VALID_ATTEMPT của Commit16: assignment/actor/startedAt có thật, attemptTime >= startedAt, assignment cùng shipment và loại DELIVERY, employee khớp assignment. FAILED cần failureCode; SUCCESS cần shipment DELIVERED và proof khớp shipment, assignment, employee, actor, deliveredAt >= attemptTime.

**Tỷ lệ giao thành công theo lần = SUCCESS hợp lệ / (SUCCESS hợp lệ + FAILED hợp lệ) ×100**, làm tròn2 số lẻ HALF_UP. Mẫu số0 → N/A. Một shipment FAILED2 lần rồi SUCCESS1 lần cho 33,33%, không phải100%; số shipment có FAILED là1. Các KPI SUCCESS/FAILED/failedShipments/ratio giữ toàn bộ kết quả trong branch/service/time filter, không bị status filter của bảng chi tiết làm sai mẫu số; label UI/CSV ghi rõ điều này.

DELIVERED dùng OperationalReportRules.VALID_PROOF chung với Manager: trạng thái shipment DELIVERED, attempt SUCCESS có startedAt, shipment/assignment/DELIVERY type/employee/actor/time khớp. Mốc là proof.deliveredAt, khác attemptTime. Không dùng createdAt làm ngày giao, không đếm record thiếu/mismatched proof.

ReturnRequest không phải receipt. RETURN_STARTED chỉ nói đã vào quy trình RETURNING, không khẳng định đã mang về nguồn. RETURNED cần ReturnReceipt tại đúng origin branch, request COMPLETED và shipment RETURNED, theo receivedAt. Pending được tra riêng, không lấy số request làm số hàng đã trả. Không cộng các loại metric này thành tổng vận đơn duy nhất.

## 6. Branch operations và chống đếm trùng

Bảng BRANCHES dùng scalar subqueries trong **một query/paged batch**, không N+1 entity fetch và không tải contacts. Các cột:

- Đơn nguồn trong kỳ: origin + createdAt.
- Đơn đích trong kỳ: destination + createdAt.
- Lượt intake trong kỳ: ShipmentIntake.branch + receivedAt.
- Custody hiện tại: currentBranch trùng, chưa DELIVERED/RETURNED/CANCELLED, có intake hoặc trip receipt hoặc recovery RECEIVED tại branch đó, theo predicate Commit16.
- Chờ dispatch: custody hợp lệ, trạng thái nguồn/hub phù hợp, không active manifest, reuse WorkGroup.DISPATCH.
- Chờ giao: destination=currentBranch, AT_DESTINATION_BRANCH, custody hợp lệ, chưa assignment DELIVERY active, reuse WorkGroup.DELIVERY; đây là **chờ phân công giao**.
- Đang hoàn: Shipment RETURNING đang có currentBranch tại đây; không giả rằng chi nhánh đang giữ kiện nếu currentBranchNULL. Pending returns theo destination có dataset riêng.

Không cộng origin/destination/custody/intake thành tổng shipment toàn hệ thống. Một kiện đi qua hai chặng có thể có2 TripReceipt/lượt vận chuyển nhưng chỉ1 shipment DISTINCT. Branch rows có thể cùng liên quan một shipment ở những vai trò khác nhau; mỗi cột ghi rõ đơn vị và mốc.

## 7. Transport và discrepancy

TRIPS nhóm các trạng thái thực tế DRAFT/SEALED/DEPARTED/RECONCILING/CLOSED theo cohort createdAt; nhóm theo leg/tuyến. DEPARTED không tạo receipt giả; vehicle ARRIVED/RECONCILING cũng chưa đủ để đếm từng kiện đã nhận.

Card “CLOSED đã kiểm nhận đủ manifest” xác minh thêm expectedCount>0, số manifest khớp expectedCount, từng item có receipt tại đúng điểm đến sau departedAt và không còn discrepancy OPEN. Luồng close hiện hữu vốn yêu cầu mọi item RECEIVED và không sai lệch OPEN; báo cáo vẫn kiểm tra chứng từ thay vì tin đơn thuần vào một status row, không sửa transition.

Card chuyến có discrepancy dùng EXISTS để đếm mỗi trip một lần, gồm sai lệch đã/chưa xử lý. Dataset OPEN_DISCREPANCIES tra backlog độc lập với cohort/date. Không che lịch sử resolved discrepancy khi trip CLOSED, không chọn notes/resolution/reference tự do.

## 8. Support ticket và thời gian giải quyết

TICKETS phân nhóm category/priority/status/source/handling branch trên cohort tạo trong kỳ. CUSTOMER được tách khỏi MANAGER_PHONE/EMAIL/COUNTER, legacy sourceNULL có nhãn chưa xác định, không được suy diễn.

TICKET_EVENTS đếm action thực tế theo occurredAt; mỗi chu kỳ giải quyết có event riêng. Customer REOPEN_REQUESTED không tính là REOPENED. Số resolved/closed events trong kỳ khác số ticket tạo trong kỳ và khác snapshot đang mở. Bảng/CSV thể hiện đơn vị **sự kiện** khi đếm nhiều lần xử lý cùng ticket.

Metric thời gian giải quyết gần nhất dùng đúng định nghĩa Manager16: root hiện còn RESOLVED/CLOSED, resolvedAt >= createdAt và resolvedAt trong kỳ, AVG timestampdiff(second,createdAt,resolvedAt), kèm số mẫu; mẫu trống N/A. Actual reopen xóa mốc resolution hiện tại nên ra khỏi mẫu này, nhưng historical RESOLVED event vẫn còn. Không lấy updatedAt hoặc customer reopen request làm kết quả giải quyết; đây không phải SLA giờ làm việc.

## 9. Trend, biểu đồ và UI

Thymeleaf + Bootstrap local, layout/nav Admin có Reports/Audit. Có bảng chi tiết, fallback table cho mỗi chart, empty/error/loading state, pagination, ghi đơn vị và query time UTC. Không thêm thư viện chart/CDN/framework/dependency.

DB group theo ngày. Java chỉ gộp **tối đa93 daily aggregate rows** thành DAY, WEEK bắt đầu thứ Hai hoặc MONTH; không group Shipment entities trong RAM. Week/month ngoài biên ngày được chọn chỉ chứa phần giao của filter, không tự mở rộng query ra cả tuần/tháng. Snapshot không dựng trend theo createdAt giả.

Breakdowns lấy top100 nhóm ở DB; khi còn phần dư thêm “Nhóm khác” bằng tổng trừ phần đã hiển thị, để tổng biểu đồ không mất nhóm. CSV summary dùng đúng model đó. Chart widths từ số server0..100; nhãn th:text. JS chỉ đổi dataset thì xóa status không tương thích và submit GET; loading/back-forward reuse operations.js của Commit16.

## 10. CSV export và tính nhất quán

`kind=DETAILS` và SUMMARY dùng chung Request → ReportPolicy → Definition/queries với UI. Canonical export links giữ dataset, CUSTOM from/to đã resolve, branch, service, status, trend. Detail headers/projection/order là cùng query; summary gọi chính AdminReportService để lấy KPI/breakdown/trend. Không nhận filepath, tùy ý chọn cột, entity hoặc sort.

UTF-8 BOM, CRLF, tất cả cell quoted, `"` → `""`, giữ đúng comma/newline bên trong cell. Server prefix apostrophe nếu cell bắt đầu `= + - @`, whitespace/space Unicode, control hoặc FORMAT (bao gồm BOM/zero-width), ngăn leading-character bypass. Escape CSV không dựa vào JS/frontend.

Giới hạn detail **5000 rows** và output **4 MiB**, count preflight và cursor cap5001 để phát hiện tăng vượt ngưỡng. Cursor fetch size250, nằm trong transaction đọc ngắn; file được buffer **có giới hạn**, để trả lỗi rõ ràng trước khi gửi HTTP headers, không giữ transaction trong lúc client tải chậm. Không gom file lớn không giới hạn trong RAM hoặc tạo temp file theo client path. Summary chỉ từ aggregate/bounded groups, không bị cấm vì cohort lớn; vẫn có byte cap.

Response `text/csv;charset=UTF-8`, Content-Disposition attachment với filename dựng từ dataset/date/kind allowlist, Cache-Control no-store. Vượt giới hạn trả lỗi400 yêu cầu thu hẹp filter, không trả file bị cắt im lặng. Không export ticket subject/content/messages, old/new audit payload, proof/evidence, contacts, COD, giá trị hàng, token/password hoặc free-text discrepancy notes.

Không tạo export success audit trong GET: export là read-only, không phát business event/Notification hay audit “nghiệp vụ thay đổi”. Có thể thiết kế access/export logging tách biệt sau khi thống nhất outcome/correlation semantics; hiện không giả rằng đã ghi log tải thành công.

**Giới hạn snapshot:** UI/CSV chia sẻ định nghĩa, quyền và filter, nhưng là các lần đọc dữ liệu vận hành riêng. READ_COMMITTED/multiple queries không phải snapshot nguyên tử toàn DB; dữ liệu có thể đổi giữa request UI, các metric queries và export. UI hiển thị generatedAt và nêu rõ. Không tự bật SQL Server snapshot isolation, cache hoặc tạo reporting snapshot table; nếu cần exact point-in-time download của màn hình cũ phải bổ sung mô hình version/snapshot có chủ đích.

## 11. Unified Audit Viewer

AdminAuditQueries dùng HQL UNION ALL/derived table tại database, có DB count/offset/limit, sort timestamp/source/id ổn định. Không load mọi nguồn vào RAM để sort, không copy history vào audit_logs. Logical key là `(source,id)`; ID trùng số giữa các bảng vẫn là những sự kiện khác đối tượng/nguồn.

| Nguồn | Timestamp | Actor/action/entity hiển thị | Không chọn |
|---|---|---|---|
| ADMIN / AuditLog | createdAt | actor FK ID, action, resource type/id | actorUsername/IP, old/new MAX payload |
| SHIPMENT / ShipmentEvent | occurredAt | actor ID nullable, `STATUS_` + trạng thái đích, shipment ID, event branch | eventTitle/description/location, notification context/payload |
| TRANSPORT / TransportTripEvent | occurredAt | actor ID, TripEventType, trip ID, branch | notes/reference, evidence |
| TICKET / SupportTicketEvent | occurredAt | actor ID, TicketAction, ticket ID, branch | INTERNAL/PUBLIC body, detail, assignment payload |

Shipment source cố ý không suy từ free-text title để gọi là administrative action; các event cùng trạng thái đích vẫn có source ID/time riêng. Proof/attempt/handoff/return history chuyên biệt tiếp tục là chứng từ nghiệp vụ chi tiết; viewer không tạo dòng copy thứ hai cho từng chứng từ nếu đã có business event tương ứng. Các TripEvent và ShipmentEvent vốn được viết cùng một thao tác có thể cùng xuất hiện vì chúng ghi lịch sử **đối tượng khác nhau**, không phải audit bị nhân đôi mới.

Filter period/date, source, actor ID, action code, entity type/id và branch; allowlist enum/type, bounded strings và named parameters. AuditLog cũ không có branch context nên khi branch filter có giá trị, các ADMIN rows này bị loại; không đoán branch từ resourceId/JSON. Không có branch filter thì giữ đủ nguồn.

ActorNULL hiển thị “Không xác định (legacy)”, không gắn SYSTEM/role giả; schema chưa có metadata chứng minh system actor hoặc role snapshot. Actor là User#ID, không email/name snapshot. Viewer nói “Bản ghi quản trị đã lưu” hoặc “Lịch sử nghiệp vụ đã ghi nhận”, không bịa outcome của legacy/failed access. Thông tin thiếu giữ thiếu.

## 12. Audit hooks bổ sung và transaction

AdminUserService giữ nguyên authorization, mutex DB ROLE_ADMIN, thứ tự khóa actor/target, bảo vệ self/lastAdmin và các state transition. Bổ sung:

- USER_LOCKED, resource USER/id, before ACTIVE → after LOCKED.
- USER_UNLOCKED, before LOCKED → ACTIVE.
- USER_ROLES_CHANGED, before/after chỉ là danh sách role IDs đã sắp xếp, gồm effective roles thực sự được giữ/thêm/xóa. Không log role change nếu lựa chọn không làm thay đổi membership.

Actor từ authenticated context sau DB verification, không nhận client actor/role. createdAt Clock UTC cắt100ns. Không lưu raw request, email/phone/name/password/token/cookies/IP hoặc full target object.

AuditLog saveAndFlush cùng transaction hiện hữu: audit fail → rollback status/memberships; outer rollback → không có audit success giả. Không đưa audit vào NotificationWorker/outbox vì đây là ghi DB đồng bộ cùng business mutation, không có side effect ngoài DB. Không audit lại các sự kiện Shipment/Trip/Ticket/Pricing đã lưu; không backfill quá khứ.

Không có API update/delete audit hoặc retention job. Entity/DB hiện hữu vẫn có thể bị sửa bởi code/quyền DB ngoài UI; **không tuyên bố chống sửa đổi tuyệt đối**. Chống sửa ở DB, quyền write chuyên dụng, retention/WORM/correlation và access-denied logging cần thiết kế riêng; không tự cấp quyền SQL Server trong commit này.

## 13. V13, mapping và index

`V13__add_admin_reports_audit.sql` chỉ có10 CREATE INDEX:

- shipments(created_at,id), transport_trips(created_at,id), support_tickets(created_at,id).
- trip_discrepancies(recorded_at,id).
- return_receipts(received_at,id), trip_receipts(received_at,id).
- audit_logs(created_at DESC,id DESC).
- shipment_events/transport_trip_events/support_ticket_events(occurred_at DESC,id DESC).

Các index cũ bắt đầu bởi branch/parent/actor được giữ để phục vụ scope/detail. Index mới bắt đầu theo thời gian cho reporting/audit toàn hệ thống, không trùng danh sách key hiện hữu. DeliveryAttempt/Proof/ReturnRequest đã có global time-leading indexes từ V11 nên không tạo thêm index tương đương. Không thêm report table/cache/snapshot, FK/cột/enum/status/fee thay đổi hoặc DML seed/backfill.

JPA @Index đồng bộ cho10 Entity. CoreDatabaseMappingTests mở cumulative schema tới V13 và compile AdminReportQueries/AdminAuditQueries offline SQLServerDialect; entity count/FK/check/types/defaults vẫn như Commit17. SQL Server syntax index cơ bản, không giả định H2-only function trong migration. H2 test bulk fixtures/SYSTEM_RANGE chỉ dùng trong test memory DB, không nằm trong migration/code production.

Mỗi index tăng chi phí ghi/storage; chưa có execution plan thực hoặc benchmark write/aggregation trên SQL Server để định lượng. Cần review index trên dữ liệu thật trước deploy, không tự thay DB isolation/permissions. Chưa chạy V13 hoặc Hibernate ddl-auto=validate trên SQL Server trong phiên này.

## 14. Kiểm thử và kết quả

Các test mới dùng AdminReportingJdbcConfig → OperationsJdbcConfig: H2 memory riêng, schema Hibernate create-drop, JPA repositories/services thật, method security/validation/transaction/row locks, không load DB production hoặc chạy Flyway SQL Server. AdminCsvTests có functional fixture5001 rows để kiểm tra guard; **không gọi đây là benchmark**.

- AdminReportingJdbcTests: 15 shapes, dates/UTC/boundaries, branch/service/status, aggregates, ratio/proof, requests/receipt, branch custody, snapshots, unknown legacy, read no writes, UNION/filter/pagination, minimal audit, failure rollback/role changes/top100 remainder.
- AdminOperationalReportTests: multi-leg DISTINCT, DEPARTED/arrival không phải receipt, discrepancy backlog/privacy, start-return vs request/receipt, resolved old ticket/reopen request vs actual reopen/current duration, 4-source audit không copy, mixed-service trip và discrepancy itemNULL.
- AdminCsvTests: formula vectors/Unicode controls, quote/comma/CRLF parser, BOM, exact UI↔CSV projection/metrics, safe filenames, stored formula names, empty export, >5000 guard.
- AdminReportingMvcTests: real Security/MVC/Thymeleaf/JDBC render15 datasets, direct export authorization, stale roles/disabled admin, injection/XSS, headers/filename, no audit writes/deletes, Admin không thành Manager.
- AdminAuditConcurrencyTests: real H2 global-role mutex, hai Admin cùng khóa một User chỉ1 change/audit; cùng role selection chỉ1 change audit, không log no-op.
- Existing Admin unit/security/concurrency fixtures chỉ được bổ sung dependency AuditLogRepository/Clock, không bỏ assertion/test để che lỗi. Core mapping test giữ toàn bộ kiểm tra cũ, thêm V13/catalog mới.
- JS reports.test.mjs kiểm tra đổi dataset reset status + server submit và không ảnh hưởng audit/unrelated pages. Reuse notifications6 và operations3 checks.

Kết quả xác nhận cuối ngày 25/10/2026:

| Kiểm tra | Kết quả |
|---|---|
| `.\mvnw.cmd clean test` | **BUILD SUCCESS: 1146 tests, 1145 passed, 0 failures, 0 errors, 1 skipped**, đối chiếu lại Surefire XML |
| Baseline 01–17 | 1031 tests được giữ, thêm115 tests |
| AdminReportingJdbcTests | 43 passed |
| AdminOperationalReportTests | 10 passed |
| AdminCsvTests | 18 passed |
| AdminReportingMvcTests | 42 passed |
| AdminAuditConcurrencyTests | 2 passed |
| `.\mvnw.cmd -DskipTests compile` | BUILD SUCCESS |
| JavaScript | reports3 + operations3 + notifications6 checks passed bằng Node runtime node_repl; không cần npm; Node CLI chưa có trong PATH |
| HTML templates | 4/4 file có tags cân bằng qua HTMLParser; tất cả15 datasets và audit render bằng MockMvc |
| `git diff --check` | Sạch, kiểm tra lại cuối phiên |

Warning còn lại: Flyway Community metadata repository HTTP401 (Maven vẫn BUILD SUCCESS); Mockito/ByteBuddy dynamic Java agent trên Java21; explicit SQLServerDialect warning ở offline mapping test; unchecked operations trong test LastMile cũ khi javac testCompile. H2 CHECK violations là fault injection để chứng minh rollback và STOMP denial errors là regression security mong đợi, không phải failures của suite. Chưa kiểm tra giao diện trên trình duyệt đồ họa, chưa benchmark aggregation lớn, chưa chạy SQL Server JDBC migration/validate. Regression WebSocket embedded Tomcat là socket thật nhưng persistence ở nhóm đó dùng mock; không dùng kết quả này để suy ra SQL Server concurrency.

## 15. Hướng dẫn kiểm thử thủ công

1. Review base Commit17 và V13; main local đầu phiên chưa có Commit17, cần tích hợp17 trước18 theo workflow của nhóm. Chủ động chạy migration/validate trên DB được ủy quyền, không dùng test opt-in Windows integrated authentication như bằng chứng cho database SQL Authentication hiện tại.
2. Dùng Admin ACTIVE/DB ROLE_ADMIN thật, mở `/admin` → Báo cáo. USER/MANAGER/SHIPPER/Guest mở URL trực tiếp hoặc export phải bị chặn. Admin mở Manager routes vẫn bị chặn nếu không có role Manager thật.
3. Chọn dataset, TODAY/7/30/THIS_MONTH hoặc CUSTOM, branch/service ID từ directory. Kiểm tra cuối ngày UTC được bao gồm, quá93 ngày/đảo ngày/future/ID lạ/status sai nhóm trả400.
4. Đối chiếu cohort createdAt với SQL count/group; status cohort là trạng thái hiện tại, không số transitions. Đối chiếu2 FAILED+1 SUCCESS của một shipment:3 lượt,1 failed shipment,33.33%.
5. Đối chiếu proof mismatch/legacy attempt thiếu context bị loại; request hoàn không tăng RETURNED; receipt nguồn+COMPLETED/RETURNED mới tăng. Kiểm tra multi-leg cho2 lượt/1 shipment DISTINCT.
6. Kiểm tra branch origin/destination/intake/custody theo từng cột; không cộng làm tổng shipment. Chuyến có nhiều service: filter service chỉ đếm kiện khớp; UNEXPECTED discrepancy không có item không tự mang service của chuyến.
7. Ticket tạo cũ nhưng resolve trong kỳ có event kết quả, không vào cohort mới. Customer reopen request không đổi REOPENED; actual Manager reopen giữ event cũ nhưng ra khỏi mẫu resolvedAt hiện tại.
8. Xuất SUMMARY/DETAILS từ link giữ filter; so các cột, order, metrics/labels/buckets với UI khi không có writer đồng thời. Dùng Excel để kiểm tra tiếng Việt, quotes/comma/newline, ô bắt đầu =/+/-/@/whitespace không chạy công thức. Vượt5000 rows phải có lỗi rõ ràng, không file bị cắt.
9. `/admin/audit` lọc từng source/actor/action/entity/date/branch, kiểm tra source IDs/time rõ, unknown actor giữ unknown, không thấy notes/body/evidence/raw old/new. Thử XSS trong legacy action/resource ID phải hiển thị text.
10. Khóa/mở khóa/đổi role target qua Admin User UI: audit tối thiểu được ghi đúng actor. Thao tác no-op role không có dòng thay đổi giả. Không có nút/API sửa/xóa audit. Khi tài khoản Admin bị khóa hoặc mất role, session cũ không export/read được nữa.
11. Kiểm tra mobile viewport, loading/back navigation, biểu đồ/bảng fallback và pagination bằng trình duyệt thật; chưa được thực hiện trong phiên coding.

## 16. Giới hạn và kết nối module hiện hữu

Không financial ledger/revenue/profit/COD reconciliation, XLSX/PDF, export audit, PII-rich drilldown, reporting cache/snapshot consistency xuyên request, cron report, retention/tamper-proof storage, login failure logging hoặc backfill history. Unified audit chỉ thể hiện metadata nguồn thực sự có, không hoàn thiện metadata quá khứ bằng suy đoán.

Không tạo quyền Admin cấp trường chi tiết mới. Nếu triển khai phân quyền reporting theo branch/field về sau, phải mở rộng cả GATE/DTO/export theo cùng policy; không tái sử dụng ManagerBranch để ngầm biến Admin thành Manager. Notification/customer portal quyền giữ nguyên; báo cáo/audit không phát notification mới.

Index/UNION/correlated branch aggregates cần execution plan/load test trên SQL Server, kiểm tra ảnh hưởng ghi, isolation và timeout thực tế; H2/SQLServerDialect offline không thay thế những bước đó. Không tuyên bố production-ready khi các xác minh này chưa được thực hiện.

## 17. Manifest file

Tổng **28 file mới + 19 file sửa = 47 file**, không xóa, không staged. `git diff --stat` chỉ bao gồm tracked files, không thay thế danh sách untracked.

File mới, đồng thời là output đầy đủ của `git ls-files --others --exclude-standard`:

```text
docs/admin-reports-audit.md
src/main/java/com/ute/uteexpress/controller/AdminAuditController.java
src/main/java/com/ute/uteexpress/controller/AdminReportingErrorAdvice.java
src/main/java/com/ute/uteexpress/controller/AdminReportsController.java
src/main/java/com/ute/uteexpress/dto/admin/AuditViews.java
src/main/java/com/ute/uteexpress/dto/admin/ReportLabels.java
src/main/java/com/ute/uteexpress/dto/admin/ReportViews.java
src/main/java/com/ute/uteexpress/repository/AdminAuditQueries.java
src/main/java/com/ute/uteexpress/repository/AdminReportQueries.java
src/main/java/com/ute/uteexpress/repository/OperationalReportRules.java
src/main/java/com/ute/uteexpress/service/reports/AdminAuditService.java
src/main/java/com/ute/uteexpress/service/reports/AdminReportService.java
src/main/java/com/ute/uteexpress/service/reports/AdminReportingAccess.java
src/main/java/com/ute/uteexpress/service/reports/ReportCsvService.java
src/main/java/com/ute/uteexpress/service/reports/ReportPolicy.java
src/main/resources/db/migration/V13__add_admin_reports_audit.sql
src/main/resources/static/js/reports.js
src/main/resources/templates/reports/audit.html
src/main/resources/templates/reports/error.html
src/main/resources/templates/reports/fragments.html
src/main/resources/templates/reports/index.html
src/test/java/com/ute/uteexpress/operations/AdminAuditConcurrencyTests.java
src/test/java/com/ute/uteexpress/operations/AdminCsvTests.java
src/test/java/com/ute/uteexpress/operations/AdminOperationalReportTests.java
src/test/java/com/ute/uteexpress/operations/AdminReportingJdbcConfig.java
src/test/java/com/ute/uteexpress/operations/AdminReportingJdbcTests.java
src/test/java/com/ute/uteexpress/operations/AdminReportingMvcTests.java
src/test/js/reports.test.mjs
```

File sửa, theo `git diff --name-only`:

```text
docs/customer-support.md
docs/operations-dashboard-tickets.md
src/main/java/com/ute/uteexpress/entity/AuditLog.java
src/main/java/com/ute/uteexpress/entity/ReturnReceipt.java
src/main/java/com/ute/uteexpress/entity/Shipment.java
src/main/java/com/ute/uteexpress/entity/ShipmentEvent.java
src/main/java/com/ute/uteexpress/entity/SupportTicket.java
src/main/java/com/ute/uteexpress/entity/SupportTicketEvent.java
src/main/java/com/ute/uteexpress/entity/TransportTrip.java
src/main/java/com/ute/uteexpress/entity/TransportTripEvent.java
src/main/java/com/ute/uteexpress/entity/TripDiscrepancy.java
src/main/java/com/ute/uteexpress/entity/TripReceipt.java
src/main/java/com/ute/uteexpress/repository/OperationsDashboardQueries.java
src/main/java/com/ute/uteexpress/service/AdminUserService.java
src/main/resources/templates/admin/fragments.html
src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java
src/test/java/com/ute/uteexpress/security/AdminServiceSecurityTests.java
src/test/java/com/ute/uteexpress/service/AdminConcurrencyTests.java
src/test/java/com/ute/uteexpress/service/AdminUserServiceTests.java
```
