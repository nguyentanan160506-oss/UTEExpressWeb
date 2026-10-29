# Review và kế hoạch Git Commit 24/25

Ngày 29/10/2026. **Chỉ đề xuất; chưa chạy bất kỳ lệnh Git ghi nào.** Branch `feature/auto-intake-receipts`, HEAD `785d20a`; origin được kiểm tra offline trỏ GitHub `nguyentanan160506-oss/UTEExpressWeb.git`, không có credential/query trong URL. Không fetch/push trong lượt này.

## Trạng thái và phân loại

Snapshot cuối lượt: **66 tracked modified + 54 untracked = 120 file**, staged 0, deleted 0. Đầu lượt 117 file; lượt này tạo hai docs, sửa năm file có sẵn (`.gitignore`, V15 QA, release checklist, final QA report, nationwide pricing). Không sửa code/helper/migrations/test; không thay database hoặc app 8083.

Phân chia đề xuất: **33 file C24 toàn bộ + phần C24 của 5 file MIX** vào commit biên nhận; **82 file còn lại + phần còn lại của 5 file MIX** vào commit giá toàn quốc và nghiệm thu. Các docs/evidence lịch sử gộp vào commit thứ hai để không lưu báo cáo cuối của cả hai trong commit core trung gian. Số file không phải số chức năng/test.

- C24: biên nhận nguồn tự sinh cả HOME_PICKUP/BRANCH_DROPOFF, quyền trước replay, idempotency chính xác, khóa/UNIQUE/transaction, form/timeline và V14 legacy an toàn.
- C25: catalog public 34 tỉnh, năm nhóm, một QuoteService/formula, default pickup NULL và branch override, version publish/audit/snapshot, V15, UI/validation và fixture giá test-only.
- QA14+25: helper V14 cuối được dùng trên checkout đã có V15, **có phụ thuộc ORM override cho snapshot mới**; giữ toàn bộ ở commit thứ hai. Không đưa nguyên bộ vào C24 trung gian rồi khiến mapping test đòi thuộc tính V15 chưa tồn tại.
- QA25: helper V15 và tests guard/parser/JDBC/H2, chỉ test scope; không production JAR.
- DOC: báo cáo, browser JSON đã loại runtime secrets, checklist/ignore. Bảng dưới liệt kê đầy đủ, không gộp `target`/TEMP vào file cần commit.

## 5 file phải chia hunk

| File | Chọn cho C24 | Để lại C25 |
|---|---|---|
| `CoreDatabaseMappingTests.java` | Comment baseline V14; thêm **chỉ filename V14** vào list migration | Filename V15 và parser `ALTER COLUMN` |
| `SqlDemoApplication.java` | Target/history 13→14 | Lời gọi và method `requireCompatibleSchemaPolicy` chặn checkout V15 |
| `BrowserLogisticsJourneys.java` | Bỏ receipt nhập tay, kiểm mã RCV | Kỳ vọng phí 52.500→60.000 |
| `BrowserQaTool.java` | Intake/dropoff/handoff không gửi reference và kiểm RCV | Giá quote mới và gọi `BrowserNationwidePricingJourney` |
| `LocalDemoSeed.java` | Constructor `BranchIntakeCommand` bỏ reference | `NationwideDemoPricing.seed` thay giá generic |

Chọn `s` để tách hunk hoặc `e` để chỉnh patch ở `git add -p`; không chọn `a` cho nguyên file MIX. `CoreDatabaseMappingTests` có V14/V15 trên cùng dòng nên cần `e`: C24 kết thúc list ở `V14__add_generated_origin_intake_receipts.sql`, chưa có V15/ALTER COLUMN parser. Không đổi working tree để stage.

**Chưa chứng nhận build riêng của snapshot C24 đã stage**: lượt này không được phép tạo index/tree hoặc commit. Phải kiểm thử staged snapshot cô lập như lệnh dưới trước commit. Full working tree hiện có cả C24/C25; chạy tests trực tiếp trên nó không chứng minh intermediate C24 độc lập.

Nếu không muốn chia hunk thủ công, có thể xin duyệt một commit kết hợp với toàn bộ tree đã QA. Không giả vờ đã tách an toàn hoặc để migration V15 thiếu entity/test tương ứng.

## Manifest đầy đủ

`MIX` chia hunk; `QA14+25`, `QA25`, `DOC`, `C25` stage ở commit thứ hai. Git `??` là file mới, ` M` là tracked sửa. Browser JSON là evidence H2 399 checkpoint; logs/raw secrets không nằm trong manifest.

| Scope | Loại | File | Git |
|---|---|---|---|
| DOC | Git hygiene | `.gitignore` | ` M` |
| C24 | Docs/evidence | `docs/automatic-origin-receipts.md` | `??` |
| DOC | Docs/evidence | `docs/browser-qa-results.json` | ` M` |
| DOC | Docs/evidence | `docs/browser-qa.md` | ` M` |
| DOC | Docs/evidence | `docs/commit24-25-git-plan.md` | `??` |
| DOC | Docs/evidence | `docs/commit24-final-qa.md` | `??` |
| DOC | Docs/evidence | `docs/commit25-final-qa.md` | `??` |
| DOC | Docs/evidence | `docs/final-qa-report.md` | ` M` |
| DOC | Docs/evidence | `docs/nationwide-shipping-pricing.md` | `??` |
| DOC | Docs/evidence | `docs/release-checklist.md` | ` M` |
| C24 | Docs/evidence | `docs/sql-demo-setup.md` | ` M` |
| DOC | Docs/evidence | `docs/v14-null-context-investigation.md` | `??` |
| DOC | Docs/evidence | `docs/v14-sql-server-qa.md` | `??` |
| DOC | Docs/evidence | `docs/v15-sql-server-qa.md` | `??` |
| QA14+25 | Helper opt-in | `scripts/v14-sql-qa.ps1` | `??` |
| QA25 | Helper opt-in | `scripts/v15-sql-qa.ps1` | `??` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/controller/ManagerHandoffController.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/controller/ManagerLogisticsController.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/controller/PublicQuoteController.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/controller/ShipperPickupController.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/controller/UserShipmentController.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/dto/manager/ManagerForms.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/pricing/GuestQuoteForm.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/pricing/PriceBreakdown.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/pricing/PricingCommands.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/pricing/PricingForms.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/pricing/PublicQuoteView.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipment/BranchIntakeCommand.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipment/HandoffReviewCommand.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipment/OriginIntakeResult.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipment/ShipmentDetailView.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipment/ShipmentQuoteView.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipper/PickupForms.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/dto/shipper/PickupViews.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/entity/PickupFeeRule.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/entity/Shipment.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/entity/ShipmentIntake.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/entity/ShipmentQuote.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/entity/ShippingPriceSnapshot.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/entity/ShippingRateRule.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/enums/RateScopeType.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/enums/ShippingRegion.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/repository/PickupFeeRuleRepository.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/service/AdminPricingService.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/service/ManagerHandoffService.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/service/OriginReceiptCodes.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/service/ProvinceCatalog.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/service/QuoteService.java` | ` M` |
| C24 | Code/UI | `src/main/java/com/ute/uteexpress/service/ShipmentLifecycleService.java` | ` M` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/service/ShippingChargeCalculator.java` | `??` |
| C25 | Code/UI | `src/main/java/com/ute/uteexpress/service/UserShipmentService.java` | ` M` |
| C25 | Catalog public | `src/main/resources/catalog/vietnam-provinces-2025.csv` | `??` |
| C24 | Migration | `src/main/resources/db/migration/V14__add_generated_origin_intake_receipts.sql` | `??` |
| C25 | Migration | `src/main/resources/db/migration/V15__add_nationwide_shipping_pricing.sql` | `??` |
| C24 | Code/UI | `src/main/resources/templates/manager/detail.html` | ` M` |
| C24 | Code/UI | `src/main/resources/templates/manager/handoff-detail.html` | ` M` |
| C25 | Code/UI | `src/main/resources/templates/pricing/detail.html` | ` M` |
| C25 | Code/UI | `src/main/resources/templates/pricing/estimate.html` | ` M` |
| C25 | Code/UI | `src/main/resources/templates/pricing/rules.html` | ` M` |
| C25 | Code/UI | `src/main/resources/templates/shipments/create.html` | ` M` |
| C25 | Code/UI | `src/main/resources/templates/shipments/detail.html` | ` M` |
| C25 | Code/UI | `src/main/resources/templates/shipments/quote.html` | ` M` |
| MIX | Test/demo/QA | `src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java` | ` M` |
| MIX | Test/demo/QA | `src/test/java/com/ute/uteexpress/demo/SqlDemoApplication.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/demo/SqlDemoSafetyTests.java` | ` M` |
| MIX | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/BrowserNationwidePricingJourney.java` | `??` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java` | ` M` |
| MIX | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/CustomerJourneySupport.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/CustomerShipmentJourneyTests.java` | ` M` |
| MIX | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/LogisticsConcurrencyIntegrationTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/LogisticsGuardIntegrationTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/LogisticsJourneySupport.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/LogisticsMvcIntegrationTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/LogisticsRollbackIntegrationTests.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/NationwideDemoPricing.java` | `??` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/NationwidePricingIntegrationTests.java` | `??` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/operations/OriginIntakeIntegrationTests.java` | `??` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/pricing/PricingManagementTests.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/pricing/ProvinceCatalogTests.java` | `??` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/NationwidePricingMigrationTests.java` | `??` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/OriginIntakeMigrationTests.java` | `??` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java` | ` M` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14CallbackLifecycleTests.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14HelperRegressionTests.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14MappingOverrideTests.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14QaDiagnostics.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14QaFixtures.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14QaTarget.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14ReadOnlyInspection.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java` | `??` |
| QA14+25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v14/V14TransactionProbe.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15BusinessChecks.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15BusinessHarnessTests.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15CallbackLifecycleTests.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15ConstraintChecks.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15HelperRegressionTests.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15LegacyFixtures.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15QaDiagnostics.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15QaSafetyTests.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15QaTarget.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15ReadOnlyInspection.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15SchemaChecks.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15SourceReview.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15Sql.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15SqlServerQaTool.java` | `??` |
| QA25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/release/v15/V15TransactionProbe.java` | `??` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/security/LogisticsServiceSecurityTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/security/ManagerLogisticsControllerTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/security/ManagerLogisticsFlowTests.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/security/PricingFlowTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/security/ShipperPickupFlowTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/service/ManagerLifecycleTests.java` | ` M` |
| C24 | Test/demo/QA | `src/test/java/com/ute/uteexpress/service/PickupLifecycleTests.java` | ` M` |
| C25 | Test/demo/QA | `src/test/java/com/ute/uteexpress/service/UserShipmentServiceTests.java` | ` M` |
| C25 | Test/demo/QA | `src/test/resources/demo/nationwide-shipping-prices.csv` | `??` |
| QA14+25 | Test/demo/QA | `src/test/resources/orm-v14-qa.xml` | `??` |

## Lệnh dự kiến — chỉ sau khi chủ dự án phê duyệt

Các block sau chưa được thực thi. Dừng ở lỗi đầu tiên; không clean/reset/stash/force-push. Không chạy SQL helper, migrate/seed hoặc app startup. Không đổi branch hiện tại.

### 1. Preflight Git chỉ đọc

```powershell
cd D:\WebCode\uteexpress\uteexpress
$ErrorActionPreference = 'Stop'
if ((git branch --show-current).Trim() -cne 'feature/auto-intake-receipts') { throw 'Wrong branch' }
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw 'Index is not empty; stop and review' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Diff check failed' }
git status --short --untracked-files=all
```

### 2. Stage C24 theo danh sách cố định và hunk

```powershell
$commit24 = @(
    'docs/automatic-origin-receipts.md'
    'docs/sql-demo-setup.md'
    'src/main/java/com/ute/uteexpress/controller/ManagerHandoffController.java'
    'src/main/java/com/ute/uteexpress/controller/ManagerLogisticsController.java'
    'src/main/java/com/ute/uteexpress/controller/ShipperPickupController.java'
    'src/main/java/com/ute/uteexpress/dto/manager/ManagerForms.java'
    'src/main/java/com/ute/uteexpress/dto/shipment/BranchIntakeCommand.java'
    'src/main/java/com/ute/uteexpress/dto/shipment/HandoffReviewCommand.java'
    'src/main/java/com/ute/uteexpress/dto/shipment/OriginIntakeResult.java'
    'src/main/java/com/ute/uteexpress/dto/shipper/PickupForms.java'
    'src/main/java/com/ute/uteexpress/dto/shipper/PickupViews.java'
    'src/main/java/com/ute/uteexpress/entity/ShipmentIntake.java'
    'src/main/java/com/ute/uteexpress/service/ManagerHandoffService.java'
    'src/main/java/com/ute/uteexpress/service/OriginReceiptCodes.java'
    'src/main/java/com/ute/uteexpress/service/ShipmentLifecycleService.java'
    'src/main/resources/db/migration/V14__add_generated_origin_intake_receipts.sql'
    'src/main/resources/templates/manager/detail.html'
    'src/main/resources/templates/manager/handoff-detail.html'
    'src/test/java/com/ute/uteexpress/operations/CustomerShipmentJourneyTests.java'
    'src/test/java/com/ute/uteexpress/operations/LogisticsConcurrencyIntegrationTests.java'
    'src/test/java/com/ute/uteexpress/operations/LogisticsGuardIntegrationTests.java'
    'src/test/java/com/ute/uteexpress/operations/LogisticsJourneySupport.java'
    'src/test/java/com/ute/uteexpress/operations/LogisticsMvcIntegrationTests.java'
    'src/test/java/com/ute/uteexpress/operations/LogisticsRollbackIntegrationTests.java'
    'src/test/java/com/ute/uteexpress/operations/OriginIntakeIntegrationTests.java'
    'src/test/java/com/ute/uteexpress/release/OriginIntakeMigrationTests.java'
    'src/test/java/com/ute/uteexpress/release/SqlServerBusinessSmoke.java'
    'src/test/java/com/ute/uteexpress/security/LogisticsServiceSecurityTests.java'
    'src/test/java/com/ute/uteexpress/security/ManagerLogisticsControllerTests.java'
    'src/test/java/com/ute/uteexpress/security/ManagerLogisticsFlowTests.java'
    'src/test/java/com/ute/uteexpress/security/ShipperPickupFlowTests.java'
    'src/test/java/com/ute/uteexpress/service/ManagerLifecycleTests.java'
    'src/test/java/com/ute/uteexpress/service/PickupLifecycleTests.java'
)
$mixed = @(
    'src/test/java/com/ute/uteexpress/CoreDatabaseMappingTests.java'
    'src/test/java/com/ute/uteexpress/demo/SqlDemoApplication.java'
    'src/test/java/com/ute/uteexpress/operations/BrowserLogisticsJourneys.java'
    'src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java'
    'src/test/java/com/ute/uteexpress/operations/LocalDemoSeed.java'
)
git add -- $commit24
if ($LASTEXITCODE -ne 0) { throw 'C24 add failed' }
git add -p -- $mixed
if ($LASTEXITCODE -ne 0) { throw 'Mixed hunk staging stopped' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed' }
git diff --cached --stat
git diff --cached
```

Review thủ công rằng index không có V15, `ShippingPriceSnapshot`, `ProvinceCatalog`, pricing CSV hoặc lời gọi class V15 mới; working tree giữ chúng cho C25. Đọc lại 5 hunk theo bảng trên. Staged secret scan/review bắt buộc trước commit, không chỉ dựa vào `.gitignore`.

### 3. Kiểm thử chính xác staged snapshot, không stash hay đổi branch

Function dưới **chỉ dùng sau phê duyệt Git**, vì `git write-tree` ghi tree object local. Nó không sửa branch/working files; archive nằm TEMP mới, Maven chỉ H2/mock với SQL opt-in false. Không gọi helper CLI hoặc chạm app 8083.

```powershell
function Test-StagedSnapshot([string]$label) {
    $stageDir = Join-Path $env:TEMP ('uteexpress-' + $label + '-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stageDir | Out-Null
    $tree = (git write-tree).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot snapshot index' }
    $archive = Join-Path $stageDir 'source.zip'
    git archive --format=zip --output=$archive $tree
    if ($LASTEXITCODE -ne 0) { throw 'Cannot archive staged tree' }
    $source = Join-Path $stageDir 'source'
    Expand-Archive -LiteralPath $archive -DestinationPath $source
    Push-Location -LiteralPath $source
    try {
        $env:JAVA_HOME = 'D:\WebCode\Java\jdk-21'
        $env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
        .\mvnw.cmd -o '-Duteexpress.sqlserver.validate=false' verify
        if ($LASTEXITCODE -ne 0) { throw 'Staged snapshot verification failed; do not commit' }
    } finally { Pop-Location }
}
Test-StagedSnapshot 'commit24'
# Chỉ khi verify và staged review đều PASS:
git commit -m "feat(logistics): generate origin intake receipts automatically"
if ($LASTEXITCODE -ne 0) { throw 'C24 commit failed' }
```

Nếu archive thiếu Maven distribution/dependencies cache thì gate BLOCKED, chuẩn bị cache riêng; không fallback SQL hoặc bỏ tests. Giữ archive QA ngoài Git; không tự xóa workspace để xử lý lỗi. Đây là kế hoạch kiểm thử tương lai, chưa được gọi trong lượt này.

### 4. Stage và kiểm thử C25 + nghiệm thu

```powershell
$commit25Only = @(
    '.gitignore'
    'docs/browser-qa-results.json'
    'docs/browser-qa.md'
    'docs/commit24-25-git-plan.md'
    'docs/commit24-final-qa.md'
    'docs/commit25-final-qa.md'
    'docs/final-qa-report.md'
    'docs/nationwide-shipping-pricing.md'
    'docs/release-checklist.md'
    'docs/v14-null-context-investigation.md'
    'docs/v14-sql-server-qa.md'
    'docs/v15-sql-server-qa.md'
    'scripts/v14-sql-qa.ps1'
    'scripts/v15-sql-qa.ps1'
    'src/main/java/com/ute/uteexpress/controller/PublicQuoteController.java'
    'src/main/java/com/ute/uteexpress/controller/UserShipmentController.java'
    'src/main/java/com/ute/uteexpress/dto/pricing/GuestQuoteForm.java'
    'src/main/java/com/ute/uteexpress/dto/pricing/PriceBreakdown.java'
    'src/main/java/com/ute/uteexpress/dto/pricing/PricingCommands.java'
    'src/main/java/com/ute/uteexpress/dto/pricing/PricingForms.java'
    'src/main/java/com/ute/uteexpress/dto/pricing/PublicQuoteView.java'
    'src/main/java/com/ute/uteexpress/dto/shipment/ShipmentDetailView.java'
    'src/main/java/com/ute/uteexpress/dto/shipment/ShipmentQuoteView.java'
    'src/main/java/com/ute/uteexpress/entity/PickupFeeRule.java'
    'src/main/java/com/ute/uteexpress/entity/Shipment.java'
    'src/main/java/com/ute/uteexpress/entity/ShipmentQuote.java'
    'src/main/java/com/ute/uteexpress/entity/ShippingPriceSnapshot.java'
    'src/main/java/com/ute/uteexpress/entity/ShippingRateRule.java'
    'src/main/java/com/ute/uteexpress/enums/RateScopeType.java'
    'src/main/java/com/ute/uteexpress/enums/ShippingRegion.java'
    'src/main/java/com/ute/uteexpress/repository/PickupFeeRuleRepository.java'
    'src/main/java/com/ute/uteexpress/service/AdminPricingService.java'
    'src/main/java/com/ute/uteexpress/service/ProvinceCatalog.java'
    'src/main/java/com/ute/uteexpress/service/QuoteService.java'
    'src/main/java/com/ute/uteexpress/service/ShippingChargeCalculator.java'
    'src/main/java/com/ute/uteexpress/service/UserShipmentService.java'
    'src/main/resources/catalog/vietnam-provinces-2025.csv'
    'src/main/resources/db/migration/V15__add_nationwide_shipping_pricing.sql'
    'src/main/resources/templates/pricing/detail.html'
    'src/main/resources/templates/pricing/estimate.html'
    'src/main/resources/templates/pricing/rules.html'
    'src/main/resources/templates/shipments/create.html'
    'src/main/resources/templates/shipments/detail.html'
    'src/main/resources/templates/shipments/quote.html'
    'src/test/java/com/ute/uteexpress/demo/SqlDemoSafetyTests.java'
    'src/test/java/com/ute/uteexpress/operations/BrowserNationwidePricingJourney.java'
    'src/test/java/com/ute/uteexpress/operations/BrowserOtpJourney.java'
    'src/test/java/com/ute/uteexpress/operations/CustomerJourneySupport.java'
    'src/test/java/com/ute/uteexpress/operations/NationwideDemoPricing.java'
    'src/test/java/com/ute/uteexpress/operations/NationwidePricingIntegrationTests.java'
    'src/test/java/com/ute/uteexpress/pricing/PricingManagementTests.java'
    'src/test/java/com/ute/uteexpress/pricing/ProvinceCatalogTests.java'
    'src/test/java/com/ute/uteexpress/release/NationwidePricingMigrationTests.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14CallbackLifecycleTests.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14HelperRegressionTests.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14MappingOverrideTests.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14QaDiagnostics.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14QaFixtures.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14QaSafetyTests.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14QaTarget.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14ReadOnlyInspection.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14SqlServerQaTool.java'
    'src/test/java/com/ute/uteexpress/release/v14/V14TransactionProbe.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15BusinessChecks.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15BusinessHarnessTests.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15CallbackLifecycleTests.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15ConstraintChecks.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15HelperRegressionTests.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15LegacyFixtures.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15QaDiagnostics.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15QaSafetyTests.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15QaTarget.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15ReadOnlyInspection.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15SchemaChecks.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15SourceReview.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15Sql.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15SqlServerQaTool.java'
    'src/test/java/com/ute/uteexpress/release/v15/V15TransactionProbe.java'
    'src/test/java/com/ute/uteexpress/security/PricingFlowTests.java'
    'src/test/java/com/ute/uteexpress/service/UserShipmentServiceTests.java'
    'src/test/resources/demo/nationwide-shipping-prices.csv'
    'src/test/resources/orm-v14-qa.xml'
)
$commit25 = @($commit25Only) + @($mixed)
$remaining = @(git ls-files --modified --others --exclude-standard)
$unexpected = @($remaining | Where-Object { $_ -notin $commit25 })
if ($unexpected.Count -gt 0) { throw 'Unexpected remaining files; review manifest again' }
git add -- $commit25
if ($LASTEXITCODE -ne 0) { throw 'C25 add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed' }
git diff --cached --stat
git diff --cached
Test-StagedSnapshot 'commit25'
# Chỉ khi verify, secret review và manifest đều PASS:
git commit -m "feat(pricing): add nationwide tariffs and complete V14 V15 QA"
if ($LASTEXITCODE -ne 0) { throw 'C25 commit failed' }
git status --short --untracked-files=all
git log -2 --oneline
```

Kỳ vọng index và working tree sạch sau hai commit; nếu còn file thì dừng review, không `git add .`. Dùng identity tác giả đã thống nhất của nhóm; agent không sửa Git account/config hay giả tác giả. Nếu cần nhận xét hai commit trước publish, dừng tại đây để chủ dự án xem.

### 5. Push feature branch, sau phê duyệt push riêng

```powershell
if (@(git status --porcelain=v1).Count -ne 0) { throw 'Working tree is not clean; stop before push' }
git push --dry-run origin HEAD:refs/heads/feature/auto-intake-receipts
if ($LASTEXITCODE -ne 0) { throw 'Push dry-run failed; do not force' }
git push --set-upstream origin HEAD:refs/heads/feature/auto-intake-receipts
if ($LASTEXITCODE -ne 0) { throw 'Push failed; stop for review' }
```

Origin đã được kiểm tra offline ở đầu báo cáo; nếu credential/quyền GitHub bị chặn, ghi BLOCKED, không đưa token vào command line hoặc URL.

### 6. Merge sau review/CI và phê duyệt merge riêng

Ưu tiên chủ dự án tạo PR trên GitHub từ `feature/auto-intake-receipts` → `main`, review đủ hai commit và diff. Chọn **Create a merge commit** nếu muốn giữ hai commit riêng; squash sẽ gom lịch sử. Không push thẳng main hoặc tự mở/merge PR ở lượt này. Không cần đổi branch local, giúp giữ ứng dụng 8083 đang chạy nguyên trạng.

Nếu main đã tiến thêm, dừng để xử lý tương thích/CI trong phạm vi được duyệt; không tự rebase, force-push hoặc giải conflict bằng cách bỏ file đã QA. Merge source không cấp phép migrate/restart database demo; guard launcher V14 vẫn giữ khi checkout có V15.

## Secret/artifact review

Static scan đầu lượt: 574 candidate files tracked/untracked, 574 text files, 0 artifact path cần loại và 0 match đáng nghi theo private key/token/JWT/JDBC password/raw cookie/quote-token/config literal rules. Scan cuối sau docs: **576/576 text files, 0 findings**; scan path kể cả ignored ngoài target/.git cũng 0 DPAPI/private artifact/DB backup/HAR/.env thật. Không đọc DPAPI/.env thật và không in matched values. Không tuyên bố scan heuristic thay thế staged review.

Offline kết sổ: 75/75 targeted PASS; 6 scripts và 5 blocks lệnh Git parse AST PASS (không execute); JAR không tooling và 15 migration/catalog khớp source; whitespace 120 file, UTF-8/local links và manifest/status khớp. Source/helper/migrations/test giữ nguyên theo hash đầu lượt. `git diff --check` PASS, chỉ cảnh báo LF→CRLF hiện có (kèm `.gitignore` sau bổ sung).

Giữ ngoài Git: credential DPAPI/candidate/backup trong TEMP, `.env`, log/transcript/raw SQL/browser frames/cookies/OTP/token, database backup/MDF/LDF, target JAR/classes/Surefire và Chrome profile/evidence TEMP. `.gitignore` nay chặn thêm ba pattern DPAPI; không tạo credential giả để thử. Kiểm tra ignore bằng tên giả không tạo file. CSV giá fixture academic thuộc test scope là file **cần commit**; catalog tỉnh public và V14/V15 migration cũng cần commit.

Scope QA cuối: SQL V14/V15 PASS theo báo cáo tương ứng; browser H2 399 PASS lịch sử; chưa browser E2E SQL hoặc production load. Không chạy lại SQL, không thay database hoặc app 8083, không Commit 26. [Kết luận nghiệm thu](commit25-final-qa.md).
