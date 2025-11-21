# Script de prueba simple para Plan de Practica
$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   PRUEBA PLAN PRACTICA" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

try {
    # 1. Registrar usuario
    Write-Host "[1/5] Registrando usuario..." -ForegroundColor Yellow
    $randomNum = Get-Random -Minimum 1000 -Maximum 9999
    $emailTest = "test-plan-$randomNum@example.com"

    $registerResponse = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method POST `
        -ContentType "application/json" -Body (@{
            email = $emailTest
            password = "Test123456!"
            nombre = "Test User"
            idioma = "es"
        } | ConvertTo-Json)

    $token = $registerResponse.token
    Write-Host "Usuario creado: $emailTest" -ForegroundColor Green
    Write-Host "Token obtenido`n" -ForegroundColor Green

    # 2. Crear perfil
    Write-Host "[2/5] Creando perfil..." -ForegroundColor Yellow
    Invoke-RestMethod -Uri "$baseUrl/me/perfil" -Method PUT `
        -Headers @{ "Authorization" = "Bearer $token" } `
        -ContentType "application/json" -Body (@{
            nivelExperiencia = "Tengo experiencia intermedia"
            area = "TI"
            pais = "AR"
            notaObjetivos = "Quiero mejorar"
        } | ConvertTo-Json) | Out-Null
    Write-Host "Perfil creado`n" -ForegroundColor Green

    # 3. GET /me
    Write-Host "[3/5] Obteniendo informacion del usuario..." -ForegroundColor Yellow
    $meData = Invoke-RestMethod -Uri "$baseUrl/me" -Method GET `
        -Headers @{ "Authorization" = "Bearer $token" }

    Write-Host "Datos del usuario:" -ForegroundColor Cyan
    Write-Host "  Email: $($meData.email)" -ForegroundColor White
    Write-Host "  Nombre: $($meData.nombre)" -ForegroundColor White
    if ($meData.perfil) {
        Write-Host "  Area: $($meData.perfil.area)" -ForegroundColor White
        Write-Host "  Nivel: $($meData.perfil.nivelExperiencia)" -ForegroundColor White
    }
    Write-Host "  Meta carrera: $(if ($meData.meta) { $meData.meta } else { 'No definida' })`n" -ForegroundColor White

    # 4. GET /plan-practica (primera vez)
    Write-Host "[4/5] Generando plan de practica (primera llamada)..." -ForegroundColor Yellow
    $plan1 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method GET `
        -Headers @{ "Authorization" = "Bearer $token" }

    Write-Host "Plan generado exitosamente:" -ForegroundColor Green
    Write-Host "  ID del plan: $($plan1.id)" -ForegroundColor White
    Write-Host "  Area: $($plan1.area)" -ForegroundColor White
    Write-Host "  Meta cargo: $($plan1.metaCargo)" -ForegroundColor White
    Write-Host "  Nivel: $($plan1.nivel)" -ForegroundColor White
    Write-Host "  Total de pasos: $($plan1.pasos.Count)`n" -ForegroundColor White

    if ($plan1.pasos -and $plan1.pasos.Count -gt 0) {
        Write-Host "  Pasos del plan:" -ForegroundColor Cyan
        foreach ($paso in $plan1.pasos) {
            Write-Host "    $($paso.orden). $($paso.titulo)" -ForegroundColor White
            Write-Host "       $($paso.descripcion)" -ForegroundColor Gray
            Write-Host "       Sesiones por semana: $($paso.sesionesPorSemana)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ADVERTENCIA: No se generaron pasos en el plan" -ForegroundColor Yellow
    }

    $planId1 = $plan1.id

    # 5. GET /plan-practica (segunda vez)
    Write-Host "`n[5/5] Recuperando plan existente (segunda llamada)..." -ForegroundColor Yellow
    $plan2 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method GET `
        -Headers @{ "Authorization" = "Bearer $token" }

    $planId2 = $plan2.id

    if ($planId1 -eq $planId2) {
        Write-Host "EXITO: Se recupero el mismo plan (ID: $planId2)" -ForegroundColor Green
        Write-Host "  Pasos recuperados: $($plan2.pasos.Count)" -ForegroundColor White
    } else {
        Write-Host "ERROR: Se genero un plan diferente" -ForegroundColor Red
        Write-Host "  ID original: $planId1" -ForegroundColor Red
        Write-Host "  ID nuevo: $planId2" -ForegroundColor Red
    }

    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "   TODAS LAS PRUEBAS COMPLETADAS" -ForegroundColor Green
    Write-Host "========================================`n" -ForegroundColor Cyan

} catch {
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Detalles: $($_.ErrorDetails.Message)" -ForegroundColor Red
    exit 1
}
