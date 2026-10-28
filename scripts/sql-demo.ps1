[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)] [ValidateSet('Credential','Inspect','Migrate','Seed','Run')] [string]$Mode,
    [ValidateRange(1024,65535)] [int]$Port=8081,
    [ValidateSet('UTEExpress_Demo')] [string]$ApprovedDatabase
)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$credentialPath=Join-Path $env:TEMP 'uteexpress-sql-demo.credential.xml'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Windows DPAPI is required.' }
if ([IO.Path]::GetFullPath($credentialPath).StartsWith($projectRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Credential must be outside the repository.'
}
if ($Mode -eq 'Credential') {
    $credential=Get-Credential -UserName 'uteexpress_demo' -Message 'SQL Authentication for UTEExpress_Demo only; do not use QA or sa'
    if ($null -eq $credential -or $credential.UserName -cne 'uteexpress_demo') { throw 'Dedicated demo SQL login is required.' }
    $credential | Export-Clixml -LiteralPath $credentialPath
    $credential=$null
    Write-Output 'DPAPI credential saved outside Git for the current Windows account. No SQL connection was opened.'
    return
}
if ($Mode -ne 'Inspect' -and $ApprovedDatabase -cne 'UTEExpress_Demo') {
    throw 'Stop: obtain owner approval for this operation, then specify -ApprovedDatabase UTEExpress_Demo. Run also permits business/worker writes.'
}
if ($Mode -eq 'Run' -and [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains $Port) {
    throw 'Port is occupied. No existing application will be stopped; choose a free port.'
}
if (-not (Test-Path -LiteralPath $credentialPath)) { throw 'Run Credential mode after creating the separate SQL login; never supply passwords in arguments.' }
Push-Location -LiteralPath $projectRoot
$child=$null
$childStarted=$false
$start=$null
$credential=$null
$seedSecret=$null
try {
    & .\mvnw.cmd test-compile dependency:build-classpath '-Duteexpress.sqlserver.validate=false' '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/release-tools-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. SQL demo was not started.' }
    $dependencies=(Get-Content -LiteralPath 'target/release-tools-classpath.txt' -Raw).Trim()
    $classpath=(Join-Path $projectRoot 'target/test-classes')+';'+(Join-Path $projectRoot 'target/classes')+';'+$dependencies
    $credential=Import-Clixml -LiteralPath $credentialPath
    if ($credential -isnot [Management.Automation.PSCredential] -or $credential.UserName -cne 'uteexpress_demo') { throw 'Invalid dedicated demo credential.' }
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    $start.WorkingDirectory=$projectRoot
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    foreach ($key in @($start.EnvironmentVariables.Keys)) {
        if ($key -match '^(SPRING_|LOADER_|DB_|UTE_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)') { $start.EnvironmentVariables.Remove($key) }
    }
    $start.EnvironmentVariables['UTE_SQL_DEMO_USERNAME']=$credential.UserName
    $start.EnvironmentVariables['UTE_SQL_DEMO_PASSWORD']=$credential.GetNetworkCredential().Password
    if ($Mode -ne 'Inspect') { $start.EnvironmentVariables['UTE_SQL_DEMO_APPROVED']='UTEExpress_Demo' }
    if ($Mode -eq 'Seed') {
        $seedSecret=Read-Host 'Initial demo web password (16-72 UTF-8 bytes; enter privately, saved only as BCrypt hashes)' -AsSecureString
        $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($seedSecret)
        try { $start.EnvironmentVariables['UTE_SQL_DEMO_SEED_PASSWORD']=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
    $start.Arguments='-Dspring.devtools.restart.enabled=false -cp "'+$classpath+'" com.ute.uteexpress.demo.SqlDemoApplication '+$Mode.ToLowerInvariant()+' '+$Port
    Write-Output ('Target: local SQLEXPRESS / UTEExpress_Demo. Operation: '+$Mode+'. No fallback, reset or automatic seed.')
    $child=New-Object Diagnostics.Process
    $child.StartInfo=$start
    if (-not $child.Start()) { throw 'Java could not start.' }
    $childStarted=$true
    $stderr=$child.StandardError.ReadToEndAsync()
    # Expose only deliberately redacted operator messages, never framework/provider logs or raw stack traces.
    while ($null -ne ($line=$child.StandardOutput.ReadLine())) {
        if ($line -match '^(PASS identity:|PASS migration |PASS demo reference |PASS seed no-op|READY http://127\.0\.0\.1:|History V[0-9]+ checksum=)') { Write-Output $line }
    }
    $child.WaitForExit()
    $errors=$stderr.GetAwaiter().GetResult()
    foreach ($line in ($errors -split '\r?\n')) {
        if ($line -match '^SQL DEMO STOPPED: [A-Za-z0-9]+( SQLState=[A-Z0-9]+ code=-?[0-9]+)?$') { Write-Output $line }
    }
    if ($child.ExitCode -ne 0) { throw 'SQL demo stopped safely. Check SQL availability, separate database/login, permissions and validated V1-V13 history. No fallback or repair was attempted.' }
} finally {
    if ($child) { if ($childStarted -and -not $child.HasExited) { $child.Kill();$child.WaitForExit(5000) | Out-Null };$child.Dispose() }
    if ($start) { foreach ($key in @('UTE_SQL_DEMO_USERNAME','UTE_SQL_DEMO_PASSWORD','UTE_SQL_DEMO_SEED_PASSWORD')) { $start.EnvironmentVariables.Remove($key) } }
    $credential=$null;$seedSecret=$null;$errors=$null
    Pop-Location
}
