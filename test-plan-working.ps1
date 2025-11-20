# Test completo del Plan de Practica
$baseUrl = "http://localhost:8080"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   TEST PLAN DE PRACTICA" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Generar credenciales aleatorias
$randomNum = Get-Random -Minimum 1000 -Maximum 9999
$testEmail = "testplan$randomNum@correo.com"
$testPassword = "testplan$randomNum"

# PASO 1: REGISTRAR USUARIO
Write-Host "[1/5] Registrando nuevo usuario..." -ForegroundColor Yellow
$registerBody = @{
    email    = $testEmail
    password = $testPassword
    nombre   = "Test Plan $randomNum"
    idioma   = "es"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $registerBody
    Write-Host "Usuario registrado: $testEmail" -ForegroundColor Green
} catch {
    Write-Host "Error al registrar: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# PASO 2: LOGIN
Write-Host "`n[2/5] Haciendo login..." -ForegroundColor Yellow
$loginBody = @{
    email    = $testEmail
    password = $testPassword
} | ConvertTo-Json

try {
    $login = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body $loginBody

    if (-not $login.token) {
        # Usar el token del registro
        $token = $registerResponse.token
        Write-Host "Usando token del registro" -ForegroundColor Yellow
    } else {
        $token = $login.token
        Write-Host "Login exitoso" -ForegroundColor Green
    }

    Write-Host "Token: $($token.Substring(0, 30))..." -ForegroundColor Gray
} catch {
    Write-Host "Error en login: $($_.Exception.Message)" -ForegroundColor Red
    # Intentar usar token del registro
    $token = $registerResponse.token
    Write-Host "Usando token del registro como fallback" -ForegroundColor Yellow
}

# PASO 3: CREAR PERFIL
Write-Host "`n[3/5] Creando perfil..." -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $token"
}

$perfilBody = @{
    nivelExperiencia = "Tengo experiencia intermedia"
    area             = "TI"
    pais             = "AR"
    notaObjetivos    = "Quiero mejorar mis habilidades"
} | ConvertTo-Json

try {
    $perfilResponse = Invoke-RestMethod -Uri "$baseUrl/me/perfil" -Method Put -Headers $headers -ContentType "application/json" -Body $perfilBody
    Write-Host "Perfil creado exitosamente" -ForegroundColor Green
} catch {
    Write-Host "Error al crear perfil: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# PASO 4: VERIFICAR GET /me
Write-Host "`n[4/5] Verificando GET /me..." -ForegroundColor Yellow
try {
    $me = Invoke-RestMethod -Uri "$baseUrl/me" -Method Get -Headers $headers
    Write-Host "Datos del usuario:" -ForegroundColor Cyan
    Write-Host "  Email: $($me.email)" -ForegroundColor White
    Write-Host "  Nombre: $($me.nombre)" -ForegroundColor White
    if ($me.perfil) {
        Write-Host "  Area: $($me.perfil.area)" -ForegroundColor White
        Write-Host "  Nivel: $($me.perfil.nivelExperiencia)" -ForegroundColor White
    }
    Write-Host "  Meta: $(if ($me.meta) { $me.meta } else { '(sin meta definida)' })" -ForegroundColor White
} catch {
    Write-Host "Error en GET /me: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# PASO 5: GENERAR PLAN DE PRACTICA (primera vez)
Write-Host "`n[5/6] Generando plan de practica (primera llamada)..." -ForegroundColor Yellow
try {
    $plan1 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method Get -Headers $headers

    Write-Host "`nPlan generado exitosamente:" -ForegroundColor Green
    Write-Host "  ID: $($plan1.id)" -ForegroundColor White
    Write-Host "  Area: $($plan1.area)" -ForegroundColor White
    Write-Host "  Meta Cargo: $($plan1.metaCargo)" -ForegroundColor White
    Write-Host "  Nivel: $($plan1.nivel)" -ForegroundColor White
    Write-Host "  Cantidad de pasos: $($plan1.pasos.Count)" -ForegroundColor White

    if ($plan1.pasos -and $plan1.pasos.Count -gt 0) {
        Write-Host "`n  Pasos del plan:" -ForegroundColor Cyan
        foreach ($paso in $plan1.pasos) {
            Write-Host "    Paso $($paso.orden): $($paso.titulo)" -ForegroundColor White
            Write-Host "      Descripcion: $($paso.descripcion)" -ForegroundColor Gray
            Write-Host "      Sesiones/semana: $($paso.sesionesPorSemana)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ADVERTENCIA: No se generaron pasos" -ForegroundColor Yellow
    }

    $planId1 = $plan1.id
} catch {
    Write-Host "Error al generar plan: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# PASO 6: RECUPERAR PLAN (segunda vez - debe ser el mismo)
Write-Host "`n[6/6] Recuperando plan existente (segunda llamada)..." -ForegroundColor Yellow
try {
    $plan2 = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method Get -Headers $headers

    if ($plan1.id -eq $plan2.id) {
        Write-Host "EXITO: Se recupero el mismo plan" -ForegroundColor Green
        Write-Host "  Plan ID: $($plan2.id)" -ForegroundColor White
        Write-Host "  Pasos: $($plan2.pasos.Count)" -ForegroundColor White
    } else {
        Write-Host "ERROR: Se genero un plan diferente!" -ForegroundColor Red
        Write-Host "  ID plan 1: $planId1" -ForegroundColor Red
        Write-Host "  ID plan 2: $($plan2.id)" -ForegroundColor Red
    }
} catch {
    Write-Host "Error al recuperar plan: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   TODAS LAS PRUEBAS COMPLETADAS" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "" -ForegroundColor Cyan
