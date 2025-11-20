# Test simple del Plan de Practica usando usuario existente
$baseUrl = "http://localhost:8080"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   TEST PLAN DE PRACTICA" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Login con usuario existente
Write-Host "[1/4] Haciendo login..." -ForegroundColor Yellow
$loginBody = @{
    email    = "nico1@correo.com"
    password = "nico1234"
} | ConvertTo-Json

$login = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $login.token
Write-Host "Login exitoso" -ForegroundColor Green
Write-Host ""

# Headers
$headers = @{
    "Authorization" = "Bearer $token"
}

# GET /me
Write-Host "[2/4] GET /me..." -ForegroundColor Yellow
$me = Invoke-RestMethod -Uri "$baseUrl/me" -Method Get -Headers $headers
Write-Host "Usuario: $($me.email)" -ForegroundColor White
Write-Host "Nombre: $($me.nombre)" -ForegroundColor White
if ($me.perfil) {
    Write-Host "Area: $($me.perfil.area)" -ForegroundColor White
    Write-Host "Nivel: $($me.perfil.nivelExperiencia)" -ForegroundColor White
}
Write-Host "Meta: $(if ($me.meta) { $me.meta } else { '(no definida)' })" -ForegroundColor White
Write-Host ""

# GET /plan-practica (primera vez)
Write-Host "[3/4] GET /plan-practica (generando plan)..." -ForegroundColor Yellow
$plan1 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method Get -Headers $headers

Write-Host "Plan generado:" -ForegroundColor Green
Write-Host "  ID: $($plan1.id)" -ForegroundColor White
Write-Host "  Area: $($plan1.area)" -ForegroundColor White
Write-Host "  Meta: $($plan1.metaCargo)" -ForegroundColor White
Write-Host "  Nivel: $($plan1.nivel)" -ForegroundColor White
Write-Host "  Pasos: $($plan1.pasos.Count)" -ForegroundColor White
Write-Host ""

if ($plan1.pasos -and $plan1.pasos.Count -gt 0) {
    Write-Host "  Detalles de los pasos:" -ForegroundColor Cyan
    foreach ($paso in $plan1.pasos) {
        Write-Host "    $($paso.orden). $($paso.titulo)" -ForegroundColor White
        Write-Host "       $($paso.descripcion)" -ForegroundColor Gray
        Write-Host "       Sesiones/semana: $($paso.sesionesPorSemana)" -ForegroundColor Gray
    }
    Write-Host ""
}

# GET /plan-practica (segunda vez)
Write-Host "[4/4] GET /plan-practica (recuperando plan existente)..." -ForegroundColor Yellow
$plan2 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method Get -Headers $headers

if ($plan1.id -eq $plan2.id) {
    Write-Host "EXITO: Mismo plan recuperado" -ForegroundColor Green
    Write-Host "  ID: $($plan2.id)" -ForegroundColor White
    Write-Host "  Pasos: $($plan2.pasos.Count)" -ForegroundColor White
} else {
    Write-Host "ERROR: Plan diferente generado" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   PRUEBAS COMPLETADAS" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
