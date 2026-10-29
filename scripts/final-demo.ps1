[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('Credential','Inspect','Migrate','Seed','Validate','Run')]
    [string]$Mode,
    [ValidateSet('UTEExpress_FinalDemo')] [string]$ApprovedDatabase,
    [ValidateRange(1024,65535)] [int]$Port=18085,
    [switch]$DisableNotificationWorker
)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$sqlCredentialPath=Join-Path $env:TEMP 'uteexpress-final-demo-sql.credential.xml'
$webCredentialPath=Join-Path $env:TEMP 'uteexpress-final-demo-web.credential.xml'
if([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT){throw 'Windows DPAPI is required.'}
foreach($path in @($sqlCredentialPath,$webCredentialPath)){
    if([IO.Path]::GetFullPath($path).StartsWith($projectRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){
        throw 'Credentials must remain outside the repository.'
    }
}
if($Mode -eq 'Credential'){
    $sqlCredential=Get-Credential -UserName 'uteexpress_finaldemo' -Message 'Dedicated SQL login for UTEExpress_FinalDemo only; never use sa, Demo or QA credentials'
    if($null -eq $sqlCredential -or $sqlCredential.UserName -cne 'uteexpress_finaldemo'){throw 'Dedicated Final Demo SQL login required.'}
    $webCredential=Get-Credential -UserName 'final-demo-web' -Message 'Initial Final Demo website password: 20+ characters, 3 character classes; never paste into chat'
    if($null -eq $webCredential){throw 'Initial website password required.'}
    $webPassword=$webCredential.GetNetworkCredential().Password
    $classes=0
    foreach($pattern in @('[a-z]','[A-Z]','[0-9]','[^a-zA-Z0-9]')){if($webPassword -cmatch $pattern){$classes++}}
    if($webPassword.Length -lt 20 -or [Text.Encoding]::UTF8.GetByteCount($webPassword) -gt 72 -or $classes -lt 3){
        $webPassword=$null;throw 'Website password must have 20+ characters, at most 72 UTF-8 bytes and at least 3 character classes.'
    }
    $webPassword=$null
    $sqlCredential | Export-Clixml -LiteralPath $sqlCredentialPath
    $webCredential | Export-Clixml -LiteralPath $webCredentialPath
    $sqlCredential=$null;$webCredential=$null
    Write-Output 'PASS DPAPI credentials saved in TEMP for the current Windows account. No SQL connection opened; no secrets printed.'
    return
}
if ($DisableNotificationWorker -and $Mode -ne 'Run') { throw 'DisableNotificationWorker is only supported in Run mode.' }
if ($Port -in @(8083,18084)) { throw 'Protected ports are forbidden; no application will be stopped.' }
if ($Mode -ne 'Inspect' -and $ApprovedDatabase -cne 'UTEExpress_FinalDemo') {
    throw 'Stop: obtain owner approval for this operation, then specify -ApprovedDatabase UTEExpress_FinalDemo. Run also permits business/worker writes.'
}
if ($Mode -eq 'Run' -and [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains $Port) {
    throw 'Port is occupied. No existing application will be stopped; choose a free port.'
}
if (-not (Test-Path -LiteralPath $sqlCredentialPath)) { throw 'Run Credential mode after creating the separate SQL login; never supply passwords in arguments.' }
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
    $credential=Import-Clixml -LiteralPath $sqlCredentialPath
    if ($credential -isnot [Management.Automation.PSCredential] -or $credential.UserName -cne 'uteexpress_finaldemo') { throw 'Invalid dedicated demo credential.' }
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
    $start.EnvironmentVariables['UTE_FINAL_DEMO_USERNAME']=$credential.UserName
    $start.EnvironmentVariables['UTE_FINAL_DEMO_PASSWORD']=$credential.GetNetworkCredential().Password
    if ($Mode -ne 'Inspect') { $start.EnvironmentVariables['UTE_FINAL_DEMO_APPROVED']='UTEExpress_FinalDemo' }
    if ($DisableNotificationWorker) { $start.EnvironmentVariables['UTE_FINAL_DEMO_WORKER_DISABLED']='YES' }
    if ($Mode -eq 'Seed') {
        if (-not (Test-Path -LiteralPath $webCredentialPath)) { throw 'Run Credential mode to supply the private website password.' }
        $webCredential=Import-Clixml -LiteralPath $webCredentialPath
        if ($webCredential -isnot [Management.Automation.PSCredential]) { throw 'Invalid web DPAPI credential.' }
        $start.EnvironmentVariables['UTE_FINAL_DEMO_WEB_PASSWORD']=$webCredential.GetNetworkCredential().Password
        $webCredential=$null
    }
    $start.Arguments='-Dspring.devtools.restart.enabled=false -cp "'+$classpath+'" com.ute.uteexpress.finaldemo.FinalDemoApplication '+$Mode.ToLowerInvariant()+' '+$Port
    Write-Output ('Target: local SQLEXPRESS / UTEExpress_FinalDemo. Operation: '+$Mode+'. No fallback, reset or automatic seed.')
    $child=New-Object Diagnostics.Process
    $child.StartInfo=$start
    if (-not $child.Start()) { throw 'Java could not start.' }
    $childStarted=$true
    $stderr=$child.StandardError.ReadToEndAsync()
    # Expose only deliberately redacted operator messages, never framework/provider logs or raw stack traces.
    while ($null -ne ($line=$child.StandardOutput.ReadLine())) {
        if ($line -match '^(PASS (identity|migration|seed|Flyway|Hibernate|SQL|network) |READY http://127\.0\.0\.1:|History V[0-9]+ checksum=)') { Write-Output $line }
    }
    $child.WaitForExit()
    $errors=$stderr.GetAwaiter().GetResult()
    foreach ($line in ($errors -split '\r?\n')) {
        if ($line -match '^FINAL DEMO STOPPED stage=[a-z]+ type=[A-Za-z0-9]+( SQLState=[A-Z0-9]+ code=-?[0-9]+)?$') { Write-Output $line }
    }
    if ($child.ExitCode -ne 0) { throw 'SQL demo stopped safely. Check SQL availability, separate database/login, permissions and validated V1-V15 history. No fallback or repair was attempted.' }
} finally {
    if ($child) { if ($childStarted -and -not $child.HasExited) { $child.Kill();$child.WaitForExit(5000) | Out-Null };$child.Dispose() }
    if ($start) { foreach ($key in @('UTE_FINAL_DEMO_USERNAME','UTE_FINAL_DEMO_PASSWORD','UTE_FINAL_DEMO_WEB_PASSWORD','UTE_FINAL_DEMO_WORKER_DISABLED')) { $start.EnvironmentVariables.Remove($key) } }
    $credential=$null;$seedSecret=$null;$errors=$null
    Pop-Location
}
