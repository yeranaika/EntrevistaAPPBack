# Test simple sin autenticacion para verificar que el endpoint existe
Write-Host "Probando GET /plan-practica sin autenticacion..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/plan-practica" -Method Get -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Body: $($response.Content)" -ForegroundColor Cyan
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    
    if ($_.Exception.Response.StatusCode.value__ -eq 401) {
        Write-Host "401 = Endpoint existe pero requiere autenticacion (CORRECTO)" -ForegroundColor Green
    } elseif ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "404 = Endpoint NO existe (PROBLEMA DE ROUTING)" -ForegroundColor Red
    } else {
        Write-Host "Otro error inesperado" -ForegroundColor Yellow
    }
}
