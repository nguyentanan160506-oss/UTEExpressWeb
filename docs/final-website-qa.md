# Commit 23 — Final website QA

Ngày kiểm tra: 28/10/2026 (UTC+7). Nhánh `feature/team-final-website-completion`, HEAD `017a4af` (main đã merge Commit 22). Đây là tiến độ theo phạm vi sản phẩm cuối, chưa phải kết luận hoàn tất.

## Các nhóm đã triển khai

1. Audit và hồ sơ/địa chỉ: [audit](final-product-audit.md), `AccountForms`, `AccountProfileController`, `AccountProfileService`, repository query theo owner, route Security và template. Dùng schema cũ, không migration. Kiểm tra CSRF, guest/role, ownership 404, validation, default address và revoked roles. Giữ bản vá dropdown từ lượt trước, không đổi bộ lọc/thuật toán giá.
2. SQL demo: `SqlDemoTarget`, `SqlDemoApplication`, `SqlDemoSeed`, `demo-sql.properties`, `scripts/sql-demo.ps1`. Inspect/migrate/seed/run tách biệt; target/credential riêng, kiểm tra identity mỗi connection, validate khi run, không auto seed/migrate. Bộ SMTP test-only được mở rộng có guard cho đúng SQL demo; inbox vẫn ADMIN. **Chưa kết nối SQL Server hoặc import DPAPI ở lượt này.**
3. QA: thêm guard/seed policy offline và H2, hồ sơ CRUD qua HTTP, mở rộng Chrome hành trình User mới OTP → hồ sơ/địa chỉ → lookup → quote → shipment. Thêm repository mock vào fixture Security dùng chung, giữ logic kiểm thử cũ.
4. Tài liệu: README sản phẩm, hướng dẫn Windows/SQL/credential/seed, kịch bản SQL demo, sơ đồ Mermaid+SVG dùng Word, báo cáo và release scope.

## Kết quả thực thi

| Hạng mục | Kết quả | Phạm vi |
|---|---|---|
| Targeted giai đoạn đầu | PASS 34/34 | LocalDemoQaTests, LocalDemoMailTests, SqlDemoSafetyTests |
| Maven clean test | PASS 1.485 total, 1.484 passed, 1 skipped | H2/offline; kết thúc 22:50:07 UTC+7, trước 7 test policy cuối |
| Maven clean verify cuối | PASS 1.493 total, 1.492 passed, 1 skipped | Kết thúc 22:59:41 UTC+7; gồm 8 policy/binding test, toàn bộ regression và package JAR |
| WebSocket backend | PASS 17/17 | NotificationWebSocketTests, network thật với repository mock theo test thiết kế |
| JavaScript | PASS 12/12 | notifications 6, operations 3, reports 3; Node runtime qua harness giữ assertions, không có Node CLI |
| Chrome headless cuối | PASS 365 / FAIL 0 / BLOCKED 0 | H2 độc lập 18082, 2026-10-28T16:02:51Z; đủ logistics, OTP/User mới, hồ sơ/địa chỉ, WS/reconnect, Admin; không Computer Use hoặc SQL |
| PowerShell AST | PASS | sql-demo.ps1 parse được bằng Windows PowerShell |
| Script guard không approval | PASS | Run bị từ chối trước đọc credential/kết nối SQL |
| Seed no-op | PASS trên H2 | Cùng mapped tables row count, password hash và tên User đã sửa không đổi; không phải SQL persistence |
| Sơ đồ | PASS XML và render headless | Hai SVG/nguồn Mermaid, không credential |
| JAR test/demo isolation | PASS, 0 forbidden entries | Không H2/DevTools, launcher/seed/SMTP, profile demo hoặc credential trong production JAR |
| SQL demo identity/migrate/validate | BLOCKED | Chưa có database/login demo được xác nhận và chưa cho phép ghi |
| SQL persistence sau restart | BLOCKED | Chưa chạy; không thay bằng kết quả H2 |
| SQL full logistics/browser/OTP | BLOCKED | Chờ môi trường và quyền SQL demo |
| Production-ready | Chưa xác nhận | Ngoài phạm vi laptop; không yêu cầu cloud |

Lần clean test đầu gặp lỗi import/overload trong test mới, đã sửa. Lần chạy toàn bộ kế tiếp phát hiện shared Security fixture thiếu mock UserAddressRepository khiến 257 test không load context; đã thêm mock, lần clean test sau đạt như trên. Không sửa production security để né lỗi test.

## Lỗi checkbox bắt được trong browser

Lượt Chrome đầu Commit 23 dừng ở 19 PASS / 1 FAIL / 1 BLOCKED: User mới không lưu được địa chỉ khi không chọn mặc định. Không có 5xx/JavaScript error. Đã tái hiện bằng ServletRequestDataBinder: checkbox unchecked không gửi tham số, record dùng primitive boolean nhận binding error. Regression `uncheckedDefaultCheckboxMustBindWithoutRequiringClientField` đã FAIL trước patch. Đổi riêng DTO thành Boolean và chuyển null → false trong service; entity/database vẫn BIT NOT NULL. Test binder sau patch PASS, HTTP regression kiểm cả checked/unchecked. Không bỏ validation hoặc CSRF. Clean verify cuối PASS và Chrome chạy lại trọn vẹn 365 PASS / 0 FAIL / 0 BLOCKED. Kết quả cũ bị dừng được giữ như lịch sử phát hiện lỗi, không dùng thay kết quả cuối.

Evidence browser cuối: `%TEMP%/uteexpress-browser-evidence-c023fac2767a4abc841d0c0d7e184757`; log Maven `%TEMP%/uteexpress-commit23-final-verify.log`; log browser `%TEMP%/uteexpress-commit23-final-browser.log`. Không có uncaught JS/console.error hoặc HTTP 5xx; runtime secret guard PASS. 365 là số checkpoint/assertion, không phải 365 hành trình độc lập. Live H2 của người dùng tại 8081 không bị dừng/restart.

## Các gate còn lại

- Chủ sở hữu tạo database UTEExpress_Demo và login uteexpress_demo chỉ cho DB này, secure DPAPI riêng. Xem [thao tác cụ thể](sql-demo-setup.md).
- Chủ sở hữu xác nhận target và quyền chạy Inspect → migrate V1–V13 DB trống → seed tham chiếu → app business writes. Mỗi bước phải dừng nếu không khớp/FAIL, không fallback/repair/reset.
- Sau migration/validate: kiểm thử thực tế seed no-op và giữ User mới, địa chỉ, quote/shipment, timeline/proof/receipt qua restart; chạy logistics, OTP, WS hai phiên trên SQL demo; cập nhật bằng chứng.
- Rehearsal bằng cửa sổ Chrome của người trình bày; headless không chứng minh mọi trải nghiệm/thẩm mỹ thao tác tay.
- Tất cả release gate cho SQL demo phải có bằng chứng trước khi đóng Commit 23. Kết luận hiện tại: **BLOCKED cho mục tiêu SQL demo bền vững**, không phải PRODUCTION-READY.

Không chỉnh V1–V13/pom/application.properties. Không SQL migration/seed ở lượt này; không đụng hai DB được bảo vệ, không Git write. Bảy checksum GO từ Commit 22 giữ nguyên; rủi ro và phạm vi đã xác nhận ở [audit](final-product-audit.md), [SQL validation cũ](sql-server-validation.md).

## File thay đổi

Tổng working tree so với HEAD: 40 file; 18 modified, 22 untracked, 0 deleted, 0 staged. Bao gồm 9 file có sẵn trước lượt này (7 modified, 2 untracked); giữ nguyên thay đổi trước đó, một số được mở rộng thêm. Không Git add/commit/push/merge/stash/switch/PR.

### File modified

- `README.md`
- `docs/browser-qa-results.json`
- `docs/browser-qa.md`
- `docs/demo-script.md`
- `docs/final-qa-report.md`
- `docs/release-checklist.md`
- `src/main/java/com/ute/uteexpress/config/SecurityConfig.java`
- `src/main/java/com/ute/uteexpress/controller/UserShipmentController.java`
- `src/main/java/com/ute/uteexpress/repository/UserAddressRepository.java`
- `src/main/resources/templates/auth/account.html`
- `src/main/resources/templates/shipments/create.html`
- `src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java`
- `src/test/java/com/ute/uteexpress/operations/DemoMailConfiguration.java`
- `src/test/java/com/ute/uteexpress/operations/DemoMailboxController.java`
- `src/test/java/com/ute/uteexpress/operations/LocalDemoMailTests.java`
- `src/test/java/com/ute/uteexpress/operations/LocalDemoQaTests.java`
- `src/test/java/com/ute/uteexpress/security/SecurityConfigTests.java`
- `src/test/java/com/ute/uteexpress/security/UserShipmentControllerTests.java`

### File mới (untracked)

- `docs/branch-dropdown-qa.md`
- `docs/diagrams/shipment-lifecycle.mmd`
- `docs/diagrams/shipment-lifecycle.svg`
- `docs/diagrams/system-architecture.mmd`
- `docs/diagrams/system-architecture.svg`
- `docs/final-product-audit.md`
- `docs/final-website-qa.md`
- `docs/product-diagrams.md`
- `docs/sql-demo-setup.md`
- `scripts/sql-demo.ps1`
- `src/main/java/com/ute/uteexpress/controller/AccountProfileController.java`
- `src/main/java/com/ute/uteexpress/dto/account/AccountForms.java`
- `src/main/java/com/ute/uteexpress/service/AccountProfileService.java`
- `src/main/resources/templates/auth/profile.html`
- `src/test/java/com/ute/uteexpress/demo/AccountProfilePolicyTests.java`
- `src/test/java/com/ute/uteexpress/demo/SqlDemoApplication.java`
- `src/test/java/com/ute/uteexpress/demo/SqlDemoSafetyTests.java`
- `src/test/java/com/ute/uteexpress/demo/SqlDemoSeed.java`
- `src/test/java/com/ute/uteexpress/demo/SqlDemoSeedTests.java`
- `src/test/java/com/ute/uteexpress/demo/SqlDemoTarget.java`
- `src/test/java/com/ute/uteexpress/operations/DemoShipmentAdvice.java`
- `src/test/resources/demo-sql.properties`
