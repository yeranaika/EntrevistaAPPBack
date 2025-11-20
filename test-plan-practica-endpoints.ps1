# Script para probar los endpoints del Plan de Práctica
# Requiere tener un usuario registrado y obtener su token JWT

$baseUrl = "http://localhost:8080"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   PRUEBA DE ENDPOINTS - PLAN PRACTICA" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# PASO 1: Crear un usuario de prueba
Write-Host "[1/4] Registrando usuario de prueba..." -ForegroundColor Yellow
$registerBody = @{
    email = "test-plan-$(Get-Random -Minimum 1000 -Maximum 9999)@example.com"
    password = "Test123456!"
    nombre = "Test User Plan"
    idioma = "es"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method POST -Body $registerBody -ContentType "application/json"
    $token = $registerResponse.token
    Write-Host "✓ Usuario registrado exitosamente" -ForegroundColor Green
    Write-Host "  Token: $($token.Substring(0, 20))..." -ForegroundColor Gray
} catch {
    Write-Host "✗ Error al registrar usuario: $_" -ForegroundColor Red
    exit 1
}

# PASO 2: Crear perfil del usuario
Write-Host "`n[2/4] Creando perfil del usuario..." -ForegroundColor Yellow
$perfilBody = @{
    nivelExperiencia = "Tengo experiencia intermedia"
    area = "TI"
    pais = "AR"
    notaObjetivos = "Quiero mejorar mis habilidades técnicas"
} | ConvertTo-Json

try {
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    }

    Invoke-RestMethod -Uri "$baseUrl/me/perfil" -Method PUT -Body $perfilBody -Headers $headers | Out-Null
    Write-Host "✓ Perfil creado exitosamente" -ForegroundColor Green
} catch {
    Write-Host "✗ Error al crear perfil: $_" -ForegroundColor Red
    exit 1
}

# PASO 3: Verificar endpoint GET /me (debe incluir perfil y meta)
Write-Host "`n[3/4] Probando GET /me..." -ForegroundColor Yellow
try {
    $meResponse = Invoke-RestMethod -Uri "$baseUrl/me" -Method GET -Headers $headers

    Write-Host "✓ GET /me exitoso" -ForegroundColor Green
    Write-Host "`nRespuesta:" -ForegroundColor Cyan
    Write-Host "  Email: $($meResponse.email)" -ForegroundColor Gray
    Write-Host "  Nombre: $($meResponse.nombre)" -ForegroundColor Gray
    Write-Host "  Perfil:" -ForegroundColor Gray
    Write-Host "    - Nivel: $($meResponse.perfil.nivelExperiencia)" -ForegroundColor Gray
    Write-Host "    - Area: $($meResponse.perfil.area)" -ForegroundColor Gray
    Write-Host "    - Pais: $($meResponse.perfil.pais)" -ForegroundColor Gray
    Write-Host "  Meta: $($meResponse.meta)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Error en GET /me: $_" -ForegroundColor Red
    exit 1
}

# PASO 4: Probar GET /plan-practica (primera llamada - debe generar el plan)
Write-Host "`n[4/4] Probando GET /plan-practica (primera llamada)..." -ForegroundColor Yellow
try {
    $planResponse = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method GET -Headers $headers

    Write-Host "✓ Plan de práctica generado exitosamente" -ForegroundColor Green
    Write-Host "`nDetalles del Plan:" -ForegroundColor Cyan
    Write-Host "  ID: $($planResponse.id)" -ForegroundColor Gray
    Write-Host "  Area: $($planResponse.area)" -ForegroundColor Gray
    Write-Host "  Meta Cargo: $($planResponse.metaCargo)" -ForegroundColor Gray
    Write-Host "  Nivel: $($planResponse.nivel)" -ForegroundColor Gray
    Write-Host "  Cantidad de Pasos: $($planResponse.pasos.Count)" -ForegroundColor Gray

    Write-Host "`n  Pasos:" -ForegroundColor Cyan
    foreach ($paso in $planResponse.pasos) {
        Write-Host "    $($paso.orden). $($paso.titulo)" -ForegroundColor White
        Write-Host "       Descripción: $($paso.descripcion)" -ForegroundColor Gray
        Write-Host "       Sesiones por semana: $($paso.sesionesPorSemana)" -ForegroundColor Gray
    }

    # Guardar el ID del plan para la siguiente llamada
    $planId = $planResponse.id
} catch {
    Write-Host "✗ Error al generar plan: $_" -ForegroundColor Red
    Write-Host "Detalles: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# PASO 5: Probar GET /plan-practica nuevamente (debe retornar el mismo plan)
Write-Host "`n[5/5] Probando GET /plan-practica (segunda llamada - debe ser el mismo)..." -ForegroundColor Yellow
try {
    $planResponse2 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method GET -Headers $headers

    if ($planResponse2.id -eq $planId) {
        Write-Host "✓ Plan recuperado correctamente (mismo ID)" -ForegroundColor Green
        Write-Host "  Plan ID: $($planResponse2.id)" -ForegroundColor Gray
    } else {
        Write-Host "✗ ERROR: Se generó un plan diferente" -ForegroundColor Red
        Write-Host "  Plan ID original: $planId" -ForegroundColor Red
        Write-Host "  Plan ID nuevo: $($planResponse2.id)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Error al recuperar plan: $_" -ForegroundColor Red
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   TODAS LAS PRUEBAS COMPLETADAS ✓" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan
