# Script para probar los endpoints del Plan de Practica
$baseUrl = "http://localhost:8080"

Write-Host "`n========================================"
Write-Host "   PRUEBA DE ENDPOINTS - PLAN PRACTICA"
Write-Host "========================================`n"

# PASO 1: Crear un usuario de prueba
Write-Host "[1/4] Registrando usuario de prueba..."
$randomEmail = "test-plan-$(Get-Random -Minimum 1000 -Maximum 9999)@example.com"
$registerBody = @{
    email = $randomEmail
    password = "Test123456!"
    nombre = "Test User Plan"
    idioma = "es"
} | ConvertTo-Json

$registerResponse = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method POST -Body $registerBody -ContentType "application/json"
$token = $registerResponse.token
Write-Host "Usuario registrado: $randomEmail"
Write-Host "Token obtenido`n"

# PASO 2: Crear perfil del usuario
Write-Host "[2/4] Creando perfil del usuario..."
$perfilBody = @{
    nivelExperiencia = "Tengo experiencia intermedia"
    area = "TI"
    pais = "AR"
    notaObjetivos = "Quiero mejorar mis habilidades"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

Invoke-RestMethod -Uri "$baseUrl/me/perfil" -Method PUT -Body $perfilBody -Headers $headers | Out-Null
Write-Host "Perfil creado exitosamente`n"

# PASO 3: Verificar endpoint GET /me
Write-Host "[3/4] Probando GET /me..."
$meResponse = Invoke-RestMethod -Uri "$baseUrl/me" -Method GET -Headers $headers

Write-Host "Email: $($meResponse.email)"
Write-Host "Nombre: $($meResponse.nombre)"
Write-Host "Nivel: $($meResponse.perfil.nivelExperiencia)"
Write-Host "Area: $($meResponse.perfil.area)"
Write-Host "Meta: $($meResponse.meta)`n"

# PASO 4: Probar GET /plan-practica (primera llamada)
Write-Host "[4/4] Probando GET /plan-practica (primera llamada)..."
$planResponse = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method GET -Headers $headers

Write-Host "Plan ID: $($planResponse.id)"
Write-Host "Area: $($planResponse.area)"
Write-Host "Meta Cargo: $($planResponse.metaCargo)"
Write-Host "Nivel: $($planResponse.nivel)"
Write-Host "Cantidad de Pasos: $($planResponse.pasos.Count)`n"

Write-Host "Pasos del Plan:"
foreach ($paso in $planResponse.pasos) {
    Write-Host "  $($paso.orden). $($paso.titulo)"
    Write-Host "     Descripcion: $($paso.descripcion)"
    Write-Host "     Sesiones/semana: $($paso.sesionesPorSemana)"
}

$planId = $planResponse.id

# PASO 5: Probar GET /plan-practica nuevamente
Write-Host "`n[5/5] Probando GET /plan-practica (segunda llamada)..."
$planResponse2 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method GET -Headers $headers

if ($planResponse2.id -eq $planId) {
    Write-Host "Plan recuperado correctamente (mismo ID: $($planResponse2.id))"
} else {
    Write-Host "ERROR: Se genero un plan diferente"
}

Write-Host "`n========================================"
Write-Host "   TODAS LAS PRUEBAS COMPLETADAS"
Write-Host "========================================`n"
