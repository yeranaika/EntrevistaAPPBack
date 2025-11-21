# Debug test - ver el token y la respuesta completa
$loginBody = @{
    email    = "nico1@correo.com"
    password = "nico1234"
} | ConvertTo-Json

Write-Host "Haciendo login..." -ForegroundColor Yellow
$login = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -ContentType "application/json" -Body $loginBody

Write-Host "Login response:" -ForegroundColor Cyan
$login | ConvertTo-Json -Depth 5

Write-Host "`nToken recibido:" -ForegroundColor Cyan
Write-Host $login.token

Write-Host "`nIntentando GET /me..." -ForegroundColor Yellow
$headers = @{ 
    "Authorization" = "Bearer $($login.token)"
}

try {
    $me = Invoke-RestMethod -Uri "http://localhost:8080/me" -Method Get -Headers $headers
    Write-Host "Exito!" -ForegroundColor Green
    $me | ConvertTo-Json -Depth 5
}
catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    
    # Intentar leer el cuerpo de la respuesta
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $responseBody = $reader.ReadToEnd()
    Write-Host "Response body: $responseBody" -ForegroundColor Yellow
}
