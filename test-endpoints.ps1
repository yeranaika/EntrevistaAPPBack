# Script de prueba rápida de endpoints
# Ejecutar: .\test-endpoints.ps1

$baseUrl = "http://localhost:8080"
$email = "test-$(Get-Random)@test.com"
$password = "Password123!"

Write-Host "🧪 Probando endpoints de EntrevistaAPP..." -ForegroundColor Cyan
Write-Host ""

# 1. Health Check
Write-Host "1️⃣  Health Check..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
    Write-Host "   ✅ Servidor funcionando: $health" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Error: Servidor no responde" -ForegroundColor Red
    Write-Host "   💡 Ejecuta: .\gradlew run" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 2. Register
Write-Host "2️⃣  Register..." -ForegroundColor Yellow
$registerBody = @{
    email = $email
    password = $password
    nombre = "Test User"
    idioma = "es"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "$baseUrl/register" -Method Post -Body $registerBody -ContentType "application/json"
    $accessToken = $registerResponse.accessToken
    Write-Host "   ✅ Usuario registrado: $email" -ForegroundColor Green
    Write-Host "   🔑 Token obtenido" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Error en register: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 3. Onboarding
Write-Host "3️⃣  Onboarding..." -ForegroundColor Yellow
$onboardingBody = @{
    area = "Desarrollo"
    nivelExperiencia = "Junior"
    nombreCargo = "Desarrollador Full Stack"
    descripcionObjetivo = "Quiero trabajar en una startup"
} | ConvertTo-Json

try {
    $headers = @{
        "Authorization" = "Bearer $accessToken"
        "Content-Type" = "application/json"
    }
    $onboardingResponse = Invoke-RestMethod -Uri "$baseUrl/onboarding" -Method Post -Body $onboardingBody -Headers $headers
    Write-Host "   ✅ Onboarding completado" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Error en onboarding: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 4. Iniciar Test
Write-Host "4️⃣  Iniciar Test de Nivelación..." -ForegroundColor Yellow
try {
    $testResponse = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10" -Method Get -Headers $headers
    $preguntas = $testResponse.preguntas
    Write-Host "   ✅ Test iniciado: $($preguntas.Count) preguntas obtenidas" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Error al iniciar test: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 5. Evaluar Test
Write-Host "5️⃣  Evaluar Test..." -ForegroundColor Yellow
$respuestas = @()
foreach ($pregunta in $preguntas) {
    $respuestas += @{
        preguntaId = $pregunta.id
        respuestaSeleccionada = Get-Random -Minimum 0 -Maximum 4
    }
}

$evaluarBody = @{
    habilidad = "Desarrollo"
    respuestas = $respuestas
} | ConvertTo-Json -Depth 10

try {
    $evaluarResponse = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/evaluar" -Method Post -Body $evaluarBody -Headers $headers
    $testId = $evaluarResponse.testId
    Write-Host "   ✅ Test evaluado" -ForegroundColor Green
    Write-Host "   📊 Puntaje: $($evaluarResponse.puntaje)%" -ForegroundColor Cyan
    Write-Host "   🎯 Nivel: $($evaluarResponse.nivelSugerido)" -ForegroundColor Cyan
} catch {
    Write-Host "   ❌ Error al evaluar test: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 6. Generar Plan
Write-Host "6️⃣  Generar Plan de Práctica..." -ForegroundColor Yellow
$planBody = @{
    testNivelacionId = $testId
} | ConvertTo-Json

try {
    $planResponse = Invoke-RestMethod -Uri "$baseUrl/plan-practica/generar-desde-test" -Method Post -Body $planBody -Headers $headers
    Write-Host "   ✅ Plan generado" -ForegroundColor Green
    Write-Host "   📚 Pasos: $($planResponse.pasos.Count)" -ForegroundColor Cyan
    Write-Host "   🎓 Nivel: $($planResponse.nivel)" -ForegroundColor Cyan
} catch {
    Write-Host "   ❌ Error al generar plan: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 7. Ver Plan
Write-Host "7️⃣  Ver Plan Actual..." -ForegroundColor Yellow
try {
    $verPlanResponse = Invoke-RestMethod -Uri "$baseUrl/plan-practica" -Method Get -Headers $headers
    Write-Host "   ✅ Plan obtenido" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Error al ver plan: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 8. Ver Perfil
Write-Host "8️⃣  Ver Mi Perfil..." -ForegroundColor Yellow
try {
    $perfilResponse = Invoke-RestMethod -Uri "$baseUrl/me" -Method Get -Headers $headers
    Write-Host "   ✅ Perfil obtenido" -ForegroundColor Green
    Write-Host "   👤 Usuario: $($perfilResponse.nombre)" -ForegroundColor Cyan
} catch {
    Write-Host "   ❌ Error al ver perfil: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🎉 ¡Todos los endpoints funcionan correctamente!" -ForegroundColor Green
Write-Host ""
Write-Host "📝 Datos de prueba:" -ForegroundColor Cyan
Write-Host "   Email: $email" -ForegroundColor White
Write-Host "   Password: $password" -ForegroundColor White
Write-Host "   Test ID: $testId" -ForegroundColor White
Write-Host ""
Write-Host "💡 Ahora puedes usar estos datos en Postman" -ForegroundColor Yellow
