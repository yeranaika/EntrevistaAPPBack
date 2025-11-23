# Test rapido de API
$baseUrl = "http://localhost:8080"
$email = "test-$(Get-Random)@test.com"
$password = "Password123!"

Write-Host "Probando endpoints..." -ForegroundColor Cyan

# Health Check
Write-Host "1. Health Check..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
Write-Host "OK: $health" -ForegroundColor Green

# Register
Write-Host "2. Register..." -ForegroundColor Yellow
$registerBody = @{
    email = $email
    password = $password
    nombre = "Test User"
    idioma = "es"
} | ConvertTo-Json

$registerResponse = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $registerBody -ContentType "application/json"
$accessToken = $registerResponse.accessToken
Write-Host "OK: Usuario registrado" -ForegroundColor Green

# Onboarding
Write-Host "3. Onboarding..." -ForegroundColor Yellow
$onboardingBody = '{"area":"Desarrollo","nivelExperiencia":"Junior","nombreCargo":"Desarrollador Full Stack","descripcionObjetivo":"Quiero mejorar mis habilidades"}'
$cargo = "Desarrollador Full Stack"

$headers = @{
    "Authorization" = "Bearer $accessToken"
    "Content-Type" = "application/json"
}

Invoke-RestMethod -Uri "$baseUrl/onboarding" -Method Post -Body $onboardingBody -Headers $headers | Out-Null
Write-Host "OK: Onboarding completado" -ForegroundColor Green

# Iniciar Test
Write-Host "4. Iniciar Test por Cargo..." -ForegroundColor Yellow
$testResponse = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/iniciar?cargo=$cargo&cantidad=10" -Method Get -Headers $headers
Write-Host "OK: $($testResponse.preguntas.Count) preguntas" -ForegroundColor Green

# Evaluar Test
Write-Host "5. Evaluar Test..." -ForegroundColor Yellow
$respuestas = @()
foreach ($pregunta in $testResponse.preguntas) {
    $respuestas += @{
        preguntaId = $pregunta.id
        respuestaSeleccionada = Get-Random -Minimum 0 -Maximum 4
    }
}

$evaluarBody = @{
    habilidad = $cargo
    respuestas = $respuestas
} | ConvertTo-Json -Depth 10

$evaluarResponse = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/evaluar" -Method Post -Body $evaluarBody -Headers $headers
Write-Host "OK: Puntaje $($evaluarResponse.puntaje)% - Nivel $($evaluarResponse.nivelSugerido)" -ForegroundColor Green

# Generar Plan
Write-Host "6. Generar Plan..." -ForegroundColor Yellow
$planBody = @{
    testNivelacionId = $evaluarResponse.testId
} | ConvertTo-Json

$planResponse = Invoke-RestMethod -Uri "$baseUrl/plan-practica/generar-desde-test" -Method Post -Body $planBody -Headers $headers
Write-Host "OK: Plan con $($planResponse.pasos.Count) pasos" -ForegroundColor Green

Write-Host ""
Write-Host "Todos los endpoints funcionan!" -ForegroundColor Green
Write-Host "Email: $email" -ForegroundColor Cyan
Write-Host "Password: $password" -ForegroundColor Cyan
