# Commit 05 — Quản trị tài khoản

Chức năng sử dụng bảng User, Role, UserRole và trạng thái ACTIVE, UNVERIFIED,
LOCKED hiện có. Không cần migration hoặc cấu hình dependency mới.

## Endpoint

| Method | Route | Chức năng |
| --- | --- | --- |
| GET | /admin | Thống kê tổng tài khoản và từng trạng thái |
| GET | /admin/users | Tìm kiếm, lọc trạng thái, phân trang |
| GET | /admin/users/{id} | Chi tiết tài khoản và quyền |
| POST | /admin/users/{id}/lock | ACTIVE sang LOCKED |
| POST | /admin/users/{id}/unlock | LOCKED sang ACTIVE |
| POST | /admin/users/{id}/roles | Cập nhật các quyền được phép |

Danh sách tìm theo email, username, tên đầy đủ hoặc số điện thoại; ký tự wildcard
được escape cho SQL Server. Tham số q tối đa 150 ký tự, page bắt đầu từ 0,
size từ 1 tới 100. Sắp xếp cố định theo id giảm dần. DTO chỉ chứa thông tin
hiển thị và tên/id role, không chứa password hash hoặc OTP.

## Bảo vệ quyền và thao tác đồng thời

SecurityConfig bảo vệ toàn bộ /admin và /admin/** bằng ROLE_ADMIN. Controller và
service có kiểm tra method security. Service kiểm tra lại trạng thái ACTIVE và
quyền ADMIN trong database ở mỗi lần gọi, tránh quyền ADMIN cũ trong session
tiếp tục truy cập sau khi bị thu hồi. Mọi POST yêu cầu CSRF và kiểm tra dữ liệu
đầu vào. Form role chỉ bind danh sách id quyền; trường ngoài danh sách bị từ chối.

Mỗi transaction thay đổi Admin khóa pessimistic hàng ROLE_ADMIN trước, rồi khóa
tài khoản thực hiện và tài khoản đích. Hàng role này là điểm đồng bộ dùng chung
cho các instance ứng dụng. Sau khi lấy khóa, service kiểm tra lại quyền của
tài khoản thực hiện và số ADMIN ACTIVE trước khi vô hiệu hóa ADMIN đích.
Thứ tự khóa phải được giữ nguyên trong các chức năng Admin bổ sung sau này.

Không được tự khóa, tự bỏ ADMIN hoặc vô hiệu hóa ADMIN ACTIVE cuối cùng. Chỉ
quyền ROLE_USER, ROLE_MANAGER, ROLE_ADMIN thực sự có trong database được chọn;
role khác nếu đã tồn tại được giữ nguyên. Membership không đổi được giữ nguyên
cả assignedAt. Không tự tạo role hoặc tự cấp ADMIN khi đăng ký.

Chỉ tài khoản ACTIVE được khóa. UNVERIFIED không được khóa hoặc mở khóa để
tránh bỏ qua xác minh email. Schema không lưu trạng thái trước khi khóa, nên
LOCKED cũ được mở về ACTIVE theo quy ước này; cần xác định nguồn gốc dữ liệu
LOCKED có sẵn trước khi vận hành. Không suy đoán hoặc bổ sung cột lịch sử.

Việc cập nhật role không viết lại authorities trong các session đã đăng nhập.
Quyền mới có hiệu lực đầy đủ sau lần đăng nhập tiếp theo; truy cập Admin được
kiểm tra lại từ database ngay. Module này không tự kết thúc mọi session của
tài khoản bị khóa: đăng nhập mới bị từ chối và truy cập Admin bị từ chối, nhưng
session sẵn có ở các route khác vẫn theo cơ chế của Commit 02–04. Nếu cần thu
hồi toàn bộ session tức thời, bổ sung cơ chế quản lý session trong commit sau.

## Kiểm tra

Chạy Maven clean test, Maven -DskipTests compile và git diff --check theo yêu cầu.
Test service sử dụng repository mock; test MVC render template thật và kiểm tra
security/CSRF. Test đồng thời dùng mô hình khóa chia sẻ để kiểm tra thứ tự và
bất biến ADMIN, không chứng minh hành vi khóa trên SQL Server thật. Không kết
nối SQL Server, gửi email hoặc chạy migration trong các test Admin.

Để kiểm tra thủ công với database hiện có, có thể chạy từ PowerShell:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.sql.init.mode=never"

Giữ cấu hình Hibernate validate hiện tại. Dùng tài khoản ACTIVE đã có ROLE_ADMIN
để đăng nhập rồi mở /admin. Kiểm tra thống kê, tìm kiếm, lọc, phân trang, chi tiết,
khóa/mở khóa và thay đổi quyền với các tài khoản kiểm thử được phép. Đăng nhập
bằng USER/MANAGER phải nhận 403 khi truy cập Admin; khách nhận 401 theo handler
hiện có (HTML hoặc JSON tương ứng Accept), rồi có thể mở /login để đăng nhập.
Tự khóa/tự bỏ ADMIN phải bị từ chối. Không dùng
dữ liệu thật để thử thao tác thay đổi nếu chưa được người quản lý cho phép.

Việc kiểm tra thủ công có thay đổi dữ liệu khi gửi POST; quá trình triển khai
Commit 05 không tự thực hiện những thao tác đó trên SQL Server thật.
