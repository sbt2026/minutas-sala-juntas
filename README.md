# Minutas — Sala de Juntas

App Android nativa para el panel interactivo de 66" (Android 14). Graba la reunión con el micrófono del panel, la transcribe en vivo en español y genera una minuta profesional (resumen, acuerdos, pendientes, próximos pasos) con la API de Claude. Exporta a Word (.docx) y PDF.

## Cómo funciona

1. Escribe el título de la reunión y los asistentes (opcional).
2. Toca **GRABAR**. La transcripción aparece en vivo en pantalla. La app usa el reconocimiento de voz de Android (gratuito, sin claves) y mantiene la pantalla encendida.
3. Toca **DETENER** al terminar.
4. Toca **Generar minuta**. Claude estructura la minuta a partir de la transcripción.
5. Exporta a **Word** o **PDF**. Los archivos se guardan en `Documentos/Minutas` del panel y se pueden compartir (correo, Drive, etc.).

## Compilar el APK

Requiere JDK 17. Dos opciones:

**Opción A — Android Studio (recomendada)**
1. Abre Android Studio → *Open* → selecciona esta carpeta.
2. Espera la sincronización de Gradle (descarga dependencias automáticamente).
3. Menú *Build → Build App Bundle(s)/APK(s) → Build APK(s)*.
4. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

**Opción B — GitHub Actions (sin instalar nada)**
1. Sube esta carpeta a un repositorio de GitHub.
2. El workflow `.github/workflows/build-apk.yml` compila automáticamente en cada push (o manualmente desde la pestaña *Actions*).
3. Descarga el APK del artefacto `minutas-apk`.

## Instalar en el panel

1. Copia `app-debug.apk` a una USB (o descárgalo en el panel).
2. En el panel: Ajustes → Seguridad → permitir *Instalar apps de origen desconocido* para el explorador de archivos.
3. Abre el APK desde el explorador de archivos e instala.
4. Al primer uso, concede el permiso de micrófono.

## Configuración (opcional)

La app funciona sin configurar nada: sin clave API genera la minuta en **modo plantilla** (datos de la reunión, acuerdos/pendientes detectados por palabras clave y transcripción completa).

**IA gratis con Gemini**: entra a https://aistudio.google.com → *Get API key* → crea una clave (gratis, sin tarjeta) y pégala en **⚙ Ajustes** de la app. Con eso las minutas se generan con IA (modelo `gemini-flash-latest`).

Si más adelante tienes clave API de Claude, tócala en **⚙ Ajustes** y las minutas se generarán con IA:
- **Clave API de Claude**: se obtiene en https://console.anthropic.com (se guarda solo en el panel).
- **Modelo**: `claude-sonnet-5` por defecto.
- **Idioma de transcripción**: `es-MX` por defecto (acepta `es-ES`, `en-US`, etc.).

## Notas y limitaciones

- El panel necesita internet para generar la minuta (la transcripción puede funcionar sin conexión si el panel tiene el paquete de voz en español descargado; si no, también usa red).
- La app no guarda archivo de audio: en Android el reconocimiento de voz y la grabación simultánea de audio compiten por el micrófono y no es confiable. El registro de la reunión es la transcripción, que permanece visible y editable en pantalla hasta iniciar una nueva reunión.
- La precisión mejora si los participantes hablan cerca del panel y de uno en uno.
- El reconocimiento de voz requiere que el panel tenga la app de Google / servicios de voz (este panel los tiene: cuenta con GSF ID).
