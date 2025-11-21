# Test solo el endpoint de plan-practica con detalles del error
$loginBody = @{
    email    = "nico1@correo.com"
    password = "nico1234"
} | ConvertTo-Json

$login = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$headers = @{ "Authorization" = "Bearer $($login.accessToken)" }

Write-Host "Probando GET /plan-practica..." -ForegroundColor Yellow

try {
    $plan = Invoke-RestMethod -Uri "http://localhost:8080/plan-practica" -Method Get -Headers $headers -Verbose
    Write-Host "Exito!" -ForegroundColor Green
    $plan | ConvertTo-Json -Depth 10
}
catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "`nResponse body:" -ForegroundColor Yellow
        Write-Host $responseBody
    }
    
    Write-Host "`nException details:" -ForegroundColor Yellow
    $_.Exception | Format-List *
}
