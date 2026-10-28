[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('Demo','SqlInspect','SqlFresh','SqlV3','SqlV4','SqlV8','SqlV9','SqlV10','SqlV11','SqlV12','SqlPending','SqlValidate','SqlSmoke','SqlJar')]
    [string]$Mode,
    [ValidateRange(1024,65535)] [int]$Port = 8081,
    [string]$CredentialFile,
    [switch]$TestMail
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$suppliedDemoPassword = $false
$importedSqlCredential = $false
$previousDemoMail = $env:UTE_DEMO_TEST_MAIL
$previousSqlUser = $env:UTE_QA_SQLSERVER_USERNAME
$previousSqlPassword = $env:UTE_QA_SQLSERVER_PASSWORD
Push-Location -LiteralPath $projectRoot
try {
    if ($TestMail -and $Mode -ne 'Demo') { throw 'TestMail is available only in disposable H2 Demo mode.' }
    if ($Mode -eq 'Demo') { $env:UTE_DEMO_TEST_MAIL = if ($TestMail) { 'YES' } else { 'NO' } }
    if ($CredentialFile) {
        if ($Mode -eq 'Demo') { throw 'SQL credentials cannot be used for the local H2 demo.' }
        $approvedCredentialPath = [IO.Path]::GetFullPath((Join-Path $env:TEMP 'uteexpress-sql-qa.credential.xml'))
        if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or
                [IO.Path]::GetFullPath($CredentialFile) -ne $approvedCredentialPath) {
            throw 'Only the designated Windows DPAPI credential in TEMP may be imported; never use a credential file in Git.'
        }
        if ($approvedCredentialPath.StartsWith($projectRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'The credential file must be outside the repository.'
        }
        if ($env:UTE_QA_SQLSERVER_DATABASE -cne 'UTEExpress_Test' -or
                $env:UTE_QA_SQLSERVER_APPROVED_DATABASE -cne 'UTEExpress_Test' -or
                $env:UTE_QA_SQLSERVER_INSTANCE -ine 'SQLEXPRESS') {
            throw 'This credential handoff is restricted to the approved local SQLEXPRESS / UTEExpress_Test target.'
        }
    }
    if ($Mode -eq 'Demo' -and -not $env:UTE_DEMO_PASSWORD) {
        $secureDemoPassword = Read-Host 'Dedicated local demo password (16-72 characters; never saved)' -AsSecureString
        $credentialPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureDemoPassword)
        try { $env:UTE_DEMO_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($credentialPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($credentialPointer) }
        $suppliedDemoPassword = $true
    }
    & .\mvnw.cmd test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/release-tools-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Maven tool compilation failed; no demo/SQL tool was started.' }
    $dependencies = (Get-Content -LiteralPath 'target/release-tools-classpath.txt' -Raw).Trim()
    $toolClasspath = (Join-Path $projectRoot 'target/test-classes') + ';' + (Join-Path $projectRoot 'target/classes') + ';' + $dependencies
    $javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    if ($CredentialFile) {
        # Decrypt only after Maven has finished; only the SQL tool child receives the temporary credential environment.
        $sqlCredential = Import-Clixml -LiteralPath $approvedCredentialPath
        if ($sqlCredential -isnot [System.Management.Automation.PSCredential]) { throw 'Invalid credential type.' }
        $importedSqlCredential = $true
        $env:UTE_QA_SQLSERVER_USERNAME = $sqlCredential.UserName
        $env:UTE_QA_SQLSERVER_PASSWORD = $sqlCredential.GetNetworkCredential().Password
    }
    if ($Mode -eq 'Demo') {
        & $javaExecutable '-cp' $toolClasspath 'com.ute.uteexpress.operations.LocalDemoApplication' "--port=$Port"
    } elseif ($Mode -eq 'SqlJar') {
        & $javaExecutable '-cp' $toolClasspath 'com.ute.uteexpress.release.SqlServerQaTool' 'validate'
        if ($LASTEXITCODE -ne 0) { throw 'SQL identity/schema validation failed before JAR startup.' }
        $jarPath = Join-Path $projectRoot 'target/uteexpress-0.0.1-SNAPSHOT.jar'
        if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Run Maven verify before the explicit JAR check.' }
        if ([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains $Port) {
            throw 'Selected loopback port is in use; no existing process will be stopped.'
        }
        $jarUrl = 'jdbc:sqlserver://127.0.0.1:1433;databaseName=UTEExpress_Test;encrypt=true;trustServerCertificate=true;loginTimeout=10;socketTimeout=20000;applicationName=UTEExpress_Commit22_JAR_QA'
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = $javaExecutable
        $start.WorkingDirectory = $projectRoot
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        # Do not inherit configuration overrides that could route an external application away from Test.
        foreach ($key in @($start.EnvironmentVariables.Keys)) {
            if ($key -match '^(SPRING_|LOADER_|DB_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)') { $start.EnvironmentVariables.Remove($key) }
        }
        $start.EnvironmentVariables['DB_URL'] = $jarUrl
        $start.EnvironmentVariables['DB_USERNAME'] = 'uteexpress_qa'
        $start.EnvironmentVariables['DB_PASSWORD'] = $env:UTE_QA_SQLSERVER_PASSWORD
        $start.EnvironmentVariables['SPRING_DATASOURCE_PASSWORD'] = $env:UTE_QA_SQLSERVER_PASSWORD
        $start.EnvironmentVariables['SPRING_FLYWAY_PASSWORD'] = $env:UTE_QA_SQLSERVER_PASSWORD
        $start.EnvironmentVariables['SESSION_COOKIE_SECURE'] = 'false'
        $start.Arguments = '-jar "' + $jarPath + '" --spring.config.location=classpath:/application-release.properties --spring.profiles.active=release --server.address=127.0.0.1 --server.port=' + $Port +
            ' "--spring.datasource.url=' + $jarUrl + '" --spring.datasource.username=uteexpress_qa "--spring.flyway.url=' + $jarUrl + '" --spring.flyway.user=uteexpress_qa' +
            ' --spring.jpa.hibernate.ddl-auto=validate --spring.flyway.enabled=true --spring.flyway.clean-disabled=true --spring.flyway.baseline-on-migrate=false --spring.flyway.out-of-order=false' +
            ' --uteexpress.notifications.worker.enabled=false --spring.mail.host=127.0.0.1 --spring.mail.port=1 --uteexpress.mail.from=qa@example.test' +
            ' --debug=false --trace=false --logging.level.root=WARN --logging.level.org.hibernate.SQL=OFF --logging.level.org.hibernate.orm.jdbc.bind=OFF --server.servlet.session.cookie.secure=false'
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $start
        $client = $null
        $started = $false
        try {
            if (-not $process.Start()) { throw 'JAR process could not start.' }
            $started = $true
            $output = $process.StandardOutput.ReadToEndAsync()
            $errorOutput = $process.StandardError.ReadToEndAsync()
            Add-Type -AssemblyName System.Net.Http
            $handler = New-Object Net.Http.HttpClientHandler
            $handler.AllowAutoRedirect = $false
            $client = New-Object Net.Http.HttpClient($handler)
            $client.Timeout = [TimeSpan]::FromSeconds(5)
            $baseUrl = 'http://127.0.0.1:' + $Port
            $ready = $false
            for ($attempt = 0; $attempt -lt 45; $attempt++) {
                if ($process.HasExited) { throw 'JAR exited before HTTP readiness; inspect the sanitized TEMP log.' }
                try {
                    $response = $client.GetAsync($baseUrl + '/login').GetAwaiter().GetResult()
                    $ready = [int]$response.StatusCode -eq 200
                    $response.Dispose()
                    if ($ready) { break }
                } catch { }
                Start-Sleep -Milliseconds 1000
            }
            if (-not $ready) { throw 'JAR HTTP readiness timed out.' }
            foreach ($path in @('/login','/register','/tracking','/quote','/css/vendor/bootstrap.min.css')) {
                $response = $client.GetAsync($baseUrl + $path).GetAwaiter().GetResult()
                try {
                    if ([int]$response.StatusCode -ne 200) { throw ('JAR public HTTP check failed: ' + $path) }
                    if (-not $path.EndsWith('.css')) {
                        $html = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                        if ($html -notmatch '<html') { throw ('JAR HTML rendering check failed: ' + $path) }
                        if ($path -eq '/login' -and $html -notmatch 'name="_csrf"') { throw 'JAR login CSRF form did not render.' }
                    }
                }
                finally { $response.Dispose() }
                Write-Output ('JAR HTTP PASS 200 ' + $path)
            }
            foreach ($path in @('/user/shipments','/admin/users','/api/notifications')) {
                $response = $client.GetAsync($baseUrl + $path).GetAwaiter().GetResult()
                try { if ([int]$response.StatusCode -ne 401) { throw ('JAR protected route check failed: ' + $path) } }
                finally { $response.Dispose() }
                Write-Output ('JAR HTTP PASS 401 ' + $path)
            }
            Write-Output 'SQL JAR startup and loopback HTTP PASS; guest/public endpoints only, no browser E2E or demo seed.'
        } finally {
            if ($client) { $client.Dispose() }
            if ($started -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit(5000) | Out-Null }
            if ($output -and $errorOutput) {
                $safeLog = $output.GetAwaiter().GetResult() + $errorOutput.GetAwaiter().GetResult()
                if ($env:UTE_QA_SQLSERVER_PASSWORD) { $safeLog = $safeLog.Replace($env:UTE_QA_SQLSERVER_PASSWORD,'[REDACTED]') }
                [IO.File]::WriteAllText((Join-Path $env:TEMP 'uteexpress-commit22-sql-jar-startup.log'),$safeLog,[Text.UTF8Encoding]::new($false))
            }
            $process.Dispose()
        }
    } else {
        $sqlMode = @{SqlInspect='inspect';SqlFresh='fresh';SqlV3='v3';SqlV4='v4';SqlV8='v8';SqlV9='v9';SqlV10='v10';SqlV11='v11';SqlV12='v12';SqlPending='pending';SqlValidate='validate';SqlSmoke='smoke'}[$Mode]
        & $javaExecutable '-cp' $toolClasspath 'com.ute.uteexpress.release.SqlServerQaTool' $sqlMode
    }
    if ($LASTEXITCODE -ne 0) { throw 'Release tool failed. No cleanup, reset or Flyway repair was attempted.' }
} finally {
    $env:UTE_DEMO_TEST_MAIL = $previousDemoMail
    if ($importedSqlCredential) {
        $env:UTE_QA_SQLSERVER_USERNAME = $previousSqlUser
        $env:UTE_QA_SQLSERVER_PASSWORD = $previousSqlPassword
        $sqlCredential = $null
    }
    if ($suppliedDemoPassword) { Remove-Item Env:UTE_DEMO_PASSWORD -ErrorAction SilentlyContinue }
    Pop-Location
}
