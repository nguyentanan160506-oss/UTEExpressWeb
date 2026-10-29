# UTEExpress Final Demo — chạy và trình diễn trên một laptop

## Một lệnh khởi động

Tại D:\WebCode\uteexpress\uteexpress, dùng Java 21 và DPAPI hiện có:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Run -ApprovedDatabase UTEExpress_FinalDemo -Port 18085
```

Mở http://127.0.0.1:18085 và dùng nhất quán hostname này trong phiên; localhost là hostname khác đối với cookie nên đổi qua lại có thể cần đăng nhập lại. Nếu cổng đã có server, dùng server hiện có; không chạy bản thứ hai. Khi cần áp dụng source mới, chỉ dừng đúng terminal FinalDemo theo sự đồng ý của người vận hành, không đụng 8083/18084. Run không migrate hoặc seed.

Đối với QA chỉ đọc và muốn tắt xử lý thông báo nền, thêm `-DisableNotificationWorker`. Tùy chọn chỉ tắt worker, không làm endpoint nghiệp vụ thành read-only; bài QA dùng browser observer giới hạn request. Run bình thường bật worker.

## Phiên đăng nhập

Mỗi vai trò dùng Chrome Profile riêng (hoặc các browser contexts độc lập do automation tạo). Hai tab cùng Profile chia sẻ session; đăng nhập vai trò thứ hai sẽ thay tài khoản ở các tab khác. Các cửa sổ Incognito cùng phiên cũng chia sẻ cookie. Sau đổi tài khoản, tải lại form để lấy CSRF hiện hành.

Bản sửa FinalDemo dùng cookie UTEEXPRESS_FINALDEMO_SESSION riêng, tránh đụng JSESSIONID của ứng dụng khác cùng 127.0.0.1. Cần khởi động lại riêng FinalDemo để áp dụng cấu hình mới; không sửa app ở cổng khác.

## Tài khoản và địa chỉ

Mật khẩu là mật khẩu website riêng đã nhập trong secure prompt; không ghi trong tài liệu.

| Điểm thao tác | Tài khoản |
|---|---|
| User | final-user-01@example.test |
| Manager nguồn TP.HCM | final-manager-b-79@example.test |
| Shipper pickup TP.HCM | final-shipper-b-79@example.test |
| Manager hub phía Nam | final-manager-h-hcm@example.test |
| Manager hub Đà Nẵng | final-manager-h-dn@example.test |
| Manager chi nhánh Đà Nẵng | final-manager-b-48@example.test |
| Shipper giao Đà Nẵng | final-shipper-b-48@example.test |
| Admin | final-admin@example.test |

Người gửi: Hồ Chí Minh / Quận 1 (địa bàn cũ) / Phường Sài Gòn. Người nhận: Đà Nẵng / Hải Châu (địa bàn cũ) / Phường Hải Châu. Địa chỉ chi tiết và hàng hóa phải ghi rõ mô phỏng nếu đang demo. Danh mục đầy đủ ở final-demo-network.md và phần Vùng phục vụ đang cấu hình trên form. Trên màn hình hẹp, cuộn ngang bảng để xem cột phường/phương thức; có thể đóng mục sau khi xem địa chỉ.

34 tỉnh tính giá không có nghĩa toàn bộ địa chỉ được phục vụ. SQL FinalDemo chỉ mở 35 phường, 35 chi nhánh + 7 hub. Không nhập Núi Thành rồi kỳ vọng chi nhánh Hải Châu phục vụ nếu DB chưa có service area tương ứng. Không seed thêm coverage để làm test PASS.

## Kịch bản một đơn HOME_PICKUP đến DELIVERED

Kịch bản dưới đây là hướng dẫn thao tác thủ công có ghi; lượt QA SQL hiện tại **không tự chạy các bước ghi này** theo lựa chọn của người dùng. Các bước nghiệp vụ tương ứng đã được thực thi trong Chrome trên H2 cô lập; không coi đó là bằng chứng end-to-end ghi trên SQL.

1. User: Tạo vận đơn → HOME_PICKUP, nhập hai địa bàn mẫu trên → Tìm chi nhánh phục vụ → chọn dịch vụ FD-STANDARD, trọng lượng hợp lệ → xem thành phần phí và hạn báo giá → xác nhận một lần. Ghi mã tracking, dùng đúng liên kết từ lịch sử để nhận diện kiện.
2. Manager nguồn B-79: mở đúng đơn → xác nhận → phân công Employee Shipper thuộc chi nhánh B-79. Không dùng ID hay Shipper của chi nhánh khác.
3. Shipper B-79: lấy hàng thành công, ghi xác nhận vật lý/chứng từ mô phỏng → yêu cầu bàn giao nguồn.
4. Manager B-79: nhận bàn giao, xác nhận thực nhận → xem mã biên nhận RCV tự sinh. Không tự nhập/sửa mã, không nhầm với mã chuyến/niêm phong.
5. Manager B-79 tạo chuyến COLLECTION B-79 → H-HCM, đưa đúng kiện đã tiếp nhận vào manifest → niêm phong → xuất phát. Manager H-HCM đánh dấu đến, thực nhận kiện và đóng chuyến.
6. Manager H-HCM tạo LINEHAUL H-HCM → H-DN, manifest/niêm phong/xuất phát. Manager H-DN đến/thực nhận/đóng chuyến.
7. Manager H-DN tạo DISTRIBUTION H-DN → B-48; Manager B-48 đến/thực nhận/đóng chuyến. Mỗi chặng phải có đúng người tại điểm giao/nhận.
8. Manager B-48 phân công Shipper B-48 và bàn giao giao hàng. Shipper nhận nhiệm vụ → bắt đầu giao → hoàn thành với người nhận và proof/reference hợp lệ.
9. User/Guest tra cứu tracking: DELIVERED và timeline công khai. Manager xem chứng từ và lịch sử bàn giao; Guest không được xem PII/COD/ghi chú nội bộ.

BRANCH_DROPOFF: bước đầu chọn gửi tại chi nhánh, sau xác nhận khách mang hàng tới nguồn; Manager thực nhận bằng form intake để sinh mã RCV. Không phân công pickup cho loại này. Sau đó dùng chung các chặng 5–9.

Giao thất bại/giao lại/hoàn: nhập đúng lý do thất bại, Manager chọn giao lại hoặc khởi tạo hoàn; Shipper bàn giao kiện về đích, Manager thực nhận rồi vận chuyển ngược B-48 → H-DN → H-HCM → B-79, cuối cùng nhận chứng từ trả người gửi. Không sửa status trực tiếp để nhảy bước.

## Các giới hạn và cách hiểu lỗi

401 cần đăng nhập; 403 có thể do sai vai trò/ownership/branch scope hoặc CSRF stale, không tự coi mọi 403 là lỗi. Dùng Profile đúng và tải lại form; không tắt CSRF. 404 không tồn tại hoặc không trong phạm vi được phép xem. Lỗi 500/503 phải báo URL, thao tác và thời điểm, không gửi credential/token.

SMTP FinalDemo chưa cấu hình thực (loopback port 1): OTP mới không được chứng nhận hoạt động tại đây. Test OTP thật qua fake SMTP chỉ có trong H2 QA test classpath. UI CRUD chi nhánh/nhân viên không có Controller trong source hiện tại; mạng lưới đã cấu hình bởi bộ FinalDemo. Không quảng cáo chức năng quản trị chưa tồn tại.

Chưa production load/LAN/Internet. Backup/restore chưa được thực thi; người dùng chọn chỉ ghi test trên H2. Không clean/repair/reset/migrate/seed lại và không tự restore đè dữ liệu. Xem final-stability-report.md để biết phần đã đo/thực thi và phần BLOCKED.
