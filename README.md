# lograre

App nativa de Android (Kotlin + Jetpack Compose) de uso personal para limitar el tiempo diario de uso de aplicaciones seleccionadas y bloquearlas con un PIN al agotarse su tiempo.

Distribuida exclusivamente como APK instalable manualmente (sin Google Play ni respaldo en la nube).

## Funcionalidades

- **Límites diarios por app:** elige cualquier app instalada con lanzador y define un límite en minutos (`>= 0`; `0` pide PIN desde el primer intento).
- **Conteo en primer plano (`AccessibilityService`):** cuenta tiempo únicamente mientras cada app está en primer plano con pantalla activa, usando `SystemClock.elapsedRealtime()` (sin `UsageStatsManager` ni lectura de contenido de pantalla).
- **Pantalla de bloqueo con PIN (`LockScreenActivity`):** se lanza sobre la app bloqueada (`FLAG_SECURE`). Salir o pulsar Atrás envía al launcher (`ACTION_MAIN` + `CATEGORY_HOME`). Con PIN correcto permite:
  - **Añadir más tiempo** (`+5`, `+15`, `+30` min o cantidad personalizada).
  - **Ignorar el bloqueo todo el día** (deja de contar hasta las `00:00`).
- **PIN seguro:** longitud numérica variable (mínimo 4 dígitos), almacenado únicamente como hash salado `PBKDF2WithHmacSHA256` (120 000 iteraciones) cifrado con **Android Keystore (AES-256-GCM)**. Obligatorio para añadir/quitar apps, editar límites y cambiar el PIN.
- **Aviso de 5 minutos:** notificación local cuando faltan ≤ 5 minutos para agotar el límite diario de una app (no se envía si el límite diario de la app es menor a 5 minutos).
- **Reanudación tras reinicio:** `BootCompletedReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) y reconexión del `AccessibilityService` restauran el reloj de confianza y el estado diario guardado.
- **Sin respaldo en la nube:** `android:allowBackup="false"`, `dataExtractionRules` y `fullBackupContent` excluyen todos los dominios; al desinstalar se borra todo.

## Requisitos de entorno

- **IntelliJ IDEA** (con el plugin oficial **Android** de JetBrains) o **Android Studio**
- **JDK 17–21** (recomendado JDK 21 LTS; evitar JDK 25 con Gradle 8.11.1)
- **Android SDK 35** (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`)

## Compilar y ejecutar pruebas unitarias

1. Abre la carpeta raíz (`lograre`) en **IntelliJ IDEA** (**File → Open**) y espera la sincronización de Gradle.
2. Verifica en **Settings → Build, Execution, Deployment → Build Tools → Gradle** que **Gradle JVM** sea JDK 17–21.
3. Para correr todas las pruebas unitarias:
   - Haz clic derecho en `app/src/test/java` → **Run 'Tests in java'**, o ejecuta la tarea Gradle `:app:testDebugUnitTest`.

## Generar e instalar el APK

### Opción 1: APK Debug instalable (rápido para uso personal)
1. En IntelliJ IDEA abre la pestaña **Gradle** → `lograre` → `app` → `Tasks` → `build` → ejecuta **`assembleDebug`**.
2. El APK generado queda en:
   `app/build/outputs/apk/debug/app-debug.apk`

### Opción 2: APK Release firmado con tu propia keystore
1. En IntelliJ IDEA / Android Studio ve a **Build → Generate Signed Bundle / APK...** → elige **APK**.
2. Crea una keystore nueva (`.jks`, ignorada automáticamente por `.gitignore` para no subirla a GitHub) con tus credenciales y selecciona la variante **`release`**.
3. El APK firmado quedará en `app/release/app-release.apk`.

### Instalación manual en el teléfono
1. Copia el `.apk` al teléfono y ábrelo para instalarlo (habilitando *Instalar apps desconocidas* si lo solicita), o por USB con:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Abre **lograre**, configura el PIN inicial y entra en **Guía de permisos** para activar:
   - **Servicio de accesibilidad** (*Obligatorio*)
   - **Notificaciones** (*Android 13+*, para el aviso de 5 minutos)
   - **Exclusión de optimización de batería** (*Recomendada*)
3. **Nota para Android 13+ al instalar un APK manualmente:** si Android marca el servicio de accesibilidad como *"Configuración restringida"*, ve a **Ajustes → Aplicaciones → lograre → menú (⋮) arriba a la derecha → Permitir ajustes restringidos**, y vuelve a **Accesibilidad** para activarlo.

## Reloj de confianza anti-trampa y mitigación tras apagado

`TrustedClock` calcula la hora real a partir de un ancla persistida en Room (`anchorUtcMillis`, `anchorElapsedRealtimeMillis`, `anchorZoneId` y `lastBootCount`) avanzando exclusivamente con `SystemClock.elapsedRealtime()`.

- **Cambio manual de hora o zona horaria:** ignorados por completo mientras el dispositivo está encendido (`TimeChangeReceiver` escucha `TIME_SET` y `TIMEZONE_CHANGED` para recalcular con el reloj de confianza y reanclar vía SNTP si hay internet).
- **La hora nunca retrocede:** si tras un reinicio la hora del sistema es anterior a la última guardada, se ignora y se reanuda de forma monótona desde `anchorUtcMillis`.
- **Sincronización NTP (SNTP por UDP):** cuando hay conexión a internet, `SntpClient` consulta `time.google.com` / `time.cloudflare.com` / `pool.ntp.org` usando únicamente la diferencia monótona de `elapsedRealtime` para el RTT (inmune a un reloj local manipulado) y reancla el reloj de confianza, rechazando saltos absurdos.
- **Limitación conocida — apagar, adelantar la hora y encender sin internet:**
  - Al apagar el dispositivo, `SystemClock.elapsedRealtime()` vuelve a `0`, por lo que sin internet el sistema operativo no ofrece una fuente fiable de cuánto tiempo real estuvo apagado el teléfono.
  - **Mitigación implementada:** al detectar un reinicio (caída de `elapsedRealtime` o incremento de `Settings.Global.BOOT_COUNT`), cualquier salto hacia adelante del reloj del sistema que **cruce la medianoche (`00:00`)** o **supere 2 horas sin confirmación NTP** se rechaza y el reloj se mantiene anclado en `lastSavedUtc`, impidiendo un reinicio diario falso. En cuanto el teléfono recupera internet, la sincronización NTP verifica la hora real y ejecuta inmediatamente el reinicio diario si efectivamente pasó un día real.

## Fuera de alcance (limitaciones aceptadas)

- Sin Administrador de dispositivo ni Device Owner: la app puede desinstalarse libremente.
- No bloquea Ajustes, Accesibilidad ni "Forzar detención".
- No bloquea apps críticas del sistema por defecto (Teléfono, Ajustes, SystemUI, launcher).
