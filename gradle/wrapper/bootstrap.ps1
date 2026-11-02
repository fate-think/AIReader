$ErrorActionPreference = 'Stop'
$version = '8.11.1'
$base = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$folder = Join-Path $base "wrapper\dists\gradle-$version"
$gradle = Join-Path $folder "gradle-$version\bin\gradle.bat"
if (-not (Test-Path $gradle)) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    $archive = Join-Path $folder "gradle-$version-bin.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $archive
    Expand-Archive -Path $archive -DestinationPath $folder -Force
    Remove-Item $archive
}
& $gradle @args
exit $LASTEXITCODE
