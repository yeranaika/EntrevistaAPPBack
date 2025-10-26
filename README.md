# EntrevistaAPPBack

Aplicación para practicar entrevistas (Backend con **Ktor**).
Backend de práctica con **Ktor**.

## Requisitos

* **JDK:** 21 (LTS)
* **Consola:** PowerShell/CMD (Windows) o Terminal (macOS/Linux)
* **Ruta:** ejecutar desde la carpeta raíz del repo (donde está `gradlew`)

## Ejecutar el backend

```
# Limpiar y compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Levantar el servidor
./gradlew run

# combinacion de comando comun 
./gradlew --refresh-dependencies clean test run
```

En Windows usa `.\gradlew ...`.

## Git: flujo básico

```
# Actualizar main
git fetch origin
git switch main
git pull

# Crear rama para tu cambio
git switch -c feature/mi-cambio

# Guardar trabajo
git add .
git commit -m "feat: describe tu cambio"

# Publicar rama
git push -u origin feature/mi-cambio

# Mantener la rama al día con main (rebase)
git fetch origin
git rebase origin/main
git push --force-with-lease
```

Listo. Copia y pega en `README.md`.

---

## Solución de problemas

* **Inconsistent JVM Target (25 vs 24)**
  Estás usando un JDK distinto. Asegúrate de que **JAVA_HOME** y `java -version` apunten a **JDK 21** y vuelve a ejecutar:

  ```bash
  # Win
  .\gradlew --refresh-dependencies clean run
  # macOS/Linux
  ./gradlew --refresh-dependencies clean run
  ```
