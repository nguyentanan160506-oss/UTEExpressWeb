# Mạng lưới UTEExpress Final Demo

**Dữ liệu mô phỏng dành cho đồ án; không phải danh sách cơ sở kinh doanh thực.** Tên tỉnh/thành và phường theo danh mục hành chính áp dụng từ 01/07/2025. Mã phường là thông tin nguồn đối chiếu, không thêm cột mới vào database.

35 chi nhánh nhận/giao, 7 hub, 112 tuyến có hướng. Mỗi chi nhánh có 2 service area ACTIVE (HOME_PICKUP và BRANCH_DROPOFF), supportsDelivery=true. Hub không nhận đơn khách trực tiếp.

## Nhập địa chỉ trên giao diện

Form hiện có vẫn bắt buộc trường quận/huyện. Sau mô hình chính quyền hai cấp, bộ dữ liệu giữ **tên địa bàn cũ + `(địa bàn cũ)`** trong trường legacy này; đây không phải tên cấp hành chính hiện hành. Chép đúng cột tương ứng bên dưới vào form. Không tự đổi tên phường cũ, không nhập “Khu vực demo” hoặc “Phường demo”.

Chọn tỉnh/thành, nhập địa bàn cũ và phường ở cả hai đầu, chọn phương thức, rồi bấm **Tìm chi nhánh phục vụ**. Chọn chi nhánh được trả về, chọn dịch vụ tiêu chuẩn mô phỏng và xem báo giá trước xác nhận. Coverage chỉ gồm **35 phường cụ thể**, không phải toàn bộ phường/xã của 34 tỉnh/thành.

Ví dụ TP.HCM → Đà Nẵng: người gửi tỉnh `Hồ Chí Minh`, địa bàn `Quận 1 (địa bàn cũ)`, phường `Phường Sài Gòn`; người nhận tỉnh `Đà Nẵng`, địa bàn `Hải Châu (địa bàn cũ)`, phường `Phường Hải Châu`.

## 35 chi nhánh phục vụ

| Mã trong DB | Tỉnh/thành | Trường quận/huyện legacy | Phường hiện hành (mã) | Hub phụ trách |
|---|---|---|---|---|
| FD-B-01 | Hà Nội | Hoàn Kiếm (địa bàn cũ) | Phường Cửa Nam (00082) | FD-H-HN |
| FD-B-04 | Cao Bằng | Thành phố Cao Bằng (địa bàn cũ) | Phường Thục Phán (01273) | FD-H-HN |
| FD-B-08 | Tuyên Quang | Thành phố Tuyên Quang (địa bàn cũ) | Phường Minh Xuân (02215) | FD-H-LC |
| FD-B-11 | Điện Biên | Thành phố Điện Biên Phủ (địa bàn cũ) | Phường Điện Biên Phủ (03127) | FD-H-LC |
| FD-B-12 | Lai Châu | Thành phố Lai Châu (địa bàn cũ) | Phường Tân Phong (03408) | FD-H-LC |
| FD-B-14 | Sơn La | Thành phố Sơn La (địa bàn cũ) | Phường Tô Hiệu (03646) | FD-H-LC |
| FD-B-15 | Lào Cai | Thành phố Yên Bái (địa bàn cũ) | Phường Yên Bái (04252) | FD-H-LC |
| FD-B-19 | Thái Nguyên | Thành phố Thái Nguyên (địa bàn cũ) | Phường Phan Đình Phùng (05443) | FD-H-HN |
| FD-B-20 | Lạng Sơn | Thành phố Lạng Sơn (địa bàn cũ) | Phường Lương Văn Tri (05983) | FD-H-HN |
| FD-B-22 | Quảng Ninh | Thành phố Hạ Long (địa bàn cũ) | Phường Hạ Long (06688) | FD-H-HN |
| FD-B-24 | Bắc Ninh | Thành phố Bắc Giang (địa bàn cũ) | Phường Bắc Giang (07210) | FD-H-HN |
| FD-B-25 | Phú Thọ | Thành phố Việt Trì (địa bàn cũ) | Phường Việt Trì (07900) | FD-H-HN |
| FD-B-31 | Hải Phòng | Hồng Bàng (địa bàn cũ) | Phường Hồng Bàng (11311) | FD-H-HN |
| FD-B-33 | Hưng Yên | Thành phố Hưng Yên (địa bàn cũ) | Phường Phố Hiến (11953) | FD-H-HN |
| FD-B-37 | Ninh Bình | Thành phố Ninh Bình (địa bàn cũ) | Phường Hoa Lư (14329) | FD-H-HN |
| FD-B-38 | Thanh Hóa | Thành phố Thanh Hóa (địa bàn cũ) | Phường Hạc Thành (14797) | FD-H-TH |
| FD-B-40 | Nghệ An | Thành phố Vinh (địa bàn cũ) | Phường Thành Vinh (16681) | FD-H-TH |
| FD-B-42 | Hà Tĩnh | Thành phố Hà Tĩnh (địa bàn cũ) | Phường Thành Sen (18073) | FD-H-TH |
| FD-B-44 | Quảng Trị | Thành phố Đồng Hới (địa bàn cũ) | Phường Đồng Hới (18880) | FD-H-TH |
| FD-B-46 | Huế | Thuận Hóa (địa bàn cũ) | Phường Thuận Hóa (19789) | FD-H-DN |
| FD-B-48 | Đà Nẵng | Hải Châu (địa bàn cũ) | Phường Hải Châu (20242) | FD-H-DN |
| FD-B-51 | Quảng Ngãi | Thành phố Quảng Ngãi (địa bàn cũ) | Phường Cẩm Thành (21025) | FD-H-DN |
| FD-B-52 | Gia Lai | Thành phố Quy Nhơn (địa bàn cũ) | Phường Quy Nhơn (21583) | FD-H-DN |
| FD-B-56 | Khánh Hòa | Thành phố Nha Trang (địa bàn cũ) | Phường Nha Trang (22366) | FD-H-KH |
| FD-B-66 | Đắk Lắk | Thành phố Buôn Ma Thuột (địa bàn cũ) | Phường Buôn Ma Thuột (24133) | FD-H-KH |
| FD-B-68 | Lâm Đồng | Thành phố Đà Lạt (địa bàn cũ) | Phường Xuân Hương - Đà Lạt (24781) | FD-H-KH |
| FD-B-75 | Đồng Nai | Thành phố Biên Hòa (địa bàn cũ) | Phường Trấn Biên (26041) | FD-H-HCM |
| FD-B-79 | Hồ Chí Minh | Quận 1 (địa bàn cũ) | Phường Sài Gòn (26740) | FD-H-HCM |
| FD-B-80 | Tây Ninh | Thành phố Tân An (địa bàn cũ) | Phường Long An (27694) | FD-H-HCM |
| FD-B-82 | Đồng Tháp | Thành phố Mỹ Tho (địa bàn cũ) | Phường Mỹ Tho (28261) | FD-H-CT |
| FD-B-86 | Vĩnh Long | Thành phố Vĩnh Long (địa bàn cũ) | Phường Long Châu (29551) | FD-H-CT |
| FD-B-91 | An Giang | Thành phố Rạch Giá (địa bàn cũ) | Phường Rạch Giá (30742) | FD-H-CT |
| FD-B-92 | Cần Thơ | Ninh Kiều (địa bàn cũ) | Phường Ninh Kiều (31135) | FD-H-CT |
| FD-B-96 | Cà Mau | Thành phố Cà Mau (địa bàn cũ) | Phường An Xuyên (32002) | FD-H-CT |
| FD-B-79B | Hồ Chí Minh | Thành phố Thủ Đức (địa bàn cũ) | Phường Thủ Đức (26824) | FD-H-HCM |

## 7 hub và tuyến

| Mã hub | Vị trí | Manager vận hành |
|---|---|---|
| FD-H-HN | Hà Nội / Phường Cửa Nam | final-manager-h-hn@example.test |
| FD-H-LC | Lào Cai / Phường Yên Bái | final-manager-h-lc@example.test |
| FD-H-TH | Thanh Hóa / Phường Hạc Thành | final-manager-h-th@example.test |
| FD-H-DN | Đà Nẵng / Phường Hải Châu | final-manager-h-dn@example.test |
| FD-H-KH | Khánh Hòa / Phường Nha Trang | final-manager-h-kh@example.test |
| FD-H-HCM | Hồ Chí Minh / Phường Sài Gòn | final-manager-h-hcm@example.test |
| FD-H-CT | Cần Thơ / Phường Ninh Kiều | final-manager-h-ct@example.test |

Mỗi chi nhánh ↔ hub phụ trách có 2 tuyến riêng: COLLECTION và DISTRIBUTION (70 tuyến). Mỗi cặp hub khác nhau có 2 tuyến LINEHAUL riêng (42 tuyến). Tổng **112**, không phải 82. Source TransportRouting hiện yêu cầu chặng trực tiếp giữa hai hub của đơn; chuỗi 12 tuyến liên hub không đáp ứng đầy đủ mô hình đó.

Tất cả **35 × 35 = 1.225 cặp chi nhánh** có đường vận hành phù hợp (cùng chi nhánh không cần chuyến; cùng hub không cần LINEHAUL). Về tỉnh/thành, 34 × 34 = 1.156 cặp được phân loại giá C25, nhưng chỉ nhận/giao tại các địa chỉ coverage ở bảng trên. Không suy ra phục vụ toàn tỉnh từ khả năng tính giá.

## Tài khoản vận hành

- Admin: `final-admin@example.test`.
- 12 User: `final-user-01@example.test` … `final-user-12@example.test`.
- Manager tại từng điểm: `final-manager-<node viết thường>@example.test`, ví dụ `final-manager-b-79@example.test`, `final-manager-h-hcm@example.test`, `final-manager-h-dn@example.test`, `final-manager-b-48@example.test`.
- Shipper tại từng chi nhánh: `final-shipper-<node viết thường>@example.test`, ví dụ `final-shipper-b-79@example.test`, `final-shipper-b-48@example.test`.
- Mật khẩu ban đầu là giá trị riêng người vận hành nhập trong secure prompt; không ghi trong báo cáo. Mỗi tài khoản có BCrypt salt riêng. Không dùng trên môi trường production.
- Manager có đúng một ManagerBranch và Employee tại cùng điểm; Shipper có Employee COURIER_DRIVER tại chi nhánh. Không mở rộng quyền chéo điểm. Chuyển tài khoản cho từng chặng khi thao tác.

## Dữ liệu giá

`prices.csv` gồm 5 scope C25; `pickup-fees.csv` chứa phụ phí HOME_PICKUP. Đây là **mức phí giả định học thuật**, không phải giá thị trường. Seed ghi vào PricingVersion PUBLISHED và các bảng giá; QuoteService đọc DB, không có công thức tính tiền song song. BRANCH_DROPOFF không chịu phụ phí pickup. Chưa cấu hình sản phẩm bảo hiểm; phí bảo hiểm theo logic hiện có. Không tự cập nhật giá khi seed lại.

## Nguồn địa danh

Đối chiếu các mã/tên phường đã chọn với phụ lục Quyết định 19/2025/QĐ-TTg, Công báo Chính phủ (bản PDF có lớp text):

- [Văn bản Chính phủ](https://chinhphu.vn/?classid=0&docid=214409&pageid=27160).
- [Phụ lục phần đầu](https://congbaocdn.chinhphu.vn/CongBaoCP/VanBan/2025/6/45430/57438-1-2025919-92019-2025-qd-ttg.pdf).
- [Phụ lục phần sau](https://congbaocdn.chinhphu.vn/CongBaoCP/VanBan/2025/6/45430/57441-1-2025921-92219-2025-qd-ttg.pdf).

Tên địa bàn cũ chỉ phục vụ tương thích trường district; không khẳng định là cấp hành chính còn hoạt động. Địa chỉ chi tiết các cơ sở và chứng từ đều được ghi rõ mô phỏng.
