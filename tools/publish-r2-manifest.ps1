# OneFilm release publisher: APKs -> Cloudflare R2, then the update manifest.
#
# In-app updates download from R2 (fast in mainland China); GitHub Releases stay
# as the public download page. The manifest is the only thing the app reads, so
# every APK referenced from it must exist in the bucket.
#
# Object keys are content-addressed: "<original-name>-<sha256-first-8>.apk".
# Cache rule on the public host keeps APKs for a long time; reusing a key with
# different bytes would serve stale content, so the hash goes into the key.
#
# Manifest contract (keep in sync with app UpdateManifest.java):
#   versionCode   int
#   versionName   string
#   tag_name      "v<version>"
#   assets[]      { name, browser_download_url }
#   body          release notes
#
# Example:
#   .\tools\publish-r2-manifest.ps1 -Version 0.1.5 -VersionCode 6 `
#       -Repo Viciy2023/Onefilm -NotesFile .\docs\release-notes-0.1.5.md `
#       -ApkFiles .\app\build\outputs\apk\leanback\release\OneFilm-TV-v0.1.5-arm64-v8a.apk `
#       -PruneOldApks
#
# -PruneOldApks deletes every .apk object that the freshly published manifest does
# not reference, so the bucket keeps exactly one version. Old APKs are removed
# only after the new manifest is verified live; rollback then needs a re-upload.
#
# Without -DryRun, set R2_ACCOUNT_ID, R2_BUCKET and either R2_API_TOKEN or
# R2_TOKEN_FILE. Never commit the token.
#
# Keep this file ASCII-only: Windows PowerShell 5.1 decodes .ps1 with the legacy
# ANSI codepage unless a UTF-8 BOM is present.

[CmdletBinding()]
param(
  # 省略 -Version/-VersionCode 时从 gradle.properties 的 onefilm.versionName /
  # onefilm.versionCode 读取，保证包与清单版本一致。
  [string]$Version = "",
  [Parameter(Mandatory = $true)][string]$Repo,
  [string]$Tag = "",
  [int]$VersionCode = 0,
  [string]$BaseUrl = "https://onefilmapk.alibabagroup.de5.net",
  [string]$ManifestKey = "onefilm-stable.json",
  [string]$NotesFile = "",
  [string]$Notes = "OneFilm update",
  [string[]]$ApkFiles = @(),
  [string[]]$MappingFiles = @(),
  [string]$AccountId = $env:R2_ACCOUNT_ID,
  [string]$Bucket = $env:R2_BUCKET,
  [string]$TokenFile = $env:R2_TOKEN_FILE,
  [string]$Token = $env:R2_API_TOKEN,
  [switch]$ManifestOnly,
  [switch]$PruneOldApks,
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# 版本单一来源：gradle.properties
$gradleProps = @{}
$gradlePropsPath = Join-Path $PSScriptRoot "..\gradle.properties"
if (Test-Path $gradlePropsPath) {
  foreach ($line in Get-Content $gradlePropsPath) {
    if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $gradleProps[$matches[1].Trim()] = $matches[2].Trim() }
  }
}
if ([string]::IsNullOrWhiteSpace($Version)) { $Version = [string]$gradleProps['onefilm.versionName'] }
if ($VersionCode -le 0 -and $gradleProps['onefilm.versionCode']) { $VersionCode = [int]$gradleProps['onefilm.versionCode'] }
if ([string]::IsNullOrWhiteSpace($Version)) { throw "-Version is required (or set onefilm.versionName in gradle.properties)" }
if ($VersionCode -le 0) { throw "-VersionCode is required (or set onefilm.versionCode in gradle.properties)" }

if ($Version -match '^[vV]') { throw "-Version must not include the v prefix" }
if ([string]::IsNullOrWhiteSpace($Tag)) { $Tag = "v$Version" }
if (-not $Tag.StartsWith("v")) { throw "-Tag must start with v" }
if ([string]::IsNullOrWhiteSpace($Repo)) { throw "-Repo is required, e.g. Viciy2023/Onefilm" }
if ($BaseUrl -notmatch '^https://') { throw "-BaseUrl must be https" }
if ($ManifestKey -notmatch '\.json$') { throw "-ManifestKey must end with .json" }

if (-not [string]::IsNullOrWhiteSpace($NotesFile)) {
  if (-not (Test-Path $NotesFile)) { throw "notes file not found: $NotesFile" }
  $Notes = (Get-Content $NotesFile -Raw -Encoding UTF8).Trim()
}
if ([string]::IsNullOrWhiteSpace($Notes)) { $Notes = "OneFilm update" }

$base = $BaseUrl.TrimEnd('/')

# ---------------------------------------------------------------------------
# APK upload plan (content-addressed keys)
# ---------------------------------------------------------------------------
$uploads = @()
foreach ($file in $ApkFiles) {
  if ([string]::IsNullOrWhiteSpace($file)) { continue }
  if (-not (Test-Path $file)) { throw "APK not found: $file" }
  $item = Get-Item $file
  if ($item.Length -le 0) { throw "APK is empty: $file" }
  $sha = (Get-FileHash $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
  $sha8 = $sha.Substring(0, 8)
  $md5 = (Get-FileHash $item.FullName -Algorithm MD5).Hash.ToLowerInvariant()
  $key = "{0}-{1}{2}" -f [IO.Path]::GetFileNameWithoutExtension($item.Name), $sha8, $item.Extension.ToLowerInvariant()
  $uploads += [pscustomobject]@{
    Name = $item.Name
    Path = $item.FullName
    Size = $item.Length
    Sha256 = $sha
    Md5    = $md5
    Key    = $key
    Url    = "$base/$key"
  }
}

function Resolve-AssetUrl([string]$friendlyName) {
  foreach ($u in $uploads) {
    if ([string]::Equals($u.Name, $friendlyName, [StringComparison]::OrdinalIgnoreCase)) { return $u.Url }
  }
  return "https://github.com/$Repo/releases/download/$Tag/$friendlyName"
}

$assets = @(
  @{ name = "OneFilm-TV-v$Version-armeabi-v7a.apk"; browser_download_url = Resolve-AssetUrl "OneFilm-TV-v$Version-armeabi-v7a.apk" },
  @{ name = "OneFilm-TV-v$Version-arm64-v8a.apk"; browser_download_url = Resolve-AssetUrl "OneFilm-TV-v$Version-arm64-v8a.apk" },
  @{ name = "OneFilm-Mobile-v$Version-armeabi-v7a.apk"; browser_download_url = Resolve-AssetUrl "OneFilm-Mobile-v$Version-armeabi-v7a.apk" },
  @{ name = "OneFilm-Mobile-v$Version-arm64-v8a.apk"; browser_download_url = Resolve-AssetUrl "OneFilm-Mobile-v$Version-arm64-v8a.apk" }
)

$manifest = [ordered]@{
  versionCode = $VersionCode
  versionName = $Version
  tag_name    = $Tag
  assets      = $assets
  body        = $Notes
}
$json = $manifest | ConvertTo-Json -Depth 6 -Compress

Write-Host "manifest key : $ManifestKey"
Write-Host "tag          : $Tag"
Write-Host "version      : $Version (code=$VersionCode)"
Write-Host "assets       : $($assets.Count)"
Write-Host "apk uploads  : $($uploads.Count)"
foreach ($u in $uploads) {
  Write-Host ("  {0}  {1:N0} bytes  ->  {2}" -f $u.Name, $u.Size, $u.Key)
}
Write-Host "json         : $json"

if ($DryRun) {
  Write-Host "DRY RUN: not uploading"
  if ($PruneOldApks) { Write-Host "DRY RUN: would delete every .apk object not referenced by this manifest" }
  exit 0
}

if ([string]::IsNullOrWhiteSpace($AccountId)) { throw "R2_ACCOUNT_ID is required" }
if ([string]::IsNullOrWhiteSpace($Bucket)) { throw "R2_BUCKET is required" }
if ([string]::IsNullOrWhiteSpace($Token) -and -not [string]::IsNullOrWhiteSpace($TokenFile)) {
  if (-not (Test-Path $TokenFile)) { throw "token file not found: $TokenFile" }
  $Token = ([regex]::Match((Get-Content $TokenFile -Raw), '[A-Za-z0-9_\-]{35,80}')).Value
}
if ([string]::IsNullOrWhiteSpace($Token)) { throw "R2_API_TOKEN or R2_TOKEN_FILE is required" }

$curl = if (Get-Command curl.exe -ErrorAction SilentlyContinue) { "curl.exe" } else { "curl" }
$objectsApi = "https://api.cloudflare.com/client/v4/accounts/$AccountId/r2/buckets/$Bucket/objects"

foreach ($u in $uploads) {
  if ($ManifestOnly) {
    Write-Host ("  skip upload: {0} (assumed present as {1})" -f $u.Name, $u.Key)
    continue
  }
  Write-Host "PUT $($u.Key)"
  & $curl -sS --max-time 1800 -X PUT `
    -H "Authorization: Bearer $Token" `
    -H "Content-Type: application/vnd.android.package-archive" `
    -H "Cache-Control: public, max-age=31536000, immutable" `
    --data-binary "@$($u.Path)" `
    -o "$env:TEMP/onefilm-apk-upload.json" `
    -w "HTTP=%{http_code}`n" `
    "$objectsApi/$($u.Key)"
  if ($LASTEXITCODE -ne 0) { throw "APK upload failed: $($u.Name) (curl exit $LASTEXITCODE)" }

  # R2 answers a single-PUT with JSON metadata; a plain GET would stream the
  # whole object back, so validate from the PUT response instead.
  $resp = Get-Content "$env:TEMP/onefilm-apk-upload.json" -Raw -Encoding UTF8 | ConvertFrom-Json
  if (-not $resp.success) { throw "APK upload rejected: $($u.Name)" }
  $remoteSize = [int64]$resp.result.size
  $remoteEtag = ([string]$resp.result.etag).Trim('"').ToLowerInvariant()
  if ($remoteSize -ne $u.Size) { throw "size mismatch for $($u.Name): remote=$remoteSize local=$($u.Size)" }
  if ($remoteEtag -ne $u.Md5) { throw "etag/md5 mismatch for $($u.Name): remote=$remoteEtag local=$($u.Md5)" }
  Write-Host ("  verified {0} size={1:N0} md5={2}" -f $u.Key, $remoteSize, $remoteEtag)
}

# R8 mapping：按版本归档到私有前缀 mapping/v<version>/，只用于还原线上崩溃栈。
foreach ($mapping in $MappingFiles) {
  if ([string]::IsNullOrWhiteSpace($mapping)) { continue }
  if (-not (Test-Path $mapping)) { throw "mapping file not found: $mapping" }
  $mappingItem = Get-Item $mapping
  if ($mappingItem.Length -le 0) { throw "mapping file is empty: $mapping" }
  $mappingKey = "mapping/v$Version/$($mappingItem.Name)"
  $mappingMd5 = (Get-FileHash $mappingItem.FullName -Algorithm MD5).Hash.ToLowerInvariant()
  Write-Host "PUT $mappingKey"
  & $curl -sS --max-time 600 -X PUT `
    -H "Authorization: Bearer $Token" `
    -H "Content-Type: text/plain; charset=utf-8" `
    -H "Cache-Control: no-store" `
    --data-binary "@$($mappingItem.FullName)" `
    -o "$env:TEMP/onefilm-mapping-upload.json" `
    -w "HTTP=%{http_code}`n" `
    "$objectsApi/$($mappingKey)"
  if ($LASTEXITCODE -ne 0) { throw "mapping upload failed: $($mappingItem.Name) (curl exit $LASTEXITCODE)" }
  $mappingResp = Get-Content "$env:TEMP/onefilm-mapping-upload.json" -Raw -Encoding UTF8 | ConvertFrom-Json
  if (-not $mappingResp.success) { throw "mapping upload rejected: $($mappingItem.Name)" }
  if ([int64]$mappingResp.result.size -ne $mappingItem.Length) { throw "mapping size mismatch: $($mappingItem.Name)" }
  Write-Host ("  verified {0} size={1:N0}" -f $mappingKey, $mappingItem.Length)
}

# MANIFEST must NOT be cached: clients need the newest version immediately.
$tmp = Join-Path $env:TEMP "onefilm-update-manifest.json"
[IO.File]::WriteAllText($tmp, $json, (New-Object System.Text.UTF8Encoding($false)))
& $curl -sS --max-time 120 -X PUT `
  -H "Authorization: Bearer $Token" `
  -H "Content-Type: application/json; charset=utf-8" `
  -H "Cache-Control: no-cache, max-age=60" `
  --data-binary "@$tmp" `
  -w "manifest HTTP=%{http_code}`n" `
  "$objectsApi/$ManifestKey"
if ($LASTEXITCODE -ne 0) { throw "manifest upload failed (curl exit $LASTEXITCODE)" }

$publicUrl = "$base/$ManifestKey"
$check = Invoke-RestMethod -Method Get -Uri "$publicUrl`?t=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())" -Headers @{ "Cache-Control" = "no-cache" }
if ($check.versionCode -ne $VersionCode -or -not $check.assets -or $check.assets.Count -lt 1) {
  throw "published manifest failed read-back check"
}
Write-Host "PUBLISHED $publicUrl"

# ---------------------------------------------------------------------------
# Optional cleanup: keep only the APK set referenced by the manifest we just
# published (i.e. one version). Runs after the manifest is live, so a failed
# publish never strands clients without a downloadable APK.
# ---------------------------------------------------------------------------
if ($PruneOldApks) {
  $keep = @{}
  foreach ($u in $uploads) { $keep[$u.Key.ToLowerInvariant()] = $true }

  $listed = @()
  $cursor = ""
  do {
    $listUrl = "$objectsApi`?per_page=1000"
    if (-not [string]::IsNullOrEmpty($cursor)) { $listUrl += "&cursor=" + [Uri]::EscapeDataString($cursor) }
    $page = Invoke-RestMethod -Method Get -Uri $listUrl -Headers @{ Authorization = "Bearer $Token" }
    if (-not $page.success) { throw "R2 object listing failed" }
    $listed += @($page.result)
    $cursor = if ($page.result_info -and $page.result_info.is_truncated) { [string]$page.result_info.cursor } else { "" }
  } while (-not [string]::IsNullOrEmpty($cursor))

  $stale = @($listed | Where-Object {
      $_.key -match '(?i)\.apk$' -and -not $keep.ContainsKey(([string]$_.key).ToLowerInvariant())
    })
  $freed = 0
  foreach ($s in $stale) { $freed += [int64]$s.size }
  Write-Host ("prune: keeping {0} apk object(s), deleting {1} stale ({2:N1} MB)" -f $keep.Count, $stale.Count, ($freed / 1MB))

  foreach ($s in $stale) {
    Write-Host ("  DELETE {0}" -f $s.key)
    $deleteUrl = "$objectsApi/" + [Uri]::EscapeDataString([string]$s.key)
    & $curl -sS --max-time 120 -X DELETE `
      -H "Authorization: Bearer $Token" `
      -o "$env:TEMP/onefilm-apk-delete.json" `
      -w "HTTP=%{http_code}`n" `
      $deleteUrl
    if ($LASTEXITCODE -ne 0) { throw "delete failed: $($s.key) (curl exit $LASTEXITCODE)" }
    $resp = Get-Content "$env:TEMP/onefilm-apk-delete.json" -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $resp.success) { throw "delete rejected by R2: $($s.key)" }
  }
  if ($stale.Count -gt 0) { Write-Host "prune done" }
}
