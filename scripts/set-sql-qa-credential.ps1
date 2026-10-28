[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This credential handoff requires Windows DPAPI; no portable/plaintext credential file will be created.'
}
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\') + '\'
$credentialPath = [IO.Path]::GetFullPath((Join-Path $env:TEMP 'uteexpress-sql-qa.credential.xml'))
if ($credentialPath.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'TEMP must be outside the repository. No credential will be saved in Git.'
}
if (Test-Path -LiteralPath $credentialPath) {
    throw 'The TEMP credential file already exists. Review/remove it yourself before provisioning a new credential.'
}
$sqlLogin = Read-Host 'SQL Authentication login authorized for UTEExpress_Test (do not enter a Windows login)'
if ([string]::IsNullOrWhiteSpace($sqlLogin)) { throw 'A SQL Authentication login is required.' }
$secureSqlPassword = Read-Host 'SQL password (hidden; never printed)' -AsSecureString
if ($secureSqlPassword.Length -eq 0) { throw 'An empty password will not be saved.' }
try {
    $sqlCredential = [System.Management.Automation.PSCredential]::new($sqlLogin.Trim(), $secureSqlPassword)
    # On Windows, Export-Clixml protects SecureString with current-user DPAPI; no password conversion here.
    $sqlCredential | Export-Clixml -LiteralPath $credentialPath -NoClobber
    $fileAcl = [System.Security.AccessControl.FileSecurity]::new()
    $fileAcl.SetAccessRuleProtection($true, $false)
    $ownerSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $fileAcl.SetOwner($ownerSid)
    $fileAcl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($ownerSid, 'FullControl', 'Allow'))
    $systemSid = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    $fileAcl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($systemSid, 'FullControl', 'Allow'))
    Set-Acl -LiteralPath $credentialPath -AclObject $fileAcl
} finally {
    $sqlCredential = $null
    $secureSqlPassword.Dispose()
    $secureSqlPassword = $null
    $sqlLogin = $null
}
Write-Output 'Windows DPAPI credential saved in TEMP for this Windows user. No database connection was opened.'
Write-Output 'Path: <TEMP>\uteexpress-sql-qa.credential.xml'
Write-Output 'Tell the agent that provisioning is complete; do not send the password or XML content. Remove the file after QA.'
