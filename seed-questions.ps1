# Script para poblar la base de datos con preguntas de prueba
$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

# 1. Registrar/Login usuario de prueba
$email = "tester@example.com"
$password = "Password123!"

Write-Host "1. Autenticando usuario ($email)..." -ForegroundColor Cyan

try {
    # Intentar login primero
    $loginBody = @{ correo = $email; contrasena = $password } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.accessToken
    Write-Host "   Login exitoso." -ForegroundColor Green
} catch {
    # Si falla, intentar registro
    Write-Host "   Login falló, intentando registro..." -ForegroundColor Yellow
    try {
        $registerBody = @{ correo = $email; contrasena = $password; nombre = "Tester" } | ConvertTo-Json
        $registerRes = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $registerBody -ContentType "application/json"
        $token = $registerRes.accessToken
        Write-Host "   Registro exitoso." -ForegroundColor Green
    } catch {
        Write-Error "No se pudo autenticar: $($_.Exception.Message)"
    }
}

$headers = @{ "Authorization" = "Bearer $token" }

# 2. Definir preguntas de prueba
$preguntas = @(
    @{
        habilidad = "logica"
        dificultad = 1
        enunciado = "¿Cuál es el siguiente número en la serie: 2, 4, 8, 16...?"
        opciones = @("20", "24", "32", "64")
        respuestaCorrecta = 2
        explicacion = "Cada número se multiplica por 2."
        activa = $true
    },
    @{
        habilidad = "logica"
        dificultad = 2
        enunciado = "Si A es padre de B, y B es padre de C, ¿qué es A de C?"
        opciones = @("Padre", "Tío", "Abuelo", "Bisabuelo")
        respuestaCorrecta = 2
        explicacion = "El padre del padre es el abuelo."
        activa = $true
    },
    @{
        habilidad = "logica"
        dificultad = 1
        enunciado = "¿Qué pesa más: un kilo de hierro o un kilo de plumas?"
        opciones = @("Hierro", "Plumas", "Pesan lo mismo", "Depende de la gravedad")
        respuestaCorrecta = 2
        explicacion = "Ambos pesan exactamente un kilo."
        activa = $true
    },
    @{
        habilidad = "algoritmos"
        dificultad = 1
        enunciado = "¿Cuál es la complejidad temporal de acceder a un elemento en un array por índice?"
        opciones = @("O(1)", "O(n)", "O(log n)", "O(n^2)")
        respuestaCorrecta = 0
        explicacion = "El acceso por índice es directo."
        activa = $true
    },
    @{
        habilidad = "algoritmos"
        dificultad = 2
        enunciado = "¿Qué estructura de datos utiliza el principio LIFO?"
        opciones = @("Cola (Queue)", "Pila (Stack)", "Lista enlazada", "Árbol")
        respuestaCorrecta = 1
        explicacion = "Last In, First Out (Último en entrar, primero en salir) es la característica de una Pila."
        activa = $true
    }
)

# 3. Insertar preguntas
Write-Host "`n2. Insertando preguntas de prueba..." -ForegroundColor Cyan

foreach ($p in $preguntas) {
    try {
        $body = $p | ConvertTo-Json -Depth 10
        Invoke-RestMethod -Uri "$baseUrl/admin/preguntas-nivelacion" -Method Post -Headers $headers -Body $body -ContentType "application/json" | Out-Null
        Write-Host "   [OK] Pregunta de $($p.habilidad) creada." -ForegroundColor Gray
    } catch {
        Write-Host "   [ERROR] Falló al crear pregunta: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n✅ Proceso completado. Base de datos poblada." -ForegroundColor Green
