<#
.SYNOPSIS
    下载 CI 产出的 R8 mapping，并上传到 Bugly（TV / 手机两个包）。

.DESCRIPTION
    发版收尾用。mapping 归属该版本对应的 Release 流水线 artifact
    （r8-mapping-v<version>，保留 90 天），保证与线上 APK 完全一致。

    前提：本机有 gh（已登录）、java，以及 tools/bugly/buglyqq-upload-symbol.jar。
    这个 jar 不进仓库（.gitignore），首次使用需自行从 Bugly 下载。

.EXAMPLE
    .\tools\upload-bugly-mapping.ps1 -Version 0.1.20

.EXAMPLE
    .\tools\upload-bugly-mapping.ps1 -Version 0.1.20 -RunId 42046252823
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$RunId = "",
    [string]$Repo = "",
    [switch]$SkipTv,
    [switch]$SkipMobile,
    [switch]$KeepTemp
)

$ErrorActionPreference = 'Stop'

$Version = $Version.TrimStart('v')
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $PSScriptRoot 'bugly\buglyqq-upload-symbol.jar'
$propsFile = Join-Path $root 'gradle.properties'
$artifactName = "r8-mapping-v$Version"

function Fail([string]$message) {
    Write-Host "错误：$message" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $jar)) {
    Fail "缺少上传工具：$jar`n请从 Bugly 官网下载 buglyqq-upload-symbol.jar 放到 tools\bugly\ 下。"
}
if (-not (Test-Path $propsFile)) {
    Fail "找不到 gradle.properties：$propsFile"
}

$props = @{}
Get-Content $propsFile | ForEach-Object {
    if ($_ -match '^([^#=]+?)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2].Trim() }
}

if (-not $Repo) {
    $url = (git -C $root remote get-url origin).Trim()
    $Repo = ($url -replace '^.*github\.com[:/]', '') -replace '\.git$', ''
}
if (-not $Repo) { Fail "无法从 git remote 推断仓库，请用 -Repo owner/name 指定" }

if (-not $RunId) {
    Write-Host "查找 $artifactName 对应的 Release 流水线..."
    $json = gh api "repos/$Repo/actions/artifacts?name=$artifactName&per_page=100"
    if ($LASTEXITCODE -ne 0) { Fail "查询 artifact 失败（$Repo）" }
    $artifact = ($json | ConvertFrom-Json).artifacts |
        Where-Object { -not $_.expired } |
        Sort-Object created_at -Descending |
        Select-Object -First 1
    if (-not $artifact) {
        Fail "没有找到未过期的 $artifactName。`n请确认该版本 Release 流水线已成功（artifact 保留 90 天），或用 -RunId 指定。"
    }
    $RunId = [string]$artifact.workflow_run.id
    Write-Host "使用 Run ID：$RunId"
}

$temp = Join-Path $env:TEMP ("bugly-" + $Version + "-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $temp | Out-Null

try {
    Write-Host "下载 artifact $artifactName ..."
    gh run download $RunId -n $artifactName -D $temp
    if ($LASTEXITCODE -ne 0) { Fail "下载 artifact 失败（Run $RunId）" }

    function Upload([string]$flavor, [string]$idKey, [string]$keyKey, [string]$relativePath) {
        $mapping = Join-Path $temp $relativePath
        if (-not (Test-Path $mapping)) { Fail "artifact 中缺少 $relativePath" }
        $sizeMb = [math]::Round((Get-Item $mapping).Length / 1MB, 2)
        if ($sizeMb -le 0) { Fail "$relativePath 是空文件" }

        $appId = $props[$idKey]
        $appKey = $props[$keyKey]
        if (-not $appId -or -not $appKey) { Fail "gradle.properties 缺少 $idKey / $keyKey" }

        Write-Host "上传 $flavor（appId=$appId, version=$Version, mapping=${sizeMb}MB）..."
        $output = & java -jar $jar -appid $appId -appkey $appKey -version $Version -platform Android -inputMapping $mapping 2>&1
        $code = $LASTEXITCODE
        if ($code -ne 0) {
            $output | Select-Object -Last 12 | ForEach-Object { Write-Host $_ }
            Fail "$flavor 上传失败（退出码 $code）"
        }
        Write-Host "  $flavor 上传成功" -ForegroundColor Green
    }

    if (-not $SkipTv) {
        Upload 'TV' 'onefilm.bugly.appId.tv' 'onefilm.bugly.appKey.tv' 'leanbackRelease\mapping.txt'
    }
    if (-not $SkipMobile) {
        Upload '手机' 'onefilm.bugly.appId.mobile' 'onefilm.bugly.appKey.mobile' 'mobileRelease\mapping.txt'
    }

    Write-Host ""
    Write-Host "完成：$Version 的 Bugly 符号表已更新。" -ForegroundColor Green
}
finally {
    if (-not $KeepTemp -and (Test-Path $temp)) {
        Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
    } elseif ($KeepTemp) {
        Write-Host "mapping 保留在：$temp"
    }
}
