# Disposable browser QA only. Never imports SQL credentials or invokes Flyway/JDBC.
param([switch]$UiPolish)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$qaJava=Join-Path $env:JAVA_HOME 'bin\java.exe'
$qaChrome=Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'
if(!(Test-Path -LiteralPath $qaJava) -or !(Test-Path -LiteralPath $qaChrome)){throw 'Java 21 and installed Chrome are required'}
foreach($qaPort in @(18082,19222)){if(Get-NetTCPConnection -LocalPort $qaPort -State Listen -ErrorAction SilentlyContinue){throw ('QA port is already occupied: '+$qaPort)}}
$qaCpFile=Join-Path (Get-Location) 'target\release-tools-classpath.txt'
if(!(Test-Path -LiteralPath $qaCpFile)){throw 'Run test-compile dependency:build-classpath with -Dmdep.outputFile=target/release-tools-classpath.txt first'}
$qaCp='target\test-classes;target\classes;'+[IO.File]::ReadAllText($qaCpFile).Trim()
$qaSuffix=[Guid]::NewGuid().ToString('N')
$qaProfile=Join-Path $env:TEMP ('uteexpress-headless-'+$qaSuffix)
$qaEvidence=Join-Path $env:TEMP ('uteexpress-browser-evidence-'+$qaSuffix)
New-Item -ItemType Directory -Path $qaEvidence | Out-Null
$qaOutput=Join-Path $qaEvidence 'h2.log'
$qaError=Join-Path $qaEvidence 'h2-error.log'
$qaChromeProcess=$null
try {
 $qaRandom=[Security.Cryptography.RandomNumberGenerator]::Create()
 $qaRandomBytes=New-Object byte[] 24
 $qaRandom.GetBytes($qaRandomBytes)
 $env:UTE_DEMO_PASSWORD=[Convert]::ToBase64String($qaRandomBytes)
 [Array]::Clear($qaRandomBytes,0,$qaRandomBytes.Length)
 $qaRandom.Dispose()
 $env:UTE_BROWSER_H2_CONFIRMED='YES'
 $env:UTE_DEMO_TEST_MAIL='YES'
 $env:UTE_BROWSER_EVIDENCE_DIR=$qaEvidence
 if($UiPolish){$env:UTE_UI_POLISH_QA='YES'}else{Remove-Item Env:UTE_UI_POLISH_QA -ErrorAction SilentlyContinue}
 $qaApp=Start-Process -FilePath $qaJava -WindowStyle Hidden -ArgumentList @('-cp',$qaCp,'com.ute.uteexpress.operations.LocalDemoApplication','--port=18082') -RedirectStandardOutput $qaOutput -RedirectStandardError $qaError -PassThru
 $qaDeadline=(Get-Date).AddSeconds(90)
 while(!(Test-Path -LiteralPath $qaOutput) -or !((Get-Content -LiteralPath $qaOutput -Raw) -match 'LOCAL H2 DEMO seeded 18 scenarios')){if($qaApp.HasExited){throw 'H2 startup failed; inspect isolated log'};if((Get-Date)-gt $qaDeadline){throw 'H2 seed timeout'};Start-Sleep -Milliseconds 300}
 $qaChromeProcess=Start-Process -FilePath $qaChrome -WindowStyle Hidden -ArgumentList @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-address=127.0.0.1','--remote-debugging-port=19222',('--user-data-dir='+$qaProfile),'about:blank') -PassThru
 $qaDeadline=(Get-Date).AddSeconds(20)
 while($true){try{$qaVersion=Invoke-RestMethod 'http://127.0.0.1:19222/json/version' -TimeoutSec 2;break}catch{if((Get-Date)-gt $qaDeadline){throw 'Headless Chrome startup timeout'};Start-Sleep -Milliseconds 200}}
 if($qaVersion.'User-Agent' -notmatch 'HeadlessChrome'){throw 'Dedicated headless browser identity mismatch'}
 & $qaJava -cp $qaCp com.ute.uteexpress.operations.BrowserQaTool
 if($LASTEXITCODE -ne 0){throw 'Browser runner failed'}
 Write-Output ('BROWSER_EVIDENCE='+$qaEvidence)
} finally {
 Remove-Item Env:UTE_DEMO_PASSWORD,Env:UTE_DEMO_TEST_MAIL,Env:UTE_BROWSER_H2_CONFIRMED,Env:UTE_BROWSER_EVIDENCE_DIR,Env:UTE_UI_POLISH_QA -ErrorAction SilentlyContinue
 if($qaChromeProcess -and !$qaChromeProcess.HasExited){Stop-Process -Id $qaChromeProcess.Id}
 if($qaApp -and !$qaApp.HasExited){Stop-Process -Id $qaApp.Id}
 # No recursive cleanup; evidence and isolated Chrome profile remain in TEMP for inspection.
}
