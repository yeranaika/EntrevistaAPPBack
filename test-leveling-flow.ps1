# Script para probar el flujo completo de Test de Nivelación
$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

# 1. Login
Write-Host "1. Iniciando sesión..." -ForegroundColor Cyan
$email = "tester@example.com"
$password = "Password123!"

try {
    $loginBody = @{ correo = $email; contrasena = $password } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.accessToken
    Write-Host "   Login exitoso." -ForegroundColor Green
} catch {
    Write-Error "Error de login. Asegúrate de haber ejecutado seed-questions.ps1 primero."
}

$headers = @{ "Authorization" = "Bearer $token" }

# 2. Obtener Test
Write-Host "`n2. Solicitando test de Lógica..." -ForegroundColor Cyan
try {
    $test = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion?habilidad=logica&cantidad=3" -Headers $headers
    Write-Host "   Test recibido con $($test.preguntas.Count) preguntas." -ForegroundColor Green
    Write-Host "   Habilidad: $($test.habilidad)"
} catch {
    Write-Error "Error obteniendo test: $($_.Exception.Message)"
}

# 3. Responder Test
Write-Host "`n3. Enviando respuestas..." -ForegroundColor Cyan

$respuestas = @()
foreach ($p in $test.preguntas) {
    # Respondemos siempre la opción 2 (índice 2) o la 0 si no existe, solo para probar
    $opcion = 2
    if ($p.opciones.Count -le 2) { $opcion = 0 }
    
    $respuestas += @{
        preguntaId = $p.id
        respuestaSeleccionada = $opcion
    }
}

$responderBody = @{
    habilidad = "logica"
    respuestas = $respuestas
} | ConvertTo-Json

try {
    $resultado = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/responder" -Method Post -Headers $headers -Body $responderBody -ContentType "application/json"
    
    Write-Host "   Resultados recibidos:" -ForegroundColor Green
    Write-Host "   ---------------------"
    Write-Host "   Puntaje: $($resultado.puntaje)%"
    Write-Host "   Nivel: $($resultado.nivelSugerido)"
    Write-Host "   Correctas: $($resultado.preguntasCorrectas)/$($resultado.totalPreguntas)"
    Write-Host "   Feedback: $($resultado.feedback)"
} catch {
    Write-Error "Error enviando respuestas: $($_.Exception.Message)"
}

# 4. Ver Historial
Write-Host "`n4. Consultando historial..." -ForegroundColor Cyan
try {
    $historial = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/historial" -Headers $headers
    Write-Host "   Historial recuperado ($($historial.Count) tests):" -ForegroundColor Green
    $historial | Format-Table -Property habilidad, puntaje, nivelSugerido, fechaCompletado
} catch {
    Write-Error "Error consultando historial: $($_.Exception.Message)"
}
