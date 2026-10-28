# Commit 23 — Audit và bản đồ sản phẩm

Nhánh: `feature/team-final-website-completion`, xuất phát từ main `017a4af` đã merge Commit 22. Giữ nguyên 9 file đang sửa của bản vá dropdown trước khi bắt đầu. Đây là audit source/schema và kết quả QA được dẫn nguồn, không phải một lần kiểm thử SQL Server mới.

## Phạm vi đã có

| Vai trò / chức năng | Controller / lớp xử lý chính | Bằng chứng và giới hạn |
|---|---|---|
| Guest trang công khai | `AuthController.home`, `PublicTrackingController`, `PublicQuoteController` | Root redirect tracking; tracking DTO riêng, whitelist event, giới hạn dò mã; báo giá chưa tạo đơn |
| Đăng ký, OTP, đăng nhập | `RegistrationService.register/verify/resend`, `DatabaseUserDetailsService`, `SecurityConfig` | BCrypt, UNVERIFIED/LOCKED bị chặn, CSRF, đổi session ID, logout POST; SMTP demo chạy riêng ngoài JAR |
| Hồ sơ, sổ địa chỉ | `AccountProfileController`, `AccountProfileService.view/update/saveAddress/deleteAddress` | Bổ sung Commit 23; dùng `users`, `user_addresses` hiện có; DTO không có role, status, password; đọc/ghi theo chủ sở hữu |
| Tạo vận đơn, báo giá | `UserShipmentController`, `QuoteService.branchChoices/quote/review/confirm` | HOME_PICKUP/BRANCH_DROPOFF; giá DB; quote có chủ, hạn và khóa; snapshot, chống xác nhận lặp |
| Danh sách, chi tiết, timeline | `UserShipmentService`, `UserShipmentController` | Quyền sở hữu; giữ phí và method null của dữ liệu cũ |
| Manager nguồn/pickup | `ManagerLogisticsController`, `ShipmentLifecycleService`, `ShipperPickupService`, `ManagerHandoffService` | ManagerBranch, Employee, version, chứng từ, tiếp nhận vật lý; không suy ra custody từ phân công |
| Gom hàng và trung chuyển | `TransportTripService.create/addItems/seal/depart/arrive/receive/close`, `TransportRouting` | Manifest nhiều kiện, COLLECTION/LINEHAUL/DISTRIBUTION, receipts, discrepancies, reverse legs |
| Giao/hoàn | `LastMileService.assign/prepare/accept/start/complete/fail/redelivery/requestRecovery/reviewRecovery/initiateReturn/returnToSender` | Proof, failure reason, thu hồi và bàn giao độc lập, hoàn về người gửi |
| Dashboard, hỗ trợ | `OperationsDashboardReadService`, `TicketService`, `CustomerSupportController`, `ManagerTicketController` | Routing DB, PUBLIC/INTERNAL, ownership, throttle và lịch sử |
| Notifications | `NotificationService`, `NotificationWorker`, `TicketNotificationWorker`, `NotificationWebSocketConfig`, `notifications.js` | STOMP theo principal, thông báo chỉ báo refresh; fallback/reconnect; không broadcast dữ liệu riêng tư |
| Admin người dùng/quyền | `AdminController`, `AdminUserService` | Không mặc định kế thừa USER/MANAGER/SHIPPER; bảo vệ quản trị viên cuối cùng |
| Catalog, pricing | `AdminPricingController`, `AdminPricingService` | Draft/publish/retire, validity, cùng bộ tính giá với Guest/User |
| Reports/CSV/audit | `AdminReportService`, `ReportCsvService`, `AdminAuditService` | Phân trang, filter, CSV injection/escaping, audit dữ liệu tối thiểu |

Source hiện có 41 entity/bảng ứng dụng và 13 migration; SQL Server có thêm `flyway_schema_history`, tổng 42 bảng sau V13. `UserRoleId` là khóa kép; VARCHAR enum dùng JDBC VARCHAR; NVARCHAR(MAX), DATETIME2 UTC và các filtered index có kiểm thử riêng. Không cần V14 cho các thay đổi hiện tại.

## Lỗi đã xác minh và cách xử lý

- Dropdown trống: danh sách lấy từ phạm vi phục vụ, không phải toàn bộ chi nhánh ACTIVE. H2 chỉ phục vụ hai bộ địa danh demo; chuỗi `tp đà nẵng / huyện núi thành / xã núi thành` không có coverage. Trước đây UI không báo riêng cho từng đầu. Bản vá trước Commit 23 bổ sung lỗi gần trường và gợi ý lấy từ DB; chi tiết [điều tra dropdown](branch-dropdown-qa.md). Không tự thêm coverage thực tế hoặc đoán chi nhánh.
- H2 mất tài khoản/đơn sau restart: đúng tính chất memory QA nhưng không đáp ứng sản phẩm cuối. Chuẩn bị runner SQL Server riêng [SQL demo](sql-demo-setup.md), chưa chạy khi chưa được cấp phép.
- `/account` chỉ đọc email/role, chưa quản lý hồ sơ/địa chỉ: bổ sung CRUD có owner check, giới hạn 50 địa chỉ, mặc định tối đa một địa chỉ qua khóa User. Không cho sửa email, role, trạng thái hay mật khẩu qua form này. Địa chỉ lưu độc lập, chưa có chọn tự điền vào form vận đơn.
- README cũ còn nói OTP không nằm trong QA và chỉ nêu H2 DEMO-READY: cập nhật phân biệt QA H2 đã có bằng chứng và SQL demo bền vững chưa nghiệm thu.

Không phát hiện căn cứ để viết lại thuật toán giá: backend dùng bảng giá hiệu lực; từ chối thiếu/nhiều quy tắc; HOME_PICKUP thêm pickup rule, BRANCH_DROPOFF không có dịch vụ pickup. Phí vượt cân làm tròn lên bước quy định. COD thu hộ và giá trị khai báo không phải phí; insurance chưa bán. Client chỉ xác nhận token, không quyết định số tiền.

## Việc chưa hoàn tất / UX còn hạn chế

- SQL demo mới, migration, seed và kiểm thử tồn tại sau restart: chờ chủ sở hữu tạo DB/login và cấp phép; không dùng lại UTEExpress_Test.
- Coverage hiện dùng chuỗi tỉnh/quận/phường phải khớp DB, không có bản đồ hoặc danh mục hành chính toàn quốc. Có hướng dẫn và lỗi nhưng chưa phải autocomplete toàn quốc.
- Chọn chi nhánh là một lượt submit; giá được tính tự động ở backend khi bấm lấy báo giá, không phải giá cập nhật mỗi lần gõ. Thay đổi dữ liệu phải báo giá lại.
- Coverage/parent hub hợp lệ không tự chứng minh mọi chặng vật lý đã có route. Quote không lọc destination theo origin hoặc tìm đường toàn mạng; cấu hình demo phải có đủ tuyến xuôi/ngược, còn TransportTripService kiểm tra từng chặng khi lập chuyến. Thiếu route là lỗi cấu hình cần giải quyết, không đoán tuyến để tạo chuyến.
- UI vận hành nhiều bước và nhiều actor, cần rehearsal có hướng dẫn; Chrome headless không thay thế đánh giá thẩm mỹ và thao tác trình bày trực tiếp.
- Không có UI tổng quát cho mọi bảng cấu hình logistics. Seed được duyệt cung cấp mạng demo; Admin quản lý catalog/pricing và các chức năng sẵn có. Không mở thêm module CRUD không cần thiết.
- Không có bằng chứng production-ready/public Internet: TLS, SMTP thật, broker phân tán, backup/restore, giám sát, tải lớn và quy trình upgrade trên dữ liệu thật thuộc phạm vi khác.

## Rủi ro H2 → SQL Server

H2 không mô phỏng đầy đủ collation, filtered unique index, khóa/range lock, compile batch, kế hoạch truy vấn và NVARCHAR(MAX). SQL Server đã được kiểm chứng ở Commit 22 trên database test, nhưng đó không phải bằng chứng cho DB demo mới hay persistence sau restart.

Bảy migration từng được duyệt thêm GO: V3, V4, V8, V9, V10, V11, V12. Commit 23 không sửa chúng. DB đã SUCCESS checksum cũ sẽ không validate với source mới; phải điều tra và thống nhất riêng, không tự repair/history edit. Theo chủ dự án, UTEExpressDB chỉ V1/V2 và không có môi trường khác dùng checksum cũ. Không dùng nhận định này để cho phép ghi DB gốc.

SQL demo dùng validate, Flyway migrate tường minh, target cố định và credential riêng. Startup không seed/migrate; OTP giả lập chỉ profile demo/test, inbox ADMIN, loopback, @example.test. Thư mất sau restart nhưng tài khoản và OTP hash nằm SQL; có thể yêu cầu gửi lại theo cooldown bình thường. Mật khẩu demo ban đầu dùng riêng cho các actor, không dùng ngoài laptop.
