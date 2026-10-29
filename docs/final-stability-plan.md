# Final Stability & Browser E2E — kế hoạch và rủi ro

Ngày 2026-10-29; branch feature/final-demo-data. Không commit/push/merge. Giữ nguyên 17 file FinalDemo đang untracked từ các lượt trước.

## Phát hiện/rủi ro ban đầu

1. 18085 đang chạy bằng Java 21, launcher FinalDemo được nhận diện; PID tại lúc kiểm tra 34456. 8083 cũng đang nghe. Không dừng/restart các tiến trình này hoặc tạo server trùng port.
2. Chưa có templates/error riêng: lỗi 401/403/404/500 có thể hiện Whitelabel. Cần giữ nguyên HTTP status và API problem JSON, không che lỗi hay redirect tùy tiện.
3. Hai tài khoản cùng Chrome Profile dùng chung phiên đăng nhập theo thiết kế. Cần profile/browser context độc lập. Incognito windows chung một phiên không tự thành các tài khoản độc lập.
4. FinalDemo còn dùng cookie mặc định JSESSIONID như ứng dụng khác trên cùng 127.0.0.1. Cookie không được cô lập theo port, có thể làm mất phiên/CSRF khi chuyển giữa ứng dụng. Đề xuất cookie riêng cho FinalDemo, không thay đổi ứng dụng 8083/18084.
5. Có 34 tỉnh tính giá nhưng coverage chỉ ở 35 phường. Form chưa hiển thị danh mục phục vụ thật. Cần hướng dẫn từ DB, không seed thêm/đoán chi nhánh/nới kiểm tra coverage. Trường district legacy vẫn bắt buộc.
6. Chưa có bằng chứng backup/khôi phục đủ điều kiện trước khi ghi thử SQL FinalDemo. Chỉ đọc + xác thực trên SQL cho đến khi gate này được đáp ứng. Không dùng giao dịch rollback để giả định các browser request thuộc cùng transaction.
7. Chưa đo query/trang hiện tại; không tối ưu dựa trên phỏng đoán. Bắt đầu đo navigation/TTFB trên localhost, phân trang, lỗi JS/5xx; chỉ chỉnh query khi có bằng chứng.

## Thứ tự kiểm tra

A. Lấy baseline Chrome headless trên server SQL hiện có: năm browser contexts độc lập; Guest và bốn vai trò; GET/read-only branch lookup/estimate, login/logout/CSRF/RBAC, viewport và timing. Không dùng Chrome profile cá nhân, không in credential/cookie/token.

B. Sửa theo nhóm trên source: trang lỗi thân thiện, cookie riêng FinalDemo và hướng dẫn vùng phục vụ từ dữ liệu đang ACTIVE. Regression embedded HTTP/H2 cho error status, API JSON, session/cookie cách ly và coverage không đoán địa bàn. Không sửa migrations V1–V15 hay giá/logic trạng thái.

C. Chạy Chrome journeys có ghi/phá hủy trên H2 cô lập trước: hai phương thức, báo giá, nguồn/hub/chặng cuối, giao thất bại/giao lại/hoàn, chứng từ, admin/pricing/support/OTP giả lập nội bộ nếu runner hiện có hỗ trợ. Báo riêng H2 với SQL, không gộp kết quả.

D. Chỉ apply source mới lên server SQL khi được phép restart riêng tiến trình 18085 sau khi patch/test reviewable; không đụng 8083/18084. Nếu chưa có phép restart, SQL browser baseline vẫn có giá trị, còn retest bản sửa SQL phải BLOCKED.

E. Maven verify, JavaScript và Chrome regression sau sửa, diff/secret/artifact scan; cập nhật báo cáo, lệnh chạy và kịch bản demo.

## Gate backup và khôi phục

`scripts/final-demo-stability-backup.sql` dành cho người quản trị chạy SSMS: đúng machine/instance/TCP/database, COPY_ONLY + CHECKSUM vào tên .bak UUID mới ở thư mục backup mặc định ngoài Git, rồi RESTORE VERIFYONLY. Không dùng INIT/FORMAT/REPLACE, không thực hiện RESTORE DATABASE. Quyền db_owner có thể BACKUP nhưng không đủ quyền VERIFYONLY; không tự tăng quyền login FinalDemo.

Sau VERIFYONLY, cần lưu đường dẫn backup ngoài repo. Khôi phục chỉ là phương án dự phòng: quản trị viên review FILELISTONLY và MOVE vào database cô lập được phê duyệt riêng; không restore đè FinalDemo hoặc database cũ khi chưa có phê duyệt cụ thể. VERIFYONLY không thay thế một restore rehearsal đã được kiểm chứng. Khi chưa chốt được phương án đủ an toàn, các bước ghi SQL sẽ BLOCKED và dùng H2 cho hành trình có ghi.

Nguồn quyền backup/verify: [BACKUP](https://learn.microsoft.com/en-us/sql/t-sql/statements/backup-transact-sql?view=sql-server-ver17), [RESTORE VERIFYONLY](https://learn.microsoft.com/en-us/sql/t-sql/statements/restore-statements-verifyonly-transact-sql?view=sql-server-ver17).

## Quyết định bổ sung

Người dùng chọn chỉ kiểm thử ghi trên H2; giữ SQL chế độ đọc/xác thực. Không chạy script backup hoặc bất kỳ bài ghi SQL nào trong lượt này. Source Manager tickets đã xác minh read-only: lỗi SQL 169 ở candidates do ORDER BY id lặp; sửa bỏ tie-breaker trùng, service SQL list/candidates đều PASS sau sửa.
