# Commit 21 — Security, Admin, RBAC & Full-System Integration Testing

Ngày thực hiện: 26/10/2026 (UTC+7). Phụ trách Thuận.
Nhánh `feature/thuan-security-system-integration` đã có sẵn và sạch khi bắt đầu, cùng HEAD `8c83053` với `main`,
đã merge Commit 20 (`66088dd`) và các Commit 01–19. Không tạo/sửa lịch sử Git, không stage/commit/push/merge/stash hoặc PR.

## Baseline và mục tiêu

Baseline thực chạy trước khi sửa: `.\mvnw.cmd clean test` → **1.309 test, 1.308 passed,
0 failure, 0 error, 1 skipped; BUILD SUCCESS; 1 phút 32 giây**. Khớp tham chiếu Commit 20.

Mục tiêu là kiểm thử quyền thực ở HTTP và service/JPA, account/role revocation, Admin/audit atomicity,
cross-module privacy, CSV và session. Tái sử dụng fixture/service Commit 16/18/19/20; không viết module mới.
Khảo sát SecurityConfig, DAO UserDetailsService, BCrypt/login/logout handlers, User/Role/Employee/ManagerBranch,
AdminUserService/controller/DTO, LogisticsAccessService, ownership/support/throttle, notifications/WS,
AdminReport/Audit/CSV queries và các regression tương ứng.

Không đổi V1–V13, pom, application.properties, DTO/enum chung, SecurityFilterChain, nghiệp vụ pricing/logistics/support.
Không đọc credentials cấu hình, không kết nối dữ liệu thật. Chuỗi xác thực test được sinh ngẫu nhiên trong bộ nhớ,
BCrypt thật và ghi vào H2 test, không tài khoản/mật khẩu/secret hardcode hoặc log giá trị đó.

## Security architecture thực tế

Authentication: email + BCrypt strength 12 qua DaoAuthenticationProvider/DatabaseUserDetailsService.
UserDetails lấy role đúng từ DB, không gán role mặc định, không ROLE_VENDOR hoặc hierarchy tự suy ra.
Chỉ User ACTIVE được authenticate; LOCKED/UNVERIFIED trả lỗi generic. Không có UserStatus INACTIVE.

Employee ACTIVE/position/home branch là gate nghiệp vụ vật lý, không điều kiện login chung.
Employee INACTIVE vẫn có thể login nếu User ACTIVE, nhưng Shipper/physical action bị chặn.
Manager confirm/planning theo ManagerBranch; physical intake/seal/receive còn cần Employee hợp lệ.
Không tự thắt hoặc nới các contract này để khớp tên role/trạng thái trong prompt.

Session IF_REQUIRED, session fixation `changeSessionId`, POST logout có CSRF, invalidate/clear authentication/JSESSIONID.
Redirect thành công cố định `/account`, không dùng client redirect. Login thất bại HTML `/login?error`, JSON 401 generic.
HTML protected request chưa login trả 401 theo thiết kế hiện tại, không ép mọi lỗi thành redirect.
401 = chưa authenticate, 403 = sai quyền/trạng thái, 404 = tài nguyên không tồn tại/không thuộc phạm vi, validation lỗi = 400.

CSRF bật cho login, logout và mọi state-changing form/API. GET không mutate. Static GET/HEAD và public allowlist
được mở hẹp; endpoint ngoài allowlist vẫn deny. Controller và service đều có gate, không dựa vào nút bị ẩn.

### RBAC matrix được chạy bằng login DB thật

`SystemRbacMvcTests.realDatabaseLoginAndRouteMatrixDoNotInventRoleInheritance` chạy năm actor dưới đây qua 11 route.
“200” ở đây là fixture ACTIVE, đúng branch/owner theo route; quyền object vẫn phải kiểm tra riêng.

| GET route | Guest | USER | SHIPPER | MANAGER | ADMIN |
|---|---:|---:|---:|---:|---:|
| `/tracking`, `/quote` | 200 | 200 | 200 | 200 | 200 |
| `/account` | 401 | 200 | 200 | 200 | 200 |
| `/user/shipments`, `/account/support` | 401 | 200 | 403 | 403 | 403 |
| `/shipper/pickups` | 401 | 403 | 200 | 403 | 403 |
| `/manager/trips` | 401 | 403 | 403 | 200 | 403 |
| `/admin/users`, `/admin/reports`, `/admin/audit` | 401 | 403 | 403 | 403 | 200 |
| `/api/notifications` | 401 | 200 | 200 | 200 | 200 |

Object gates: shipment.sender thật; ticket.user và shipment.sender cùng owner; Shipper đúng assignment/type/branch;
ManagerBranch DB ACTIVE đúng điểm; Admin cần authority trong session + User ACTIVE/ROLE_ADMIN DB hiện tại.
Không dùng tracking, notice ID, hidden owner/actor/branch để cấp ownership. Admin không mặc định Manager/Shipper/User.
Nhiều role có thể được Admin cấp có chủ đích, nhưng grant không có nghĩa session cũ tự nhận role mới.

## Admin, session và role changes

Luồng mới dùng actual MockMvc/filter/BCrypt/session + AdminUserService/JPA:

- Admin khóa User đã có shipment: các request shipment/support/notification từ session cũ bị 403,
  login mới generic failure, lịch sử shipment/phí/role vẫn tồn tại; unlock chỉ đổi status và cho access lại theo policy.
- Grant thêm ROLE_ADMIN trong DB: session USER cũ vẫn bị filter 403; login mới có authority mới.
  Revoke ADMIN: session đang giữ ROLE_ADMIN bị service/report gates chặn ngay lần truy cập nghiệp vụ tiếp theo.
- Account shell `/account` dùng session identity, không cam kết refresh role display hoặc hủy mọi HTTP session ngay khi Admin lock.
  Không có password change/reset mới. Không tự phát triển JWT hoặc session store phân tán.
- Invalid ID/role, tự khóa, tự bỏ ADMIN, khóa/mở UNVERIFIED, unlock ACTIVE bị chặn theo contract.
  Duplicate selected role IDs không tạo duplicate membership; no-op không tạo success audit.
- Role lạ đã tồn tại được bảo toàn, không xuất hiện trong danh sách role có thể cấp của Admin module.
  Bỏ toàn bộ role hợp lệ không tự cấp USER lúc unlock; không có default privilege.

Admin mutation giữ lock order: mutex Role ADMIN → actor User → target User. Status/roles và AuditLog cùng transaction.
Audit dùng actor thực, target USER/id thực, action USER_LOCKED/USER_UNLOCKED/USER_ROLES_CHANGED và old/new tối thiểu.
Không ghi username/IP/hash/free text không cần thiết. Audit failure rollback mutation; transaction thất bại không có success audit.

## SEC-21-01 — Stale identity có thể vượt qua account/employee/branch revocation

**Expected:** nếu Admin lock User hoặc trạng thái Employee/Branch bị vô hiệu hóa trước gate nghiệp vụ sau khóa Shipment,
workflow không được commit pickup/delivery proof hoặc lịch sử thành công.

**Actual trước sửa:** 6/8 regression cases thất bại. User LOCKED, Employee INACTIVE và Branch INACTIVE vẫn cho phép
pickup SUCCESS/delivery complete sau cửa sổ chờ; hai role-removal cases đã chặn vì role query đọc lại DB.

**Root cause:** Shipper workflow đọc User/Employee/Branch trước `findForLifecycle`. Sau đó query entity lại
trong cùng persistence context vẫn trả object đã cache. SQL entity query không tự refresh trạng thái đang managed.
`requireActor`/`requireShipper` vì vậy thấy status cũ dù transaction Admin đã commit.

**Sửa tối thiểu:** inject EntityManager vào LogisticsAccessService và refresh User, Employee cùng User/Branch liên quan
trước các check hiện hữu. Không đổi role, branch, position, assignment, fee, lifecycle hoặc transaction lock order.
Giữ nguyên `noRollbackFor=AccessDeniedException` cho authorization probe chỉ đọc của Commit 20.

**Bằng chứng:** `SystemAccountRevocationTests` có 8 case PICKUP/DELIVERY × account lock, employee inactive,
branch disabled, role removed; không event/proof/attempt/notice giả sau denial, audit Admin thay đổi quyền vẫn commit đúng.
Trước sửa có failing test thật; sau sửa cả 8 đạt cùng các regression logistics/rollback Commit 20.

Test-only WorkflowProbe là advisor quanh **repository thật**, pause bằng ThreadLocal/latch sau identity read và
trước parent-lock invocation; thread Admin thay đổi DB bằng service thật (hoặc fixture status cho Employee/Branch chưa có UI).
Advisor không mock return value, authorization, repository, transaction hoặc chứng từ. Đây là lịch thực thi có kiểm soát
tại boundary dễ bị stale cache, không phải bằng chứng SQL Server lock-wait/isolation.

Không cam kết hủy transaction đã vượt qua check cuối hoặc khóa toàn bộ session tức thời; các gates đọc lại trạng thái
ở boundary hiện hữu. Strict global in-flight revocation cần policy/locking riêng, chưa thiết kế trong commit này.

Constructor mới cần EntityManager: 2 file test cũ và 1 fixture cũ chỉ bổ sung dependency/mock EM ở hạ tầng vốn đã mock;
không đổi assertion/expected access, không xóa hoặc disable test. Test JDBC mới dùng EM thật.

## Audit Viewer, Reports và CSV

Audit hợp nhất bốn nguồn ADMIN/SHIPMENT/TRANSPORT/TICKET. Journey mới tạo các nguồn bằng service thật,
kiểm tra pagination ổn định trong tập dữ liệu tĩnh, cùng timestamp và numeric IDs trùng giữa các bảng nhưng không trộn key:
logical key là `(source,id)`. Filter actor/action/entity/branch vẫn theo contract; không chọn old/new payload hoặc note body.

Full logistics → report chạy lại qua nhiều leg/receipt/proof. Ba chuyến vẫn một unique shipment; sáu chặng đi/hoàn
vẫn một unique shipment; ReturnRequest/AT_ORIGIN không phải completed return, final sender receipt mới vào RETURNED.
Không suy doanh thu từ fee snapshot. Admin report/audit/export có statistics assertion zero entity INSERT/UPDATE/DELETE,
không notice và không success audit phát sinh khi chỉ GET/read. Query có DB gate và recheck Admin, không dựa riêng filter chain.

CSV mới có kiểm tra HTTP/JDBC thật: UTF-8 BOM, text/csv, safe Content-Disposition, filename server cố định,
dấu phẩy/quote/CRLF/tiếng Việt và formula prefixes `= + - @`, tab, NBSP, zero-width, BOM từ tên branch đã lưu DB.
5000 rows export thành công; 5001 trả 400, không Content-Disposition hoặc successful partial CSV; DB không đổi.

`SystemCsvByteBoundaryTests` kiểm tra **unit** backstop 4 MiB tại private buffer writer: đúng ngưỡng nhận,
overflow không ghi phần dòng, tính theo UTF-8 bytes gồm quotes/CRLF. Đây không phải HTTP/JDBC export 4 MiB;
không phá schema/field length để dựng payload không hợp lệ. Không thay giới hạn 5000/4 MiB.

Invalid dataset/export/source/actor/action/entity/date/page/size/branch/service trả lỗi an toàn, không SQL/stack trace
hoặc dữ liệu chưa escape. Range đảo không trả file. Legacy AuditLog free-text không xuất vào HTML/CSV.

## User, Support và Notification privacy

Journey mới tạo Shipment A và ticket A, Manager đúng route reply PUBLIC/note INTERNAL, worker thật xử lý outbox;
User B không đọc shipment/detail/search, ticket/read/message/reopen, notification target/read/count của A bằng đổi ID/code.
Manager nhiều ManagerBranch đọc đúng scope; branch bị thu hồi không xác nhận đơn được; denial không thêm event/notice.

Customer DTO chỉ PUBLIC/resolution theo policy; INTERNAL note không vào customer view, notice hoặc Guest tracking.
Guest dùng DTO/allowlist riêng, không contacts/tiền/proof/internal notes; tracking không tự cấp ownership.
Authenticated owner shipment detail có contract khác: title/description/location của ShipmentEvent đang được dùng
cho lịch sử chủ vận đơn. Schema chưa có cờ INTERNAL/PUBLIC cho ShipmentEvent. Không tự suy diễn phân loại mới hoặc
đổi contract của Tân; cần nhóm duyệt thêm chính sách nội dung vận hành trên owner timeline ở Commit 22.

Notification còn kiểm tra User ACTIVE/role/ownership/assignment/branch từ DB, read endpoint cần CSRF;
notice URL server quyết định, không là bằng chứng cấp quyền. Retry/dedup/offline/broker outage giữ theo regression cũ.
Worker và delivery trong journey mới là thật, broker là channel trong tiến trình; payload chỉ REFRESH.

## WebSocket/STOMP và mức bằng chứng

Giữ nguyên 17 regression tests: embedded Tomcat, HTTP login/cookie/CSRF, WebSocket handshake/STOMP network thật,
origin/anonymous/CONNECT CSRF, private queue/subscription/SEND, account/role revoke, logout/session expiry/reconnect/ticket link.
Persistence của 17 ca này vẫn mock; không gọi là full network + JDBC E2E.

Không tạo WS server/worker mới. Policy hiện hữu: same origin, session authenticated; chỉ subscribe
`/user/queue/notifications`, deny arbitrary SEND/destination; check identity khi inbound/outbound và sweep session định kỳ.
Không gửi userId/secret/internal payload do client tự chọn. Socket traffic không kéo dài session HTTP.

Các lớp JDBC/service mới không mock security/business repositories; MVC dùng servlet simulation, không trình duyệt thật.
Byte-boundary là unit test. Regression cũ vẫn gồm unit/mocked MVC và H2/JDBC theo từng lớp; không gộp chúng thành E2E.

## Transaction và concurrency

8 deterministic cross-module revocation cases ở trên + **8 concurrency scenarios** mới, dùng latch/executor/timeouts,
clear SecurityContext riêng từng thread, không Thread.sleep:

1. Admin lock đối đầu unlock: chỉ audit các transition thực sự thành công, status cuối khớp lịch đã commit.
2. Hai Admin chọn role khác nhau: một bộ role hoàn chỉnh cuối cùng, không lost/duplicate assignment, audit cả hai thay đổi.
3. Role change đối đầu account lock: giữ cả status và role update, hai audit đúng action.
4. Hai Admin gỡ ADMIN của nhau: còn một ACTIVE Admin, actor bị revoke không tiếp tục mutate.
5. Hai Admin khóa nhau: còn một ACTIVE Admin, một audit.
6. Hai export đồng thời: nội dung giống nhau với dữ liệu tĩnh, không mutation/audit/notice.
7. Notification read đối đầu delivery retry: giữ read flag, retry metadata và một notice persistent, không trùng.
8. Manager reopen đối đầu customer reopen-request cùng version: một event mới, CLOSED/pending hoặc REOPENED đúng winner.

Admin audit fault injection kiểm tra lock/unlock/role change rollback cùng audit, history/fees không mất, retry được sau gỡ CHECK.
Các test Commit 20 về caught/uncaught denial, receipt/proof/return rollback và enum candidate binding đều chạy full regression.
Không sửa rollback rule chỉ để test pass. Không cam kết H2 locking giống SQL Server.

Chưa có lịch interleaving riêng report query đối đầu writer nghiệp vụ. Reports hiện ReadCommitted/multiple queries,
không cam kết atomic snapshot khi dữ liệu đang thay đổi; hai export trên dữ liệu tĩnh không chứng minh điều đó.

## Ma trận scenario và journey

Actor/role từ fixture DB thật; MVC login BCrypt thật. Cột môi trường phân biệt JDBC và unit, không ngầm coi unit là integration.

| Scenario | Actor | Role | Resource | Action | Expected access | Actual result | Database invariant | Test class | Test environment | Pass/fail |
|---|---|---|---|---|---|---|---|---|---|---|
| RBAC matrix | Năm actor riêng | Guest/USER/SHIPPER/MANAGER/ADMIN | 11 public/account/business/Admin/notice routes | GET | Matrix 401/403/200 ở trên | Đúng 55 ô | Không audit/notice do GET | SystemRbacMvcTests | MVC + H2/JPA | PASS |
| Lock/unlock với session cũ | Admin A, chủ shipment | ADMIN, USER | User, shipment/support/notice | Lock/unlock, login, GET | Khóa chặn request nghiệp vụ; unlock khôi phục theo role | Đúng status/login/session policy | Giữ history/phí/role; audit actor/target đúng | SystemRbacMvcTests | MVC + H2/JPA | PASS |
| Grant/revoke role | Admin A, User được đổi role | ADMIN, USER | UserRole, session | Update role, login, GET | Grant cần login mới; revoke chặn business gate | Session cũ không tự tăng quyền | Membership/audit đúng | SystemRbacMvcTests | MVC + H2/JPA | PASS |
| Auth negatives | User LOCKED/UNVERIFIED, employee inactive | USER/SHIPPER | Login, physical action | POST | Login lỗi generic; inactive employee không thao tác vật lý | 401/403 theo policy | Không proof/event trái phép | SystemRbacMvcTests | MVC + H2/JPA | PASS |
| Request tampering | Admin đang login | ADMIN | Roles/lock forms, invalid IDs | GET/POST, CSRF thiếu/sai | 400/403/404/405 theo contract | Không escalation/hash leak | Không mutation/audit giả | SystemRbacMvcTests | MVC + H2/JPA | PASS |
| Domain guard | Non-Admin, Admin tự tác động | USER/MANAGER/SHIPPER/ADMIN | User, UserRole | Lock/unlock/updateRoles | Deny sai policy; no-op không audit | Đúng self/legacy/duplicate rules | Bảo toàn role lạ, không duplicate | SystemAdminIntegrationTests | Service + H2/JPA | PASS |
| Audit failure rollback | Admin A | ADMIN | User/roles/audit | Mutation gặp DB CHECK failure | Không thành công một phần | Exception; retry sau gỡ fault đạt | Account/roles/audit cùng rollback | SystemAdminIntegrationTests | H2/JPA | PASS |
| Revocation sau identity read | Shipper, Admin A | SHIPPER, ADMIN | Pickup/delivery | Thu hồi account/employee/branch/role rồi tiếp tục workflow | AccessDenied | 8/8 đạt; 6/8 fail trước fix | Không proof/attempt/event/notice giả | SystemAccountRevocationTests | H2/JPA + scheduling probe | PASS |
| Forward + support + reports | Owner, Manager, Shipper, Admin | USER/MANAGER/SHIPPER/ADMIN | Shipment/trips/proof/ticket/reports/audit | Forward, PUBLIC reply, INTERNAL note, đọc | Actor đúng scope; Admin đọc reports | Đếm đúng, audit bốn source, nội dung an toàn | Unique shipment/trip/proof đúng; read-only | SystemCrossModuleIntegrationTests | Service + H2/JPA | PASS |
| Failure → return → audit | Manager, Shipper, Admin | MANAGER/SHIPPER/ADMIN | Return, reverse trips, sender receipt | Return rồi filter audit/report | RETURNED chỉ sau final receipt | Đúng action/actor/branch | RETURNING chưa counted returned; sáu receipt | SystemCrossModuleIntegrationTests | Service + H2/JPA | PASS |
| Ownership/branch revocation | User B, Manager ngoài scope | USER/MANAGER | Shipment/ticket/notice/branch | Đọc/ghi/revoke scope | 404/403 theo contract | Không cross-user/cross-branch bypass | Không event/notice thay đổi trái phép | SystemCrossModuleIntegrationTests | H2/JPA | PASS |
| CSV untrusted input/row limits | Admin A | ADMIN | Export Branches/Shipments | CSV formula/Unicode/5000–5001 rows | 200 hợp lệ; 400 vượt giới hạn | BOM/escape/filename đúng, không partial success | Không thay DB/audit/notice | SystemReportSecurityTests | MVC + H2/JPA | PASS |
| Invalid filters/legacy payload | Admin A | ADMIN | Reports/audit/export | Read/filter/export | 400 an toàn; payload riêng tư không render | Không raw payload/hash/SQL | Không entity writes | SystemReportSecurityTests | MVC + H2/JPA | PASS |
| Byte limit | Test buffer caller | Không áp dụng | CSV byte buffer | Exact/overflow/UTF-8 4 MiB | Nhận đúng ngưỡng; từ chối overflow | Không append phần dòng | Không áp dụng, unit không dùng DB | SystemCsvByteBoundaryTests | Unit | PASS |
| Eight concurrency pairs | Admin A/B, customer/Manager, worker | ADMIN/USER/MANAGER/system | Account/roles/export/ticket/notice | Đồng thời bằng latch | Đúng một lịch commit hợp lệ | Không escalation/lost roles/audit/notice | DB cuối/versions/counts đúng | SystemAdminConcurrencyTests | H2/JPA | PASS |

10 journey ưu tiên của yêu cầu có bằng chứng bằng các nhóm trên và journey thật tái sử dụng Commit 20:
Admin lock/auth; role change/RBAC; User shipment/Manager scope; logistics/report; failed delivery/return/audit;
ticket/notice; cross-user privacy; unauthorized Manager; readonly reports/audit; revocation giữa workflow.
Không gọi đây là browser/network E2E hoặc phủ mọi race possible.

## Test fixture và kết quả

SystemSecurityIntegrationConfig import LogisticsJourneyConfig; SystemSecuritySupport thêm Admin B và helper ticket/role;
SystemSecurityWebSupport import actual controllers/security, tạo credential ngẫu nhiên/BCrypt và session MockMvc.
Reset H2/Clock theo từng test, ID sinh bởi database, không phụ thuộc thứ tự test. Không shared mutable scenario data giữa test.
Helper giải phóng principal fixture service trước HTTP để request thật dùng session đã login; không mock role/security.

| Lớp mới | Test cases | Phân loại |
|---|---:|---|
| SystemAccountRevocationTests | 8 | H2/JPA transaction + real authorization + test scheduling probe |
| SystemRbacMvcTests | 17 | Real filter/BCrypt/session/controller/services/JDBC, 5×11 route matrix |
| SystemAdminIntegrationTests | 15 | Admin/roles/audit/rollback JDBC |
| SystemCrossModuleIntegrationTests | 4 | Real cross-module service journeys + reports/audit/privacy/JDBC |
| SystemReportSecurityTests | 25 | Reports/audit/CSV MVC + JDBC |
| SystemCsvByteBoundaryTests | 3 | Unit buffer byte-limit assertions |
| SystemAdminConcurrencyTests | 8 | Real transaction pairs / H2 JDBC |
| Tổng | 80 | 77 H2/JDBC/MVC cases + 3 unit cases |

Full `.\mvnw.cmd clean test` sau thay đổi: **1.389 test, 1.388 passed, 0 failure, 0 error, 1 skipped;
BUILD SUCCESS; 3 phút 16 giây**. Tăng đúng 80 case so với 1.309 case cũ. Không thêm skipped/disabled hoặc xóa test.
Hai lớp test cũ và một fixture cũ chỉ được bổ sung dependency EntityManager; không đổi nội dung test/assertion.

| Lệnh/nhóm | Kết quả thực chạy |
|---|---|
| `./mvnw.cmd clean test` baseline | BUILD SUCCESS, 1.309 tests / 1.308 passed / 0 failures / 0 errors / 1 skipped; 1:32 |
| `./mvnw.cmd clean test` sau sửa | BUILD SUCCESS, 1.389 tests / 1.388 passed / 0 failures / 0 errors / 1 skipped; 3:16 |
| `./mvnw.cmd verify` | BUILD SUCCESS, cùng 1.389 tests và kết quả trên; jar + Boot repackage thành công; 3:11 |
| `./mvnw.cmd -DskipTests compile` | BUILD SUCCESS; classes up to date sau clean test/verify; 4,263 giây |
| `git diff --check` | Exit 0, không lỗi whitespace |
| UTF-8/final newline/trailing whitespace, cả untracked | 15/15 file hợp lệ |
| JavaScript | Notifications 6 + Operations 3 + Reports 3 = 12/12 checks đạt |
| WebSocket/STOMP regression | 17/17 đạt, network thật + persistence mock |
| SQL Server / Flyway V1–V13 / Hibernate validate | Chưa chạy, không đủ môi trường test riêng được cấp phép |

Đối chiếu Surefire XML sau verify: tổng 1.389, failure/error 0, skipped 1; 7 suite mới đúng 80 cases.
Maven verify không bổ sung bộ Failsafe riêng hoặc tự kiểm thử SQL Server. Không cộng hai lượt Maven thành số test khác nhau.

Test methods/assertions cũ sửa: **0**; file test/fixture cũ sửa dependency: **3**; test xóa/disable mới: **0**.
Một skipped là `UteexpressApplicationTests.contextLoads`, opt-in bằng `uteexpress.sqlserver.validate=true`,
đã có từ baseline; không bật do chưa có SQL Server test được cấp phép. WebSocket/STOMP giữ nguyên **17/17 đạt**.
JavaScript chạy cùng 3 module qua Node runtime công cụ (Node CLI không trên PATH): Notifications 6,
Operations 3, Reports 3 — **12/12 checks đạt**. Không npm install hoặc sửa JS.

## SQL Server và giới hạn cần bàn giao Commit 22

Repository không có Testcontainers SQL Server/test instance profile được cấp phép. Có driver SQL Server/Flyway
không đồng nghĩa có database test an toàn. Không đọc credentials, không dùng UTEExpressDB hoặc tự tạo/xóa DB.
Test SQL Server opt-in cũ dùng integrated authentication trong khi môi trường đã xác nhận SQL Authentication,
nên không bật hoặc đổi config. **Chưa chạy SQL Server JDBC, Flyway V1–V13 hoặc Hibernate validate.**

Bàn giao Commit 22: test database riêng để fresh/upgrade migrations, validate FK/CHECK/index/filtered unique,
UTC precision/collation/isolation/deadlock, security/concurrency smoke trên SQL Server; không sửa migration đã merge.
Không có lỗi schema được chứng minh, không tạo V14.

Các phần chưa xác minh: browser UI/mobile/accessibility, TLS/cookie deployment settings, network WebSocket + JDBC
trong cùng journey, strict global in-flight/session revocation, report interleaving snapshot, hiệu năng/SQL plans,
distributed limiter/broker/failover/exactly-once. Owner shipment timeline content policy vẫn cần review như đã nêu.
Không xem kết quả test là chứng nhận security production hoàn chỉnh.

Warnings baseline: Flyway Community GitHub metadata HTTP 401 không làm build thất bại;
Mockito/ByteBuddy Java 21 dynamic-agent; explicit SQLServerDialect trong offline mapping test;
unchecked operations ở test LastMile cũ. H2 constraint failure, STOMP deny và worker deferred logs trong negative tests là cố ý.

## Cách chạy và kiểm thử thủ công

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests compile
git diff --check
.\mvnw.cmd '-Dtest=SystemAccountRevocationTests,SystemRbacMvcTests,SystemAdminIntegrationTests,SystemCrossModuleIntegrationTests,SystemReportSecurityTests,SystemCsvByteBoundaryTests,SystemAdminConcurrencyTests' test
node src/test/js/notifications.test.mjs
node src/test/js/operations.test.mjs
node src/test/js/reports.test.mjs
```

Maven pom không có Failsafe/profile integration riêng; các `*Tests` này vào Surefire, verify chạy lại tests rồi package.
SQL Server opt-in và graphical browser không được bao gồm ngầm trong verify.

Checklist thủ công cho môi trường test có dữ liệu được cấp phép (chưa thao tác bằng trình duyệt trong phiên này):

1. Dùng năm vai trò kiểm tra matrix; truy cập URL trực tiếp, không dựa vào ẩn menu. Sai branch/assignment/owner phải bị chặn.
2. Login/logout có CSRF; thử sai password/tài khoản không tồn tại/khóa/unverified, lỗi generic; không hash trong HTML/JSON.
3. Admin khóa User đang mở tab, request nghiệp vụ sau bị chặn; unlock không thay role/phí/history. Kiểm tra audit actor/target.
4. Grant role có chủ đích, session cũ chưa tự tăng quyền; login mới áp dụng. Revoke làm request nghiệp vụ cũ bị từ chối.
5. Thử role form field lạ, actor/branch/owner giả, invalid IDs/enums/date/page và GET mutation; DB/audit không thay đổi trái phép.
6. User A tạo shipment/ticket, Manager reply PUBLIC + note INTERNAL; User B không xem/ghi/đánh dấu notice của A.
7. Logistics forward/failed/return đối chiếu Reports/Audit unique vs trip/attempt/receipt; GET không ghi dữ liệu.
8. CSV tiếng Việt/quotes/newlines/formula prefixes, đúng filename/BOM; vượt 5000 rows không nhận file thành công một phần.
9. Kiểm tra logout/lock/role revoke khi socket đang mở, origin/CONNECT CSRF, subscribe/SEND trái phép và reconnect reconciliation.
10. Review các giới hạn/SQL Server ở trên trước khi quyết định production readiness.

## Danh sách file

**15 file thay đổi: 11 file mới/untracked, 4 file tracked sửa, 0 file xóa.** Toàn bộ file mới hiện vẫn untracked,
chưa stage; index rỗng. Không git add/commit/push/merge/stash, tạo PR hoặc thay đổi lịch sử Git.

11 file mới (10 Java gồm 7 lớp test và 3 lớp cấu hình/helper, cùng 1 tài liệu):

```text
docs/security-system-integration-testing.md
src/test/java/com/ute/uteexpress/operations/SystemAccountRevocationTests.java
src/test/java/com/ute/uteexpress/operations/SystemAdminConcurrencyTests.java
src/test/java/com/ute/uteexpress/operations/SystemAdminIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/SystemCrossModuleIntegrationTests.java
src/test/java/com/ute/uteexpress/operations/SystemCsvByteBoundaryTests.java
src/test/java/com/ute/uteexpress/operations/SystemRbacMvcTests.java
src/test/java/com/ute/uteexpress/operations/SystemReportSecurityTests.java
src/test/java/com/ute/uteexpress/operations/SystemSecurityIntegrationConfig.java
src/test/java/com/ute/uteexpress/operations/SystemSecuritySupport.java
src/test/java/com/ute/uteexpress/operations/SystemSecurityWebSupport.java
```

4 file sửa (1 production fix, 3 file test/fixture bổ sung dependency):

```text
src/main/java/com/ute/uteexpress/service/LogisticsAccessService.java
src/test/java/com/ute/uteexpress/delivery/DeliveryScenario.java
src/test/java/com/ute/uteexpress/security/LogisticsServiceSecurityTests.java
src/test/java/com/ute/uteexpress/service/LogisticsAccessServiceTests.java
```

`git diff --stat` chỉ liệt kê 4 tracked files (19 insertions, 3 deletions); không dùng nó để bỏ sót 11 untracked files.
Đối chiếu thêm `git status --untracked-files=all` và `git ls-files --others --exclude-standard`.
Log trong TEMP tiền tố `uteexpress-commit21-`, Surefire XML trong `target/surefire-reports`;
generated artifacts không đưa vào Git. File mới và file sửa được kiểm tra UTF-8/final newline/trailing whitespace riêng
vì `git diff --check` không bao gồm nội dung untracked.
