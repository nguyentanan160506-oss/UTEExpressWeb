# QA — Dropdown chi nhánh nhận với User mới đăng ký

28/10/2026, workspace nhận được ở main, HEAD017a4af, ban đầu working tree sạch. Không đổi nhánh/Git history.
Không truy cập SQL Server hoặc credential DPAPI. Phiên H2 của người dùng ở8081 được giữ nguyên; regression dùng H2 mới/18082 và random port.

## Kết luận và dữ liệu được cung cấp

Người dùng xác nhận đã bấm “Tìm chi nhánh phục vụ”, dùng HOME_PICKUP và địa danh:
tp đà nẵng / huyện núi thành / xã núi thành. Không có địa chỉ chi tiết, số điện thoại hoặc credential được yêu cầu/đọc.

Các chuỗi này **không khớp vùng phục vụ seed của H2 demo**, không phải kết luận địa danh ngoài đời không hợp lệ.
H2 dùng Đà Nẵng hoặc Hồ Chí Minh / Khu vực demo / Phường demo. Không tự thêm vùng phục vụ hoặc đoán chi nhánh cho địa chỉ chưa cấu hình.
Bộ lọc có trim và bỏ phân biệt hoa/thường, không chuyển alias “tp đà nẵng” thành “Đà Nẵng”, không tự map quận/xã thực tế sang dữ liệu demo.

Nếu bộ địa danh trên dùng ở đầu nhận: không có chi nhánh nhận phù hợp. Nếu chỉ dùng ở đầu gửi: chi nhánh gửi không phù hợp,
nhưng danh sách nhận vẫn phải độc lập theo địa chỉ nhận. Người dùng chưa cung cấp một bộ địa danh riêng cho từng đầu,
nên không khẳng định địa chỉ gửi không hợp lệ tự nó làm mất dropdown nhận.

Lỗi UI được xác định: kết quả tìm bằng0 chỉ hiện placeholder, không giải thích thiếu coverage; hướng dẫn tìm đặt xa dropdown,
không có gợi ý địa bàn thực sự có trong H2. Đây là vấn đề phản hồi/khả năng sử dụng, không phải tài khoản mới thiếu quyền hay thiếu Employee.

## 1. Điều kiện có dữ liệu

GET /user/shipments/create tạo form rỗng. formPage gọi QuoteService.branchChoices, nhưng thiếu method/address thì trả empty lists
qua catch ShipmentCreationException; GET không báo validation khi chưa nhập. Thay đổi text input không tự nạp danh sách.

POST /user/shipments/branches cần:
- USER ACTIVE và ROLE_USER thật từ DB; không đòi có lịch sử vận đơn, Employee hoặc ManagerBranch.
- Chọn HOME_PICKUP/BRANCH_DROPOFF.
- Nhập đủ tỉnh, quận/huyện, phường/xã của cả đầu gửi và đầu nhận, mỗi chuỗi không rỗng và tối đa100ký tự.
- Bấm nút tìm (formnovalidate để không bị chặn bởi tên/phone/trọng lượng còn trống).

Repository trả branch nhận khi service area active, supportsDelivery=true; branch LOCAL_BRANCH ACTIVE,
parent hub CENTRAL_HUB ACTIVE và khác chính branch; province/district khớp không phân biệt case sau trim,
ward khớp hoặc area.ward rỗng cho cả quận/huyện. ACTIVE đơn thuần chưa đủ.

## 2. Kiểm tra dữ liệu H2

Trên phiên8081, dùng Guest POST /quote/branches chỉ tra cứu, không tạo quote/shipment:
- Gửi Đà Nẵng/Khu vực demo/Phường demo, nhận Hồ Chí Minh/Khu vực demo/Phường demo: option chi nhánh TP.HCM demo xuất hiện.
- Đổi receiverDistrict sang quận chưa cấu hình: chỉ placeholder.
Endpoint này dùng cùng QuoteService.choices/BranchNetworkService/repository nên chứng minh branch nhận/hub/coverage hợp lệ đang có trên phiên đó.

H2 mới trong regression xác minh5branch đều ACTIVE,4service area active và supportsDelivery.
Các area chỉ gắn nguồn Đà Nẵng và đích TP.HCM cho hai method; hai hub và branch ngoài scope không được tự đưa vào dropdown.
Không sửa fixture hoặc dữ liệu của người dùng để làm test đạt.

## 3. Controller/service và tuyến vận chuyển

UserShipmentController.formPage / branchChoices → QuoteService.branchChoices → choices →
BranchNetworkService.quoteServingBranches / quoteDeliveryBranches → BranchServiceAreaRepository.

UserShipmentService.options cung cấp các branch ACTIVE cho gợi ý tỉnh/danh mục; dropdown nhận dùng branchChoices.destinations,
không dùng toàn bộ options.branches. Vì thế datalist tỉnh có tên không đồng nghĩa đã có service area phục vụ mọi quận/xã.

Nguồn lọc theo địa chỉ gửi và shipmentMethod; đích lọc độc lập theo địa chỉ nhận và supportsDelivery.
Không lọc dropdown đích bằng originBranchId hoặc transport_routes. Quote kiểm branch/hub và pricing rule; tuyến vật lý được xử lý
ở TransportTripService khi lập chuyến. Không sửa các quy tắc này trong bản vá.
Regression đưa chính địa danh người dùng ở đầu gửi và giữ đầu nhận HCM hợp lệ: báo lỗi nguồn nhưng vẫn có option đích.

## 4. Thymeleaf/JavaScript và bản sửa

Không có AJAX/change handler nạp branch trong form; JavaScript notifications không cung cấp branch.
Thymeleaf th:each từ branchChoices.destinations render đúng danh sách server trả. Empty list không phải lỗi render.

Sửa tối thiểu:
- Lookup trả empty origins/destinations thì controller gắn lỗi rõ vào đúng field; không thay filter, không fallback danh sách tất cả branch.
- Thêm hướng dẫn và nút tìm ngay cạnh chi nhánh nhận, giữ CSRF/formnovalidate/required/server validation.
- DemoShipmentAdvice chỉ src/test, profile demo-local + demo.enabled; kiểm owned H2 prefix rồi đọc service areas để hiển thị
  địa bàn demo thực tế. Không hardcode địa chỉ mẫu vào logic nghiệp vụ/seed thêm data, không vào production JAR.
- Gợi ý này không tự động chọn branch và không cho phép bỏ bước xem báo giá.

## 5. Vì sao350PASS bỏ sót

BrowserOtpJourney cũ chỉ đi register→verify→login→GET danh sách shipment rồi kết thúc ở tài khoản mới.
Các flow tạo đơn dùng owner, điền sẵn đúng địa bàn seed và luôn bấm tìm; không kiểm trải nghiệm kết quả rỗng ở người vừa đăng ký.
Controller tests còn mock branchChoices thành danh sách có sẵn, nên không chứng minh initial empty state hoặc empty-coverage messaging.
350 là các assertion đã thực thi, không phải bảo chứng mọi UX/path. Đã mở rộng đúng khoảng trống này, không đổi logic để làm test đạt.

## 6. Regression/bằng chứng

Targeted LocalDemoMailTests + LocalDemoQaTests + UserShipmentControllerTests:52/52PASS.
Bổ sung2controller tests empty destination/empty origin; mở rộng test thật đăng ký/SMTP/OTP/login với cùng tài khoản:
không Employee, branch lookup hai method, địa bàn hợp lệ/không hợp lệ và input Núi Thành, giữ nguyên counts quote/shipment khi chỉ tìm.
Browser kiểm initial hint→input báo cáo ở đầu gửi/nhận→lỗi rõ→đổi về vùng hợp lệ→dropdown có branch→quote/create cùng User mới.
Kết quả browser/full verify mới ở phần cuối; không lấy số350 cũ làm bằng chứng cho regression mới.

## Cách thử sau cập nhật

Phiên8081 hiện tại không bị dừng để tránh mất user/dữ liệu H2. Cần khởi động lại entry point demo khi muốn nạp class mới;
restart H2 làm mất dữ liệu phiên trước, phải đăng ký lại hoặc dùng account seed. Chỉ làm lúc bạn đã sẵn sàng.

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 8081 -TestMail
~~~

Chọn HOME_PICKUP:
- Gửi: Đà Nẵng / Khu vực demo / Phường demo.
- Nhận: Hồ Chí Minh / Khu vực demo / Phường demo.
- Bấm Tìm chi nhánh phục vụ, chọn Chi nhánh TP.HCM demo rồi xem báo giá.
- Thử lại địa danh Núi Thành: nhận thông báo ngoài coverage thay vì dropdown rỗng không giải thích.

Không SQL Server/migration/seed; không tự git add/commit/push/merge/stash/PR. Không tự restart phiên8081.

Kết quả cuối 28/10/2026: Chrome headless358PASS/0FAIL/0BLOCKED,0JS/console.error/HTTP5xx; runtimeSecretCheck=PASS. Maven verify BUILD SUCCESS1.472tests/1.471passed/0failures/errors/1skipped,2:55,kết thúc22:13:42UTC+7. Skipped duy nhất là SQL opt-in cũ, giữ false. JAR không chứa DemoShipmentAdvice. git diff --check PASS; không sửa pom/application.properties/Flyway hoặc SQL Server.
Log: %TEMP%/uteexpress-branch-dropdown-final-browser.log, uteexpress-branch-dropdown-final-verify.log. Ảnh/evidence: uteexpress-browser-evidence-3f5e5e9019f04ad19b09ec6e3db64fc7. Dữ liệu test vẫn thuộc H2 riêng, không restart hoặc sửa dữ liệu8081.

## File thay đổi

Hai file mới:

- src/test/java/com/ute/uteexpress/operations/DemoShipmentAdvice.java
- docs/branch-dropdown-qa.md

Bảy file sửa:

- src/main/java/com/ute/uteexpress/controller/UserShipmentController.java
- src/main/resources/templates/shipments/create.html
- src/test/java/com/ute/uteexpress/operations/LocalDemoMailTests.java
- src/test/java/com/ute/uteexpress/security/UserShipmentControllerTests.java
- src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java
- docs/browser-qa-results.json
- docs/browser-qa.md

Không file xóa, không staged/commit/push; giữ nhánh main đã có lúc bắt đầu.
