param(
 [string]$JarPath='',
 [string]$FrontendRoot='',
 [switch]$SkipGitIndex,
 [switch]$ForPublication
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
if($ForPublication -and $SkipGitIndex){throw 'Publication checks cannot skip the Git index'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $JarPath){$JarPath=Join-Path $projectRoot 'target/moxiang-picture-backend-0.0.1-SNAPSHOT.jar'}
if(-not(Test-Path -LiteralPath $JarPath -PathType Leaf)){throw 'Missing packaged JAR'}
$script:issues=New-Object 'System.Collections.Generic.List[string]'
$script:indexIssues=New-Object 'System.Collections.Generic.List[string]'
$script:knownSecrets=New-Object 'System.Collections.Generic.List[string]'
# Inspect values only in memory. Never print credential values, YAML lines or Git blobs.
$credentialLine='(?im)^\s*(?:password|secret[-_]?id|secret[-_]?key|api[-_]?key|MOXIANG_(?:DB_PASSWORD|REDIS_PASSWORD|COS_SECRET_ID|COS_SECRET_KEY|AI_API_KEY))\s*[:=]\s*([^\r\n]*)'
function Literal-Credentials([string]$content){
 foreach($match in [regex]::Matches($content,$credentialLine)){
  $value=($match.Groups[1].Value -replace '\s+#.*$','').Trim().Trim('"',"'")
  if($value -and $value -notmatch '^\$\{[A-Za-z_][A-Za-z0-9_]*(?::)?\}$'){return $true}
 }
 return $false
}
function Text-Issue([string]$name,[string]$content){
 if($name -match '\.(yml|yaml|properties|env)$|(^|/)\.env' -and (Literal-Credentials $content)){return $true}
 foreach($secret in $script:knownSecrets){if($content.Contains($secret)){return $true}}
 return $false
}
$privateConfig=Join-Path $projectRoot 'src/main/resources/application-local.yml'
if(Test-Path -LiteralPath $privateConfig){
 $privateText=[IO.File]::ReadAllText($privateConfig)
 foreach($match in [regex]::Matches($privateText,$credentialLine)){
  $value=($match.Groups[1].Value -replace '\s+#.*$','').Trim().Trim('"',"'")
  # Long known tokens are searched everywhere. Short passwords only via structured
  # credential fields, to avoid matching ordinary IDs, image bytes and fixtures.
  if($value.Length -ge 12 -and -not $value.StartsWith('${')){$script:knownSecrets.Add($value)}
 }
 $privateText=$null
}
$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath).Path)
$ownEntryCount=0;$baseFound=$false;$mainFound=$false
try{
 foreach($entry in $zip.Entries){
  if(-not $entry.FullName.StartsWith('BOOT-INF/classes/')){continue}
  $name=$entry.FullName.Substring('BOOT-INF/classes/'.Length)
  if(-not $entry.Length){continue};$ownEntryCount++
  if($name -eq 'application.yml'){$baseFound=$true}
  if($name -eq 'com/moxiang/picture/MoxiangPictureApplication.class'){$mainFound=$true}
  if($name -match '(^|/)application-.*\.(yml|yaml|properties)$|(^|/)\.env|\.(pem|key|p12|jks)$|^(private|secrets)/'){$script:issues.Add('PRIVATE_ENTRY: '+$name)}
  if($entry.Length -gt 8388608){$script:issues.Add('UNSCANNED_LARGE_ENTRY: '+$name);continue}
  $reader=New-Object IO.StreamReader($entry.Open(),[Text.Encoding]::UTF8)
  try{$content=$reader.ReadToEnd()}finally{$reader.Dispose()}
  if(Text-Issue $name $content){$script:issues.Add('CREDENTIAL_CONTENT: '+$name)}
 }
}finally{$zip.Dispose()}
if(-not $baseFound -or -not $mainFound){$script:issues.Add('MISSING_BASE_OR_MAIN_CLASS')}
# Scan publishable resources, not private configuration, backups or build caches.
foreach($file in Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/resources') -File -Recurse){
 if($file.Name -match '^application-.*\.(yml|yaml|properties)$' -and $file.Name -notmatch '\.example\.') {continue}
 if($file.Extension -in @('.yml','.yaml','.properties','.json','.xml','.txt','.js','.html')){
  if(Text-Issue $file.Name ([IO.File]::ReadAllText($file.FullName))){$script:issues.Add('SOURCE_RESOURCE_CREDENTIAL: '+$file.Name)}
 }
}
if($FrontendRoot){
 $frontend=[IO.Path]::GetFullPath($FrontendRoot)
 if(-not(Test-Path -LiteralPath $frontend -PathType Container)){throw 'Frontend directory missing'}
 foreach($relative in @('src','dist')){
  $folder=Join-Path $frontend $relative
  if(Test-Path -LiteralPath $folder){foreach($file in Get-ChildItem -LiteralPath $folder -Recurse -File){
   if($file.Extension -in @('.js','.ts','.vue','.html','.json','.css','.map')){
    if(Text-Issue $file.Name ([IO.File]::ReadAllText($file.FullName))){$script:issues.Add('FRONTEND_KNOWN_CREDENTIAL: '+$relative+'/'+$file.Name)}
   }
  }}
 }
 foreach($file in Get-ChildItem -LiteralPath $frontend -File -Force | Where-Object Name -Like '.env*'){
  $text=[IO.File]::ReadAllText($file.FullName)
  if((Text-Issue $file.Name $text) -or $text -match '(?im)^VITE_.*(?:SECRET|PASSWORD|API_KEY|PRIVATE_KEY)\s*=\s*\S+'){$script:issues.Add('FRONTEND_ENV_CREDENTIAL: '+$file.Name)}
 }
}
$indexRoot=$null
$indexLayout='NONE'
if(Test-Path -LiteralPath (Join-Path $projectRoot '.git')){
 $indexRoot=$projectRoot;$indexLayout='SINGLE_PROJECT'
}else{
 $parentRoot=Split-Path $projectRoot -Parent
 # Recognize only the deliberate backend/frontend layout, not an unrelated
 # ancestor repository that happens to contain a copied project directory.
 if((Split-Path $projectRoot -Leaf) -eq 'backend' -and
    (Test-Path -LiteralPath (Join-Path $parentRoot '.git')) -and
    (Test-Path -LiteralPath (Join-Path $parentRoot 'frontend/package.json'))){
  $indexRoot=$parentRoot;$indexLayout='MONOREPO_ROOT'
 }
}
if(-not $SkipGitIndex -and $indexRoot){
 $git=Get-Command git -ErrorAction Stop
 $paths=& $git.Source -c core.quotepath=false -C $indexRoot ls-files
 if($LASTEXITCODE -ne 0){throw 'Git index enumeration failed'}
 Write-Output ('INDEX_FILES_SCANNED='+@($paths).Count)
 foreach($name in $paths){
  if([string]::IsNullOrEmpty($name)){continue}
  if($name -match '(^|/)application-(?!.*\.example\.).*\.(yml|yaml|properties)$|(^|/)\.env(?!\.example$)|\.(pem|key|p12|jks)$'){$script:indexIssues.Add('PRIVATE_INDEX_ENTRY: '+$name)}
  $readBlobArgs=@('-C',$indexRoot,'cat-file','blob',(':'+$name))
  $blob=(& $git.Source @readBlobArgs 2>$null) -join "`n"
  if($LASTEXITCODE -ne 0){$script:indexIssues.Add('UNREADABLE_INDEX_ENTRY: '+$name);continue}
  if(Text-Issue $name $blob){$script:indexIssues.Add('INDEX_CREDENTIAL: '+$name)}
 }
}
Write-Output ('JAR_OWN_ENTRIES_SCANNED='+$ownEntryCount)
Write-Output ('JAR_SHA256='+(Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant())
foreach($issue in $script:issues){Write-Output $issue}
foreach($issue in $script:indexIssues){Write-Output $issue}
Write-Output ('ARTIFACT_FINDINGS='+$script:issues.Count)
Write-Output ('INDEX_FINDINGS='+$script:indexIssues.Count)
Write-Output ('INDEX_LAYOUT='+$indexLayout)
Write-Output 'SCOPE: own JAR entries, public resources, optional frontend, current Git index; not dependency/history/security audit.'
if($script:issues.Count){throw 'Artifact/source credential safety checks failed'}
if($ForPublication -and -not $indexRoot){throw 'Publication blocked: no supported local Git index was found'}
if($ForPublication -and $script:indexIssues.Count){throw 'Publication blocked: index needs explicit user-approved review; do not commit as-is'}
