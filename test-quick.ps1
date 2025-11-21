# Test rapido del Plan de Practica
param(
    [string]$Email = "test@example.com",
    [string]$Password = "password123"
)

Write-Host ""
Write-Host "Probando Plan de Practica..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor DarkGray

try {
    # 1. Login
    Write-Host ""
    Write-Host "[1/3] Haciendo login..." -ForegroundColor Yellow
    $loginBody = @{
        email    = $Email
        password = $Password
    } | ConvertTo-Json

    $login = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    
    $token = $login.accessToken
    Write-Host "      OK - Login exitoso" -ForegroundColor Green
    
    # Headers
    $headers = @{ 
        "Authorization" = "Bearer $token"
    }
    
    # 2. Test GET /me
    Write-Host ""
    Write-Host "[2/3] Probando GET /me..." -ForegroundColor Yellow
    $me = Invoke-RestMethod -Uri "http://localhost:8080/me" -Method Get -Headers $headers
    
    Write-Host "      OK - Respuesta recibida" -ForegroundColor Green
    Write-Host "      Email: $($me.email)" -ForegroundColor Gray
    Write-Host "      Nombre: $($me.nombre)" -ForegroundColor Gray
    Write-Host "      Meta: $($me.meta)" -ForegroundColor Cyan
    if ($me.perfil) {
        Write-Host "      Nivel: $($me.perfil.nivelExperiencia)" -ForegroundColor Gray
        Write-Host "      Area: $($me.perfil.area)" -ForegroundColor Gray
    }
    
    # 3. Test GET /plan-practica
    Write-Host ""
    Write-Host "[3/3] Probando GET /plan-practica..." -ForegroundColor Yellow
    $plan = Invoke-RestMethod -Uri "http://localhost:8080/plan-practica" -Method Get -Headers $headers
    
    Write-Host "      OK - Plan generado/recuperado" -ForegroundColor Green
    Write-Host "      Plan ID: $($plan.id)" -ForegroundColor Gray
    Write-Host "      Area: $($plan.area)" -ForegroundColor Gray
    Write-Host "      Meta: $($plan.metaCargo)" -ForegroundColor Cyan
    Write-Host "      Nivel: $($plan.nivel)" -ForegroundColor Gray
    Write-Host "      Pasos: $($plan.pasos.Count)" -ForegroundColor Magenta
    
    if ($plan.pasos -and $plan.pasos.Count -gt 0) {
        Write-Host ""
        Write-Host "      Pasos del plan:" -ForegroundColor Yellow
        foreach ($paso in $plan.pasos) {
            Write-Host "        $($paso.orden). $($paso.titulo)" -ForegroundColor White
            Write-Host "           Sesiones/semana: $($paso.sesionesPorSemana)" -ForegroundColor DarkGray
        }
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor DarkGray
    Write-Host "TODAS LAS PRUEBAS PASARON" -ForegroundColor Green
    Write-Host ""
    
}
catch {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor DarkGray
    Write-Host "ERROR EN LAS PRUEBAS" -ForegroundColor Red
    Write-Host ""
    Write-Host "Mensaje: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "Verifica que:" -ForegroundColor Yellow
    Write-Host "   1. El backend este corriendo (./gradlew run)" -ForegroundColor Gray
    Write-Host "   2. Las credenciales sean correctas" -ForegroundColor Gray
    Write-Host "   3. El usuario tenga perfil y objetivo configurados" -ForegroundColor Gray
    Write-Host ""
}
