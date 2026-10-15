# Commit 06 — Vận đơn cho USER (Tân)

## Phạm vi

Tạo vận đơn, lịch sử riêng của người dùng và chi tiết/theo dõi bằng Thymeleaf,
Bootstrap cục bộ, Spring Security và các Entity/Repository hiện có.
Không thay đổi schema, dependency hoặc cấu hình kết nối. Không cần migration mới.
Không tự kết nối SQL Server hoặc gửi dữ liệu vào database thật khi triển khai.

## Endpoint và quyền

| Method | Endpoint | Ý nghĩa |
| --- | --- | --- |
| GET | /user/shipments/create | Biểu mẫu tạo vận đơn |
| POST | /user/shipments | Tạo vận đơn, CSRF bắt buộc |
| GET | /user/shipments | Lịch sử, tìm theo mã, lọc trạng thái, phân trang |
| GET | /user/shipments/{id} | Chi tiết và timeline có phân trang |

Các route yêu cầu ROLE_USER ở filter, Controller và service. Tài khoản có nhiều
role được sử dụng nếu có ROLE_USER; ADMIN hoặc MANAGER riêng lẻ không được tự
suy diễn thành USER. Service lấy người dùng qua email trong SecurityContext,
kiểm tra lại trạng thái ACTIVE và quyền ROLE_USER hiện tại trong database.
Không có ownerId/userId, trạng thái xử lý, phí hoặc mã vận đơn trong DTO tạo.
Binder chỉ cho phép các trường form cụ thể, từ chối trường ngoài danh sách.

sender_id là chủ sở hữu, không suy ra từ tên/số điện thoại người gửi do khách
nhập. Truy vấn lịch sử luôn AND sender.id với bộ lọc; truy vấn chi tiết và sự
kiện đều kèm senderId. Vận đơn không tồn tại và không thuộc sở hữu cùng trả
404, không đọc timeline của người khác. DTO hiển thị không trả về User,
password hash, OTP hoặc dữ liệu nhân viên/actor. Nội dung dùng th:text để escape.

## Quy tắc tạo

Người dùng nhập thông tin người gửi/nhận, mô tả, trọng lượng, kích thước tùy
chọn, COD, giá trị khai báo, bên trả phí và ghi chú. Áp dụng các giới hạn
NVARCHAR/VARCHAR và DECIMAL trong V1, số điện thoại theo quy ước đăng ký hiện có.
Trường text được strip; tiền và kích thước không được vượt precision/scale.

origin_branch_id, destination_branch_id và service_id là bắt buộc trong schema.
Form cho chọn dữ liệu hiện có; chi nhánh phải tồn tại, ACTIVE và thuộc tỉnh/thành
được nhập, dịch vụ phải tồn tại và is_active=true. Chưa có danh mục địa giới
hoặc bộ máy điều phối nên so khớp tỉnh bằng tên sau strip, không phân biệt hoa
thường; không tự đoán tên đồng nghĩa, phân vùng hoặc tự tạo chi nhánh.
Chọn chi nhánh ở đây ghi nhận yêu cầu gửi/nhận, không tạo phân công vận chuyển.

Theo quy ước được xác nhận cho Commit 06:

- Cùng tên tỉnh/thành: INTRA_PROVINCE.
- Khác tỉnh/thành: INTER_PROVINCE.
- Không sử dụng INTER_REGION khi chưa có dữ liệu phân vùng.
- Chỉ dùng bảng giá thuộc dịch vụ, is_active=true, effective_from <= UTC hiện tại,
  và effective_to null hoặc lớn hơn UTC hiện tại; khoảng thời gian [from, to).
- Không có bảng giá phù hợp: hiển thị lỗi tại dịch vụ, không lưu vận đơn.
- Nhiều bảng giá cùng hiệu lực: báo cấu hình chồng lấn, không tùy ý chọn giá.

Phí lấy trực tiếp từ bảng giá:

    extra = max(0, weight_kg - base_weight_kg)
    steps = ceil(extra / extra_weight_step_kg)
    shipping_fee = base_price + steps * extra_price_per_step

Giá trị được tính bằng BigDecimal, kiểm tra giới hạn DECIMAL(18,2) trước khi lưu.
Chưa có sản phẩm/chính sách bảo hiểm trong flow này nên giữ insurance_fee=0
theo DEFAULT hiện có; total_fee là shipping_fee + insurance_fee. COD và giá trị
khai báo chỉ được ghi nhận, không cộng vào phí và không tạo thanh toán/đối soát.
Không hardcode giá vận chuyển hoặc tạo bảng giá mẫu.

Mã UTE + UUID v4 dạng hex viết hoa (35 ký tự) được sinh trên server.
UQ_shipments_tracking trong V1 là bảo vệ cuối cùng chống trùng, kể cả request
đồng thời. Nếu insert thất bại (bao gồm xung đột mã), rollback toàn bộ transaction
và hiển thị lỗi chung, người dùng có thể thử lại. Không retry trong transaction
đã bị đánh dấu rollback. Không bảo đảm idempotency cho hai lần gửi form hợp lệ.

Trạng thái CREATED, payment_status UNPAID và bộ đếm giao hàng dùng quy ước/schema
hiện có. current_branch_id để null vì chưa có sự kiện nhận hàng tại chi nhánh.
Cùng transaction lưu vận đơn và ShipmentEvent CREATED thực tế (from_status null,
actor là người đăng nhập, thời gian UTC). Nếu lưu sự kiện thất bại, transaction
rollback; không hiển thị thành công. Không có endpoint cập nhật trạng thái.

## Lịch sử và timeline

Lịch sử nhận q tối đa 50 ký tự, status thuộc ShipmentStatus, page bắt đầu 0,
size 1–100 (mặc định 20). Sắp xếp createdAt DESC, id DESC và escape wildcard
SQL Server trong tìm kiếm mã. Liên kết phân trang giữ nguyên bộ lọc.

Chi tiết dùng EntityGraph cho các quan hệ cần hiển thị, chuyển sang DTO trong
transaction. Timeline truy vấn bản ghi thật từ shipment_events, phân trang 20
sự kiện, occurredAt DESC và id DESC; tham số eventPage bắt đầu 0. Vận đơn cũ
không có sự kiện được thông báo rõ, không dựng mốc giả. Mọi thời gian hiển thị UTC.

## Kiểm thử và giới hạn

Test bao gồm service, Bean Validation, Controller/Security, method security và
luồng Controller–service–template thật với các Repository mock. Test rollback
kiểm tra transaction interceptor, không chứng minh rollback JDBC trên SQL Server.
Test mapping/query hiện có khởi tạo Repository với Hibernate mà không truy cập JDBC.

Chưa kiểm thử tích hợp SQL Server thật, lock/UNIQUE thật, dữ liệu bảng giá thực tế,
hoặc trình duyệt với tài khoản thật. Không chạy migration tự động để kiểm thử.
Các tài khoản, địa chỉ và giá tiền trong test là fixture cô lập, không seed database.
Test SQL Server opt-in của các commit trước giữ nguyên.

Chưa có INTER_REGION, quy đổi trọng lượng thể tích, chính sách bảo hiểm, báo giá
động trước khi submit, idempotency, điều phối, gán nhân viên hoặc nghiệp vụ thanh
toán. Thông tin phí chính xác hiển thị sau khi tạo thành công theo bảng giá tại
thời điểm đó. Các bước này cần nghiệp vụ và phạm vi commit tiếp theo.

## Chạy và kiểm tra thủ công

Database hiện có cần ACTIVE USER có ROLE_USER, chi nhánh ACTIVE tại tỉnh gửi/nhận,
dịch vụ hoạt động và đúng một bảng giá hợp lệ cho dịch vụ/phạm vi được chọn.
V1/V2 chỉ seed role nên dữ liệu chi nhánh/dịch vụ/bảng giá có thể chưa có; khi
thiếu dữ liệu, giao diện báo rõ. Commit này không bổ sung seed để vượt qua điều kiện.

Giữ cấu hình kết nối và Hibernate validate hiện tại; từ PowerShell chạy:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.sql.init.mode=never"

Sau khi ứng dụng khởi động, mở /login, đăng nhập bằng USER đã xác minh, chọn
Vận đơn của tôi ở /account, rồi tạo vận đơn. Kiểm tra danh sách, lọc, phân trang,
chi tiết và sự kiện CREATED. Thử tỉnh cùng/khác với các bảng giá đã được cấu hình.
Thử thiếu bảng giá/chi nhánh không hợp lệ phải không tạo vận đơn. Dùng tài khoản
USER thứ hai để kiểm tra URL chi tiết của tài khoản thứ nhất trả 404. Người chưa
đăng nhập nhận 401; role khác thiếu ROLE_USER nhận 403 theo handler Auth hiện có.
POST trên giao diện này có ghi dữ liệu; chỉ thực hiện với database kiểm thử được phép.

Kiểm tra source:

    .\mvnw.cmd clean test
    .\mvnw.cmd -DskipTests compile
    git diff --check
