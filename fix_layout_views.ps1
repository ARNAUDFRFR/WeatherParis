$path = "C:\Users\agrim\.gemini\antigravity\scratch\WeatherParisAndroid\app\src\main\res\layout\widget_layout.xml"
Write-Host "Reading $path..."
$content = [System.IO.File]::ReadAllText($path)

# Replace all occurrences of '<View' with '<ImageView' (handling cases where there might be spaces/newlines)
$fixedContent = $content -replace '<View\b', '<ImageView'
$fixedContent = $fixedContent -replace '</View>', '</ImageView>'

[System.IO.File]::WriteAllText($path, $fixedContent, [System.Text.Encoding]::UTF8)
Write-Host "Replaced all <View> tags with <ImageView> tags successfully!"
