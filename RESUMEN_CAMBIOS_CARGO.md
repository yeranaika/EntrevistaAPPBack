# ✅ Resumen: Tests de Nivelación por Cargo

## Cambio Principal

Los tests de nivelación ahora se obtienen por **cargo de trabajo** en lugar de área general.

---

## 🔄 Antes vs Ahora

### Antes (por área)
```
GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10
```

### Ahora (por cargo)
```
GET /tests/nivelacion/iniciar?cargo=Desarrollador Full Stack&cantidad=10
```

---

## 📝 Archivos Modificados

### Backend
1. ✅ `data/repository/nivelacion/PreguntaNivelacionRepository.kt`
   - Nuevos métodos: `countByCargo()`, `findRandomByCargo()`, `findByCargoYNivel()`
   - Consultan el campo `meta_cargo` en lugar de `sector`

2. ✅ `routes/nivelacion/testNivelacionRoutes.kt`
   - Endpoint `/iniciar` usa parámetro `cargo`
   - Validación y consultas actualizadas

### Postman
3. ✅ `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`
   - Renombrado: "Iniciar Test por Cargo"
   - Eliminados: Endpoints específicos por área
   - Query param: `habilidad` → `cargo`
   - Script automático guarda `test_cargo`
   - Evaluar test usa `{{test_cargo}}` automáticamente

4. ✅ `EntrevistaAPP.postman_environment.json`
   - Nueva variable: `test_cargo`

### Scripts
5. ✅ `test-api.ps1`
   - Actualizado para usar cargo

### Migraciones
6. ✅ `migrations/008_populate_meta_cargo.sql`
   - Script SQL para poblar `meta_cargo` en preguntas existentes

### Documentación
7. ✅ `CAMBIO_CARGO_NIVELACION.md` - Documentación completa
8. ✅ `RESUMEN_CAMBIOS_CARGO.md` - Este archivo

---

## 🚀 Cómo Usar (Postman)

### 1. Re-importar Colección
```
1. Delete colección antigua
2. Import → EntrevistaAPP-Sistema-Nivelacion.postman_collection.json
3. Import → EntrevistaAPP.postman_environment.json (si no está)
4. Seleccionar environment "EntrevistaAPP - Local"
```

### 2. Flujo Completo
```
1. Auth → Register
2. Onboarding → POST (configurar cargo: "Desarrollador Full Stack")
3. Tests → Iniciar Test por Cargo (usa el cargo del onboarding)
4. Tests → Evaluar Test (usa {{test_cargo}} automáticamente)
5. Plan → Generar Plan
```

---

## ⚠️ Acción Requerida

### Poblar Base de Datos

Las preguntas existentes necesitan tener el campo `meta_cargo` poblado:

```sql
-- Ejecutar migración
\i migrations/008_populate_meta_cargo.sql
```

O manualmente:
```sql
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Full Stack'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND (meta_cargo IS NULL OR meta_cargo = '');
```

---

## 🧪 Verificar que Funciona

### Test Rápido
```powershell
.\test-api.ps1
```

Deberías ver:
```
4. Iniciar Test por Cargo...
OK: 10 preguntas
```

### Verificar en BD
```sql
-- Ver preguntas por cargo
SELECT meta_cargo, COUNT(*) 
FROM app.pregunta 
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY meta_cargo;
```

---

## 📋 Checklist

- [x] Backend actualizado
- [x] Postman actualizado
- [x] Script de prueba actualizado
- [x] Migración SQL creada
- [x] Documentación completa
- [ ] **Ejecutar migración SQL** ← PENDIENTE
- [ ] Probar flujo completo en Postman
- [ ] Actualizar frontend (si aplica)

---

## 💡 Notas Importantes

1. **Compatibilidad:** El campo JSON sigue siendo `habilidad` pero ahora representa el cargo
2. **Automático:** El cargo se guarda automáticamente en Postman al iniciar un test
3. **Flexible:** Puedes usar cualquier cargo configurado en el onboarding
4. **Migración:** Las preguntas existentes necesitan tener `meta_cargo` poblado

---

**Siguiente paso:** Ejecutar `migrations/008_populate_meta_cargo.sql` en la base de datos
