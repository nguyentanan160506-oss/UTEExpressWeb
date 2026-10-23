# Commit 14 — Danh mục dịch vụ, quy tắc giá và phiên bản

Phụ trách Thuận; nhánh `feature/thuan-service-pricing-rules`. Working tree ban đầu sạch, HEAD `e0a6429`
chứa Commit 01–13, bản sửa thiếu source Commit 11 và Flyway V1–V8. Chuyển sang nhánh đã tồn tại theo yêu cầu;
không stage/commit/push/merge/stash/tạo PR. Không sửa V1–V8, pom.xml, cấu hình kết nối hoặc SMTP.
Không chạy ứng dụng/Flyway trên database thật trong quá trình triển khai.

## 1. Khảo sát kiến trúc trước triển khai

| Nội dung | Hiện trạng đã xác định từ source |
|---|---|
| Bảng liên quan | shipping_services, shipping_rate_rules, pickup_fee_rules, shipment_quotes, shipments |
| Danh mục | ShippingService: code duy nhất, tên/mô tả, giờ giao dự kiến, is_active, timestamps |
| Chính sách | ShippingRateRule theo service/scope; PickupFeeRule theo service/branch; chưa có version/audit quản trị giá |
| Tuyến/vùng | Tỉnh/huyện/xã dạng chuỗi, branch_service_areas ACTIVE, hỗ trợ phương thức đầu gửi và supports_delivery đầu nhận; LOCAL_BRANCH và CENTRAL_HUB hợp lệ |
| Công thức | Cân thực, giá cơ bản + bước cân vượt; không có bậc min/max độc lập, hệ số thể tích, bảo hiểm hoặc giá theo INTER_REGION |
| Snapshot | Quote lưu input, FK shipping/pickup rule và phí; Shipment lấy nguyên các phí từ quote; quote chỉ được xác nhận một lần |
| Giá trị cố định | VND đang hiển thị trong UI; TTL quote mặc định 15 phút, tối đa 24 giờ; ROUND CEILING bước cân, tiền 2 số lẻ; không có giá bán hardcode |
| Dữ liệu chưa có | Chuẩn địa giới/zone ID, phân vùng INTER_REGION, bảo hiểm, thể tích, thu COD, thuế/thanh toán; không tự bổ sung công thức hoặc dữ liệu giả |

Source chưa có Guest Quote. Commit này thêm `/quote` với DTO công khai riêng; Guest Tracking vẫn giữ whitelist cũ.
BranchNetworkService giữ các method có quyền cũ, thêm method catalog an toàn cho báo giá công khai dùng chung query/vùng phục vụ.

## 2. Service catalog

Tái sử dụng shipping_services. V9 thêm @Version BIGINT (DEFAULT 0 cho dòng cũ), created_by/updated_by nullable
để không dựng người thao tác lịch sử, và các giới hạn max_weight_kg/max_length_cm/max_width_cm/max_height_cm tùy chọn.
Tất cả giới hạn DECIMAL(10,2) dương; không có nghĩa là quy đổi thể tích. Nếu một chiều có giới hạn nhưng chưa nhập số đo,
quote/confirm phải báo lỗi thay vì đoán kích thước.

ADMIN tạo/sửa mô tả, tên, giờ giao, giới hạn và bật/tắt nhận đơn mới. Mã 1–50 ký tự A–Z, 0–9, `_`, `-`,
bắt đầu bằng chữ/số; kiểm tra trùng không phân biệt hoa thường và unique database. Mã không sửa sau khi tạo
(kể cả chưa tham chiếu); ORM cũng đánh dấu updatable=false. Dịch vụ mới mặc định ngừng nhận đơn để cấu hình có chủ đích.
Không cung cấp DELETE. Dịch vụ đã ngừng vẫn tồn tại trong Shipment/quote cũ.

Đổi mô tả không tính lại phí. Ngừng dịch vụ chặn quote mới và xác nhận quote chưa tạo đơn; gửi lại quote đã consumed
chỉ trả Shipment cũ, không tạo đơn mới. Điều kiện số đo hiện hành còn được kiểm tra khi xác nhận quote.

## 3. Phiên bản và dữ liệu legacy

PricingVersion/pricing_versions liên kết một ShippingService, code duy nhất trong service, trạng thái DRAFT/PUBLISHED,
VND, valid_from/valid_to, retired_from, người tạo/sửa/công bố, timestamps và @Version.

ShippingRateRule và PickupFeeRule vẫn là bảng quy tắc nguồn, chỉ thêm pricing_version_id nullable và retired_from nullable.
Rule trong DRAFT không tham gia báo giá thật. Khi công bố, các rule đang bật của bản nháp được sử dụng ở khoảng đã định.
Quy tắc đã công bố không sửa trực tiếp; thay bằng bản nháp khác. Không có xóa bản công bố hoặc mở lại DRAFT.

Không backfill giá hiện tại thành PUBLISHED. Các rule không có version là **legacy**; tiếp tục hiệu lực theo dữ liệu cũ,
được hiển thị riêng tại chi tiết dịch vụ, chỉ đọc số tiền/khoảng gốc. Trùng legacy đã tồn tại không được tự sửa;
quote vẫn từ chối nhiều quy tắc phù hợp.

Phiên bản có thể quản lý riêng một scope hoặc phụ phí một số chi nhánh; không bắt buộc bán mọi dịch vụ/phạm vi.
Cần ít nhất một rule đang bật với đủ dữ liệu. Bản chỉ cập nhật phụ phí vẫn cần quy tắc vận chuyển hiệu lực từ phiên bản khác/legacy
để HOME_PICKUP có thể báo giá; thiếu bất kỳ thành phần bắt buộc nào sẽ lỗi, không tạo giá thay thế bằng 0.

## 4. Công thức, phạm vi và giới hạn

QuoteService vẫn là nơi tính phí duy nhất, phần tính đã được dùng chung cho User, Guest, Admin giá thật và mô phỏng nháp.

```text
scope = INTRA_PROVINCE nếu tỉnh gửi = tỉnh nhận (không phân biệt hoa thường)
scope = INTER_PROVINCE nếu khác tỉnh
steps = ceil(max(0, weightKg - baseWeightKg) / extraWeightStepKg)
shippingFee = basePrice + steps * extraPricePerStep
pickupFee = pickup_fee_rules đúng chi nhánh/service nếu HOME_PICKUP
pickupFee = 0 nếu BRANCH_DROPOFF (không cung cấp lấy hàng)
insuranceFee = 0 (chưa bán sản phẩm bảo hiểm)
totalFee = shippingFee + pickupFee + insuranceFee
```

Giữ BigDecimal, tiền DECIMAL(18,2), bước cân DECIMAL(10,2), CEILING số bước và UNNECESSARY scale 2.
Chặn âm, bước cân 0, quá precision/scale, tổng tiền vượt giới hạn. BasePrice/extraPrice được phép 0 theo V1,
nhưng Admin phải nhập rõ; không có seed/fallback giá 0. Phụ phí lấy hàng phải >0 theo V4.

Mỗi version/scope chỉ có một công thức bao toàn bộ cân dương; filtered unique chặn công thức/bậc chồng lấn.
Mỗi version/branch chỉ có một phụ phí, kể cả dòng đang tắt. Muốn đổi phải sửa dòng đó trong DRAFT;
bật/tắt dòng thay cho xóa vật lý. Chưa thêm các bậc min/max có ngữ nghĩa mới hoặc INTER_REGION khi thiếu dữ liệu.

Tối đa 500 dòng phụ phí/version; danh sách quản trị, rule, audit và lựa chọn chi nhánh phân trang 20.
Danh mục service dùng tìm mã hoặc tên đầy đủ; Guest/User tái sử dụng danh sách dịch vụ đang hoạt động hiện có.

## 5. Hiệu lực UTC và chọn quy tắc

Dùng LocalDateTime UTC, SQL DATETIME2(7), không chuyển ngầm theo múi giờ máy/browser.
Các ô datetime-local trong Admin được ghi nhãn **UTC**; người vận hành nhập giờ UTC có chủ đích.
Khoảng nửa mở `[validFrom, validTo)`, kết thúc null là vô hạn. Cùng thời điểm hết bản A và bắt đầu bản B thì chọn B.

Rule được dùng khi ACTIVE, legacy hoặc version PUBLISHED, đã bắt đầu và chưa chạm effective_to/retired_from.
Rule managed dùng cùng khoảng với version, được đồng bộ và kiểm tra khi công bố. expired/retired hiển thị tính từ thời gian;
không cần cron sửa cờ và không có enum trạng thái vận đơn mới.

Kiểm tra phải tìm đúng một rule vận chuyển/service/scope; HOME_PICKUP cần đúng một rule phụ phí/service/branch.
Không có hoặc nhiều hơn một đều trả lỗi nghiệp vụ. Không dựa thứ tự ID để chọn giá, không lấy số tiền client.

Trước công bố: kiểm tra khoảng thời gian, đủ dữ liệu, scope hỗ trợ, chi nhánh, công thức/tiền, trùng trong nháp và xung đột
với mọi legacy ACTIVE hoặc rule PUBLISHED cùng phạm vi có khoảng giao nhau. Kiểm tra cả bản tương lai, không chỉ bản đang chạy.
Không công bố hồi tố: validFrom phải chưa qua; kiểm tra lại đồng hồ sau bước kiểm tra xung đột trước ghi publishedAt.
Nút Kiểm tra là xem trước; publish luôn kiểm tra lại trong transaction.

## 6. Chuyển giá và ngừng hiệu lực

Không sửa effective_to hoặc số tiền của dòng cũ để viết lại lịch sử. Thay vào đó, thao tác riêng lập retired_from **một lần**,
ở tương lai UTC, sau bắt đầu và trước kết thúc gốc nếu có; có xác nhận và audit trước/sau.
Managed rule ngừng theo version; endpoint legacy không nhận rule managed. Đã lập lịch không sửa/thu hồi trong phạm vi này.

Quy trình chuyển từ giá không giới hạn kết thúc:

1. Chuẩn bị DRAFT mới, validFrom T ở tương lai UTC, nhập quy tắc thay thế.
2. Xem/mô phỏng và kiểm tra; nếu xung đột, lập lịch ngừng legacy/version trước tại T.
3. Công bố bản mới bắt đầu T; khoảng cũ kết thúc mở ở T, không chồng lấn.

Các thao tác lập lịch và publish là giao dịch riêng có chủ đích. Nếu chỉ lập lịch rồi chưa công bố bản thay thế,
sau T có thể thiếu giá và quote bị từ chối rõ ràng. Không tự công bố hoặc tự duy trì giá dự phòng.
Người vận hành cần hoàn tất/kiểm tra công bố trước T; chưa có workflow gộp chuyển phiên bản hoặc thu hồi lịch ngừng.

## 7. Guest Quote, Admin preview và mô phỏng DRAFT

Guest `/quote` thu phương thức, dịch vụ, khu vực tỉnh/huyện/xã hai đầu, chi nhánh, cân và số đo; không thu tên,
điện thoại, địa chỉ nhà, COD hoặc giá trị hàng. Tìm chi nhánh dựa vùng phục vụ thật; HOME_PICKUP chỉ tự xác định
nguồn khi đúng một lựa chọn, BRANCH_DROPOFF phải chọn; đích phải phục vụ giao đến và có hub hợp lệ.

PublicQuoteView chỉ có tên dịch vụ/phương thức/thành phần phí/VND/thời gian ước tính/hạn tham khảo.
Không trả rule ID, version ID, giá mỗi bước, audit, contact hoặc token lưu quote.
Guest estimate **không giữ giá**, không persist shipment_quote/Shipment; cấu hình đổi thì phải tra lại.
Không dùng kết quả Guest làm đầu vào xác nhận tạo đơn.

Admin `/admin/pricing/preview` thử giá thật cùng engine, không ghi quote/Shipment.
`/admin/pricing/{id}/preview` mô phỏng DRAFT tại validFrom: ưu tiên dòng nháp đang bật cho scope/branch đang thử,
phần không có override dùng rule đã công bố/legacy hiệu lực tại mốc đó. Không chọn số tiền từ form.
Cho phép mô phỏng dịch vụ chưa kích hoạt; kiểm tra mạng lưới, số đo, cardinality và công thức vẫn áp dụng.
Mô phỏng không thay thế kiểm tra xung đột/công bố, không phát hành giá giữ chỗ và không làm draft dùng được bởi Guest/User.
ADMIN phải ACTIVE và có role thật khi gọi service mô phỏng; form serviceId phải đúng service của bản nháp.

## 8. Quote locking và bảo toàn Shipment

Giữ cơ chế Commit 09: User lấy quote lưu input/fees và nguồn rule; TTL mặc định PT15M, cấu hình hiện hữu,
không sửa application.properties. Hạn quote mới là min(TTL, effective_to và retired_from của các rule/version tham gia).
Quote phát hành **trước khi** lịch ngừng được lập vẫn giữ hạn đã lưu, không bị sửa âm thầm.

Khi xác nhận: khóa quote theo token + owner; nếu consumed trả đúng Shipment đã có. Nếu chưa consumed,
kiểm tra hạn, dịch vụ ACTIVE/giới hạn mới nhất và mạng lưới/hub, rồi dùng nguyên fees đã lưu, kể cả thời điểm xác nhận
đã sang giá phiên bản khác. Không tính lại hoặc sửa total từ client. Kiểm tra lại hạn/account sau chờ khóa dịch vụ.
Nếu điều kiện dịch vụ/mạng lưới không còn hợp lệ thì yêu cầu báo giá mới, không tự đổi giá của quote cũ.

Shipment giữ shipping_fee, pickup_fee, insurance_fee, total_fee và quote_id như trước.
Truy vết phiên bản qua Shipment.quote.shippingRateRule.pricingVersion và pickupFeeRule.pricingVersion;
hai thành phần có thể thuộc hai bản khác nhau theo phạm vi. Không thêm FK phiên bản đơn lẻ gây hiểu sai hoặc backfill giả.
Vận đơn legacy không quote/method giữ nguyên. Quản trị giá không đọc/ghi trạng thái, phí, sự kiện vật lý hoặc biên nhận giao/hoàn.

## 9. Phân quyền, validation và audit

SecurityConfig `/admin/**` cùng @PreAuthorize ADMIN tại controller/service, cộng kiểm tra User ACTIVE/ROLE_ADMIN thật.
EntityManager.refresh account chống trạng thái cache cũ; không suy quyền từ actorId. USER/MANAGER/SHIPPER không tự có ADMIN.
Không phân quyền theo tác giả version: mọi ADMIN hợp lệ quản trị toàn bộ catalog; chưa có chi nhánh/phạm vi Admin riêng.

Chỉ mở GET `/quote` và POST `/quote`, `/quote/branches` cho công khai. Các endpoint User/Manager/Shipper giữ quyền cũ.
POST có CSRF; mọi form allowlist. `expectedVersion`/`catalogVersion` là token so sánh, không bind vào Entity version;
không nhận status/publishedBy/actor/giá tổng từ client. ID route quyết định đối tượng; ruleId còn phải thuộc đúng version.
Validation số, độ dài, timestamp UTC, enum và dữ liệu tham chiếu nằm phía server; thông báo tiếng Việt,
không hiển thị Java binding exception/class name. HTML đầu vào được escape bằng th:text.

Tái sử dụng audit_logs, bổ sung AuditResourceType SHIPPING_SERVICE/PRICE_VERSION/PICKUP_RULE qua V9 CHECK.
Lưu actor FK, resource/id, action, before/after và UTC cùng transaction; không ghi email, thông tin khách hoặc secrets vào log.
Rule changes audit tại PRICE_VERSION, legacy retirement tại SHIPPING_SERVICE; UI lịch sử hiện actor ID và dữ liệu cấu hình.
Không sửa/xóa audit đã ghi.

## 10. Routes thực tế

| Method | Route | Chức năng |
|---|---|---|
| GET | `/admin/services`, `/admin/services/new` | Danh mục và form tạo |
| POST | `/admin/services` | Tạo dịch vụ ngừng nhận đơn |
| GET | `/admin/services/{id}`, `/admin/services/{id}/edit` | Chi tiết, legacy, audit và chỉnh thông tin |
| POST | `/admin/services/{id}`, `/admin/services/{id}/status` | Sửa thông tin hoặc bật/tắt nhận đơn |
| POST | `/admin/services/legacy/shipping/{ruleId}/retire` | Lập lịch ngừng rule legacy |
| POST | `/admin/services/legacy/pickup/{ruleId}/retire` | Lập lịch ngừng phụ phí legacy |
| GET | `/admin/pricing`, `/admin/pricing/new?serviceId=...` | Danh sách version/form tạo |
| POST | `/admin/pricing` | Tạo DRAFT |
| GET/POST | `/admin/pricing/{id}` | Chi tiết/xem trước bảng và sửa khoảng nháp |
| POST | `/admin/pricing/{id}/rules`, `/pickup-rules` | Tạo/sửa/bật/tắt dòng nháp |
| POST | `/admin/pricing/{id}/check`, `/publish`, `/retire` | Kiểm tra, công bố, lập lịch ngừng |
| GET/POST | `/admin/pricing/preview` | Thử tính giá đang hiệu lực |
| POST | `/admin/pricing/preview/branches` | Tìm chi nhánh cho công cụ thử giá |
| GET/POST | `/admin/pricing/{id}/preview` | Mô phỏng DRAFT |
| POST | `/admin/pricing/{id}/preview/branches` | Tìm chi nhánh mô phỏng |
| GET/POST | `/quote` | Form/ước tính công khai |
| POST | `/quote/branches` | Tìm chi nhánh công khai cho báo giá |

Hậu tố rút gọn trong cùng ô dùng đầy đủ prefix của ô đó. Không dùng GET thay đổi dữ liệu.
Link thêm tại nav Admin, login và tracking. Dùng Bootstrap/layout hiện có, form hỗ trợ context path.

## 11. Transaction và concurrency

shipping_services là gate theo service: PESSIMISTIC_WRITE cho quản trị, công bố, lập lịch ngừng và lúc lấy giá quote.
Sau khi chờ khóa, refresh entity từ database để không so version/giới hạn trên entity đã cache trong persistence context.
Version được tra serviceId dạng scalar trước khóa, sau đó mới đọc/refresh parent PricingVersion và so expectedVersion.
Sửa dòng con cũng touch parent version; hai request sửa cùng nháp không ghi đè nhau.

Publish giữ gate suốt validation overlap, cập nhật status/publishedAt và audit, cùng transaction.
Hai version khác nhau cùng service/phạm vi cũng tuần tự dưới gate, không chỉ khóa từng version riêng lẻ.
Quote lấy rule/thành phần phí ở cùng gate; không pha shipping mới với pickup cũ trong lúc publish bundle.
Confirm có thứ tự quote → service; quản trị không khóa quote/Shipment, không tạo đường khóa ngược.

Filtered unique bảo vệ công thức trùng trong một version; FK/CHECK/unique bảo vệ dạng dữ liệu và tham chiếu.
Khoảng thời gian giao nhau giữa các bản không thể giải quyết chỉ bằng UNIQUE đơn giản: mọi writer ứng dụng phải dùng gate.
SQL trực tiếp ngoài module không được coi là có bảo đảm overlap của service; cần kiểm soát quyền vận hành DB.

Test concurrency dùng hai thread và ReentrantLock tại repository boundary; transaction test dùng Spring interceptor,
chỉ xác minh quyết định commit/rollback. **Chưa chứng minh SQL Server row locking, isolation hoặc JDBC rollback.**

## 12. Migration V9

V9__add_service_catalog_and_pricing_management.sql:

- Mở rộng shipping_services: version, actor nullable, giới hạn tùy chọn và FK/CHECK.
- Tạo pricing_versions với identity, unique service/code, status/currency/time CHECK, actor/service FK NO ACTION và index lookup.
- Thêm nullable pricing_version_id/retired_from lên hai bảng rule; FK NO ACTION, CHECK ngừng legacy và index version.
- Thay UQ_pickup_fee_rules_start bằng filtered unique **legacy** cùng bộ branch/service/effective_from, giữ ý nghĩa dữ liệu cũ
  mà không cản hai bản nháp độc lập có cùng mốc bắt đầu. Filtered unique mới cho version/scope và version/branch.
- Mở rộng CHECK resource_type của audit_logs; không đổi giá trị audit cũ.

Không INSERT seed, không UPDATE giá/dữ liệu lịch sử, không xóa bảng giá cũ, không tạo giá mặc định,
không tạo quyền SQL hoặc đổi credentials. DEFAULT version 0 chỉ là token kỹ thuật của cột mới, không khẳng định lịch sử giá.
JPA LAZY, không CascadeType.ALL/REMOVE/orphanRemoval, VARCHAR enum, NVARCHAR text và DATETIME2(7) UTC.

## 13. Cấu hình ban đầu và kiểm thử thủ công

Chỉ áp dụng V9 có chủ đích trên database kiểm thử được phép sau review/backup; không khởi động app Flyway enabled ngoài kế hoạch.
Sau migration cần chạy Hibernate ddl-auto=validate và SQL integration tests thật (FK/CHECK/unique/overlap/gate/rollback).

1. Dùng ADMIN ACTIVE có role database thật; mở `/admin/services`. Không tạo tài khoản/ADMIN mẫu trong commit này.
2. Giữ dịch vụ/rule legacy hợp lệ nếu đã có. Với dịch vụ mới: tạo catalog (ngừng nhận đơn), tạo DRAFT từ chi tiết dịch vụ,
   nhập giá/cân theo chính sách được duyệt; không lấy số tiền trong test làm dữ liệu vận hành.
3. Chọn scope hợp lệ; thêm HOME_PICKUP surcharge cho các LOCAL_BRANCH ACTIVE cần cung cấp, từ danh sách có phân trang.
4. Cấu hình branch_service_areas thật, supports_delivery và parent hub hợp lệ theo quy trình mạng lưới hiện có;
   module không tự tạo chi nhánh, vùng hoặc tuyến.
5. Xem bảng nháp, thử mô phỏng cả phương thức/cân ở ngay ngưỡng và vượt một bước. Thiếu giá/phụ phí phải lỗi.
6. Chạy kiểm tra xung đột; nếu thay giá legacy hoặc bản cũ không có end, lập lịch ngừng tương lai tại cùng T với bản mới.
7. Công bố trước T có xác nhận. Thử double-submit, stale version, hai ADMIN khác nhau công bố nháp xung đột.
8. Kích hoạt dịch vụ có chủ đích. Kiểm tra `/quote` trước T, tại T và tại validTo. Guest không nhận token giữ giá.
9. USER tạo quote riêng trước khi lập lịch ngừng; xác nhận sau T nhưng trước expiry cũ phải giữ đúng tiền cũ.
   Quote lấy sau khi đã lập lịch phải có expiry không vượt T. Thay service trạng thái/giới hạn phải được kiểm tra khi confirm.
10. Thử gửi totalFee/actor/status/publishedBy giả, ruleId của version khác, thiếu CSRF, tài khoản bị khóa/thu hồi ADMIN.
11. Kiểm tra phí Shipment cũ, hub/pickup/giao/hoàn, Guest Tracking whitelist và nội dung audit trước/sau.
12. Kiểm tra trực quan trên trình duyệt/điện thoại, cả deployment có context path.

Chưa có vùng INTER_REGION, chuẩn hóa địa giới bằng ID, quy đổi thể tích, bảo hiểm, khuyến mãi, thuế/hóa đơn,
COD/thanh toán, nhiều tiền tệ, bulk import bảng giá, clone version, thu hồi/cải chính lịch ngừng hoặc chuyển giá gộp một thao tác.
Chưa có rate limit riêng/distributed cho Guest Quote; khi triển khai nhiều instance cần chính sách hạn mức chung phù hợp.
Chưa có SQL Server integration trong lần triển khai này; không coi Hibernate offline/Mockito là kiểm chứng database thật.

## 14. Kết quả kiểm tra và danh sách file

Kết quả ngày 23/10/2026, Java 21.0.2:

| Kiểm tra | Kết quả |
|---|---|
| `./mvnw.cmd clean test` | BUILD SUCCESS; 725 test, 724 passed, 0 failure, 0 error, 1 skipped |
| `./mvnw.cmd -DskipTests compile` | BUILD SUCCESS |
| `git diff --check` | Không lỗi |
| SQL Server thật / V9 / ddl-auto=validate | Chưa chạy; không bật test integration opt-in |
| FK/CHECK/unique/JDBC lock và rollback | Chưa kiểm chứng trên SQL Server thật |

Baseline Commit 01–13 có 637 test. Thêm 86 test qua PricingManagementTests (44), PricingTransactionTests (10),
PricingFlowTests (27), PricingServiceSecurityTests (5), và thêm 2 trường hợp trong QuoteServiceSecurityTests.
CoreDatabaseMappingTests kiểm tra 37 Entity/Repository, 127 quan hệ/FK LAZY và 27 unique constraint portable,
đối chiếu filtered indexes riêng và khởi tạo Spring Data repositories/JPQL bằng Hibernate offline.
Thymeleaf đã render qua MockMvc, gồm CSRF/context path, lỗi số bằng tiếng Việt và không lộ Java binding details.
Chưa kiểm tra trực quan trình duyệt/thiết bị thật với dữ liệu vận hành.

Cảnh báo còn lại: Maven metadata repository Flyway GitHub trả HTTP 401 nhưng build vẫn đạt; Mockito/ByteBuddy
tự gắn Java agent; javac có unchecked trong test; Hibernate offline dùng dialect tường minh, không có JDBC metadata.
Test bỏ qua là UteexpressApplicationTests SQL Server opt-in có sẵn, không phải test mới bị tắt để che lỗi.
Không thay cấu hình tích hợp cũ hoặc thử kết nối/đọc credentials; database thực dùng SQL Server Authentication
nên cần môi trường integration phù hợp khi kiểm chứng V9, không tự bật Windows integrated authentication.

Đã kiểm tra git status, diff stat, untracked và index; HEAD không đổi, không stage/commit/push.
Toàn bộ file mới bên dưới vẫn **untracked**, cần được người dùng đưa đủ vào commit sau này để tránh thiếu source.

### File mới / untracked

27 files.

```text
docs/service-pricing-management.md
src/main/java/com/ute/uteexpress/controller/AdminPricingController.java
src/main/java/com/ute/uteexpress/controller/PublicQuoteController.java
src/main/java/com/ute/uteexpress/dto/pricing/GuestQuoteForm.java
src/main/java/com/ute/uteexpress/dto/pricing/PricingCommands.java
src/main/java/com/ute/uteexpress/dto/pricing/PricingForms.java
src/main/java/com/ute/uteexpress/dto/pricing/PricingViews.java
src/main/java/com/ute/uteexpress/dto/pricing/PublicQuoteView.java
src/main/java/com/ute/uteexpress/entity/PricingVersion.java
src/main/java/com/ute/uteexpress/enums/PricingVersionStatus.java
src/main/java/com/ute/uteexpress/repository/PricingVersionRepository.java
src/main/java/com/ute/uteexpress/service/AdminPricingService.java
src/main/resources/db/migration/V9__add_service_catalog_and_pricing_management.sql
src/main/resources/templates/pricing/books.html
src/main/resources/templates/pricing/detail.html
src/main/resources/templates/pricing/estimate.html
src/main/resources/templates/pricing/fragments.html
src/main/resources/templates/pricing/new.html
src/main/resources/templates/pricing/rules.html
src/main/resources/templates/pricing/service-detail.html
src/main/resources/templates/pricing/service-form.html
src/main/resources/templates/pricing/services.html
src/test/java/com/ute/uteexpress/pricing/PricingManagementTests.java
src/test/java/com/ute/uteexpress/pricing/PricingScenario.java
src/test/java/com/ute/uteexpress/pricing/PricingTransactionTests.java
src/test/java/com/ute/uteexpress/security/PricingFlowTests.java
src/test/java/com/ute/uteexpress/security/PricingServiceSecurityTests.java
```

### File đã sửa

22 files.

```text
docs/shipment-quotes.md
src/main/java/com/ute/uteexpress/config/SecurityConfig.java
src/main/java/com/ute/uteexpress/entity/AuditLog.java
src/main/java/com/ute/uteexpress/entity/PickupFeeRule.java
src/main/java/com/ute/uteexpress/entity/ShippingRateRule.java
src/main/java/com/ute/uteexpress/entity/ShippingService.java
src/main/java/com/ute/uteexpress/enums/AuditResourceType.java
src/main/java/com/ute/uteexpress/repository/AuditLogRepository.java
src/main/java/com/ute/uteexpress/repository/BranchRepository.java
src/main/java/com/ute/uteexpress/repository/PickupFeeRuleRepository.java
src/main/java/com/ute/uteexpress/repository/ShippingRateRuleRepository.java
src/main/java/com/ute/uteexpress/repository/ShippingServiceRepository.java
src/main/java/com/ute/uteexpress/service/BranchNetworkService.java
src/main/java/com/ute/uteexpress/service/QuoteService.java
src/main/resources/templates/admin/fragments.html
src/main/resources/templates/auth/login.html
src/main/resources/templates/tracking/index.html
src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java
src/test/java/com/ute/uteexpress/security/QuoteServiceSecurityTests.java
src/test/java/com/ute/uteexpress/security/SecurityConfigTests.java
src/test/java/com/ute/uteexpress/service/QuoteServiceTests.java
src/test/java/com/ute/uteexpress/service/UserShipmentServiceTests.java
```
