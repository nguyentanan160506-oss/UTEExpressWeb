# UTEExpress_FinalDemo — provisioning and isolation

Target: `DESKTOP-31GJQ3T\SQLEXPRESS`, TCP `127.0.0.1:1433`, database **UTEExpress_FinalDemo**, dedicated SQL login **uteexpress_finaldemo**. No fallback to any other database. Ports 8083 and 18084 must not be stopped or restarted.

## Administrator setup in SSMS

The agent's Windows Authentication was rejected with SQL Server **18452**. No database was created or changed by that connection attempt. An administrator must provision the isolated database/login; never supply passwords through chat, repository files or command-line arguments.

1. In SSMS connect to **tcp:127.0.0.1,1433** using the administrator's existing authentication. Verify the instance is SQLEXPRESS on DESKTOP-31GJQ3T.
2. Open and execute `scripts/final-demo-create-database.sql`. Its guards stop if the database already exists, machine/instance/port differ, or creation permissions are missing. It creates only the new database, without migrations or seed. If it reports an existing database, stop and inspect rather than rerun/recreate.
3. Security → Logins → New Login: choose **uteexpress_finaldemo**, SQL Server Authentication, a dedicated strong password entered privately in SSMS. Keep password policy enabled; do not require a password change at next login for this noninteractive application account. Do not use `sa`.
4. Leave server roles at `public`. Under User Mapping, map the login **only** to UTEExpress_FinalDemo, with `db_owner` for controlled migrations/seed. Do not map or grant rights on UTEExpressDB, UTEExpress_Test, UTEExpress_Demo, V14/V15 QA databases or any other business database.
5. Run the secure prompt on the same Windows account used by the agent:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Credential
```

The first prompt takes the SQL login password. The second takes a separate strong initial website password (20+ characters, 3 character classes, at most 72 UTF-8 bytes). Website accounts will receive independently salted BCrypt hashes; the password is not recorded in source, reports or logs. DPAPI files stay outside Git:

- `%TEMP%\uteexpress-final-demo-sql.credential.xml`
- `%TEMP%\uteexpress-final-demo-web.credential.xml`

Credential mode opens no SQL connection. After provisioning, provide only confirmation of the instance/database/login readiness, never the passwords. The agent will inspect identity, permissions and emptiness before any Flyway/seed operation.

## Approved network size

35 customer branches plus 7 regional hubs are represented by 42 `branches` rows. The owner approved **112 directed routes: 35 collection + 35 distribution + 42 inter-hub**, **42 Manager**, **35 Shipper**, 12 User and 1 Admin. The earlier 82-route proposal is superseded: current TransportRouting needs direct connections between each pair of hubs. Pricing for 34 provinces is independent of delivery coverage at 35 explicitly configured wards. See [network and address entry guide](final-demo-network.md).

## Controlled execution after readiness confirmation

Do not execute migration/seed until the owner confirms the new database/login/DPAPI setup is ready. Inspect is read-only. A nonempty or unexpected database must be investigated; never recreate, clean or repair it. Migrate requires zero user objects and cannot resume a partially migrated schema automatically.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Inspect
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Migrate -ApprovedDatabase UTEExpress_FinalDemo
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Seed -ApprovedDatabase UTEExpress_FinalDemo
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Validate -ApprovedDatabase UTEExpress_FinalDemo
```

Every JDBC connection verifies machine DESKTOP-31GJQ3T, instance SQLEXPRESS, TCP 1433, JDBC catalog/DB_NAME(), ORIGINAL_LOGIN(), db_owner membership and absence of access to other user databases/server sysadmin/dbcreator roles. It rejects mismatches with no fallback. Only approved FinalDemo modes can write. Flyway is capped at V15, with clean disabled and no baseline/repair. Seed uses one serializable transaction plus a database session application lock. The FD-INITIAL marker and complete namespace distinguish a committed seed from an incomplete setup; rerun is a no-op and preserves password hashes, prices and subsequent user operations.

## Start later on a separate port

After migration, seed and validation have passed, run in a separate terminal:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\final-demo.ps1 -Mode Run -ApprovedDatabase UTEExpress_FinalDemo -Port 18085
```

Run refuses an occupied port and forbids 8083/18084. It validates schema and never auto-migrates or auto-seeds. URL: http://127.0.0.1:18085 . This is a local operator/test-classpath launcher, not the production JAR. Java 21 and Maven test dependencies are required; the helper builds a test classpath. Test tooling, fixtures and DPAPI are not packaged in the release JAR. No production connection properties are changed.

SMTP remains unconfigured (loopback port 1). Seeded synthetic accounts are ACTIVE initial reference data; new registration still requires the existing OTP flow. This setup does not claim registration/email is operational or bypass verification. Configure a separately approved SMTP test service if needed later.

## Implementation status

Provisioning, V1–V15, seed and DBCC validation passed previously. Current final stability results: **SQL Chrome read/auth 111 PASS**, **H2 Chrome write journeys 634 PASS**, Maven **1,645 passed / 1 skipped**, JavaScript **21 PASS**. See [final-stability-report.md](final-stability-report.md).

FinalDemo is currently running on localhost:18085 with notification workers disabled, following explicit approval to restart only this server. 8083/18084 were not restarted; checked SQL inventory is unchanged across the final read-only run. Do not create a duplicate listener or rerun Migrate/Seed.

For normal manual demo use the Run command above. To keep workers off during read-only QA append `-DisableNotificationWorker` (this flag disables background work, not business endpoints). Use separate Chrome Profiles for simultaneous roles. FinalDemo now has its own session cookie UTEEXPRESS_FINALDEMO_SESSION. See [accounts, valid addresses and complete delivery runbook](final-demo-runbook.md).

SQL browser business mutations were not run by choice; only isolated H2 was used for write/failure scenarios. SMTP/OTP on SQL and production load are not certified. Backup/restore was not performed. Credentials and runtime evidence stay outside Git.
