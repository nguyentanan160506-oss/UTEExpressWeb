# Commit 04 — Đăng ký và xác minh email

Các route: GET/POST /register, GET/POST /verify-email, POST /verify-email/resend.
Các POST đều yêu cầu CSRF. Sau xác minh, người dùng đăng nhập tại /login; không tự đăng nhập.

Schema V1 đã có email_otps và đầy đủ dữ liệu OTP. Không có migration mới; không sửa V1/V2.
users.username và phone_number bắt buộc và UNIQUE, nên form đăng ký cần hai trường này.
Tài khoản mới là UNVERIFIED, mật khẩu BCrypt, chỉ gán ROLE_USER đã có trong roles.

## SMTP

Thiết lập trong môi trường chạy, không lưu giá trị thật vào Git hoặc gửi trong chat:

| Biến | Nội dung |
|---|---|
| MAIL_HOST | Máy chủ SMTP |
| MAIL_PORT | Cổng SMTP, mặc định 587 |
| MAIL_USERNAME | Tài khoản SMTP |
| MAIL_PASSWORD | Mật khẩu SMTP/app password, chỉ thiết lập cục bộ |
| MAIL_FROM | Địa chỉ gửi được nhà cung cấp cho phép |
| MAIL_SMTP_AUTH | Mặc định true |
| MAIL_STARTTLS | Mặc định true; STARTTLS được yêu cầu |
| MAIL_SSL | Mặc định false; dùng true cho SMTP TLS trực tiếp, đồng thời MAIL_STARTTLS=false |

Ứng dụng không thử kết nối SMTP khi khởi động. Thiếu cấu hình hoặc gửi lỗi được báo rõ
trên form; không hiển thị thông báo đã gửi thành công. Các timeout kết nối/đọc/ghi là 5 giây.
Mail debug tắt; code không log OTP hoặc lỗi chi tiết của SMTP.

## Quy tắc OTP

SecureRandom tạo chuỗi 6 chữ số, bao gồm số 0 ở đầu. Chỉ hash BCrypt được lưu vào database.
Mã hết hạn sau 5 phút theo UTC, tối đa 5 lần sai. Chỉ mã REGISTER_VERIFY mới nhất được chấp nhận.
Gửi lại sau ít nhất 60 giây, tối đa 3 lần phát hành mỗi giờ và 10 lần mỗi ngày, tính cả mã ban đầu.
Mã gửi lại khác mã trước; mã trước bị vô hiệu hóa khi gửi lại thành công.

Xác minh và gửi lại lấy PESSIMISTIC_WRITE trên cùng user, trong transaction. Kích hoạt ACTIVE
và đánh dấu mã đã dùng cùng commit. Nhập sai trả kết quả bình thường để số lần sai được commit.
Lỗi gửi đăng ký/resend hoặc lỗi database làm rollback transaction; không kích hoạt tài khoản.

SMTP và database không có distributed transaction: SMTP có thể nhận email nhưng database
commit sau đó thất bại. Khi đó không có tài khoản ACTIVE/mã có hiệu lực được xác nhận thành công.
Một outbox cần schema/work riêng và chưa thuộc Commit 04.

## Thử thực tế

Sau khi schema V1/V2 đã được triển khai và cấu hình database/SMTP trong môi trường:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.sql.init.mode=never"

Mở /register, đăng ký email do bạn kiểm soát, kiểm tra email, nhập mã tại /verify-email.
Trước khi xác minh, đăng nhập phải bị từ chối; sau khi xác minh, /login và /logout hoạt động như Commit 03.
Thử sai 5 lần, mã cũ, hết hạn và gửi lại trước 60 giây. Không có endpoint hiển thị OTP.

Unit/MVC tests dùng mock mail sender, không kết nối SMTP hoặc SQL Server thật.
Test transaction kiểm tra commit/rollback qua Spring transaction interceptor;
khóa SQL Server, rollback dữ liệu thực và giao nhận SMTP cần kiểm tra trong môi trường tích hợp.
