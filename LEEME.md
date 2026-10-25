# Pulso Music — teléfono Android + Wear OS

Dos aplicaciones nativas en Java, dentro del mismo proyecto de Android Studio. El teléfono controla una sesión de música existente; el reloj muestra la canción y envía órdenes. No requiere servidor ni cuentas propias.

## Para empezar

1. Abre esta carpeta **PulsoMusic** en Android Studio y espera la sincronización de Gradle.
2. Ejecuta el módulo **phone** en el teléfono Android y **wear** en el reloj Wear OS. También puedes instalar los APK incluidos en `instaladores/`.
3. Empareja el reloj y el teléfono con la aplicación de vinculación que corresponda a tu reloj. No basta con abrir los dos emuladores. Ambos necesitan Google Play Services y el vínculo de Wear OS configurado.
4. En el teléfono abre **Pulso · Teléfono**, pulsa **Permitir acceso a notificaciones** y habilita **Pulso: acceso al reproductor**. Si Android restringe este ajuste por ser una instalación manual, entra a Información de la aplicación → menú → Permitir ajustes restringidos, si aparece esa opción; después vuelve a habilitar el acceso.
5. Abre tu reproductor de música y reproduce una canción. Abre **Pulso** en el reloj.
6. Para las barras reales, pulsa **Activar visualizador real** en el teléfono y concede los permisos. En el diálogo de captura, selecciona compartir toda la pantalla si tu versión de Android ofrece esa opción. Pulso sólo configura captura de audio; no crea una grabación de video ni guarda archivos de audio.

## Controles del reloj

- Reproducir y pausar; canción anterior y siguiente.
- Subir/bajar volumen y silenciar/restaurar la música local del teléfono.
- Deslizar la posición de la canción, avanzar o retroceder diez segundos y detener.
- Activar/desactivar aleatorio y alternar repetición: desactivada → todas → una.
- Si se pulsa una opción que el reproductor actual no admite, Pulso muestra: «Opcion incompatible con este reproductor.»
- Elegir entre los reproductores con sesiones activas.
- Seleccionar canciones de la cola, cuando el reproductor publica la cola y admite esa acción. Se muestran hasta treinta elementos.
- Otras acciones publicadas por el reproductor, con sus nombres originales.
- **Barras de audio**: interruptor persistente para mostrar u ocultar el fondo. Si Spotify es el reproductor seleccionado y las barras no se están mostrando, Pulso enseña el icono de Spotify como fondo. Con otros reproductores nunca se muestra ese icono. La captura del teléfono se detiene por separado desde Pulso o desde su notificación.

Desliza hacia arriba o gira la corona para ver el volumen, el interruptor y **Más controles**. Las opciones no compatibles muestran un aviso al pulsarlas. Pulso no puede obligar a una aplicación externa a ofrecer cola, aleatorio, repetición, búsqueda u otras funciones. El volumen de una sesión remota depende de esa sesión; el silencio se ofrece para música local.

## Barras reales y segundo plano

Las dieciséis barras representan bandas de frecuencia del audio capturado en el teléfono. No son una animación aleatoria. Se analizan muestras mono a 16 kHz y se envían niveles como máximo diez veces por segundo mientras el reloj está visible y solicita las barras. No se envía audio bruto al reloj. Si Spotify es el reproductor seleccionado pero no hay barras visibles —por ejemplo, porque está desactivado el interruptor o Spotify bloquea la captura— el reloj muestra como fondo el icono de Spotify obtenido de la aplicación instalada en el teléfono. Con otro reproductor seleccionado, el logo de Spotify no aparece.

La captura usa AudioPlaybackCapture y MediaProjection con un servicio en primer plano y una notificación **Detener**. El permiso RECORD_AUDIO es necesario para esa API, pero Pulso no abre el micrófono.

**No todos los reproductores permiten capturar su audio.** Ante audio bloqueado o silencio las barras quedan vacías y se informa en pantalla; los controles pueden seguir funcionando. Spotify puede bloquear la captura y entonces no habrá niveles de audio que mostrar; Pulso mostrará su icono de fondo sólo si Spotify es el reproductor seleccionado. Para una demostración, utiliza un reproductor de archivos locales que permita capturar audio, con una canción de prueba sin protección. No se promete compatibilidad de captura con Spotify, YouTube Music u otra aplicación específica.

Android puede terminar la proyección al bloquear la pantalla, cuando el usuario revoca el permiso o por sus políticas. En ese caso hay que volver a autorizar en el teléfono. No se reinicia una captura sin consentimiento. Los controles se atienden mediante el servicio de acceso a notificaciones; no requieren que la pantalla de Pulso permanezca abierta. Algunos fabricantes imponen restricciones adicionales de batería.

## Probar sin reloj físico

En Android Studio abre **Device Manager**, crea un teléfono con Google Play y un dispositivo **Wear OS**, e inicia ambos. Usa **Pair Wearable / Wear OS pairing assistant** para emparejarlos y completa la configuración indicada. Instala `phone` y `wear` en sus dispositivos correspondientes. También puedes emparejar un teléfono Android físico con un reloj virtual.

La PC usada para crear el proyecto ya tiene los AVD `Pixel_9_Pro_XL` y `Wear_OS_Large_Round`. Si el reloj muestra una pantalla negra, cambia sus gráficos a software. La guía oficial describe requisitos y limitaciones de emparejamiento:

- https://developer.android.com/training/wearables/get-started/connect-phone
- https://developer.android.com/training/wearables/get-started/emulator

Abrir ambos emuladores **no los empareja automáticamente**. Si Pulso indica que configures la app de vinculación, completa primero esa parte.

## Compilar e instalar

Requisitos: Android Studio con JDK 17 o 21, Android SDK 35, Android 11+ en el teléfono y Wear OS 3+ (API 30+) en el reloj. La primera sincronización necesita internet.

```powershell
.\gradlew.bat :phone:assembleDebug :wear:assembleDebug
```

O ejecuta `COMPILAR.ps1` desde PowerShell. Los APK de desarrollo están en:

- `phone/build/outputs/apk/debug/phone-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

```powershell
adb -s SERIAL_TELEFONO install -r instaladores/pulso-telefono.apk
adb -s SERIAL_RELOJ install -r instaladores/pulso-reloj.apk
```

Ambas apps usan el identificador `com.ut.pulsomusic` y deben tener la **misma firma**. Compílalas en la misma máquina/configuración o utiliza los dos APK incluidos juntos. Si recompilas sólo una app con otra clave, la comunicación no funcionará. Los APK incluidos son para desarrollo, no para publicar en Google Play.

## Estructura

| Carpeta | Función |
|---|---|
| `phone` | Configuración, permisos, sesiones multimedia, órdenes y captura de audio |
| `wear` | Interfaz del reloj, botones y visualizador |
| `shared` | Protocolo JSON sobre Wear OS Data Layer / MessageClient |
| `tests` | Prueba del análisis de frecuencia sin Android |
| `phone/src/androidTest` | Prueba de integración de controles con una sesión multimedia de prueba |
| `instaladores` | APK de teléfono y de reloj |

No se usa Bluetooth clásico directamente: Wear OS gestiona el transporte de Data Layer. Los mensajes de control no se almacenan si se pierde la conexión; el usuario debe volver a intentar la acción. El reloj vuelve a consultar el estado al abrirse y periódicamente mientras está visible.

## Pruebas

La revisión automática se ejecuta con:

```powershell
.\gradlew.bat :phone:lintDebug :wear:lintDebug
```

La prueba del espectro comprueba silencio, cuatro tonos de referencia, límites y entrada vacía. La prueba de integración se ejecuta sólo en un teléfono de pruebas/emulador con acceso a notificaciones habilitado:

```powershell
.\gradlew.bat :phone:assembleDebug :phone:assembleDebugAndroidTest
adb -s SERIAL_TELEFONO install -r phone/build/outputs/apk/debug/phone-debug.apk
adb -s SERIAL_TELEFONO install -r phone/build/outputs/apk/androidTest/debug/phone-debug-androidTest.apk
adb -s SERIAL_TELEFONO shell am instrument -w com.ut.pulsomusic.test/com.ut.pulsomusic.ControlIntegrationTest
```

Consulta `VALIDACION.md` para conocer exactamente las pruebas realizadas y las pendientes.

## Referencias técnicas

- Sesiones y controles: https://developer.android.com/reference/android/media/session/MediaController
- Repetición y aleatorio: https://developer.android.com/reference/android/support/v4/media/session/MediaControllerCompat.TransportControls
- Captura y restricciones: https://developer.android.com/media/platform/av-capture
- Consentimiento de proyección: https://developer.android.com/media/grow/media-projection
- Mensajes entre dispositivos: https://developer.android.com/training/wearables/data/client-types
