# Find java executable
$java = "java"
if (Test-Path "C:\Program Files\Android\Android Studio\jbr\bin\java.exe") {
    $java = "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
}

Write-Host "Starting CacheLab Backend Server on port 8080..." -ForegroundColor Cyan
& $java -cp "d:\acentra\cache-server\target\test-classes" com.cachelab.server.CacheLabApplication
