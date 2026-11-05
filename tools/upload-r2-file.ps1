<#
.SYNOPSIS
    上传单个文件到 R2（Cloudflare REST API），用于放置发版辅助工具。

.DESCRIPTION
    需要 R2_ACCOUNT_ID / R2_BUCKET / R2_API_TOKEN 三个环境变量，
    或在命令行显式传入。Token 需要该 bucket 的编辑权限。

.EXAMPLE
    $env:R2_ACCOUNT_ID = "<account-id>"
    $env:R2_BUCKET = "onefilmapk"
    $env:R2_API_TOKEN = "<token>"
    .\tools\upload-r2-file.ps1 -Path .\tools\bugly\buglyqq-upload-symbol.jar -Key tools/buglyqq-upload-symbol.jar
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$AccountId = $env:R2_ACCOUNT_ID,
    [string]$Bucket = $env:R2_BUCKET,
    [string]$Token = $env:R2_API_TOKEN,
    [string]$ContentType = 'application/octet-stream'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Path)) { Write-Host "错误：文件不存在 $Path" -ForegroundColor Red; exit 1 }
if (-not $AccountId) { Write-Host "错误：缺少 R2_ACCOUNT_ID" -ForegroundColor Red; exit 1 }
if (-not $Bucket) { Write-Host "错误：缺少 R2_BUCKET" -ForegroundColor Red; exit 1 }
if (-not $Token) { Write-Host "错误：缺少 R2_API_TOKEN" -ForegroundColor Red; exit 1 }

$file = Get-Item $Path
$escapedKey = ($Key -split '/' | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
$uri = "https://api.cloudflare.com/client/v4/accounts/$AccountId/r2/buckets/$Bucket/objects/$escapedKey"

Write-Host ("上传 {0}（{1:N1} MB）-> {2}/{3}" -f $file.Name, ($file.Length / 1MB), $Bucket, $Key)

$response = curl.exe -sS -X PUT $uri `
    -H "Authorization: Bearer $Token" `
    -H "Content-Type: $ContentType" `
    --data-binary "@$($file.FullName)"

if ($LASTEXITCODE -ne 0) { Write-Host "错误：上传失败（curl 退出码 $LASTEXITCODE）" -ForegroundColor Red; exit 1 }

try {
    $result = $response | ConvertFrom-Json
} catch {
    Write-Host "错误：返回内容无法解析：$response" -ForegroundColor Red
    exit 1
}

if (-not $result.success) {
    Write-Host ("错误：R2 拒绝上传：{0}" -f ($result.errors | ConvertTo-Json -Compress)) -ForegroundColor Red
    exit 1
}

Write-Host ("上传成功：size={0:N0} bytes" -f [int64]$result.result.size) -ForegroundColor Green
