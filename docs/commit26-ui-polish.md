# Commit 26 — UI Polish

Branch: `feature/ui-polish`. Base: `b9ba709715ad47745ea4247ca451e901320aed02`.

## Scope and behavior

- A real public home page at `/`: tracking GET form, supported shipping methods, process overview and role-aware entry links. No invented company statistics, shipping prices or nationwide delivery promises.
- Shared CSS tokens, local Bootstrap 5.3.8, system fonts and inline SVG icons. No new dependency or external font/CDN request.
- Shared public/customer navigation and Admin/Manager/Shipper workspace shells. Desktop sidebar; tablet/mobile disclosure menu with Escape and focus restoration; navigation remains usable without JavaScript.
- Auth/account/profile, public quote/tracking, User shipment/quote/history/detail/support/notifications, Manager logistics/trips/delivery/tickets, Admin catalog/pricing/reports/audit and Shipper pickup/delivery share the design system.
- Existing server-rendered shipment steps remain: enter addresses, find serving branches, request a quote, confirm the stored quote. The progress strip describes these steps; it does not introduce a client-side wizard or calculate prices.
- Long detail/report pages have an optional index generated from rendered section headings. Existing conditional actions remain under their original authorization and state checks.
- Tables have column scopes and captions; cards, status badges, empty/error/loading states and public/private timelines use consistent presentation. Existing enum values and UTC meaning remain unchanged.

## Design

Navy `#142B49`, primary `#2563EB`, teal `#0D9488`, amber `#F59E0B`, background `#F5F7FB`, white surfaces. Teal buttons/text use `#0F766E`; amber buttons use navy text. Body text uses `#334155`, secondary text `#526177`, errors `#B42318`.

Typography uses Segoe UI/system fonts with 16px body text. Spacing follows a 4px scale. Cards have restrained borders/shadows and approximately 14px corners; controls have visible borders, labels and focus indicators. No heavy animation; reduced-motion preference is respected.

Computed contrast examples: white/blue 5.17:1; white/dark teal 5.47:1; navy/amber 6.64:1. These are token-pair checks, not a claim of complete WCAG certification.

## Preservation gates

Production Java changes are restricted to `AuthController.home()` returning `home/index`; login/account behavior and SecurityConfig are unchanged. No service, entity, repository, configuration, dependency or V1–V15 migration changes.

A source comparison against the original 62 templates checks form actions/formactions, field names, values, Thymeleaf bindings, limits, required flags, patterns and existing data hooks. Business-page form contracts are unchanged. Logout forms are composed through the shared shell with the same POST action and automatic CSRF insertion.

SQL Server databases, credentials and the application on port 8083 are outside this QA run. No SQL Server helper or SQL Server migration/repair/clean/seed was executed. The browser harness starts its own in-memory H2 application at 18082 and its own temporary Chrome profile at 19222, then stops only those owned processes.

## Verification

Final acceptance: PASS for the approved local Chrome/H2 UI scope. Ready for user review and a separately approved local commit; not a production-readiness declaration.

| Gate | Result |
|---|---|
| Pre-change Chrome/H2 baseline | PASS: 399 PASS / 0 FAIL / 0 BLOCKED |
| Targeted MVC/Thymeleaf/Auth/Admin regression | PASS: 51 tests |
| Maven offline clean test | PASS: 1,619 tests; 1,618 passed, 0 failures/errors, 1 skipped |
| Maven offline verify | PASS: 1,619 tests; 1,618 passed, 0 failures/errors, 1 skipped; release JAR built |
| JavaScript regression | PASS: 21 checks (operations 3, reports 3, notifications 6, UI 9) |
| Final Chrome/H2 + UI viewport journey | PASS: 634 PASS / 0 FAIL / 0 BLOCKED; no uncaught JavaScript or HTTP 5xx |
| Form-contract preservation | PASS for all business page templates |
| Migration/source scope | PASS; V1–V15 unchanged |
| Whitespace/secret/artifact/JAR checks | PASS: tracked diff and all 9 new files checked; no secret/artifact candidates after review; JAR has UI assets, unchanged V1?V15 and no H2/demo/test helper leaks |

The existing production-context test remains intentionally skipped; H2/mock suites and the explicit H2 HTTP/browser entry point provide the isolated evidence. Two tests that expected the historical root redirect were updated to assert the approved home view/HTTP 200 while keeping their CSRF and unauthorized-route checks. A layout class on the account logout form was moved to an outer wrapper to retain the original form markup and its strict regression assertion.

The first expanded UI run stopped with 460 PASS / 0 FAIL / 1 BLOCKED because the new test helper supplied a shipment ID to a path-only navigation method. The helper was corrected to build the existing detail URL; no business behavior was changed. An initial targeted test also caught a missing Admin identifier in the new header; the identifier was restored and the 51-test targeted suite passed. Screenshot inspection caught a navbar rule overriding amber-button text; navy text is now explicit.

## Browser coverage and evidence

The existing functional suite covers registration/OTP via isolated test SMTP, authentication, CSRF/RBAC, pricing including nationwide cases, quote confirmation/idempotency, shipment ownership, C24 source intake receipts, pickup/handoff, multi-leg transport, delivery failure/returns, support, reports/CSV and two-session WebSocket UI privacy/reconnect.

The additional UI journey checks desktop 1440, tablet 768, mobile 390 and narrow mobile 320 widths, HTTP/render success, page overflow, unique IDs, labelled form controls, POST CSRF fields, a single notification widget, section indexes and mobile menu keyboard interaction. Detail URLs are discovered from links within each authenticated role's scope.

Pre-change tracking screenshots are retained outside Git at:

`%TEMP%\uteexpress-browser-evidence-81f1af011e7c4f0dada93e2a05f0ba2f`

Final evidence directory: `%TEMP%\uteexpress-browser-evidence-382ab1d111d34d0ba57303ecde5acc0e`. It contains 40 PNG screenshots, including desktop/mobile home, User/create/detail, Manager, Admin, Shipper and ticket views plus tracking screenshots comparable with the pre-change captures. 184 additional UI viewport checks and 45 mobile keyboard checks passed. There are no before screenshots for every page; pre-change pixel evidence is limited to tracking. Screenshots and browser profiles stay in TEMP, not in the production JAR or Git. An intermediate full run completed with 629 PASS / 0 FAIL / 1 BLOCKED: the UI helper looked for a destination-owned ticket while logged in as the origin Manager. The helper now uses the existing destination Manager; no fixture, branch assignment or RBAC change was made.

Machine-readable final results: [commit26-browser-results.json](commit26-browser-results.json), executed at `2026-10-29T08:55:55Z`. Successful logs remain outside Git in TEMP: `uteexpress-c26-clean-test-accepted.log`, `uteexpress-c26-verify-final.log` and `uteexpress-c26-browser-accepted.log`.

## Reproduce on an isolated demo

```powershell
.\mvnw.cmd -o clean test
.\mvnw.cmd -o verify
.\mvnw.cmd -o -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/release-tools-classpath.txt'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\browser-qa.ps1 -UiPolish
git diff --check
```

JavaScript suites require Node 22+ and no npm dependencies: run `node` on each `src/test/js/{operations,reports,notifications,ui}.test.mjs`. For this execution, the installed Node tool runtime was used because `node` is not on the shell PATH.

For manual UI review, use the existing H2-only entry point on a free port, with a dedicated password entered through its secure prompt:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-tools.ps1 -Mode Demo -Port 18084 -TestMail
```

Open `http://127.0.0.1:18084`. Test-only seeded identities include `owner@example.test`, `manager@example.test`, `pickup-courier@example.test`, `shipper@example.test` and `admin@example.test`; use the dedicated demo password entered at startup. Do not pass a SQL credential file. Stopping this H2 demo loses its temporary data. Do not restart the SQL demo on 8083 as part of UI verification.

## Review and Git handoff

No staging, commit, push or merge is performed by this task. Review the final file manifest and screenshots, run the checks above, then approve local commit separately. Suggested message: `feat(ui): unify logistics website layouts and responsive experience`.

A reviewable exact 79-path staging manifest is prepared outside Git at `%TEMP%\uteexpress-c26-files.txt`. After approval, from the repository root, stage only those paths with `git add --pathspec-from-file="$env:TEMP\uteexpress-c26-files.txt"`, inspect `git diff --cached --check` and the staged diff, then create the suggested local commit. These commands have NOT been executed.

After an approved local commit, review/dry-run push to `feature/ui-polish`, then open a PR to main. Preserve existing Git identity. Merge only after the UI PR and regression evidence are reviewed. Existing C24/C25 commits and migration checksums are not rewritten.

## Limits

Two earlier runs stopped intermittently at registration/OTP or form navigation (no JavaScript/HTTP 5xx recorded). Their root cause was not established; these remain a test-reliability observation, not a confirmed UI defect; no business code, authorization or OTP verification was weakened. A later completed run passed all functional cases. Safe interruption diagnostics now record only route paths, field names and guarded error messages, never field values, cookies, headers or HTML.

This is local Chrome headless/H2 UI QA. It does not rerun SQL Server browser E2E, production load, screen-reader certification, or Safari/Firefox/real-device tests. The existing 8083 process is not updated by changing source; updating that demo requires a separate user-controlled restart after review. These limits do not establish production readiness.

## Final file manifest

79 files: 70 modified, 9 new, 0 deleted. Nothing staged.

### QA documentation/evidence

- `docs/commit26-browser-results.json` ? NEW
- `docs/commit26-ui-polish.md` ? NEW

### H2 browser harness

- `scripts/browser-qa.ps1` ? modified

### Presentation controller (home only)

- `src/main/java/com/ute/uteexpress/controller/AuthController.java` ? modified

### Styles and UI navigation JavaScript

- `src/main/resources/static/css/admin.css` ? modified
- `src/main/resources/static/css/auth.css` ? modified
- `src/main/resources/static/css/shipments.css` ? modified
- `src/main/resources/static/css/tokens.css` ? NEW
- `src/main/resources/static/css/tracking.css` ? modified
- `src/main/resources/static/css/ui.css` ? NEW
- `src/main/resources/static/js/ui.js` ? NEW

### Thymeleaf templates

- `src/main/resources/templates/admin/dashboard.html` ? modified
- `src/main/resources/templates/admin/fragments.html` ? modified
- `src/main/resources/templates/admin/user-detail.html` ? modified
- `src/main/resources/templates/admin/users.html` ? modified
- `src/main/resources/templates/auth/account.html` ? modified
- `src/main/resources/templates/auth/login.html` ? modified
- `src/main/resources/templates/auth/profile.html` ? modified
- `src/main/resources/templates/auth/register.html` ? modified
- `src/main/resources/templates/auth/verify-email.html` ? modified
- `src/main/resources/templates/delivery/fragments.html` ? modified
- `src/main/resources/templates/delivery/manager-detail.html` ? modified
- `src/main/resources/templates/delivery/manager-list.html` ? modified
- `src/main/resources/templates/delivery/shipper-detail.html` ? modified
- `src/main/resources/templates/delivery/shipper-list.html` ? modified
- `src/main/resources/templates/home/index.html` ? NEW
- `src/main/resources/templates/layout/fragments.html` ? NEW
- `src/main/resources/templates/manager/dashboard.html` ? modified
- `src/main/resources/templates/manager/detail.html` ? modified
- `src/main/resources/templates/manager/fragments.html` ? modified
- `src/main/resources/templates/manager/handoff-detail.html` ? modified
- `src/main/resources/templates/manager/handoffs.html` ? modified
- `src/main/resources/templates/manager/shipments.html` ? modified
- `src/main/resources/templates/notifications/fragments.html` ? modified
- `src/main/resources/templates/notifications/index.html` ? modified
- `src/main/resources/templates/operations/dashboard.html` ? modified
- `src/main/resources/templates/operations/error.html` ? modified
- `src/main/resources/templates/operations/fragments.html` ? modified
- `src/main/resources/templates/pricing/books.html` ? modified
- `src/main/resources/templates/pricing/detail.html` ? modified
- `src/main/resources/templates/pricing/estimate.html` ? modified
- `src/main/resources/templates/pricing/fragments.html` ? modified
- `src/main/resources/templates/pricing/new.html` ? modified
- `src/main/resources/templates/pricing/service-detail.html` ? modified
- `src/main/resources/templates/pricing/service-form.html` ? modified
- `src/main/resources/templates/pricing/services.html` ? modified
- `src/main/resources/templates/reports/audit.html` ? modified
- `src/main/resources/templates/reports/error.html` ? modified
- `src/main/resources/templates/reports/fragments.html` ? modified
- `src/main/resources/templates/reports/index.html` ? modified
- `src/main/resources/templates/shipments/create.html` ? modified
- `src/main/resources/templates/shipments/detail.html` ? modified
- `src/main/resources/templates/shipments/fragments.html` ? modified
- `src/main/resources/templates/shipments/history.html` ? modified
- `src/main/resources/templates/shipments/quote.html` ? modified
- `src/main/resources/templates/shipper/dashboard.html` ? modified
- `src/main/resources/templates/shipper/detail.html` ? modified
- `src/main/resources/templates/shipper/fragments.html` ? modified
- `src/main/resources/templates/shipper/pickups.html` ? modified
- `src/main/resources/templates/support/detail.html` ? modified
- `src/main/resources/templates/support/error.html` ? modified
- `src/main/resources/templates/support/list.html` ? modified
- `src/main/resources/templates/support/new.html` ? modified
- `src/main/resources/templates/tickets/detail.html` ? modified
- `src/main/resources/templates/tickets/list.html` ? modified
- `src/main/resources/templates/tickets/new.html` ? modified
- `src/main/resources/templates/tracking/index.html` ? modified
- `src/main/resources/templates/transport/detail.html` ? modified
- `src/main/resources/templates/transport/fragments.html` ? modified
- `src/main/resources/templates/transport/history.html` ? modified
- `src/main/resources/templates/transport/list.html` ? modified
- `src/main/resources/templates/transport/local.html` ? modified
- `src/main/resources/templates/transport/new.html` ? modified

### Automated tests and browser helpers

- `src/test/java/com/ute/uteexpress/operations/BrowserQaTool.java` ? modified
- `src/test/java/com/ute/uteexpress/operations/BrowserUiPolishJourney.java` ? NEW
- `src/test/java/com/ute/uteexpress/operations/LocalDemoQaTests.java` ? modified
- `src/test/java/com/ute/uteexpress/security/AuthenticationTests.java` ? modified
- `src/test/java/com/ute/uteexpress/security/SecurityConfigTests.java` ? modified
- `src/test/js/ui.test.mjs` ? NEW
