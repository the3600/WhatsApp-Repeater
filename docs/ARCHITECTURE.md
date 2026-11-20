# Arquitectura Técnica — WhatsApp Repeater 2.0

## 1. Mapa de módulos Gradle

```text
:app (Jetpack Compose Material 3, Navigation 3, Android Services, Receivers, Security Vault)
 ├── :core:model          (Dominio puro Kotlin, Variables dinámicas, LocalIntelligenceEngine, Insights)
 ├── :core:automation     (Motor 1: AutomationEngine, ContactVerifier, WhatsAppNavigator, Colas)
 ├── :core:autoresponder  (Motor 2: AutoReplyEngine, RuleMatcher, ScheduleEvaluator, ContactFilter)
 ├── :core:data           (Room v6 + migraciones 1→6, DataStore, LocalBackupManager, TemplateBundleCodec)
 └── :core:updater        (SemVer, GitHub Releases parser, SHA-256, validación estricta HTTPS + firma)
```

Todos los módulos `:core:*` (excepto wrappers Room/DataStore en `:core:data`) son **Kotlin puro o desacoplados del framework Android** para permitir pruebas unitarias deterministas y rápidas sin emulador.

---

## 2. Motores de automatización

### Motor 1 — Automatización por Accesibilidad (`:core:automation` + `AutomationAccessibilityService`)
- **Principio de seguridad**: Nunca asume que WhatsApp abrió el chat correcto. Antes de escribir cada mensaje:
  1. `AndroidAccessibilityDriver` inspecciona la jerarquía (límite de seguridad de 1,500 nodos con `node.refresh()` para evitar nodos obsoletos) excluyendo el `RecyclerView` de mensajes del chat para no confundir texto del cuerpo con la cabecera.
  2. `ContactVerifier` normaliza caracteres invisibles BiDi/NBSP, separadores telefónicos y variantes regionales (como México `+521`/`+52`), protege contra contactos de nombres similares y rechaza cabeceras truncadas ambiguas.
  3. Si la verificación falla, aparece pantalla inesperada o se solicita intervención humana (bloqueo/CAPTCHA), `AutomationEngine` detiene el lote de inmediato, notifica al usuario y registra la causa exacta en `ExecutionRecord`.
- **Controles en tiempo real**: `INICIAR`, `PAUSAR`, `REANUDAR` y `DETENER` desde UI y desde notificación persistente (`StopAutomationReceiver`).

### Motor 2 — Autorespuestas por Notificación (`:core:autoresponder` + `AutoReplyNotificationListenerService`)
- Intercepta únicamente notificaciones entrantes de `com.whatsapp` y `com.whatsapp.w4b` cuando el interruptor maestro está activado.
- Evalúa ordenadamente: filtros de app objetivo → filtros de contacto/grupo → horario global y por regla → reglas priorizadas (`RuleMatcher`) o mensajes de bienvenida/ausencia → protección anti-bucles y enfriamiento por conversación.
- Responde mediante la acción oficial `RemoteInput` de la notificación sin abrir la interfaz de WhatsApp.
- Por privacidad, el almacenamiento de historial y vista previa entrante viene desactivado por defecto y al restaurar respaldos el interruptor maestro se mantiene siempre en `false`.

---

## 3. Programador Visual y Centro de Notificaciones Activas
- **Persistencia**: Tabla `scheduled_tasks` en Room v6 con soporte para frecuencia única, diaria, semanal o días específicos de la semana (`customWeekDays`), fecha/hora de finalización (`expirationMillis`), tipo de contenido (mensaje único o secuencia), plantilla asociada e intervalo aleatorio.
- **Disparo**: `SchedulerAlarmHelper` usa `AlarmManager` (alarmas exactas cuando están autorizadas con respaldo seguro en caso contrario) y `ScheduledReminderReceiver` ejecuta automáticamente si el servicio está listo o emite alerta accionable si se requiere intervención.
- **Notificación consolidada sin drenaje de batería**: Una sola notificación activa (`ID = 70_100`) aprovecha el cronómetro nativo del sistema Android (`setUsesChronometer(true)` + `setChronometerCountDown(true)`) en lugar de hilos en segundo plano que despiertan la CPU cada segundo, e incluye acciones rápidas directas (`ScheduleNotificationActionReceiver`: **Pausar / Reanudar**, **Cancelar**, **Abrir Programador**).

---

## 4. Base de datos local y migraciones (`:core:data`)
- **Room `AppDatabase` (versión 6)**:
  - `MIGRATION_1_2`: agrega tablas de configuración guardada y borrador.
  - `MIGRATION_2_3`: agrega tablas de Motor 2 (`auto_reply_rules`, `auto_reply_logs`).
  - `MIGRATION_3_4`: agrega metadatos de biblioteca inteligente (`category`, `tags`, `isFavorite`, `isPinned`, `useCount`, `lastUsedAtMillis`) y tabla `scheduled_tasks`.
  - `MIGRATION_4_5`: agrega columna de estado de ejecución en programaciones.
  - `MIGRATION_5_6`: agrega `customWeekDays`, `expirationMillis` y campos de control del Programador Visual.
- **Respaldo pre-migración**: `DatabaseBackupManager` genera automáticamente copias SQLite antes de ejecutar migraciones de esquema.
- **Esquemas versionados**: `core/data/schemas/com.whatsapprepeater.core.data.local.AppDatabase/1.json` … `6.json`.

---

## 5. Seguridad, criptografía y actualizaciones
- **`AndroidKeystoreVault`**: Clave simétrica `AES-256-GCM` no exportable almacenada en `AndroidKeyStore`, verificable en vivo desde el **Centro de Seguridad y Privacidad**.
- **`LocalBackupManager`**: Cifrado autenticado `AES-256-GCM` con `PBKDF2WithHmacSHA256` (65,536 iteraciones, salt aleatorio de 16 bytes, IV aleatorio de 12 bytes) y verificación `SHA-256` previa a la restauración.
- **Reglas de extracción Android**: `backup_rules.xml` y `data_extraction_rules.xml` exigen `disableIfNoEncryptionCapabilities="true"` en respaldos en la nube.
- **`UpdateCompatibilityChecker` + `UpdateManager`**:
  - Permite únicamente URLs `https://` hacia dominios oficiales de GitHub (`github.com`, `api.github.com`, `*.githubusercontent.com`), validando también la URL final tras redirecciones.
  - Exige coincidencia exacta de `applicationId`, `versionCode` estrictamente superior, `minSdk` compatible, hash `SHA-256` del archivo APK y coincidencia obligatoria y no vacía del certificado de firma permanente (`0e582e7eec8712c582f7a48f1ad1d473e2ba149f4ed997914574a4daeb13c109`).

---

## 6. Sistema de diseño adaptable (`ui/theme`)
- `ThemeMode` (`DARK`, `LIGHT`, `SYSTEM`) persistido en DataStore y respaldado en `LocalBackupManager`.
- `LocalAppPalette` proporciona paletas **Oscuro OLED** y **Claro Profesional** con contrastes accesibles AA/AAA en todas las pantallas y componentes compartidos, sincronizando `enableEdgeToEdge` (iconos de barra de estado y navegación) en `MainActivity`.
