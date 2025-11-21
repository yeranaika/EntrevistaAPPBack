# Script de prueba para los endpoints del Plan de Práctica
# Asegúrate de tener un usuario registrado y obtener un token JWT primero

Write-Host "=== Test Plan de Práctica ===" -ForegroundColor Cyan
Write-Host ""

# Paso 1: Login (necesitas un usuario existente)
Write-Host "1. Primero necesitas hacer login y obtener un token JWT" -ForegroundColor Yellow
Write-Host "   Ejemplo con Invoke-RestMethod:" -ForegroundColor Gray
Write-Host '   $loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -ContentType "application/json" -Body ''{"email":"tu@email.com","password":"tupassword"}''' -ForegroundColor Gray
Write-Host ""

# Paso 2: Guardar token
Write-Host "2. Guarda el token:" -ForegroundColor Yellow
Write-Host '   $token = $loginResponse.token' -ForegroundColor Gray
Write-Host ""

# Paso 3: Probar GET /me
Write-Host "3. Probar GET /me (debería incluir campo 'meta'):" -ForegroundColor Yellow
Write-Host '   $headers = @{ "Authorization" = "Bearer $token" }' -ForegroundColor Gray
Write-Host '   $meResponse = Invoke-RestMethod -Uri "http://localhost:8080/me" -Method Get -Headers $headers' -ForegroundColor Gray
Write-Host '   $meResponse | ConvertTo-Json -Depth 5' -ForegroundColor Gray
Write-Host ""

# Paso 4: Probar GET /plan-practica
Write-Host "4. Probar GET /plan-practica (primera vez genera el plan):" -ForegroundColor Yellow
Write-Host '   $planResponse = Invoke-RestMethod -Uri "http://localhost:8080/plan-practica" -Method Get -Headers $headers' -ForegroundColor Gray
Write-Host '   $planResponse | ConvertTo-Json -Depth 5' -ForegroundColor Gray
Write-Host ""

# Paso 5: Verificar plan
Write-Host "5. Verificar que el plan tenga:" -ForegroundColor Yellow
Write-Host "   - id (UUID del plan)" -ForegroundColor Gray
Write-Host "   - area (del perfil del usuario)" -ForegroundColor Gray
Write-Host "   - metaCargo (objetivo de carrera)" -ForegroundColor Gray
Write-Host "   - nivel (del perfil del usuario)" -ForegroundColor Gray
Write-Host "   - pasos (array con los pasos del plan)" -ForegroundColor Gray
Write-Host ""

Write-Host "=== Ejemplo Completo ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "# Reemplaza con tus credenciales reales:" -ForegroundColor Green
Write-Host '$loginBody = @{' -ForegroundColor White
Write-Host '    email = "tu@email.com"' -ForegroundColor White
Write-Host '    password = "tupassword"' -ForegroundColor White
Write-Host '} | ConvertTo-Json' -ForegroundColor White
Write-Host ''
Write-Host 'try {' -ForegroundColor White
Write-Host '    # Login' -ForegroundColor White
Write-Host '    $login = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -ContentType "application/json" -Body $loginBody' -ForegroundColor White
Write-Host '    $token = $login.token' -ForegroundColor White
Write-Host '    Write-Host "✓ Login exitoso" -ForegroundColor Green' -ForegroundColor White
Write-Host '    ' -ForegroundColor White
Write-Host '    # Headers con token' -ForegroundColor White
Write-Host '    $headers = @{ "Authorization" = "Bearer $token" }' -ForegroundColor White
Write-Host '    ' -ForegroundColor White
Write-Host '    # Test GET /me' -ForegroundColor White
Write-Host '    Write-Host "`n=== GET /me ===" -ForegroundColor Cyan' -ForegroundColor White
Write-Host '    $me = Invoke-RestMethod -Uri "http://localhost:8080/me" -Method Get -Headers $headers' -ForegroundColor White
Write-Host '    $me | ConvertTo-Json -Depth 5' -ForegroundColor White
Write-Host '    ' -ForegroundColor White
Write-Host '    # Test GET /plan-practica' -ForegroundColor White
Write-Host '    Write-Host "`n=== GET /plan-practica ===" -ForegroundColor Cyan' -ForegroundColor White
Write-Host '    $plan = Invoke-RestMethod -Uri "http://localhost:8080/plan-practica" -Method Get -Headers $headers' -ForegroundColor White
Write-Host '    $plan | ConvertTo-Json -Depth 5' -ForegroundColor White
Write-Host '    ' -ForegroundColor White
Write-Host '    Write-Host "`n✓ Pruebas completadas exitosamente!" -ForegroundColor Green' -ForegroundColor White
Write-Host '} catch {' -ForegroundColor White
Write-Host '    Write-Host "✗ Error: $_" -ForegroundColor Red' -ForegroundColor White
Write-Host '    Write-Host $_.Exception.Response.StatusCode -ForegroundColor Red' -ForegroundColor White
Write-Host '}' -ForegroundColor White
