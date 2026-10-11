param(
 [string]$Java='java',
 [string]$JarPath='',
 [string]$ConfigFile='',
 [int]$Port=8123
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $JarPath){$JarPath=Join-Path $projectRoot 'target/moxiang-picture-backend-0.0.1-SNAPSHOT.jar'}
if(-not $ConfigFile){$ConfigFile=Join-Path $projectRoot 'src/main/resources/application-local.yml'}
if(-not(Test-Path -LiteralPath $JarPath -PathType Leaf)){throw 'Build the JAR before starting'}
if(-not(Test-Path -LiteralPath $ConfigFile -PathType Leaf)){throw 'Supply a private external YAML configuration; example files are not credentials'}
if($ConfigFile -match '\.example\.(yml|yaml)$'){throw 'Do not load an example file as real configuration'}
if($Port -lt 1024 -or $Port -gt 65535){throw 'Port must be 1024..65535'}
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw 'Requested port already in use; do not stop another process'}
$resolvedJar=(Resolve-Path -LiteralPath $JarPath).Path
$resolvedConfig=(Resolve-Path -LiteralPath $ConfigFile).Path
$configUri=([Uri]$resolvedConfig).AbsoluteUri
# Check only the artifact here. A Git index finding blocks publication, not local startup.
& (Join-Path $PSScriptRoot 'check-release.ps1') -JarPath $resolvedJar -SkipGitIndex
if(-not $?){throw 'Artifact safety check failed'}
$launchArgs=@('-jar',$resolvedJar,'--spring.profiles.active=local',
 ('--spring.config.additional-location='+$configUri),
 '--server.address=127.0.0.1',('--server.port='+$Port),
 '--picture.ai.enabled=false',
 '--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl',
 '--logging.level.root=INFO')
Write-Output 'LOCAL_ONLY: AI off; no database migration; Ctrl+C stops the foreground process.'
& $Java @launchArgs
if($LASTEXITCODE -ne 0){throw 'Backend exited unsuccessfully; inspect private logs without sharing credentials'}
