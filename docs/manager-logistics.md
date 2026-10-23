# Commit 10 — Manager logistics và phân công lấy hàng

**Cập nhật Commit 12:** Manager được gán đúng điểm có UI chuyến/hub và kiểm nhận từng kiện.
Xem [transport-hub.md](transport-hub.md) cho quyền nguồn/đích, V7 và route. Phạm vi nguồn Commit 10 vẫn giữ nguyên.

**Cập nhật Commit 11:** Manager có hàng đợi HOME_PICKUP, kiểm tra bằng chứng và nhận/từ chối bàn giao độc lập.
Xem [shipper-pickup.md](shipper-pickup.md). Quy tắc BRANCH_DROPOFF và phân công Commit 10 được giữ nguyên;
HOME_PICKUP tiếp nhận qua handoff V6, không qua endpoint intake trực tiếp. Phần dưới ghi nhận mốc Commit 10.

Nhánh `feature/an-manager-logistics`, phụ trách An. Trước khi triển khai, working tree sạch,
HEAD chứa merge Commit 01–09, gần nhất `fa6aec5` (Commit 09).
Không git add/commit/push/merge/PR, không kết nối hoặc thay đổi database thật khi viết code/test.

## Phạm vi và routes

Module này quản lý **yêu cầu ở chi nhánh nguồn** trong ManagerBranch đang ACTIVE.
Manager được gán nhiều chi nhánh có thể chọn từng chi nhánh hoặc tất cả chi nhánh được phân quyền.
Quyền quản lý hub/chi nhánh đích không tự cấp quyền sửa yêu cầu của chi nhánh nguồn khác.
Mạng lưới đích, chuyến gom và bàn giao trung chuyển sẽ có nghiệp vụ riêng ở Commit 12.

| Method | Route | Chức năng |
|---|---|---|
| GET | `/manager` | Dashboard đếm theo phạm vi; chọn branchId tùy chọn. |
| GET | `/manager/shipments` | Danh sách theo branchId/status/method/q, page/size. |
| GET | `/manager/shipments/{id}` | Chi tiết, timeline, lịch sử phân công, biên nhận và Shipper hợp lệ. |
| POST | `/manager/shipments/{id}/confirm` | Xác nhận yêu cầu CREATED → CONFIRMED. |
| POST | `/manager/shipments/{id}/cancel` | Hủy/từ chối CREATED/CONFIRMED, bắt buộc lý do, không có nhiệm vụ hoạt động. |
| POST | `/manager/shipments/{id}/pickup-assignments` | Phân công hoặc phân công lại HOME_PICKUP. |
| POST | `/manager/shipments/{id}/intake` | Xác nhận thực nhận BRANCH_DROPOFF ở nguồn. |

Danh sách phân trang tại database, size 1–100, mặc định 20; sort cố định createdAt DESC, id DESC.
Timeline, lịch sử phân công và danh sách Shipper có phân trang độc lập, 20 dòng/trang.
Không tải tất cả Shipment/Assignment/Employee lên bộ nhớ để lọc. Dashboard dùng count có scope.
Truy vấn Shipment luôn AND điều kiện EXISTS ManagerBranch theo actor hiện tại và trạng thái branch ACTIVE.
Mã vận đơn tìm kiếm giới hạn 50 ký tự, escape wildcard SQL Server.
DTO Manager riêng chứa dữ liệu cần cho vận hành; không trả User/Employee Entity hoặc thông tin xác thực ra HTML.

## Phân quyền và định danh nhân viên

SecurityConfig, Controller và service đều yêu cầu ROLE_MANAGER. ADMIN, USER hoặc SHIPPER riêng lẻ không kế thừa quyền này.
LogisticsAccessService đọc actor qua SecurityContext/email, kiểm tra lại User ACTIVE và ROLE_MANAGER trong database.
Không nhận managerId, actorId, role hoặc chi nhánh thao tác từ body. Chi nhánh thao tác lấy từ Shipment đã khóa.
branchId trên bộ lọc chỉ hợp lệ khi actor được gán đúng chi nhánh ACTIVE đó.
Chi tiết không tồn tại hoặc ngoài scope trả 404; ghi ngoài scope bị chặn 403 trước khi thay đổi dữ liệu.

Shipper được phân công phải là Employee ACTIVE, COURIER_DRIVER, có User liên kết ACTIVE,
có ROLE_SHIPPER thực tế trong database và thuộc đúng chi nhánh nguồn ACTIVE. Query danh sách và guard khi ghi
đều kiểm tra điều kiện; không tin kết quả danh sách cũ hoặc ID Employee do client gửi.

Tiếp nhận vật lý dùng cơ chế an toàn trên mô hình hiện có: actor phải là MANAGER của chi nhánh nguồn,
đồng thời liên kết Employee ACTIVE của chính tài khoản đó tại cùng chi nhánh,
với vị trí DISPATCHER hoặc WAREHOUSE_STAFF. COURIER_DRIVER không thể tự nhận kiện vào chi nhánh.
Manager không có hồ sơ nhân viên hợp lệ vẫn được xem/xác nhận/phân công theo scope, nhưng không được tiếp nhận.
Không tạo role mới, tự liên kết nhân viên theo tên/điện thoại hoặc cấp MANAGER cho Shipper.

Các form ghi có CSRF và allowlist riêng, luôn kèm expectedVersion.
MVC đưa URI variable `id` vào binder nên `id` được cho qua nhưng không tồn tại trong bất cứ form nào;
đích thao tác luôn là @PathVariable đã kiểm tra, cả khi client gửi `id=999` trong form.
branchId/shipmentId/assignmentId/actorId/role/status tùy ý bị từ chối 400.
replacesAssignmentId chỉ được dùng để đối chiếu nhiệm vụ PICKUP hoạt động hiện tại của đúng Shipment.
HTML dùng th:text, không render ghi chú thành HTML. 401/403 dùng handler HTML/JSON hiện có.

## Quy tắc lifecycle

Tất cả ghi trạng thái, phân công và tiếp nhận nằm trong ShipmentLifecycleService; Controller chỉ tạo command.
ShipmentTransitionPolicy có các hàm điều kiện riêng cho phân công và tiếp nhận trực tiếp;
generic transition không thể nhảy sang PICKUP_ASSIGNED/AT_ORIGIN_BRANCH để bỏ qua kiểm tra nghiệp vụ.

- Xác nhận chỉ CREATED → CONFIRMED, không tạo biên nhận hoặc đặt current_branch_id.
- Hủy/từ chối chỉ CREATED/CONFIRMED, bắt buộc lý do tối đa 500 ký tự; từ chối nếu còn phân công hoạt động.
  Lý do lưu tại ShipmentEvent.description. Không hủy tự động nhiệm vụ đang chạy hoặc chuyển sang nghiệp vụ hoàn tiền.
- Phân công mới chỉ HOME_PICKUP ở LOCAL_BRANCH nguồn, trạng thái CONFIRMED, chưa có PICKUP hoạt động.
  Tạo ShipmentAssignment ASSIGNED và chuyển PICKUP_ASSIGNED cùng event.
- Phân công lại chỉ PICKUP_ASSIGNED, đúng một nhiệm vụ cũ ASSIGNED chưa hoàn thành, đúng ID hiện tại,
  nhân viên mới khác người cũ và có lý do tối đa 255 ký tự. Nhiệm vụ IN_PROGRESS không được đổi người trong commit này.
  Giữ nguyên bản ghi cũ, chuyển CANCELLED + completed_at; flush bản ghi cũ trước INSERT nhiệm vụ mới.
  Ghi event cùng trạng thái trước/sau PICKUP_ASSIGNED, gồm ID nhân viên, ID nhiệm vụ bị thay và lý do.
  Trạng thái giống nhau vẫn cập nhật updated_at để @Version tăng, tránh thao tác từ form cũ.
- Tiếp nhận BRANCH_DROPOFF chỉ ở CONFIRMED và LOCAL_BRANCH nguồn. Cần nhân viên tiếp nhận hợp lệ,
  mã/tham chiếu biên nhận thực tế và checkbox khẳng định đã kiểm tra/thực nhận kiện. Không có phân công hoạt động bất thường.
  Lưu một ShipmentIntake, chuyển AT_ORIGIN_BRANCH, đặt current_branch_id là chi nhánh nguồn và ghi event cùng transaction.
  Checkbox/tham chiếu là xác nhận có trách nhiệm của nhân viên, chưa phải ảnh/chữ ký hoặc chứng cứ số độc lập.
- HOME_PICKUP bị chặn tiếp nhận qua endpoint này ở mọi trạng thái, kể cả dữ liệu PICKED_UP cũ:
  chưa có bằng chứng lấy hàng của Commit 11 và luồng bàn giao riêng. Không suy diễn từ role, phân công hoặc status đơn lẻ.

Đơn cũ có method null tiếp tục được xem, xác nhận logic hoặc hủy hợp lệ, nhưng không được giả định là dropoff/home pickup
để phân công/nhận vật lý. Không tính lại phí hoặc thay snapshot báo giá. Public Tracking giữ nguyên projection và whitelist event;
các event Manager mới và lý do nội bộ không được đưa vào timeline công khai.

## V5, khóa và tương thích

`V5__add_pickup_assignment_guard_and_origin_intakes.sql` không sửa V1–V4 và không backfill lịch sử:

- Kiểm tra trước phân công PICKUP ASSIGNED/IN_PROGRESS bị trùng theo Shipment. Nếu phát hiện, THROW để người vận hành
  xem xét nghiệp vụ; không âm thầm hủy/đổi nhiệm vụ hoặc sửa dữ liệu cũ cho migration chạy qua.
- `UX_assignments_active_pickup`: filtered unique index trên shipment_id khi assignment_type=PICKUP,
  status thuộc ASSIGNED/IN_PROGRESS. Cho phép giữ nhiều nhiệm vụ đã đóng; không áp đặt quy tắc này lên DELIVERY/TRANSFER.
- `shipment_intakes`: id IDENTITY, Shipment/Branch/User nhận/Employee nhận, phương thức,
  pickup_assignment_id nullable, tham chiếu NVARCHAR(100), notes NVARCHAR(500), received_at UTC DATETIME2(7).
  FK NO ACTION, unique shipment_id, CHECK phương thức và nguồn pickup, index branch/received_at.
  Đây là biên nhận **nguồn**, không dùng để ghi nhiều lượt nhận ở hub/đích.
- Cột pickup_assignment_id chuẩn bị cho HOME_PICKUP: CHECK yêu cầu có nhiệm vụ tham chiếu khi HOME_PICKUP,
  và phải null khi BRANCH_DROPOFF. Điều kiện nhiệm vụ hoàn thành, cùng Shipment, actor độc lập và bằng chứng lấy hàng
  cần được service Commit 11 xác minh; FK/CHECK hiện tại không thay được kiểm tra quan hệ giữa nhiều dòng.
  Commit 10 không có đường tạo biên nhận HOME_PICKUP.

Các writer nghiệp vụ dùng `ShipmentRepository.findForLifecycle` với PESSIMISTIC_WRITE trên Shipment,
rồi kiểm tra expectedVersion. @Version vẫn bảo vệ UPDATE; unique index là lớp bảo vệ database cuối cùng.
Thứ tự khóa chung: Shipment trước, sau đó phân công/biên nhận. Commit 11–13 phải tuân thủ cùng quy ước.
Không dùng bulk update status bỏ qua lifecycle hoặc cập nhật nhiệm vụ ngoài transaction đã khóa Shipment.
Lưu event thất bại làm rollback toàn bộ. Không tự retry lệnh cũ khi xung đột; yêu cầu người dùng xem dữ liệu mới.
Biên nhận lặp bị từ chối qua state/version và unique constraint, không tạo event tiếp nhận thứ hai.

V5 không chạy trong công việc này. Khi ứng dụng được khởi động có chủ đích với Flyway enabled,
V5 sẽ được áp dụng theo cấu hình hiện có; cần duyệt trên database thử nghiệm được phép trước.
Không tự chạy ứng dụng/migration để kiểm tra trên database chứa dữ liệu thật.

## Kiểm thử và kiểm tra thủ công

Test bổ sung gồm unit lifecycle/guard/scope, MVC/filter/CSRF/IDOR, template thật và luồng Controller–service–guard thật.
CoreDatabaseMapping vẫn kiểm tra schema V1–V5 với Hibernate 7: 22 Entity/Repository, 48 quan hệ LAZY,
16 unique constraint; ba filtered unique index được kiểm tra riêng từ migration (không biểu diễn bằng JPA @Index portable).
Không vô hiệu hóa mapping test, không thực thi DDL/DML trong test mapping.

Concurrency test có hai thread, mô hình khóa parent tại repository boundary và transaction interceptor thật.
Rollback test xác minh quyết định rollback của Spring transaction advice; không chứng minh rollback JDBC hoặc lock/index
trên SQL Server thật. MVC flow dùng repository mock, không phải integration test SQL Server.
SQL Server opt-in chưa bật; chưa thực thi V5, Flyway/schema integration hoặc ddl-auto=validate trên SQL Server thật.

Kết quả kiểm tra cuối: `mvnw.cmd clean test` BUILD SUCCESS — 412 test được liệt kê,
411 đạt, 0 failure, 0 error, 1 SQL Server opt-in bị bỏ qua. `mvnw.cmd -DskipTests compile`
BUILD SUCCESS; `git diff --check` không có lỗi. Tổng cộng 19 file mới và 17 file sửa, không stage/commit.
Cảnh báo còn lại: metadata Maven repository Flyway community trả HTTP 401, Mockito/Byte Buddy tự nạp
Java agent và Hibernate nhắc dialect tường minh trong test offline; không làm build thất bại.

Sau khi V5 được duyệt/áp dụng trong môi trường kiểm thử và đã có dữ liệu thật hợp lệ:
đăng nhập MANAGER → Tài khoản → Vận hành chi nhánh → chọn chi nhánh → lọc/xem vận đơn.
Với HOME_PICKUP: xác nhận yêu cầu, chọn Shipper, phân công lại nhiệm vụ còn chờ với lý do; kiểm tra lịch sử.
Với BRANCH_DROPOFF: xác nhận logic trước; chỉ ghi tiếp nhận khi thực nhận kiện, có hồ sơ nhân viên và tham chiếu biên nhận.
Thử URL chi nhánh khác, Employee khác chi nhánh, ID phân công khác, session bị thu hồi role, thiếu CSRF và form cũ.
Không tạo tài khoản/chi nhánh/nhân viên hoặc dữ liệu giả trong database để vượt điều kiện.

## Bàn giao

- Commit 11: UI Shipper và nghiệp vụ bắt đầu/lấy hàng thực tế, chứng cứ pickup, trạng thái nhiệm vụ và bàn giao
  độc lập cho nhân viên nguồn. Mở HOME_PICKUP intake bằng điều kiện bằng chứng đầy đủ, không chỉ status PICKED_UP.
  Schema còn thiếu bản ghi bằng chứng pickup gắn assignment/shipment, người thực hiện, picked_up_at và tham chiếu ảnh/chữ ký
  theo quy tắc được duyệt; V5 chỉ có liên kết nhiệm vụ trên biên nhận nguồn, chưa có các chứng cứ này.
- Commit 12: chuyến gom, hub/trung chuyển, quan hệ kiện–chuyến và custody/handoff nhiều chặng,
  tiếp nhận hai đầu với lịch sử riêng; không tái dùng unique biên nhận nguồn để ghi mọi chặng.
  Cần migration riêng cho transport_trips, quan hệ trip–shipment và các lần bàn giao/tiếp nhận có hai đầu,
  actor, thời điểm và bằng chứng. Không có bảng chuyến hoặc handoff nhiều chặng nào được tạo trong V5.
- Commit 13: giao cuối, thử giao, bằng chứng nhận, giao thất bại/hoàn hàng và nhiệm vụ DELIVERY.

Chưa có UI quản trị liên kết Employee–User/ManagerBranch, phục hồi phân công dữ liệu cũ không nhất quán,
đổi Shipper khi nhiệm vụ đã bắt đầu, hủy sau phân công, hoàn tiền hoặc thông báo realtime.
