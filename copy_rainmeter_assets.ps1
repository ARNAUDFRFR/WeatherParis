# Helper script to copy weather icons from local Rainmeter skin to Android project drawables
$rainmeterPath = "C:\Program Files\Rainmeter\Rainmeter\Skins\WeatherParis\@Resources\WeatherIcons"
$destinationPath = Join-Path $PSScriptRoot "app\src\main\res\drawable"

if (-not (Test-Path $rainmeterPath)) {
    Write-Error "Rainmeter skins path not found at: $rainmeterPath"
    Exit
}

if (-not (Test-Path $destinationPath)) {
    Write-Host "Creating destination directory: $destinationPath"
    New-Item -ItemType Directory -Force -Path $destinationPath
}

Write-Host "Copying picto_*.png files from $rainmeterPath to $destinationPath..."
$files = Get-ChildItem -Path $rainmeterPath -Filter "picto_*.png"

if ($files.Count -eq 0) {
    Write-Warning "No picto_*.png files found in Rainmeter folder!"
} else {
    foreach ($file in $files) {
        $destFile = Join-Path $destinationPath $file.Name
        Copy-Item -Path $file.FullName -Destination $destFile -Force
        Write-Host "  Copied $($file.Name)"
    }
    Write-Host "Successfully copied $($files.Count) weather icon assets!"
}
