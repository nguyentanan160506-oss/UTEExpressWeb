# Commit 09 — Hai phương thức gửi và báo giá

**Cập nhật Commit 14:** Catalog/giá đã có quản trị ADMIN, phiên bản DRAFT/PUBLISHED, lịch ngừng có audit,
Guest estimate và mô phỏng nháp; xem [service-pricing-management.md](service-pricing-management.md).
QuoteService vẫn tính phí chung, quote User giữ snapshot tới expiry riêng. Phần dưới là mốc lịch sử Commit 09.

Triển khai trên `feature/tan-shipment-pricing`, phụ trách Tân.
Không chạy migration hoặc kết nối SQL Server thật trong quá trình viết code/test.
V1–V3, cấu hình kết nối và dependency được giữ nguyên.

## Luồng trình duyệt và endpoint

Các endpoint đều yêu cầu USER đã đăng nhập; ADMIN/MANAGER/SHIPPER không tự kế thừa USER.
Service còn đọc lại tài khoản ACTIVE và ROLE_USER hiện có trong database để chống session cũ giữ quyền.

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/user/shipments/create` | Nhập thông tin và chọn phương thức, chưa có lựa chọn mặc định. |
| POST | `/user/shipments/branches` | Tìm chi nhánh theo tỉnh/huyện/xã hai đầu và phương thức; giữ dữ liệu đã nhập. |
| POST | `/user/shipments/quotes` | Kiểm tra đầy đủ form, mạng lưới, dịch vụ, bảng giá; lưu báo giá và redirect tới trang xem. |
| GET | `/user/shipments/quotes/{token}` | Trang báo giá riêng của chủ sở hữu, `Cache-Control: no-store`. |
| POST | `/user/shipments` | Chỉ nhận `quoteToken` và CSRF; xác nhận hoặc mở vận đơn đã tạo từ báo giá. |
| GET | `/user/shipments`, `/user/shipments/{id}` | Lịch sử/chi tiết/timeline giữ phạm vi USER như trước. |

HOME_PICKUP xác định chi nhánh nguồn từ `branch_service_areas` ACTIVE theo địa chỉ gửi và phương thức.
Nếu có duy nhất một chi nhánh thì có thể để trống lựa chọn nguồn; nếu nhiều chi nhánh, cần chọn một trong danh sách.
Không tùy ý chọn chi nhánh đầu tiên, suy ra gần nhất hoặc bỏ qua phạm vi phục vụ.
BRANCH_DROPOFF bắt buộc người gửi chọn nguồn trong vùng phục vụ BRANCH_DROPOFF và tự mang hàng đến.
Cả hai phương thức cần chi nhánh đích phục vụ giao đến địa chỉ người nhận, LOCAL_BRANCH ACTIVE và hub CENTRAL_HUB ACTIVE.
Hub được kiểm tra và snapshot tại thời điểm báo giá; thay đổi hub trước khi xác nhận yêu cầu báo giá lại.
Việc chọn tuyến chưa có nghĩa là kiện đã được tiếp nhận; `current_branch_id` vẫn null.

Địa chỉ và phương thức thay đổi cần bấm tìm lại chi nhánh. Danh sách chưa được tải sẽ trống;
người dùng không cần nhập xong trọng lượng/COD để tìm chi nhánh. Server luôn kiểm tra lại khi báo giá và xác nhận.
Địa giới dùng tên tỉnh/huyện/xã chuẩn đang có trong dữ liệu, strip và so khớp không phân biệt hoa thường.
Chưa tự chuẩn hóa tên đồng nghĩa hoặc suy diễn địa giới/địa lý.

## Migration V4

`V4__add_pickup_pricing_and_shipment_quotes.sql` thêm:

- `pickup_fee_rules`: branch/service, amount DECIMAL(18,2) dương, ACTIVE, khoảng hiệu lực `[from,to)`,
  thời gian UTC. FK NO ACTION, unique branch/service/effective_from và index tìm giá hiệu lực.
  Một phụ phí áp dụng cho vùng lấy hàng hợp lệ của chi nhánh đó, không chia tiếp theo xã hoặc trọng lượng.
  Khoảng thời gian chồng nhau được service từ chối nếu có nhiều hơn một quy tắc hiệu lực.
- `shipment_quotes`: token ngẫu nhiên 32 ký tự hex có unique constraint, requester, phương thức,
  snapshot toàn bộ địa chỉ/người liên hệ/hàng hóa/COD/kích thước/ghi chú/bên trả phí, chi nhánh/hub,
  dịch vụ và nguồn bảng giá, các thành phần phí, created/expires/consumed_at.
  FK NO ACTION, CHECK phí/phương thức/khoảng hạn, index chủ sở hữu và expiry.
  Mapping snapshot `updatable=false`, chỉ consumed_at được thay đổi sau khi phát hành.
- `shipments.quote_id` nullable, FK NO ACTION, filtered unique index cho giá trị khác null.
  `pickup_fee DECIMAL(18,2)` nullable; CHECK tổng thành phần cho đơn có quote và CHECK phí không âm.
  Đơn cũ giữ null, không bị cập nhật phương thức, tổng tiền hoặc phụ phí.
- `branch_service_areas.supports_delivery BIT NOT NULL DEFAULT(0)`: phạm vi giao đến phải được bật rõ ràng.
  shipment_method hiện có mô tả dịch vụ ở đầu gửi, không thể tự coi HOME_PICKUP/BRANCH_DROPOFF là giao cuối.
  Cờ này cho phép tái sử dụng vùng phục vụ mà không thêm phương thức gửi giả DELIVERY hoặc bảng kho trùng lặp.
  Truy vấn giao đến không dựa vào shipment_method, chỉ dựa vào supports_delivery, địa chỉ và tính hợp lệ của mạng lưới.

Không seed giá/địa chỉ/chi nhánh, không tạo giá 0 để che thiếu bảng giá. Không UPDATE vận đơn cũ.
FK/unique/index/CHECK và kiểu SQL được đối chiếu bằng CoreDatabaseMapping với Hibernate thực, không thực thi SQL.
Filtered unique index không biểu diễn bằng @UniqueConstraint nullable (SQL Server sẽ giới hạn nhiều NULL);
định nghĩa SQL này được kiểm tra riêng trong test migration.

## Một nơi tính phí

Logic tính vận chuyển được chuyển từ UserShipmentService sang QuoteService, không giữ đường tính giá/tạo trực tiếp song song.
UserShipmentService tiếp tục phục vụ catalog, lịch sử và chi tiết. ShipmentQuoteInputs sao chép tường minh các field đầu vào,
không bind Entity hoặc số tiền từ client.

```
scope = INTRA_PROVINCE nếu cùng tỉnh; khác tỉnh dùng INTER_PROVINCE
extra = max(0, weight - base_weight)
steps = ceil(extra / extra_weight_step)
shipping_fee = base_price + steps * extra_price_per_step
pickup_fee = giá pickup_fee_rules đang hiệu lực nếu HOME_PICKUP
pickup_fee = 0 nếu BRANCH_DROPOFF (không cung cấp dịch vụ lấy hàng)
total_fee = shipping_fee + pickup_fee + insurance_fee
```

Chỉ dùng đúng một shipping_rate_rule ACTIVE phù hợp service và scope, không dùng INTER_REGION.
HOME_PICKUP còn cần đúng một pickup_fee_rule ACTIVE theo branch/service. Thiếu, hết hạn, chồng lấn,
giá trị không hợp lệ hoặc vượt DECIMAL(18,2) đều bị từ chối trước khi lưu báo giá/vận đơn.
Tính BigDecimal, không dùng double. Không có bảng giá hay sản phẩm bảo hiểm trong dự án nên chưa bán bảo hiểm;
insurance_fee được snapshot 0 và giao diện thông báo chưa áp dụng, không ngầm bảo hiểm hàng giá trị khai báo.
COD/giá trị khai báo không cộng vào phí và không tạo thanh toán trong commit này.

## Hiệu lực, snapshot và chống trùng

Báo giá thuộc requester được suy ra từ SecurityContext, không nhận userId từ client.
Token UUID v4 dạng hex chỉ là định danh; mọi đọc/xác nhận còn lọc owner trong query.
Không tồn tại và không thuộc chủ sở hữu đều trả 404. Token sai định dạng trả 400 cho USER.
Form có CSRF và allowlist riêng: sửa tiền, owner, branch hay hạn trong POST xác nhận bị từ chối 400,
không có cách tạo bằng form cũ bỏ qua bước báo giá.

Thời hạn mặc định 15 phút, có thể cấu hình `uteexpress.quotes.validity` (Duration, mặc định PT15M, tối đa 24 giờ).
Không đổi application.properties. Hạn thực tế lấy nhỏ nhất của TTL và effective_to của hai bảng giá.
Thời gian UTC DATETIME2(7), chuẩn 100 ns; đúng thời điểm hết hạn cũng bị từ chối.
Giá đã báo được giữ nguyên tới hạn, kể cả bảng giá được sửa sau đó; không tính lại âm thầm khi xác nhận.
Service, phạm vi phục vụ và hub vẫn phải đủ điều kiện tại xác nhận. Thay đổi tuyến/địa chỉ cần báo giá mới.

Xác nhận dùng PESSIMISTIC_WRITE trên dòng quote theo token + owner, sau đó kiểm tra consumed_at/hạn,
kiểm tra lại mạng lưới/service, lưu Shipment từ snapshot, lưu event CREATED và cập nhật consumed_at trong cùng transaction.
Mỗi quote chỉ được dùng tạo một Shipment; filtered unique index là lớp bảo vệ database cuối cùng.
Gửi lại cùng token đã dùng (kể cả sau expiry) trả về đúng vận đơn cũ của owner; không tạo event/Shipment thứ hai.
Nếu event hoặc persistence thất bại thì transaction rollback; retry cùng token sau lỗi không chắc kết quả.
Hai quote khác nhau được coi là hai yêu cầu độc lập; chưa chống người dùng chủ động lấy hai báo giá rồi xác nhận cả hai.

Giá snapshot trên Shipment gồm shipping_fee, pickup_fee, insurance_fee, total_fee;
quote_id truy vết dữ liệu đầu vào và bảng giá nguồn. Trang chi tiết đơn cũ hiển thị phương thức/phụ phí chưa xác định,
không suy diễn BRANCH_DROPOFF hoặc tính lại tổng cũ. Public tracking không dùng DTO quote hoặc mở event mới.
Không thay đổi bất kỳ transition vận chuyển vật lý nào.

## Kiểm thử và sử dụng

Các test dùng dữ liệu fixture cô lập, không seed database. Coverage gồm báo giá hai phương thức,
phí thiếu/trùng, hạn, owner, chỉnh tiền, retry/concurrency, snapshot, hub/chi nhánh, validation, CSRF,
method security và luồng Controller–service–Thymeleaf thật; giữ regression login/OTP/Admin/tracking/logistics.
CoreDatabaseMapping tiếp tục kiểm tra schema tích lũy V1–V4 với 21 Entity/Repository, 43 quan hệ LAZY,
15 unique constraint và kiểm tra riêng filtered indexes; không tắt kiểm thử mapping.
Test hai thread dùng mô hình khóa dòng tại repository boundary và Spring transaction advice thật.
Test rollback kiểm tra quyết định rollback của interceptor, không chứng minh rollback JDBC hoặc khóa trên SQL Server thật.
SQL Server opt-in không được bật. Chưa thực thi V4 hay Hibernate ddl-auto=validate trên SQL Server thật.

Kết quả kiểm tra cuối: `mvnw.cmd clean test` BUILD SUCCESS, 347 test được liệt kê:
346 đạt, 0 failure, 0 error, 1 SQL Server opt-in bị bỏ qua. `mvnw.cmd -DskipTests compile`
BUILD SUCCESS; `git diff --check` không có lỗi. Không có file được stage hoặc commit.
Cảnh báo còn lại: metadata repository Flyway community trả HTTP 401, Mockito/Byte Buddy tự nạp
Java agent và thông báo Hibernate dialect tường minh trong test offline; chúng không làm build thất bại.

Trước kiểm tra trình duyệt thật, cần duyệt/áp dụng V4 trên database kiểm thử được phép và cấu hình dữ liệu thật:
USER ACTIVE, dịch vụ ACTIVE, vùng phục vụ nguồn theo phương thức, vùng đích supports_delivery=true,
chi nhánh LOCAL_BRANCH ACTIVE có hub CENTRAL_HUB ACTIVE, đúng một shipping_rate_rule và (HOME_PICKUP) pickup_fee_rule.
V4 không tự bổ sung các dữ liệu nghiệp vụ này; thiếu cấu hình phải thấy lỗi rõ ràng, không có giá dự phòng.
Không khởi động ứng dụng với Flyway enabled ngoài chủ đích áp dụng migration đã được duyệt.

Đăng nhập USER → `/user/shipments/create` → chọn phương thức, nhập địa chỉ → tìm chi nhánh → hoàn tất form
→ xem báo giá và hạn → xác nhận → kiểm tra chi tiết/lịch sử/timeline. Thử cả hai phương thức, hết hạn,
đổi bảng giá giữa hai bước và gửi lại cùng xác nhận để kiểm tra snapshot/idempotency.

Chưa có UI quản trị bảng phụ phí/vùng giao đến, chuẩn địa giới, sản phẩm bảo hiểm, quy đổi thể tích,
retention/dọn quote hết hạn hoặc rate limiting báo giá. Không triển khai phân công, nhận hàng, bàn giao,
transport trip, giao cuối hoặc WebSocket; tiếp tục ở Commit 10–13 và các commit sau.
