# Test completo: Objetivo de Carrera + Plan de Practica
$baseUrl = "http://localhost:8080"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   TEST OBJETIVO + PLAN DE PRACTICA" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Login con usuario existente
Write-Host "[1/6] Login..." -ForegroundColor Yellow
$loginBody = @{
    email    = "nico1@correo.com"
    password = "nico1234"
} | ConvertTo-Json

$login = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $login.token
Write-Host "Login exitoso" -ForegroundColor Green
Write-Host ""

$headers = @{
    "Authorization" = "Bearer $token"
}

# GET /me (antes de crear objetivo)
Write-Host "[2/6] GET /me (antes de crear objetivo)..." -ForegroundColor Yellow
$me1 = Invoke-RestMethod -Uri "$baseUrl/me" -Method Get -Headers $headers
Write-Host "Email: $($me1.email)" -ForegroundColor White
Write-Host "Meta actual: $(if ($me1.meta) { $me1.meta } else { '(sin meta)' })" -ForegroundColor White
Write-Host ""

# PUT /me/objetivo (crear objetivo de carrera)
Write-Host "[3/6] PUT /me/objetivo (crear objetivo)..." -ForegroundColor Yellow
$objetivoBody = @{
    nombreCargo = "Senior Backend Developer"
    sector      = "TI"
} | ConvertTo-Json

$objetivo = Invoke-RestMethod -Uri "$baseUrl/me/objetivo" -Method Put -Headers $headers -ContentType "application/json" -Body $objetivoBody
Write-Host "Objetivo creado:" -ForegroundColor Green
Write-Host "  ID: $($objetivo.id)" -ForegroundColor White
Write-Host "  Cargo: $($objetivo.nombreCargo)" -ForegroundColor White
Write-Host "  Sector: $($objetivo.sector)" -ForegroundColor White
Write-Host ""

# GET /me (despues de crear objetivo)
Write-Host "[4/6] GET /me (despues de crear objetivo)..." -ForegroundColor Yellow
$me2 = Invoke-RestMethod -Uri "$baseUrl/me" -Method Get -Headers $headers
Write-Host "Email: $($me2.email)" -ForegroundColor White
Write-Host "Meta actual: $($me2.meta)" -ForegroundColor White
Write-Host ""

# GET /me/objetivo (obtener objetivo)
Write-Host "[5/6] GET /me/objetivo..." -ForegroundColor Yellow
$objetivoGet = Invoke-RestMethod -Uri "$baseUrl/me/objetivo" -Method Get -Headers $headers
Write-Host "Objetivo recuperado:" -ForegroundColor Green
Write-Host "  ID: $($objetivoGet.id)" -ForegroundColor White
Write-Host "  Cargo: $($objetivoGet.nombreCargo)" -ForegroundColor White
Write-Host "  Sector: $($objetivoGet.sector)" -ForegroundColor White
Write-Host ""

# GET /plan-practica (debe usar el objetivo)
Write-Host "[6/6] GET /plan-practica..." -ForegroundColor Yellow
$plan = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method Get -Headers $headers

Write-Host "Plan generado:" -ForegroundColor Green
Write-Host "  ID: $($plan.id)" -ForegroundColor White
Write-Host "  Area: $($plan.area)" -ForegroundColor White
Write-Host "  Meta Cargo: $($plan.metaCargo)" -ForegroundColor White
Write-Host "  Nivel: $($plan.nivel)" -ForegroundColor White
Write-Host "  Pasos: $($plan.pasos.Count)" -ForegroundColor White
Write-Host ""

if ($plan.pasos -and $plan.pasos.Count -gt 0) {
    Write-Host "  Detalles de los pasos:" -ForegroundColor Cyan
    foreach ($paso in $plan.pasos) {
        Write-Host "    $($paso.orden). $($paso.titulo)" -ForegroundColor White
        Write-Host "       $($paso.descripcion)" -ForegroundColor Gray
        Write-Host "       Sesiones/semana: $($paso.sesionesPorSemana)" -ForegroundColor Gray
    }
} else {
    Write-Host "  ADVERTENCIA: No se generaron pasos" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   PRUEBAS COMPLETADAS EXITOSAMENTE" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
