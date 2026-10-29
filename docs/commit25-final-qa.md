# Commit 25 — nghiệm thu giá vận chuyển toàn quốc

Ngày kết sổ: 29/10/2026 (UTC+7). Branch local: `feature/auto-intake-receipts`; HEAD trước commit thủ công: `785d20a`.

**Kết luận: PASS trong phạm vi nghiệp vụ và SQL Server QA đã phê duyệt; đề nghị đóng scope Commit 25 sau review và thao tác Git thủ công.** Không chứng nhận production-ready hoặc việc triển khai checkout mới lên SQL demo đang chạy. Không bắt đầu Commit 26.

## 1. Bằng chứng SQL Server V15

Người dùng xác nhận kết quả thực tế:

```text
RESULT PASS required-gates migration-rollback=PASS database-retained-no-cleanup
```

Người dùng đồng thời xác nhận toàn bộ V1–V15, migration rollback, Hibernate validate, SQL constraints, 1.156 cặp tỉnh, pricing service, snapshot, RBAC và concurrency PASS. Đã đối chiếu source `V15SqlServerQaTool`: RESULT chỉ xuất hiện sau các gate cuối cùng; rollback PASS chỉ được ghi sau fault injection đủ proof và kiểm tra phục hồi history/schema/index/legacy.

**Nguồn chứng nhận SQL là kết quả Run do chủ dự án cung cấp.** Lượt kết sổ không chạy lại Run, không Inspect/Preflight/SELECT, không import DPAPI và không kết nối database để tái xác nhận. Không có timestamp Run hoặc transcript/history/row counts mới được cung cấp; không điền các số liệu đó bằng suy đoán. Chi tiết: [SQL Server V15](v15-sql-server-qa.md).

| Mục tiêu cố định của helper đã PASS | Giá trị |
|---|---|
| Machine / instance / TCP | `DESKTOP-31GJQ3T` / `SQLEXPRESS` / `127.0.0.1:1433` |
| Database QA V15 | `UTEExpress_V15_QA_20261029` |
| Login riêng | `uteexpress_v15qa` |
| Credential | DPAPI TEMP riêng ngoài Git; không đọc/in/copy ở lượt này |
| Trạng thái sau Run | Đã có schema/history/fixture; giữ nguyên, không còn fresh database |

| Gate SQL đã xác nhận | Kết quả |
|---|---|
| Identity/catalog/DB_NAME, quyền QA, không access bốn database được bảo vệ; fresh **trước Run** | PASS |
| Flyway V1–V14 nguyên bản và validate baseline | PASS |
| V15 nguyên bản, history SUCCESS, checksum **347627752** | PASS |
| 16 cột nullable snapshot, DECIMAL precision/scale, scope cũ/mới, FK/index/CHECK trusted/enabled | PASS |
| Default pickup branch NULL; duplicate default/version bị UNIQUE chặn | PASS |
| Snapshot đầy đủ được nhận; thiếu/âm/sai phí/scope/weight và overflow bị từ chối đúng ràng buộc | PASS |
| Legacy branch/hub/service/version/rates/pickup/shipment/quote/payment giữ nguyên; snapshot cũ vẫn NULL | PASS |
| Fault migration rollback đủ binary/SQL transaction proof; DDL/history/index/legacy phục hồi | PASS, không SKIP |
| Flyway validate và Hibernate `ddl-auto=validate` schema V15 thực, không V14 ORM override | PASS |
| Service confirm/event/quote consumption rollback | PASS |
| 1.156 cặp × hai phương thức; biên trọng lượng, giới hạn dịch vụ, pickup override/global fallback | PASS |
| Admin tạo/publish/ngừng phiên bản; snapshot đơn cũ và held quote không bị định giá lại | PASS |
| Ownership/RBAC DB, từ chối ngoài coverage, client fee binding và hai thread confirm đúng một đơn/event | PASS |

Các gate bắt buộc không còn FAIL/BLOCKED; fault injection đã PASS thay vì SKIP. Không cộng 2.312 lần estimate thành 2.312 test Maven.

## 2. Migration và checksum

V14: `V14__add_generated_origin_intake_receipts.sql`, checksum **142205694**, nghiệm thu riêng trong [Commit 24](commit24-final-qa.md).

V15: `V15__add_nationwide_shipping_pricing.sql`, checksum **347627752**, SHA-256 `faa2cf1cb51b3407b0fe642640b5479e99ee3709e1e8b2b301b84695e1696367`; hai SQL Server batches transactional. Không sửa migration V1–V15 trong lượt kết sổ.

Danh sách sau là checksum **source hiện tại tính offline theo CRC32 từng dòng không newline/BOM**; V15 còn được kiểm tra bằng Flyway `ChecksumCalculator` trong safety test. Trạng thái SUCCESS của history thuộc xác nhận Run của người dùng, không phải SELECT mới trong lượt này.

| Version | Checksum source | Trạng thái QA được xác nhận |
|---|---:|---|
| V1 | 189739357 | SUCCESS |
| V2 | 1191373203 | SUCCESS |
| V3 | -1720389847 | SUCCESS |
| V4 | -1466061241 | SUCCESS |
| V5 | 1925825129 | SUCCESS |
| V6 | -1705174727 | SUCCESS |
| V7 | 444247411 | SUCCESS |
| V8 | 2036200466 | SUCCESS |
| V9 | 61067824 | SUCCESS |
| V10 | 236607534 | SUCCESS |
| V11 | 2051557719 | SUCCESS |
| V12 | -1057022707 | SUCCESS |
| V13 | 1007916434 | SUCCESS |
| V14 | 142205694 | SUCCESS |
| V15 | 347627752 | SUCCESS |

Bảy bản vá GO V3/V4/V8/V9/V10/V11/V12 đã thuộc HEAD Commit 22–23, **không có diff mới** trong working tree này. Database từng áp dụng checksum cũ sẽ không tự tương thích; không repair/history edit/clean để vượt validation. V14/V15 hiện đã SUCCESS trong QA tương ứng nên cũng phải giữ bất biến. Không tự nâng `UTEExpressDB`, `UTEExpress_Test`, `UTEExpress_Demo` hoặc QA V14 lên V15 sau khi merge source.

## 3. Kiểm tra offline và bằng chứng lịch sử

| Kiểm tra | Kết quả và nguồn |
|---|---|
| Targeted lượt kết sổ | **75/75 PASS**, 0 failures/errors/skips; Java 21, Maven offline, SQL opt-in false; kết thúc 29/10/2026 12:00:10 UTC+7 |
| Phạm vi targeted | V14/V15 safety + callback/mapping/parser/migration adapter + nationwide pricing integration H2 |
| Full Maven gần nhất | **1.616 total / 1.615 passed / 1 SQL opt-in skipped**, 0 failure/error, ở lượt chuẩn bị V15; không chạy full lại vì chỉ docs/ignore thay đổi |
| JAR package gần nhất | PASS ở lượt chuẩn bị; archive hiện tại kiểm tra lại PASS: không helper/fixture/test/DPAPI; 15 migration và catalog khớp byte-for-byte source |
| Chrome headless H2 | **399 PASS / 0 FAIL / 0 BLOCKED**, bằng chứng lịch sử JSON có thời điểm `2026-10-28T21:25:19Z`; không chạy browser lại trong lượt này |
| JavaScript / WebSocket | 12/12 và 17/17 lịch sử; không sửa JS/WebSocket, không cộng lại vào tổng Maven |
| PowerShell AST / diff / file scan | PASS: 6 scripts + 5 blocks lệnh Git chỉ parse, `git diff --check`, whitespace 120 file, UTF-8/local links và manifest khớp status |

Logs offline nằm `target/commit25-close-targeted.log` và `target/v15-*.log`, bị Git ignore; không upload logs hoặc raw SQL/browser traces. Surefire chứa kết quả của những lớp vừa chạy, không lấy tổng XML lẫn các lượt để thay số Maven log. Cảnh báo còn lại: Mockito dynamic agent Java 21, H2 mới hơn bản Flyway đã verify, unchecked test cũ và một số LF→CRLF; không làm test thất bại.

## 4. Secret, artifact và phạm vi thay đổi

Rà soát toàn bộ file tracked/untracked có thể đưa lên Git (574 file tại đầu lượt, **576 cuối lượt**, 0 findings): không thấy private key/token theo mẫu, JWT/cookie/quote-token URL thô, password cấu hình literal hoặc credential/log/archive/temp trong candidate set. Scan path riêng, kể cả file ignored ngoài `target`/`.git`, cũng không thấy DPAPI/private-key/backup DB/HAR/.env thật trong repository. Scan chỉ báo path/rule/line, không in matched value, không đọc DPAPI hoặc file `.env` thật. Đây là static scan có giới hạn, không chứng minh tuyệt đối mọi dạng secret; vẫn cần review staged diff trước commit.

Thêm ba pattern `.gitignore` cho DPAPI credential/candidate/backup để tránh vô tình đưa file tạm lên Git. Không sửa/xóa credential đang hoạt động. `target/`, `.env` và `*.log` đã được ignore; fixture CSV academic được giữ trong test resources, danh mục 34 tỉnh là public production resource, migration SQL là source cần commit.

Danh sách đầy đủ và phân loại code Commit 24/25, test/QA, migration, docs: [commit24-25-git-plan.md](commit24-25-git-plan.md). Git status cuối: **66 tracked modified + 54 untracked = 120 file, staged 0, deleted 0**. Trong lượt kết sổ **tạo 2 file, sửa 5 file**, không phải toàn bộ 120 file đều do lượt này tạo/sửa. Hash snapshot của 117 file có sẵn xác nhận chỉ bốn docs trong số đó đổi; `.gitignore` là tracked trước đó sạch nay thêm ignore patterns, cùng hai docs mới. Toàn bộ source/helper/migrations/test giữ nguyên. Không thay `pom.xml`, application properties, app cổng 8083 hoặc bất kỳ database nào.

File lượt này: tạo `docs/commit25-final-qa.md`, `docs/commit24-25-git-plan.md`; sửa `.gitignore`, `docs/v15-sql-server-qa.md`, `docs/release-checklist.md`, `docs/final-qa-report.md`, `docs/nationwide-shipping-pricing.md`.

## 5. Giới hạn và quyết định kết thúc

- **NOT RUN:** browser E2E trên SQL Server, JAR/HTTP/browser với schema V15 và persistence sau restart SQL demo của checkout mới. Chrome H2 PASS và Hibernate/service SQL PASS là các bằng chứng khác nhau.
- **NOT RUN:** production load, độ trễ/lock/index rebuild trên dữ liệu lớn, nhiều application instance, backup/restore production hoặc vận hành HA. Hai thread confirm chỉ chứng minh trường hợp concurrent QA đã chạy.
- Fixture legacy là tổng hợp, tối thiểu; không phải nâng cấp mọi dữ liệu thật hoặc mọi môi trường triển khai.
- Method RBAC/owner kiểm qua DB; MockMvc binder standalone không phải full SQL HTTP/CSRF/login E2E. Các HTTP/browser protections có regression H2/MVC trước đó.
- Launcher SQL demo vẫn chặn checkout cần V15 vì policy chỉ đến V14. QA PASS không cấp phép sửa guard, nâng database demo hoặc restart ứng dụng 8083. Phải tách việc triển khai demo có phê duyệt khỏi Git review/merge source.

**Đủ điều kiện kết thúc phạm vi Commit 24/25 về implementation và QA đã thống nhất**, sau khi chủ dự án review và thực hiện Git. Chưa production-ready; không mở rộng vô hạn hoặc tự triển khai Commit 26. Bước còn lại của lượt này là chọn phương án commit trong kế hoạch Git, review staged snapshot/CI rồi mới push/merge theo phê duyệt riêng.
