# OneFilm update manifest publisher.
#
# The APK is distributed by GitHub Releases; only the small JSON manifest is
# uploaded to Cloudflare R2. This keeps the update endpoint on R2 while the
# actual binaries live with the repository.
#
# Example:
#   .\tools\publish-r2-manifest.ps1 -Version 0.1.1 -VersionCode 2 `
#       -Repo Viciy2023/Onefilm -NotesFile .\release-notes.txt -DryRun
#
# Without -DryRun, set R2_ACCOUNT_ID, R2_BUCKET and either R2_API_TOKEN or
# R2_TOKEN_FILE. Never commit the token.

[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Version,
  [Parameter(Mandatory = $true)][string]$Repo,
  [string]$Tag = "",
  [int]$VersionCode = 0,
  [string]$BaseUrl = "https://onefilmapk.alibabagroup.de5.net",
  [string]$ManifestKey = "onefilm-stable.json",
  [string]$NotesFile = "",
  [string]$Notes = "OneFilm update",
  [string]$AccountId = $env:R2_ACCOUNT_ID,
  [string]$Bucket = $env:R2_BUCKET,
  [string]$TokenFile = $env:R2_TOKEN_FILE,
  [string]$Token = $env:R2_API_TOKEN,
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if ($Version -match '^[vV]') {
  throw "-Version must not include the v prefix"
}
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
$assets = @(
  @{
    name = "OneFilm-TV-v$Version-armeabi-v7a.apk"
    browser_download_url = "https://github.com/$Repo/releases/download/$Tag/OneFilm-TV-v$Version-armeabi-v7a.apk"
  },
  @{
    name = "OneFilm-TV-v$Version-arm64-v8a.apk"
    browser_download_url = "https://github.com/$Repo/releases/download/$Tag/OneFilm-TV-v$Version-arm64-v8a.apk"
  }
)

$manifest = [ordered]@{
  versionCode = $VersionCode
  versionName = $Version
  tag_name = $Tag
  assets = $assets
  body = $Notes
}
$json = $manifest | ConvertTo-Json -Depth 6 -Compress

Write-Host "manifest key : $ManifestKey"
Write-Host "tag          : $Tag"
Write-Host "version      : $Version (code=$VersionCode)"
Write-Host "assets       : $($assets.Count)"
Write-Host "json         : $json"

if ($DryRun) {
  Write-Host "DRY RUN: not uploading"
  exit 0
}

if ([string]::IsNullOrWhiteSpace($AccountId)) { throw "R2_ACCOUNT_ID is required" }
if ([string]::IsNullOrWhiteSpace($Bucket)) { throw "R2_BUCKET is required" }
if ([string]::IsNullOrWhiteSpace($Token) -and -not [string]::IsNullOrWhiteSpace($TokenFile)) {
  if (-not (Test-Path $TokenFile)) { throw "token file not found: $TokenFile" }
  $Token = ([regex]::Match((Get-Content $TokenFile -Raw), '[A-Za-z0-9_\-]{35,80}')).Value
}
if ([string]::IsNullOrWhiteSpace($Token)) { throw "R2_API_TOKEN or R2_TOKEN_FILE is required" }

$objectsApi = "https://api.cloudflare.com/client/v4/accounts/$AccountId/r2/buckets/$Bucket/objects"
$tmp = Join-Path $env:TEMP "onefilm-update-manifest.json"
[IO.File]::WriteAllText($tmp, $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "PUT $ManifestKey"
curl.exe -sS --max-time 120 -X PUT `
  -H "Authorization: Bearer $Token" `
  -H "Content-Type: application/json" `
  -H "Cache-Control: no-cache, max-age=60" `
  --data-binary "@$tmp" `
  -w "HTTP=%{http_code}`n" `
  "$objectsApi/$ManifestKey"
if ($LASTEXITCODE -ne 0) { throw "R2 upload failed (curl exit $LASTEXITCODE)" }
Write-Host "PUBLISHED $base/$ManifestKey"
