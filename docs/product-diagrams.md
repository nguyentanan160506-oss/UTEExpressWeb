# Sơ đồ dùng trong báo cáo

Các file `.mmd` trong `docs/diagrams` là Mermaid source để chỉnh sửa. Các file `.svg` tương ứng có thể Insert → Pictures trong Microsoft Word rồi lưu .docx. Không chứa dữ liệu cá nhân, password hoặc OTP. Đây là mô tả kiến trúc, không phải bằng chứng thực thi.

- `system-architecture`: Chrome localhost → Spring MVC/Security → Services → JPA/SQL Server; notifications WebSocket; SMTP giả lập tách khỏi production JAR.
- `shipment-lifecycle`: hai phương thức nhập hàng, ba chặng xuôi, giao thành công/thất bại, thu hồi và ba chặng ngược trước khi trả người gửi. IN_TRANSIT lặp giữa các receipt; RETURNING giữ trong các chặng hoàn, không tự thành RETURNED khi đến nguồn.

Sơ đồ tóm tắt không thay thế guards về branch membership, assignment, version, receipt, proof hoặc ownership. Chi tiết nằm `ShipmentTransitionPolicy`, `TransportTripService`, `LastMileService`.
