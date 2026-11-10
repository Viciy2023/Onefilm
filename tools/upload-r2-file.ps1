<#
.SYNOPSIS
    上传单个文件到对象存储，用于放置发版辅助工具。

.DESCRIPTION
    凭据按以下优先级读取，仓库内不保存任何凭据：
      1. 命令行参数 -AccountId / -Bucket / -Token
      2. 环境变量 R2_ACCOUNT_ID / R2_BUCKET / R2_API_TOKEN / R2_TOKEN_FILE
      3. 本地凭据文件（路径由 R2_TOKEN_FILE 指定，已加入 .gitignore）

.EXAMPLE
    .\tools\upload-r2-file.ps1 -Path .\tools\bugly\buglyqq-upload-symbol.jar -Key tools/buglyqq-upload-symbol.jar
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$AccountId = $env:R2_ACCOUNT_ID,
    [string]$Bucket = $env:R2_BUCKET,
    [string]$Token = $env:R2_API_TOKEN,
    [string]$TokenFile = $env:R2_TOKEN_FILE,
    [string]$ContentType = 'application/octet-stream'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
if (-not $AccountId) { $AccountId = '681f9c10831c4c44e403ca40d723e587' }
if (-not $Bucket) { $Bucket = 'onefilmapk' }

function Read-TokenFromFile([string]$file) {
    if (-not $file -or -not (Test-Path $file)) { return $null }
    $match = [regex]::Match((Get-Content $file -Raw), '[A-Za-z0-9_\-]{35,80}')
    if ($match.Success) { return $match.Value }
    return $null
}

if (-not $Token) { $Token = Read-TokenFromFile $TokenFile }
if (-not $Token) { $Token = Read-TokenFromFile (Join-Path $root '.r2token') }

if (-not (Test-Path $Path)) { Write-Host "错误：文件不存在 $Path" -ForegroundColor Red; exit 1 }
if (-not $Token) { Write-Host "错误：没有可用凭据（参数 / 环境变量 / 凭据文件都没有）" -ForegroundColor Red; exit 1 }

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
