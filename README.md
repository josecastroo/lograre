# lograre

App nativa de Android (Kotlin + Jetpack Compose) de uso personal para limitar el tiempo diario de uso de aplicaciones seleccionadas y bloquearlas con un PIN al agotarse su tiempo.

## Requisitos de entorno

- **IntelliJ IDEA** (con el plugin oficial **Android** de JetBrains) o **Android Studio**
- **JDK 17** (o superior compatible con Gradle 8.11.1)
- **Android SDK 35** (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`)

## Abrir, compilar y probar en IntelliJ IDEA

1. Abre la carpeta raíz del proyecto (`lograre`) con **File → Open**.
2. Acepta la sincronización de Gradle (IntelliJ descargará automáticamente Gradle `8.11.1` según `gradle/wrapper/gradle-wrapper.properties` y configurará `local.properties` con la ruta de tu Android SDK).
3. Asegúrate de tener instalado **Android 15 (API 35)** en **Settings → Languages & Frameworks → Android SDK**.
4. Para ejecutar las pruebas unitarias:
   - Haz clic derecho sobre `app/src/test/java` → **Run 'Tests in java'**, o ejecuta la tarea Gradle `:app:testDebugUnitTest`.
5. Para generar el APK instalable de prueba:
   - Ejecuta la tarea Gradle `:app:assembleDebug`.
   - El APK generado quedará en `app/build/outputs/apk/debug/app-debug.apk`.
6. Para instalar el APK en un teléfono Android:
   - Copia `app-debug.apk` al dispositivo y ábrelo para instalarlo (habilitando "Instalar apps desconocidas"), o usa `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Estado actual (Paso 1)

- Estructura base Android/Gradle con Kotlin 2.1.20, AGP 8.9.1, Jetpack Compose, Room 2.7.1 y KSP.
- Sin respaldo en la nube (`allowBackup="false"`, `dataExtractionRules` y `fullBackupContent` excluyendo todos los dominios).
- `<queries>` configurado para listar apps con lanzador en Android 11+.
- Modelo de datos y base de datos Room (`tracked_apps` y `trusted_clock_state`).
- Almacenamiento seguro del PIN con hash salado (`PBKDF2WithHmacSHA256`) cifrado con **Android Keystore (AES-256-GCM)**.
- Pruebas unitarias para el modelo de límites, repositorio y gestión segura de PIN.
