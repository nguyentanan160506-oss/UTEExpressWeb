[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)] [ValidateSet('Credential','Inspect','Preflight','Run')] [string]$Mode,
    [ValidateSet('UTEExpress_V14_QA_20261029_7dc87af7')] [string]$ReadyDatabase,
    [switch]$AllowWrite,
    [switch]$ReplaceCredential
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$approvedDatabase='UTEExpress_V14_QA_20261029_7dc87af7'
$approvedLogin='uteexpress_v14qa_7dc87af7'
$credentialPath=[IO.Path]::GetFullPath((Join-Path $env:TEMP 'uteexpress-v14-qa-7dc87af7.credential.xml'))
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Windows DPAPI is required.' }
if ($credentialPath.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Credential must remain outside the repository.' }
if ($Mode -eq 'Credential') {
    if ($AllowWrite -or $ReadyDatabase) { throw 'Credential mode does not connect to SQL Server or accept execution approval.' }
    $exists=Test-Path -LiteralPath $credentialPath
    if ($exists -and -not $ReplaceCredential) { throw 'Dedicated credential exists. Use -ReplaceCredential only after changing the QA login password privately in SSMS.' }
    if ($ReplaceCredential -and -not $exists) { throw 'No existing credential to replace; use Credential mode without -ReplaceCredential.' }
    $credential=Get-Credential -UserName $approvedLogin -Message 'Dedicated V14 QA SQL Authentication credential only. Never use sa, QA or demo login.'
    if ($null -eq $credential -or $credential.UserName -cne $approvedLogin -or $credential.Password.Length -eq 0) { throw 'Invalid dedicated QA credential.' }
    $candidate=$credentialPath+'.candidate.'+[Guid]::NewGuid().ToString('N')
    try {
        $credential | Export-Clixml -LiteralPath $candidate -NoClobber
        $acl=[Security.AccessControl.FileSecurity]::new()
        $acl.SetAccessRuleProtection($true,$false)
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl.SetOwner($sid)
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','Allow'))
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new('S-1-5-18'),'FullControl','Allow'))
        Set-Acl -LiteralPath $candidate -AclObject $acl
        $verified=Import-Clixml -LiteralPath $candidate
        if ($verified -isnot [Management.Automation.PSCredential] -or $verified.UserName -cne $approvedLogin -or $verified.Password.Length -eq 0) { throw 'Replacement credential verification failed; original retained.' }
        if ($exists) {
            # Atomic replacement on the same TEMP volume, retaining the previous encrypted credential.
            $backup=$credentialPath+'.backup.'+[Guid]::NewGuid().ToString('N')
            [IO.File]::Replace($candidate,$credentialPath,$backup)
        } else { [IO.File]::Move($candidate,$credentialPath) }
    } finally {
        $credential=$null;$verified=$null
        if (Test-Path -LiteralPath $candidate) { Remove-Item -LiteralPath $candidate }
    }
    Write-Output 'DPAPI credential prepared outside Git. No SQL connection was opened.'
    return
}
if ($ReplaceCredential) { throw 'ReplaceCredential is only available in Credential mode, without SQL connections.' }
# Check readiness BEFORE Maven, importing the credential, or starting any connection.
if ($ReadyDatabase -cne $approvedDatabase) { throw 'Stop: confirm environment readiness first; then specify the exact -ReadyDatabase.' }
if (($Mode -eq 'Run') -ne [bool]$AllowWrite) { throw 'Run requires -AllowWrite. Inspect and Preflight must not enable writes.' }
if (-not (Test-Path -LiteralPath $credentialPath)) { throw 'Dedicated DPAPI credential is missing. Use Credential mode privately.' }
$child=$null;$started=$false;$start=$null;$credential=$null
Push-Location -LiteralPath $root
try {
    & .\mvnw.cmd test-compile dependency:build-classpath '-Duteexpress.sqlserver.validate=false' '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/release-tools-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Tool compilation failed. No SQL connection was opened.' }
    $cp=(Join-Path $root 'target/test-classes')+';'+(Join-Path $root 'target/classes')+';'+(Get-Content target/release-tools-classpath.txt -Raw).Trim()
    $credential=Import-Clixml -LiteralPath $credentialPath
    if ($credential -isnot [Management.Automation.PSCredential] -or $credential.UserName -cne $approvedLogin) { throw 'Invalid dedicated credential file.' }
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    $start.WorkingDirectory=$root;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    foreach ($key in @($start.EnvironmentVariables.Keys)) {
        if ($key -match '^(SPRING_|LOADER_|DB_|UTE_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)') { $start.EnvironmentVariables.Remove($key) }
    }
    $start.EnvironmentVariables['UTE_V14_QA_READY_DATABASE']=$approvedDatabase
    if ($AllowWrite) { $start.EnvironmentVariables['UTE_V14_QA_WRITE_DATABASE']=$approvedDatabase }
    $start.EnvironmentVariables['UTE_V14_QA_USERNAME']=$credential.UserName
    $start.EnvironmentVariables['UTE_V14_QA_PASSWORD']=$credential.GetNetworkCredential().Password
    $start.Arguments='-Dfile.encoding=UTF-8 -cp "'+$cp+'" com.ute.uteexpress.release.v14.V14SqlServerQaTool '+$Mode.ToLowerInvariant()
    $child=New-Object Diagnostics.Process;$child.StartInfo=$start
    if (-not $child.Start()) { throw 'QA process could not start.' };$started=$true
    $stderr=$child.StandardError.ReadToEndAsync()
    # Only intentionally redacted helper output is shown. No framework/provider stack traces or raw logs are written.
    while ($null -ne ($line=$child.StandardOutput.ReadLine())) {
        if ($line -match '^(PASS |SKIP |EXPECTED_FAIL |RESULT )') { Write-Output $line }
    }
    $child.WaitForExit();$safeErrors=$stderr.GetAwaiter().GetResult()
    foreach ($line in ($safeErrors -split '\r?\n')) {
        if ($line -match '^FAIL stage=[a-z0-9-]+ type=[A-Za-z0-9]+( SQLState=[A-Z0-9]+ code=-?[0-9]+)?$') { Write-Output $line }
        if ($line -match '^DIAG cause=[0-7] type=[A-Za-z0-9_.$]+$' -or
            $line -match '^DIAG cause=[0-7] frame=[0-7] class=[A-Za-z0-9_.$]+ method=[A-Za-z0-9_$<>]+ file=[A-Za-z0-9_.$-]+ line=-?[0-9]+$' -or
            $line -match '^DIAG sql=[0-7] type=[A-Za-z0-9_.$]+ SQLState=[A-Z0-9]+ code=-?[0-9]+$') { Write-Output $line }
    }
    if ($child.ExitCode -ne 0) { throw 'QA stopped. Database retained; no cleanup, fallback or retry on another database.' }
} finally {
    if ($child) { if ($started -and -not $child.HasExited) { $child.Kill();$child.WaitForExit(5000) | Out-Null };$child.Dispose() }
    if ($start) { foreach ($key in @('UTE_V14_QA_USERNAME','UTE_V14_QA_PASSWORD')) { $start.EnvironmentVariables.Remove($key) } }
    $credential=$null;$safeErrors=$null;Pop-Location
}
