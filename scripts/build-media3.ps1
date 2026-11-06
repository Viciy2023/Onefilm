param(
    [string]$Tag = "1.8.0",
    [string]$Modules = "lib-common,lib-ui,lib-exoplayer,lib-exoplayer-hls",
    [string]$WorkDir = "$env:TEMP\media3-src",
    [string]$SdkDir = "D:\android_sdk"
)

$ErrorActionPreference = "Stop"
$moduleList = @($Modules -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($moduleList.Count -eq 0) { throw "no modules specified" }
$repoRoot = Split-Path -Parent $PSScriptRoot
$patches = Join-Path $repoRoot "third_party\media3\patches"

if (-not (Test-Path $WorkDir)) {
    git clone --depth 1 --branch $Tag https://github.com/androidx/media.git $WorkDir
}

Set-Content -Path (Join-Path $WorkDir "local.properties") -Value "sdk.dir=$($SdkDir -replace '\\','\\')" -Encoding ascii

Push-Location $WorkDir
try {
    if (Test-Path $patches) {
        Get-ChildItem $patches -Filter *.patch | Sort-Object Name | ForEach-Object {
            # 幂等：已应用的补丁跳过，避免重复构建失败
            git apply --reverse --check $_.FullName 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Already applied: $($_.Name)"
                return
            }
            Write-Host "Applying $($_.Name)"
            git apply --whitespace=nowarn $_.FullName
            if ($LASTEXITCODE -ne 0) { throw "patch failed: $($_.Name)" }
        }
    }

    $tasks = @($moduleList | ForEach-Object { ":$($_):assembleRelease" })
    Write-Host "Build tasks: $($tasks -join ', ')"
    & .\gradlew.bat $tasks --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "media3 build failed: $LASTEXITCODE" }

    $map = @{
        "lib-common"        = "common"
        "lib-ui"            = "ui"
        "lib-exoplayer"     = "exoplayer"
        "lib-exoplayer-hls" = "exoplayer_hls"
    }
    foreach ($module in $moduleList) {
        $name = $map[$module]
        $src = Join-Path $WorkDir "libraries\$name\buildout\outputs\aar\$module-release.aar"
        $dst = Join-Path $repoRoot "app\libs\$module-release.aar"
        Copy-Item $src $dst -Force
        Write-Host "Copied $module-release.aar"
    }
}
finally {
    Pop-Location
}
