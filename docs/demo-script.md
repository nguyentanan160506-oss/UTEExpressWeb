# Kịch bản sản phẩm cuối — Chrome / SQL Server demo

Phạm vi sản phẩm: SQL Server UTEExpress_Demo riêng trên Windows, Chrome localhost, dữ liệu còn sau restart. Chỉ chạy sau khi chủ sở hữu duyệt theo [SQL demo setup](sql-demo-setup.md). Browser headless/H2 có bằng chứng riêng; kịch bản SQL demo dưới đây CHƯA ĐƯỢC THỰC THI. Không coi checklist này là kết quả PASS.

## Chuẩn bị

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\sql-demo.ps1 -Mode Run -ApprovedDatabase UTEExpress_Demo -Port 8081
~~~

Trước đó Inspect/Migrate/Seed đã được duyệt và thành công. Mật khẩu website đặt qua secure prompt khi Seed, không đổi mỗi lần Run; credential SQL là DPAPI riêng. Chờ READY, mở http://127.0.0.1:8081/. Nếu cổng đang dùng H2, chọn 8083 thay vì dừng phiên cũ.
Dùng cửa sổ thường cho User và một Incognito cho vận hành; Incognito tiếp theo cùng profile chia sẻ session.
Vận hành phải Đăng xuất bằng nút UI rồi đăng nhập actor tiếp theo. Không chỉnh database/status để trình diễn.
Dừng bằng Ctrl+C, Run lại không Seed/Migrate: đăng nhập lại để kiểm tra User, hồ sơ, địa chỉ, vận đơn và chứng từ vẫn tồn tại. Session và SMTP inbox memory mất khi tắt.

| Actor | Email test |
|---|---|
| User chính / khách khác | demo23-owner@example.test / demo23-other@example.test |
| Manager nguồn | demo23-manager@example.test |
| Manager hub nguồn / hub đích | demo23-hub-origin-manager@example.test / demo23-hub-dest-manager@example.test |
| Manager chi nhánh đích | demo23-destination-manager@example.test |
| Shipper lấy hàng / giao hàng | demo23-pickup-courier@example.test / demo23-shipper@example.test |
| Admin | demo23-admin@example.test |

## Trình tự và kết quả mong đợi

| Bước | Actor / URL | Thao tác | Expected / chứng từ cần nhìn |
|---|---|---|---|
|1 Public|Guest /, /tracking, /quote|Mở root, nhập mã sai/không có, mở quote|Root→tracking200, báo lỗi rõ; không phone/địa chỉ/COD |
|2 Register|Guest /register|Email mới @example.test, username/phone mới, password riêng; submit|/verify-email, chưa được login khi UNVERIFIED |
|3 Nhận OTP|Admin ở Incognito /admin/demo-mailbox|Đọc mã cho đúng email mới; không ghi/chụp mã vào tài liệu/log|Chỉ Admin được đọc, email nhận nội bộ; User/Guest không vào được |
|4 Verify/login|User /verify-email|Thử sai một mã, nhập đúng mã inbox rồi login|Sai bị chặn; đúng ACTIVE/login; dùng lại mã bị chặn; không tự activate |
|4b Hồ sơ|User /account/profile|Sửa tên, thêm/cập nhật/xóa địa chỉ; thử User khác|Không lộ địa chỉ; mutation POST/CSRF; không đổi quyền/email |
|5 Tạo hai đơn|owner /user/shipments/create|Chọn HOME_PICKUP, nhập địa chỉ/rate hợp lệ bên dưới, xem quote rồi confirm; làm2đơn|Ghi tracking S1/S2; phí52500, có expiry; confirm lại mở cùng đơn |
|6 Xác nhận/phân công|manager /manager/shipments|Mở đúng S1/S2, confirm, chọn pickup-courier|CONFIRMED→PICKUP_ASSIGNED và history; chưa có custody giả |
|7 Lấy hàng|pickup-courier /shipper/pickups|Mở đúng tracking, SUCCESS + đã thực nhận + reference, gửi handoff|PICKED_UP, evidence; chưa tự nhận thay Manager |
|8 Nhập nguồn|manager /manager/handoffs|Mở đúng tracking, kiểm thực tế, receipt reference, xác nhận|Biên nhận nguồn, AT_ORIGIN_BRANCH; nhận độc lập với courier |
|9 Gom chuyến|manager /manager/trips/new|Chọn tuyến đến Hub miền Trung, tạo draft, tickS1/S2, add, seal, depart|Manifest2kiện; niêm phong/xuất có mốc/reference; depart không tự receipt |
|10 Kho nguồn nhận|hub-origin-manager /manager/trips|Đúng chuyến: arrive, nhận riêng từng kiện với reference, close|Hai receipt; đủ kiện mới close |
|11 Liên tỉnh|hub-origin-manager rồi hub-dest-manager|Tuyến Hub miền Trung→Hub phía Nam; lặp manifest/seal/depart/arrive/receive/close|Receipt liên tỉnh, đúng actor đầu gửi/đầu nhận |
|12 Phân phối đích|hub-dest-manager rồi destination-manager|Tuyến Hub phía Nam→chi nhánh TP.HCM; lặp đầy đủ|S1/S2 đến chi nhánh đích, đủ3leg |
|13 Bàn giao giao cuối|destination-manager /manager/deliveries/{S}|Assign shipper, reference + đã kiểm tra để chuẩn bị handoff|Chưa coi shipper đã nhận khi mới assign/prepare |
|14 Giao thành côngS1|shipper /shipper/deliveries|Mở đúng tracking: accept, start, complete người nhận/reference/confirmed|DELIVERED, Manager xem proof đã lưu; phí không đổi |
|15 Giao thất bạiS2|shipper /shipper/deliveries|Accept/start, fail ABSENT + lý do + confirmed|DELIVERY_FAILED, attempt có lý do, chưa mất custody |
|16 Hoàn/thu hồi|destination-manager rồi shipper rồi destination-manager|Khởi tạo hoàn có lý do; shipper return-to-branch; Manager nhận lại có receipt|RETURNING; decision không tự coi đã thu hồi/đã trả sender |
|17 Chặng ngược1|destination-manager→hub-dest-manager|Chi nhánh TP.HCM→Hub phía Nam, chọn mục Kiện hoàn, đủmanifest/receipt/close|Đúng kiện hoàn/đúng direction |
|18 Chặng ngược2|hub-dest-manager→hub-origin-manager|Hub phía Nam→Hub miền Trung, đủthao tác/chứng từ|Receipt và history còn nguyên |
|19 Chặng ngược3|hub-origin-manager→manager|Hub miền Trung→Đà Nẵng, đủthao tác/chứng từ|Kiện hoàn về nguồn, chưa RETURNED thiếu sender receipt |
|20 Trả người gửi|manager /manager/deliveries/{S2}|Đúng người nhận, reference + confirmed return-to-sender|RETURNED có sender receipt; history đủđi/ngược; snapshot phí giữ nguyên |
|21 Tracking/ownership|owner/other/Guest|Owner detail; other dùngURLS1/S2; Guest tra tracking và numericID|Owner200, other404, Guest ID401; public đúng status/timeline, không proof nội bộ/contacts |
|22 WebSocket|owner giữ /notifications, manager ở Incognito|Tạo đơn thử mới, owner mở inbox; Manager confirm rồi cancel nếu cần|Notice đúngtracking hiện không refresh; other không có/numericnotice404 |
|23 Reconnect|Chỉ demo H2, DevTools owner|Network Offline, trạng thái đổi ở phiên Manager, Online trở lại|Client reconnect/backoff và reconcile hiện dữ liệu mới; không cần F5 |
|24 Hỗ trợ|owner /account/support; manager nguồn /manager/tickets|Xem ticket tạo qua UI, gửi message; Manager xem PUBLIC/INTERNAL|Owner không thấy INTERNAL, other không xemticket; giữ đúng routeDB |
|25 Admin|admin /admin/users, /admin/reports, /admin/audit|Lock/unlock other, lọc reports, xuất CSV, xem audit|Không khóa người đang demo, audit actor/target đúng; CSV attachmentUTF8; Admin không tự làManager |
|26 Kết thúc|Mọi actor|Logout đúngform, thử lại privateURL, dừng ứng dụng|401 sau logout; không mutation bằngGET/khôngCSRF |

## Dữ liệu cho form tạo đơn

HOME_PICKUP hoặc BRANCH_DROPOFF; gửi Đà Nẵng / Khu vực demo / Phường demo, nhận Hồ Chí Minh / Khu vực demo / Phường demo.
Địa chỉ chi tiết và tên giả; phone0901234567/0901234568; Sách demo1,01kg; dịch vụ DEMO23-STANDARD; COD và declared0.
Bấm Tìm chi nhánh phục vụ, chọn nguồn/đích hợp lệ. HOME_PICKUP52500=shipping45000+pickup7500; dropoff45000, insurance0.
Giá mô phỏng từ database demo, không phải giá thị trường. Không nhập/sửa số tiền client để ép đơn.
Mã S/A/T/H lấy từ link/list thực tế. Shipment ID không phải assignment/handoff/trip ID.

## Fallback đúng phạm vi

SQL seed chỉ tạo dữ liệu tham chiếu; không có 18 vận đơn QA dựng sẵn. Mọi đơn và chứng từ trình diễn được tạo qua UI. Nếu cần xem fixture H2 cũ, dùng [demo QA riêng](demo-setup.md) và ghi rõ đã chuyển môi trường.
Không dùng seed để đánh dấu bước vừa lỗi thành PASS. Thiếu cấu hình/rule phải báo lỗi, không tự đoán/giảm giá hoặc sửa status.
SMTP chỉ nhận @example.test, không gửi Internet; dùng địa chỉ cá nhân sẽ bị từ chối/rollback là đúng mode test.
Inbox chỉ có5phút, cần mã mới thì tuân cooldown60giây; không tắt OTP hoặc sửa clock nghiệp vụ để diễn.
Giữ một cửa sổ User, một actor vận hành; các Incognito của cùng profile không tạo nhiều session độc lập.

## Ghi nhận nghiệm thu tay

| Case | Thời gian/browser | URL/tracking/actor | Thao tác | Expected | Actual | PASS/FAIL | Evidence không secret |
|---|---|---|---|---|---|---|---|
|Presenter rehearsal|Chưa ghi|—|Theo bảng trên|Như mô tả|Chưa bấm tay trong lượt agent|NOT RUN|Không chụp password/OTP/CSRF |

Bằng chứng agent là Chrome headless ở [browser-qa.md](browser-qa.md)/[JSON](browser-qa-results.json).
Browser automation không đồng nghĩa mọi viewport/a11y/Save dialog đều được thử. Những mục đó không phải yêu cầu cloud/production để giữ Commit22 vô hạn.

## Gate dữ liệu bền vững

Sau bước 25, ghi tracking S1/S2 và phí/chứng từ, dừng Run bằng Ctrl+C rồi chạy lại cùng lệnh. Đăng nhập lại User/Manager/Admin: tài khoản mới, hồ sơ/địa chỉ, DELIVERED/RETURNED, timeline, receipt/proof và audit còn nguyên. Không chạy Seed để phục hồi dữ liệu. Chạy Seed lần hai chỉ sau khi được cho phép kiểm thử no-op; so sánh row count và hashes, không in hash/password. Trạng thái hiện tại: NOT RUN trên SQL demo.
