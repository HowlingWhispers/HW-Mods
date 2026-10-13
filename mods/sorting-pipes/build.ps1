param([string]$ApiJar = "$PSScriptRoot/lib/howl-api-sorting-pipes.jar")
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!(Test-Path $ApiJar)) {
    $apiDir = $env:HW_CODALOADER_API_DIR
    if (!$apiDir) { $apiDir = "$PSScriptRoot/../../../HW-CodaLoader/src/main/java" }
    $apiPackage = "$apiDir/dev/howlingwhispers/codaloader/api"
    if (!(Test-Path $apiPackage)) { throw 'Set HW_CODALOADER_API_DIR to H.O.W.L. 0.0.38 API sources or pass its API JAR' }
    New-Item -ItemType Directory -Force build/api,lib | Out-Null
    $apiSources = @(Get-ChildItem $apiPackage -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    & javac --release 21 -d build/api @apiSources
    if ($LASTEXITCODE -ne 0) { throw 'API compilation failed' }
    & jar --create --file lib/howl-api-sorting-pipes.jar -C build/api .
    if ($LASTEXITCODE -ne 0) { throw 'API packaging failed' }
    $ApiJar = "$PSScriptRoot/lib/howl-api-sorting-pipes.jar"
}
New-Item -ItemType Directory -Force build/classes, dist | Out-Null
Get-ChildItem build/classes -Recurse -File | Remove-Item
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp $ApiJar -d build/classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& python generate-resources.py
if ($LASTEXITCODE -ne 0) { throw 'Resource generation failed' }
Copy-Item resources/* build/classes -Recurse -Force
Copy-Item dist/HOWL-Sorting-Pipes-Resources.zip build/classes/sorting-pipes-resources.zip
& jar --create --file dist/howl-sorting-pipes-1.0.0.jar -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
