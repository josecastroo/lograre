# lograre

App nativa de Android (Kotlin + Jetpack Compose) de uso personal para limitar el tiempo diario de uso de aplicaciones seleccionadas y bloquearlas con un PIN al agotarse su tiempo.

## Requisitos de entorno

- **IntelliJ IDEA** (con el plugin oficial **Android** de JetBrains) o **Android Studio**
- **JDK 17–21** (recomendado JDK 21 LTS; evitar JDK 25 con Gradle 8.11.1)
- **Android SDK 35** (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`)

## Abrir, compilar y probar en IntelliJ IDEA

1. Abre la carpeta raíz del proyecto (`lograre`) con **File → Open**.
2. Acepta la sincronización de Gradle (IntelliJ descargará automáticamente Gradle `8.11.1` según `gradle/wrapper/gradle-wrapper.properties` y configurará `local.properties` con la ruta de tu Android SDK).
3. Verifica en **Settings → Build, Execution, Deployment → Build Tools → Gradle** que **Gradle JVM** sea JDK 17–21.
4. Para ejecutar las pruebas unitarias:
   - Haz clic derecho sobre `app/src/test/java` → **Run 'Tests in java'**, o ejecuta la tarea Gradle `:app:testDebugUnitTest`.
5. Para generar el APK instalable de prueba:
   - Ejecuta la tarea Gradle `:app:assembleDebug`.
   - El APK generado quedará en `app/build/outputs/apk/debug/app-debug.apk`.
6. Para instalar el APK en un teléfono Android:
   - Copia `app-debug.apk` al dispositivo y ábrelo para instalarlo (habilitando "Instalar apps desconocidas"), o usa `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Reloj de confianza anti-trampa y mitigación tras apagado

`TrustedClock` calcula la hora real a partir de un ancla persistida en Room (`anchorUtcMillis`, `anchorElapsedRealtimeMillis`, `anchorZoneId` y `lastBootCount`) avanzando exclusivamente con `SystemClock.elapsedRealtime()`.

- **Cambio manual de hora o zona horaria:** ignorados por completo mientras el dispositivo está encendido (`TimeChangeReceiver` escucha `TIME_SET` y `TIMEZONE_CHANGED` para recalcular con el reloj de confianza y reanclar vía SNTP si hay internet).
- **La hora nunca retrocede:** si tras un reinicio la hora del sistema es anterior a la última guardada, se ignora y se reanuda de forma monótona desde `anchorUtcMillis`.
- **Sincronización NTP (SNTP por UDP):** cuando hay conexión a internet, `SntpClient` consulta `time.google.com` / `time.cloudflare.com` / `pool.ntp.org` usando únicamente la diferencia monótona de `elapsedRealtime` para el RTT (inmune a un reloj local manipulado) y reancla el reloj de confianza, rechazando saltos absurdos.
- **Limitación conocida — apagar, adelantar la hora y encender sin internet:**
  - Al apagar el dispositivo, `SystemClock.elapsedRealtime()` vuelve a `0`, por lo que sin internet el sistema operativo no ofrece una fuente criptográfica de cuánto tiempo real estuvo apagado el teléfono.
  - **Mitigación implementada:** al detectar un reinicio (caída de `elapsedRealtime` o incremento de `Settings.Global.BOOT_COUNT`), cualquier salto hacia adelante del reloj del sistema que **cruce la medianoche (`00:00`)** o **supere 2 horas sin confirmación NTP** se considera sospechoso y se rechaza. El reloj de confianza se mantiene en `lastSavedUtc` (reanclado al nuevo `elapsedRealtime`), impidiendo un reinicio diario falso. En cuanto el teléfono recupera conexión a internet, la sincronización NTP verifica la hora real y ejecuta inmediatamente el reinicio diario si efectivamente pasó un día real.

## Estado actual

- **Paso 1:** Estructura Android/Gradle, Compose, Room (`tracked_apps` y `trusted_clock_state`), exclusión total de backup en la nube, `<queries>` para Android 11+, almacenamiento seguro de PIN (`PBKDF2WithHmacSHA256` + Android Keystore AES-256-GCM) y pruebas unitarias.
- **Paso 2:** Reloj de confianza anti-trampa (`TrustedClock`), cliente SNTP (`SntpClient`), coordinador de reinicio diario a las `00:00` (`DailyResetCoordinator`), `TimeChangeReceiver` (`TIME_SET` y `TIMEZONE_CHANGED`) y pruebas unitarias.
- **Paso 3:** `LograreAccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`, sin lectura de contenido de ventana), `ForegroundUsageTracker` basado en `elapsedRealtime`, protección de apps críticas/teclados/SystemUI, pausa en apagado de pantalla y pruebas unitarias del contador.
- **Paso 4:** `LockScreenActivity` sobre la app bloqueada (`FLAG_SECURE`, salida al launcher con `ACTION_MAIN` + `CATEGORY_HOME`), teclado de PIN reutilizable (`PinKeypad`), opciones tras PIN correcto (+5/+15/+30 min, cantidad personalizada e ignorar todo el día) y pruebas unitarias.
- **Paso 5:** Pantalla de configuración inicial de PIN, pantalla principal de apps supervisadas con íconos y barras de progreso, selector buscable de apps instaladas (`<queries>`), flujo de confirmación con PIN para añadir/editar/quitar apps y cambiar PIN, guía de permisos (accesibilidad, notificaciones y batería) y pruebas unitarias.
