# Commit 08 — Nền tảng logistics

**Cập nhật Commit 13:** Giao cuối, giao lại, thu hồi và hoàn có bằng chứng qua V8.
Xem [last-mile-delivery.md](last-mile-delivery.md); không dùng generic transition để bỏ qua các bước thực nhận.

**Cập nhật Commit 12:** Chuyến, hub và kiểm nhận từng chặng đã có lifecycle tập trung và V7.
Xem [transport-hub.md](transport-hub.md); không mở generic transition để bỏ qua bằng chứng vận chuyển.

**Cập nhật Commit 11:** HOME_PICKUP có bằng chứng từng lần lấy hàng và bàn giao nguồn độc lập.
Xem [shipper-pickup.md](shipper-pickup.md) cho V6, route và quyền hiện hành. Phần dưới ghi nhận mốc Commit 08.

**Cập nhật Commit 10:** Các thao tác phân công PICKUP, phân công lại nhiệm vụ còn chờ và tiếp nhận
BRANCH_DROPOFF có guard/biên nhận riêng đã được mở trong lifecycle. Generic transition vẫn không được
bỏ qua các điều kiện này. Xem [manager-logistics.md](manager-logistics.md) cho V5, khóa parent và phạm vi Manager hiện hành.

Nhánh triển khai: `feature/thuan-logistics-core`. Không có endpoint mới trong commit này.
Không chạy migration hoặc kết nối SQL Server thật khi triển khai code.

## V3 và tính tương thích

`V3__extend_shipper_identity_and_shipment_lifecycle.sql` bổ sung:

- Seed `ROLE_SHIPPER` bằng `WHERE NOT EXISTS`; unique constraint của `roles.name` vẫn được giữ.
- `employees.user_id` nullable, FK `NO ACTION`, unique index có bộ lọc `IS NOT NULL`.
  Một tài khoản chỉ liên kết một Employee; nhiều Employee cũ vẫn có thể chưa liên kết.
- `branches.parent_hub_id` nullable, self FK và CHECK không tự tham chiếu, chỉ LOCAL_BRANCH được đặt parent.
  Service kiểm tra parent thực sự là CENTRAL_HUB ACTIVE. FK/CHECK không kiểm tra được loại/trạng thái của dòng khác.
- `branch_service_areas`: chi nhánh, tỉnh, huyện, xã, phương thức, cờ hoạt động và thời gian UTC.
  Xã rỗng biểu diễn phạm vi toàn huyện. Không seed vùng phục vụ, hub hay chi nhánh giả.
- `shipments.shipment_method`, `origin_hub_id`, `destination_hub_id` nullable.
  `version BIGINT NOT NULL DEFAULT (0)` cho optimistic locking; Hibernate quản lý phiên bản sau đó.
- Hai trạng thái AT_ORIGIN_BRANCH, AT_DESTINATION_BRANCH; mở rộng CHECK của Shipment và bổ sung
  CHECK from/to status của ShipmentEvent theo đủ 15 giá trị Java.

V1/V2, cấu hình kết nối, dependency, phí và dữ liệu vận đơn cũ được giữ nguyên.
Đơn cũ và form Commit 06 tiếp tục để phương thức/hub null; không ngầm gán BRANCH_DROPOFF.
Không suy diễn liên kết nhân viên, không tạo sự kiện lịch sử cho dữ liệu cũ.
Không tự chạy V3 khi test. Sau này khi khởi động ứng dụng với Flyway enabled, V3 sẽ là migration pending
được Flyway áp dụng theo cấu hình hiện có; chỉ khởi động sau khi đã duyệt migration trên môi trường phù hợp.

Trước khi áp dụng V3 ở môi trường triển khai, cần kiểm tra dữ liệu ShipmentEvent hiện có phù hợp CHECK mới
(đặc biệt các giá trị đã được nhập ngoài ứng dụng). Migration không sửa dữ liệu sai thay người vận hành.
Các index có bộ lọc yêu cầu những tùy chọn SET của SQL Server; V3 khai báo chúng tường minh.
JPA không biểu diễn được predicate của filtered unique index; kiểm thử SQL hợp đồng riêng kiểm tra định nghĩa này.
Chưa kiểm chứng DDL, Hibernate validate hoặc hiệu lực index/constraint trên SQL Server thật.

## Vai trò và phạm vi

Đăng ký công khai chỉ tạo ROLE_USER. ADMIN có thể gán/gỡ ROLE_SHIPPER hiện có qua màn hình phân quyền,
không tự gán kèm USER, MANAGER hoặc ADMIN. Việc cấp role chưa tạo Employee hoặc phân công.
Account chỉ hiển thị nhãn Shipper; chưa có link thao tác Shipper. SecurityConfig vẫn deny các route chưa khai báo.

`LogisticsAccessService` lấy actor từ SecurityContext, đọc lại trạng thái ACTIVE và role trong database:

- MANAGER phải được gán trong ManagerBranch và chi nhánh phải ACTIVE; không có quyền toàn mạng mặc định.
- SHIPPER cần Employee ACTIVE, COURIER_DRIVER, liên kết đúng User, chi nhánh ACTIVE và đúng một phân công
  ASSIGNED/IN_PROGRESS chưa hoàn thành, khớp vận đơn, loại nhiệm vụ và nhân viên.
- PICKUP thuộc chi nhánh nguồn, chỉ hợp lệ với HOME_PICKUP; DELIVERY thuộc chi nhánh đích.
  TRANSFER bị chặn cho tới khi có chuyến/bàn giao Commit 12.
- Session cũ không giữ quyền tác nghiệp khi tài khoản bị khóa hoặc role bị thu hồi.

Guard trả về context nội bộ, không trả Entity ra trình duyệt. Guard này là điều kiện nền;
service thao tác Commit 10–13 phải gọi nó trong cùng transaction với kiểm tra giai đoạn, phân công và bằng chứng bàn giao.
Không dùng kết quả guard để cấp quyền dài hạn hoặc coi là đã triển khai nhận/giao hàng.

`BranchNetworkService` chỉ tra phạm vi phục vụ ACTIVE có chi nhánh LOCAL_BRANCH và parent CENTRAL_HUB ACTIVE.
Không tìm thấy phạm vi thì trả danh sách rỗng; hub thiếu/sai thì báo lỗi. Không đoán chi nhánh gần nhất.
Form Commit 06 chưa dùng service mới, tránh thay đổi nghiệp vụ tạo đơn trước Commit 09.

## Chuyển trạng thái tập trung

`ShipmentLifecycleService.transition(ShipmentTransitionCommand)` là điểm cập nhật trạng thái sau khi tạo đơn.
Command chỉ gồm shipmentId, expectedVersion và targetStatus. Không nhận actor, role hoặc event text từ client.

Hiện mở: CREATED → CONFIRMED; CREATED/CONFIRMED → CANCELLED, do MANAGER phụ trách chi nhánh nguồn.
Hủy bị chặn nếu còn phân công hoạt động. Các chuyển trạng thái vật lý/phân công trong graph tiềm năng
`ShipmentTransitionPolicy` đều bị chặn cho tới Commit 10–13; graph này không phải quyền thực thi.
DELIVERED, RETURNED, CANCELLED là trạng thái kết thúc trong graph hiện tại.

Service kiểm tra phạm vi, trạng thái, phiên bản, rồi cập nhật Shipment và tạo ShipmentEvent trong cùng transaction.
Event ghi actor thực tế, chi nhánh, trạng thái trước/sau và thời điểm UTC, làm tròn 100 ns theo DATETIME2(7).
Nếu lưu event thất bại thì transaction rollback. Hibernate `@Version` bảo vệ UPDATE với id và version cũ;
expectedVersion từ command còn chặn thao tác trên form đã cũ. Không tự retry một lệnh cũ khi xảy ra xung đột.
Chưa có controller nên chưa có giao diện/HTTP contract cho lỗi xung đột.

Các module mới không gọi `Shipment.setStatus`/bulk JPQL update trực tiếp, trừ khởi tạo CREATED + event ban đầu
trong UserShipmentService. Khi mở transition mới phải mở rộng service tập trung cùng điều kiện nghiệp vụ và test.
Khi thêm/thay đổi phân công, phải đồng bộ bằng khóa/phiên bản Shipment trong cùng transaction để tránh race với hủy.
Không có thao tác tạo phân công trong Commit 08.

Public tracking vẫn dùng projection riêng và whitelist tuple event CREATED như Commit 07.
Hai trạng thái chi nhánh mới có nhãn công khai nhưng không làm event nội bộ trở thành công khai.
Event do lifecycle service tạo chưa nằm trong whitelist timeline.

## Kiểm thử và giới hạn

CoreDatabaseMapping vẫn bootstrap Hibernate 7 thực, đối chiếu schema tích lũy V1 + V3:
19 Entity/Repository, 32 quan hệ LAZY, không cascade, 13 unique constraint, kiểu Unicode/MAX/VARCHAR enum,
CHECK, default UTC, khóa kép UserRole và phiên bản Shipment. Kiểm tra SQL UPDATE Hibernate sinh có điều kiện version.
Không xuất hoặc thực thi DDL/SQL trong kiểm thử mapping.

Các test bổ sung kiểm tra Employee/account/role/phân công, phạm vi MANAGER, method security qua proxy thật,
hub/vùng phục vụ, transition hợp lệ/sai/bị hoãn, dữ liệu legacy và public tracking không lộ event.
Test rollback sử dụng Spring transaction interceptor thật với transaction manager ghi nhận rollback;
test đồng thời sử dụng hai thread và mô hình compare-and-set của repository. Chúng xác minh hợp đồng service,
không chứng minh rollback/locking ở JDBC hay SQL Server thật. Cần integration test trên database thử nghiệm
đã được duyệt sau khi áp dụng V3; test SQL Server hiện có vẫn opt-in và không được bật trong công việc này.

Kết quả kiểm tra cuối của Commit 08: `mvnw.cmd clean test` BUILD SUCCESS, 308 test được liệt kê,
307 đạt, 0 failure, 0 error, 1 SQL Server opt-in bị bỏ qua. `mvnw.cmd -DskipTests compile` BUILD SUCCESS.
`git diff --check` không có lỗi. Cảnh báo còn lại: metadata repository Flyway community trả HTTP 401,
Mockito/Byte Buddy tự nạp Java agent và Hibernate nhắc dialect được chỉ định tường minh trong test offline.
Không đổi cấu hình build/dependency để xử lý các cảnh báo ngoài phạm vi này.

## Bàn giao

| Commit | Công việc tiếp theo |
|---|---|
| 09 | Form chọn phương thức, lựa chọn chi nhánh theo vùng, snapshot hub và báo giá/phụ phí database; xử lý đơn legacy chưa rõ phương thức. |
| 10 | Controller Manager trong phạm vi được gán, phân công Employee hợp lệ, chống phân công trùng và race với hủy; mở transition phù hợp kèm event. |
| 11 | UI Shipper lấy hàng, kiểm tra phân công trong transaction, chứng cứ lấy/tiếp nhận hàng; không nhận hộ thay chi nhánh. |
| 12 | Chuyến gom, hub/trung chuyển và bảng handoff/custody; phân loại, tiếp nhận hai đầu, lịch sử mọi bàn giao và transition vật lý. |
| 13 | Phân công giao cuối, giao thành công/thất bại, thử giao/hoàn hàng và bằng chứng; tích hợp lifecycle service. |
