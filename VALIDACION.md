# Validación realizada — 20 de octubre de 2026

## Correcto

- Compilación de `phone` y `wear`: APK debug generados sin errores.
- Android Lint en ambos módulos: sin errores. Quedan avisos de localización de textos en español, configuración de copias de seguridad y nivel objetivo de Android; no se han ocultado con una línea base.
- Firma de ambos APK verificada: mismo certificado de desarrollo SHA-256 `fc0cea9d9d23737eaa239effe52a4db4218541158e827f78832f1c76dd6e6838`.
- Instalación y apertura en AVD Pixel 9 Pro XL y Wear OS Large Round, ambos con Android API 36. Sin excepciones AndroidRuntime de Pulso en estas comprobaciones.
- Inspección de las pantallas y corrección de los márgenes del teléfono para respetar las barras del sistema.
- Interruptor del reloj: desactivación comprobada y preferencia `bars=false` guardada en disco.
- Análisis de audio: silencio, tonos de cuatro bandas conocidas, valores finitos entre cero y uno, entrada vacía y una sola muestra.
- Prueba de integración en el teléfono virtual usando una MediaSessionCompat de prueba: metadatos, reproducir, pausar, siguiente, anterior, límites de búsqueda, +10 segundos, −10 segundos, aleatorio, repetición y detener.

La prueba de integración invoca la recepción de órdenes del servicio del teléfono. **No equivale a una prueba del transporte entre reloj y teléfono.**

## Pendiente de verificar con el entorno del usuario

- Emparejar los dispositivos y probar el intercambio completo por Wear OS Data Layer. Los AVD se abrieron por separado; no se completó su vinculación. El emulador del teléfono solicita configurar la aplicación de vinculación.
- Captura completa MediaProjection → AudioRecord → niveles → reloj, con consentimiento y un reproductor real que permita capturar audio.
- Captura de audio y compatibilidad de controles con Spotify, YouTube Music u otras aplicaciones. Cada una decide qué acciones y qué audio expone; Pulso no puede saltarse sus restricciones de captura.
- Transferencia y visualización en un reloj emparejado del icono de Spotify como fondo cuando no hay barras visibles y Spotify es el reproductor seleccionado. Requiere Spotify instalado en el teléfono.
- Volumen/silencio en rutas físicas como auriculares Bluetooth y altavoces remotos.
- Batería, latencia y comportamiento prolongado con el teléfono bloqueado, restricciones del fabricante o interrupciones de Bluetooth.

Las capturas en `capturas/` son pantallas reales de los emuladores sin emparejar, no simulaciones de una conexión completada.
