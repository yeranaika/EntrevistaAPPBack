# Script de prueba usando curl
$baseUrl = "http://localhost:8080"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   PRUEBA PLAN PRACTICA - CURL" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Paso 1: Registrar usuario
Write-Host "[1/5] Registrando usuario..." -ForegroundColor Yellow
$randomNum = Get-Random -Minimum 1000 -Maximum 9999
$registerJson = @"
{
  "email": "test-plan-$randomNum@example.com",
  "password": "Test123456!",
  "nombre": "Test User",
  "idioma": "es"
}
"@

$registerResult = curl -s -X POST "$baseUrl/auth/register" -H "Content-Type: application/json" -d $registerJson | ConvertFrom-Json
$token = $registerResult.token
Write-Host "Token obtenido: $($token.Substring(0, 30))...`n" -ForegroundColor Green

# Paso 2: Crear perfil
Write-Host "[2/5] Creando perfil..." -ForegroundColor Yellow
$perfilJson = @"
{
  "nivelExperiencia": "Tengo experiencia intermedia",
  "area": "TI",
  "pais": "AR",
  "notaObjetivos": "Quiero mejorar"
}
"@

curl -s -X PUT "$baseUrl/me/perfil" `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d $perfilJson | Out-Null
Write-Host "Perfil creado`n" -ForegroundColor Green

# Paso 3: GET /me
Write-Host "[3/5] Probando GET /me..." -ForegroundColor Yellow
$meResult = curl -s -X GET "$baseUrl/me" `
  -H "Authorization: Bearer $token" | ConvertFrom-Json

Write-Host "Usuario:" -ForegroundColor Cyan
Write-Host "  Email: $($meResult.email)" -ForegroundColor Gray
Write-Host "  Nombre: $($meResult.nombre)" -ForegroundColor Gray
Write-Host "  Perfil Area: $($meResult.perfil.area)" -ForegroundColor Gray
Write-Host "  Perfil Nivel: $($meResult.perfil.nivelExperiencia)" -ForegroundColor Gray
Write-Host "  Meta: $($meResult.meta)`n" -ForegroundColor Gray

# Paso 4: GET /plan-practica (primera vez - generar)
Write-Host "[4/5] Generando plan de practica..." -ForegroundColor Yellow
$planResult = curl -s -X GET "$baseUrl/plan-practica" `
  -H "Authorization: Bearer $token" | ConvertFrom-Json

Write-Host "Plan generado:" -ForegroundColor Cyan
Write-Host "  ID: $($planResult.id)" -ForegroundColor Gray
Write-Host "  Area: $($planResult.area)" -ForegroundColor Gray
Write-Host "  Meta: $($planResult.metaCargo)" -ForegroundColor Gray
Write-Host "  Nivel: $($planResult.nivel)" -ForegroundColor Gray
Write-Host "  Pasos: $($planResult.pasos.Count)`n" -ForegroundColor Gray

Write-Host "  Detalles de los pasos:" -ForegroundColor Cyan
foreach ($paso in $planResult.pasos) {
    Write-Host "    $($paso.orden). $($paso.titulo)" -ForegroundColor White
    Write-Host "       $($paso.descripcion)" -ForegroundColor Gray
    Write-Host "       Sesiones/semana: $($paso.sesionesPorSemana)`n" -ForegroundColor Gray
}

$planId1 = $planResult.id

# Paso 5: GET /plan-practica (segunda vez - recuperar el mismo)
Write-Host "[5/5] Recuperando plan existente..." -ForegroundColor Yellow
$planResult2 = curl -s -X GET "$baseUrl/plan-practica" `
  -H "Authorization: Bearer $token" | ConvertFrom-Json

$planId2 = $planResult2.id

if ($planId1 -eq $planId2) {
    Write-Host "EXITO: Mismo plan recuperado (ID: $planId2)" -ForegroundColor Green
} else {
    Write-Host "ERROR: Plan diferente (ID1: $planId1, ID2: $planId2)" -ForegroundColor Red
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   PRUEBAS COMPLETADAS" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan
