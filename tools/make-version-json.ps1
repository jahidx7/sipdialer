<#
Creates release\<versionName>\version.json for the in-app updater.
- versionCode / versionName come from the "lite" flavor in app\build.gradle.kts
- update feed URL comes from local.properties (updateUrl, git-ignored); apkUrl is built next to it
- APK is release\<versionName>\NoxSIPLite-<versionName>.apk ; SHA-256 is computed here
Usage (from the project root):  powershell -ExecutionPolicy Bypass -File tools\make-version-json.ps1 [-Notes "what changed"]
Then upload BOTH files in that folder (APK + version.json) to the server folder behind updateUrl.
#>
param([string]$Notes = "")
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$gradle = Get-Content (Join-Path $root "app\build.gradle.kts") -Raw
$m = [regex]::Match($gradle, 'create\("lite"\)\s*\{[^}]*?versionCode\s*=\s*(\d+)[^}]*?versionName\s*=\s*"([^"]+)"')
if (-not $m.Success) { throw "Could not read versionCode/versionName of the lite flavor" }
$code = [int]$m.Groups[1].Value
$name = $m.Groups[2].Value

$propsFile = Join-Path $root "local.properties"
if (-not (Test-Path $propsFile)) { throw "local.properties not found (needs updateUrl=...)" }
$line = Get-Content $propsFile | Where-Object { $_ -match '^\s*updateUrl\s*=' } | Select-Object -First 1
if (-not $line) { throw "updateUrl is missing in local.properties" }
$feed = ($line -split '=', 2)[1].Trim()
if ($feed -notmatch '^https://') { throw "updateUrl must start with https://" }
$base = $feed.Substring(0, $feed.LastIndexOf('/') + 1)

$dir = Join-Path $root "release\$name"
$apkName = "NoxSIPLite-$name.apk"
$apk = Join-Path $dir $apkName
if (-not (Test-Path $apk)) { throw "APK not found: $apk (build it and copy it into release\$name first)" }

$sha = (Get-FileHash -Algorithm SHA256 $apk).Hash.ToLower()
$json = [ordered]@{
    versionCode = $code
    versionName = $name
    apkUrl      = "$base$apkName"
    notes       = $Notes
    sha256      = $sha
} | ConvertTo-Json
[System.IO.File]::WriteAllText((Join-Path $dir "version.json"), $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "version.json written: $dir\version.json"
Write-Host "versionCode=$code versionName=$name"
Write-Host "apkUrl=$base$apkName"
Write-Host "sha256=$sha"
Write-Host "Upload $apkName and version.json to the folder behind $feed"
