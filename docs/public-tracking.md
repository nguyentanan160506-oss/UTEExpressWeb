# Commit 07 — Tra cứu vận đơn công khai (Tân)

## Phạm vi và endpoint

GET /tracking hiển thị form công khai. GET /tracking?trackingNumber=... tra cứu
chính xác một mã vận đơn. Không có API danh sách, tra cứu theo id hoặc wildcard.
Chỉ GET đúng /tracking được permitAll; mọi quy tắc Auth, User và Admin giữ nguyên.
Trang login/register có liên kết tới tracking; trang tracking dùng Bootstrap/CSS
cục bộ và giao diện tiếng Việt. Không cần dependency, Entity, enum hoặc migration mới.

## Hợp đồng dữ liệu công khai

PublicTrackingView chỉ chứa trackingNumber, statusLabel, updatedAt và danh sách
sự kiện gồm label/occurredAt. Không tái sử dụng ShipmentDetailView. Repository
dùng scalar projection, không tải thông tin người gửi/nhận, tiền, địa chỉ,
actor, mô tả sự kiện hoặc vị trí nội bộ. Model HTML không chứa Entity/DTO riêng tư.
Thời gian hiển thị UTC. Không cache kết quả, không gửi referrer, không index.

TrackingNumberGenerator hiện sinh UTE + 32 ký tự hex. Public service chấp nhận đúng
định dạng này (35 ký tự), chuẩn hóa hoa/thường và khoảng trắng hai đầu, giới hạn
đầu vào thô 50 ký tự. Mã dạng số/id, mã khác định dạng, wildcard và đầu vào quá dài
bị từ chối trước truy vấn. Mã lịch sử khác định dạng chưa được hỗ trợ; chỉ mở rộng
allowlist định dạng sau khi kiểm tra nguồn dữ liệu thực tế, không dùng regex quá rộng.

Trạng thái hiện tại dùng danh sách 13 ShipmentStatus đang tồn tại được cho phép
rõ ràng trong PublicTrackingEventPolicy. Trạng thái mới không tự động trở thành
công khai chỉ vì bổ sung enum/nhãn. Không có cột hạn chế công khai trong schema
hiện tại; mã chuẩn của vận đơn có trạng thái được cho phép có thể được tra cứu.
Kết quả thiếu/không được policy cho phép dùng cùng thông báo và HTTP 200.

## Sự kiện được phép công khai

V1 chưa có event_type hoặc public flag. Policy bảo thủ chỉ cho phép tuple:

    from_status = NULL
    to_status = CREATED
    event_title = "Đã tạo vận đơn" (khớp chính xác trong Java)

Đây là sự kiện user-facing hiện được UserShipmentService của Commit 06 ghi.
Query lọc tuple này trong database, service kiểm tra lại để không phụ thuộc
collation SQL Server; nhãn xuất ra là hằng số an toàn. Tuyệt đối không xuất
event_title tự do, description, location_text, actor hoặc branch. Không chỉ
dựa vào to_status, vì sự kiện nội bộ có thể dùng cùng trạng thái.

Tối đa 20 sự kiện được phép, occurred_at DESC rồi id DESC; không hiển thị số
lượng sự kiện nội bộ. Nếu không có sự kiện phù hợp, thông báo trống, không dựng
timeline giả từ trạng thái hiện tại. Các mốc khác chưa được công khai tự động.

Giới hạn: không thể phân biệt hai event cố ý dùng cùng tuple nhưng khác ý nghĩa
nội bộ khi schema không có visibility/type. Các writer tương lai phải dành riêng
tuple này cho mốc công khai hoặc bổ sung visibility bằng commit được phê duyệt;
không tự mở allowlist cho mọi event trong commit này.

## Rate limiting

TrackingRateLimiter dùng cửa sổ cố định 60 giây theo địa chỉ kết nối remoteAddr:

- Mặc định 20 lần tra cứu/phút, gồm cả mã không hợp lệ.
- Mở form không nhập mã không tiêu tốn quota và không truy vấn database.
- Tối đa 10.000 bucket; hết chỗ trả 429 thay vì loại bucket đang hoạt động để reset quota.
- Response 429 có Retry-After. Controller không tự tin X-Forwarded-For/Forwarded.
- Có thể điều chỉnh qua uteexpress.tracking.rate-limit.requests-per-minute và
  uteexpress.tracking.rate-limit.max-clients; không sửa application.properties.

Giới hạn này nằm trong bộ nhớ từng instance, reset khi khởi động, không phải
bảo vệ phân tán hoặc chống bot hoàn chỉnh. Người dùng chung NAT/proxy có thể
dùng chung quota. Khi triển khai sau proxy/nhiều instance, cấu hình giới hạn tại
proxy/gateway và cơ chế xác thực địa chỉ client đáng tin cậy cần được xử lý riêng.
Không lưu tracking number, IP hoặc thông tin cá nhân vào log bởi module này.
Query string vẫn có thể xuất hiện trong lịch sử trình duyệt/access log hạ tầng;
no-store/no-referrer không thay thế chính sách access log tại nơi triển khai.

## Mã HTTP và kiểm thử

- 200: form, kết quả, hoặc không có thông tin công khai.
- 400: mã không hợp lệ.
- 429: vượt hạn mức hoặc đầy bộ đếm.
- 503: lỗi database, không trả chi tiết lỗi.

Test service, limiter đồng thời và MVC với service thật, Repository mock; không
gửi SMTP, không truy cập SQL Server thật. CoreDatabaseMappingTests kiểm tra khởi
tạo các query bằng Hibernate. Test cũ được giữ nguyên. Không khẳng định đã kiểm
thử query, collation hoặc hiệu năng rate limit trên môi trường production.

Chạy:

    .\mvnw.cmd clean test
    .\mvnw.cmd -DskipTests compile
    git diff --check

Kiểm tra trình duyệt với database có sẵn:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.sql.init.mode=never"

Mở /tracking trong cửa sổ chưa đăng nhập, nhập mã chuẩn của vận đơn đã có.
Kiểm tra mã sai/không tồn tại, timeline bảo thủ, giới hạn tra cứu; thử trực tiếp
/user/shipments/{id} phải vẫn bị từ chối. Không tạo dữ liệu mẫu để làm tra cứu
thành công và không tự chạy ứng dụng kết nối SQL Server thật khi triển khai.
