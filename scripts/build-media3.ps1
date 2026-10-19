param(
    [string]$Tag = "1.8.0",
    [string[]]$Modules = @("lib-common", "lib-ui", "lib-exoplayer"),
    [string]$WorkDir = "$env:TEMP\media3-src",
    [string]$SdkDir = "D:\android_sdk"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$patches = Join-Path $repoRoot "third_party\media3\patches"

if (-not (Test-Path $WorkDir)) {
    git clone --depth 1 --branch $Tag https://github.com/androidx/media.git $WorkDir
}

Set-Content -Path (Join-Path $WorkDir "local.properties") -Value "sdk.dir=$($SdkDir -replace '\\','\\')" -Encoding ascii

if (Test-Path $patches) {
    Get-ChildItem $patches -Filter *.patch | Sort-Object Name | ForEach-Object {
        Write-Host "Applying $($_.Name)"
        git -C $WorkDir apply --whitespace=nowarn $_.FullName
    }
}

Push-Location $WorkDir
try {
    $tasks = $Modules | ForEach-Object { ":$($_):assembleRelease" }
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "media3 build failed: $LASTEXITCODE" }

    $map = @{
        "lib-common"    = "common"
        "lib-ui"        = "ui"
        "lib-exoplayer" = "exoplayer"
    }
    foreach ($module in $Modules) {
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
