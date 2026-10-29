[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if(-not ([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains 18085)){throw 'Start the approved FinalDemo server first; this reader never starts or stops it'}
if([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains 19225){throw 'CDP 19225 occupied; no existing process will be stopped'}
$qaChrome=Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'
if(-not (Test-Path -LiteralPath $qaChrome)){throw 'Installed Chrome required'}
$qaJava=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{'java'}
$qaId=[Guid]::NewGuid().ToString('N');$qaEvidence=Join-Path $env:TEMP ('uteexpress-stability-sql-read-'+$qaId)
$qaProfile=Join-Path $env:TEMP ('uteexpress-stability-chrome-'+$qaId)
New-Item -ItemType Directory -Path $qaEvidence | Out-Null
$qaCredential=Import-Clixml -LiteralPath (Join-Path $env:TEMP 'uteexpress-final-demo-web.credential.xml')
if($qaCredential -isnot [Management.Automation.PSCredential]){throw 'Web DPAPI credential required; never pass password in arguments'}
$qaDeps=(Get-Content -LiteralPath (Join-Path $qaRoot 'target/release-tools-classpath.txt') -Raw).Trim()
$qaCp=(Join-Path $qaRoot 'target/test-classes')+';'+(Join-Path $qaRoot 'target/classes')+';'+$qaDeps
$qaStart=[Diagnostics.ProcessStartInfo]::new();$qaStart.FileName=$qaJava
$qaStart.Arguments='-cp "'+$qaCp+'" com.ute.uteexpress.finaldemo.FinalDemoBrowserReadTool'
$qaStart.WorkingDirectory=$qaRoot;$qaStart.UseShellExecute=$false;$qaStart.CreateNoWindow=$true
$qaStart.RedirectStandardOutput=$true;$qaStart.RedirectStandardError=$true
foreach($qaKey in @($qaStart.EnvironmentVariables.Keys)){if($qaKey -match '^(SPRING_|LOADER_|DB_|UTE_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)'){$qaStart.EnvironmentVariables.Remove($qaKey)}}
$qaStart.EnvironmentVariables['UTE_FINAL_DEMO_WEB_PASSWORD']=$qaCredential.GetNetworkCredential().Password
$qaStart.EnvironmentVariables['UTE_FINAL_BROWSER_READ_APPROVED']='YES'
$qaStart.EnvironmentVariables['UTE_FINAL_BROWSER_EVIDENCE']=$qaEvidence
$qaChromeProcess=$null;$qaReader=$null;$qaReaderStarted=$false
try{
 $qaChromeProcess=Start-Process -FilePath $qaChrome -WindowStyle Hidden -ArgumentList @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-address=127.0.0.1','--remote-debugging-port=19225',('--user-data-dir='+$qaProfile),'about:blank') -PassThru
 $qaDeadline=[DateTime]::UtcNow.AddSeconds(25)
 while($true){try{$qaVersion=Invoke-RestMethod 'http://127.0.0.1:19225/json/version' -TimeoutSec 2;break}catch{if([DateTime]::UtcNow -gt $qaDeadline){throw 'Dedicated Chrome startup timeout'};Start-Sleep -Milliseconds 200}}
 if($qaVersion.'User-Agent' -notmatch 'HeadlessChrome'){throw 'Dedicated Chrome identity mismatch'}
 $qaReader=[Diagnostics.Process]::new();$qaReader.StartInfo=$qaStart
 $qaReaderStarted=$qaReader.Start();if(-not $qaReaderStarted){throw 'Reader start failed'}
 $qaErrorTask=$qaReader.StandardError.ReadToEndAsync()
 while($null -ne ($qaLine=$qaReader.StandardOutput.ReadLine())){if($qaLine -match '^(PASS |FAIL |BLOCKED |PASS=|FAIL=|BLOCKED=)'){$qaLine}}
 $qaReader.WaitForExit();$qaIgnoredErrors=$qaErrorTask.GetAwaiter().GetResult()
 Write-Output ('SQL_BROWSER_EVIDENCE='+$qaEvidence)
 if($qaReader.ExitCode -ne 0){throw 'Browser checks found failures/blocks; inspect redacted JSON in TEMP. Server was not changed'}
}finally{
 if($qaReader){if($qaReaderStarted -and -not $qaReader.HasExited){$qaReader.Kill();$qaReader.WaitForExit()};$qaReader.Dispose()}
 if($qaChromeProcess -and -not $qaChromeProcess.HasExited){Stop-Process -Id $qaChromeProcess.Id}
 $qaStart.EnvironmentVariables.Remove('UTE_FINAL_DEMO_WEB_PASSWORD');$qaCredential=$null
}
