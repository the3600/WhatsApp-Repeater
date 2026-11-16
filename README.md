# WhatsApp Repeater (Android Nativo)

Aplicación nativa para Android desarrollada en **Kotlin**, **Jetpack Compose** y **Material Design 3**, diseñada para automatizar de forma **transparente, controlada y explícita** el envío secuencial o repetido de mensajes a un único contacto seleccionado en WhatsApp mediante **Android Accessibility Service**.

---

## Arquitectura modular

El proyecto está organizado en módulos Gradle independientes y clases desacopladas:

- **`:core:model`**: Modelos de dominio (`ContactInfo`, `MessageMode`, `IntervalRange`, `SafetyLimits`, `AutomationConfig`, `AutomationState`, `ExecutionRecord`, `SavedMessageList`, `PersistedAutomationDraft`, `AppSettings`).
- **`:core:automation`**: Motor de automatización desacoplado del framework para facilitar pruebas unitarias:
  - `AutomationEngine`: Orquesta el flujo completo de 14 pasos con soporte para `INICIAR`, `PAUSAR`, `REANUDAR` y `DETENER`.
  - `WhatsAppNavigator`: Reconoce pantallas de WhatsApp (`OpenConversation`, `HomeChatList`, `SearchMode`, `UnexpectedOrError`, `ExternalApp`) mediante heurísticas resilientes sin depender de un único ID fijo.
  - `MessageQueueManager`: Gestiona las colas en modalidad de mensajes múltiples o mensaje repetido.
  - `RandomIntervalGenerator`: Calcula esperas aleatorias dentro del rango `[mínimo, máximo]` validado.
- **`:core:data`**: Persistencia local con migraciones controladas y respaldo previo:
  - `AppDatabase` (`version = 2`, `MIGRATION_1_2` sin borrado destructivo) + `DatabaseBackupManager` (crea copias SQLite automáticas antes de migrar).
  - `HistoryRepository`: Almacena metadatos de ejecución. No guarda contraseñas ni sesiones de WhatsApp.
  - `SavedConfigRepository`: Conserva mensajes guardados, listas de mensajes, contacto seleccionado e intervalos entre reinicios y actualizaciones.
  - `SettingsRepository` (DataStore): Guarda preferencias del usuario y repositorio de actualizaciones.
  - `ContactsRepository`: Consulta contactos locales únicamente tras la autorización explícita del usuario.
- **`:core:updater`**: Comparador semántico, parser de GitHub Releases, verificador `SHA-256` y validador de compatibilidad (`applicationId`, `versionCode`, firma, `minSdk`).
- **`:app`**: Interfaz Compose oscura con acento verde WhatsApp, `MainActivity`, `AutomationAccessibilityService`, `UpdateManager`, `UpdatesScreen` y las pantallas principales.

---

## Seguridad, control y privacidad

- **Activación manual obligatoria**: El usuario debe habilitar el servicio de accesibilidad manualmente en Ajustes de Android.
- **Alcance limitado**: El servicio declara `android:packageNames="com.whatsapp,com.whatsapp.w4b"` para inspeccionar exclusivamente WhatsApp / WhatsApp Business durante la ejecución.
- **Sin automatizaciones ocultas**: Siempre se abre WhatsApp de forma visible y se muestra una notificación persistente con botón **DETENER** mientras la tarea está activa.
- **Doble confirmación de destinatario**: El usuario confirma el contacto elegido en el selector y vuelve a autorizar antes de iniciar; además, antes de escribir cada mensaje el motor verifica la cabecera del chat abierto.
- **Detención inmediata ante bloqueos o cambios**: Si la cabecera no coincide, la interfaz cambia de forma inesperada o aparece un diálogo de error, PIN o CAPTCHA, la automatización se detiene al instante (nunca intenta evadir controles ni CAPTCHAs).
- **Límites prudentes**: Intervalos entre `5s` y `300s` (recomendado `15s–35s`) y tope máximo de `50` mensajes por ejecución para evitar envíos masivos.

---

## Requisitos de compatibilidad

- **Android 9.0 (API 28) o superior** (`minSdk = 28`, `targetSdk = 35`).
- WhatsApp (`com.whatsapp`) o WhatsApp Business (`com.whatsapp.w4b`) instalado y con sesión iniciada por el propio usuario.

---

## Compilación local y generación del APK

Requisitos: **JDK 17** y **Android SDK (API 36)** (`compileSdk = 36`, `targetSdk = 35`, `minSdk = 28`).

```bash
# Ejecutar pruebas unitarias de todos los módulos
./gradlew test

# Compilar APK de depuración instalable
./gradlew assembleDebug
```

El APK generado se ubicará en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Instalación del APK en el teléfono

### Opción A: Instalación directa en el teléfono
1. Descarga `WhatsAppRepeater-debug.apk` (desde **GitHub Releases** o desde `app/build/outputs/apk/debug/app-debug.apk`).
2. Ábrelo desde el gestor de archivos de tu teléfono Android.
3. Si Android lo solicita, habilita *"Instalar aplicaciones de orígenes desconocidos"* para el navegador o gestor de archivos.
4. Confirma la instalación.

### Opción B: Instalación mediante ADB desde PC
1. Activa la **Depuración USB** en *Opciones de desarrollador* de tu teléfono y conéctalo por USB.
2. Ejecuta:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Cómo activar el Servicio de Accesibilidad

> **Nota para Android 13, 14 y 15**: Si instalaste el APK manualmente fuera de Google Play, Android puede restringir temporalmente los ajustes de accesibilidad ("Configuración restringida"). Para habilitarlo: ve a **Ajustes > Aplicaciones > WhatsApp Repeater > menú (⋮) > Permitir ajustes restringidos**.

1. Abre **WhatsApp Repeater**.
2. En la pantalla de inicio, pulsa **"Activar permisos"** y luego **"Ir a Accesibilidad"**.
3. Entra en **Aplicaciones instaladas** / **Servicios descargados**.
4. Selecciona **"WhatsApp Repeater — Automatización"** y activa el interruptor.
5. Vuelve a la app y comprueba que la insignia indique **"Servicio activo"**.

---

## Guía rápida de uso

1. **Inicio**: Verifica que el servicio aparezca activo y pulsa **"Crear automatización"**.
2. **Seleccionar contacto**: Concede el permiso de contactos (solo se solicita en este paso), busca por nombre o número, elige **un único destinatario** y confírmalo.
3. **Configurar mensajes**:
   - **Modalidad 1 (Mensajes múltiples)**: Agrega, edita, elimina o reordena mensajes en una lista numerada; cada uno se enviará una sola vez.
   - **Modalidad 2 (Repetir mensaje)**: Escribe un mensaje, ajusta la cantidad de repeticiones dentro del límite configurado y revisa la vista previa.
4. **Configurar intervalos**: Define el intervalo mínimo y máximo en segundos (validando que mínimo ≤ máximo) y elige si deseas guardar los valores por defecto o usar WhatsApp Business.
5. **Ejecución**: Revisa el resumen, marca la casilla de autorización explícita, pulsa **INICIAR** y confirma el diálogo final. Podrás **PAUSAR**, **REANUDAR** o **DETENER** en cualquier momento desde la app o desde la notificación.

---

## Limitaciones conocidas por restricciones de Android / WhatsApp

- **Cambios de interfaz en WhatsApp**: Aunque `WhatsAppNavigator` utiliza heurísticas combinadas (tipo de nodo, posición en pantalla, descripciones de accesibilidad en español/inglés y texto de cabecera) en lugar de depender de un único ID, una actualización mayor de WhatsApp podría modificar su árbol de accesibilidad. Por seguridad, si no reconoce el campo de texto, el botón de envío o la cabecera del destinatario, la ejecución se detiene automáticamente.
- **Pantalla apagada o bloqueo**: Android impide interactuar con la UI cuando el teléfono está bloqueado; mantén la pantalla desbloqueada durante la ejecución.
- **Alternativa segura ante números no guardados o no reconocidos**: La aplicación abre el enlace oficial `https://api.whatsapp.com/send?phone=...` y, si llega a la pantalla principal de chats, utiliza el buscador interno de WhatsApp antes de verificar la cabecera. Si WhatsApp indica que el número no existe o solicita verificación/CAPTCHA, la automatización se detiene sin intentar omitir la restricción.

---

## Actualizaciones sin perder datos y Publicación en GitHub Releases

- **Identidad fija**: `applicationId = "com.whatsapprepeater.android"` (no cambia nunca entre versiones).
- **Versionado**: Controlado desde [version.properties](version.properties) (`VERSION_CODE` incremental + `VERSION_NAME` semántico).
- **Actualizador integrado**: Desde la pantalla **Actualizaciones** puedes consultar GitHub Releases, leer las novedades, descargar el APK, verificar automáticamente su `SHA-256`, validar firma y `versionCode`, y lanzar el instalador oficial de Android conservando todos tus datos (mensajes, listas, contactos, intervalos, historial y preferencias).
- **Firma de producción y guía completa**: Consulta [docs/RELEASE_AND_SIGNING.md](docs/RELEASE_AND_SIGNING.md) para configurar tu keystore en GitHub Secrets y publicar nuevas versiones con `git tag vX.Y.Z && git push origin vX.Y.Z`.

