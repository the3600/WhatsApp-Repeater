# WhatsApp Repeater 2.0 (Android Nativo)

Aplicación nativa para Android desarrollada en **Kotlin**, **Jetpack Compose** y **Material Design 3**, diseñada para redactar, organizar, programar, repetir y automatizar mensajes en WhatsApp y WhatsApp Business de forma **100% local, transparente, segura y bajo control explícito del usuario**.

---

## Arquitectura modular

El proyecto está organizado en 6 módulos Gradle desacoplados:

- **`:core:model`**: Modelos de dominio (`ContactInfo`, `MessageMode`, `IntervalRange`, `SafetyLimits`, `AutomationConfig`, `AutomationState`, `ExecutionRecord`, `SavedMessageList`, `ScheduledTask`, `AutoReplyRule`, `AutoReplySettings`, `AppSettings`, `ThemeMode`), motor de variables dinámicas (`DynamicVariablesEngine`), formateador de cuenta regresiva (`ScheduleCountdownFormatter`) y asesor proactivo 100% local (`LocalIntelligenceEngine`).
- **`:core:automation`**: **Motor 1 (Automatización controlada por Accesibilidad)**:
  - `AutomationEngine`: Orquesta el flujo completo con soporte para `INICIAR`, `PAUSAR`, `REANUDAR`, `DETENER` y detención inmediata por seguridad.
  - `ContactVerifier`: Verificación estricta de destinatario antes de escribir (normalización Unicode/BiDi/NBSP, equivalencias México `+521`/`+52`, protección contra contactos similares y exclusión del `RecyclerView` del chat).
  - `WhatsAppNavigator`: Reconocimiento resiliente de pantallas (`OpenConversation`, `HomeChatList`, `SearchMode`, `UnexpectedOrError`, `ExternalApp`) sin depender de un único ID frágil.
  - `MessageQueueManager` y `RandomIntervalGenerator`.
- **`:core:autoresponder`**: **Motor 2 (Autorespuestas por Notificación)**:
  - `AutoReplyEngine`, `RuleMatcher`, `ScheduleEvaluator`, `ContactFilterEvaluator` y protección anti-bucles y límites por conversación mediante `RemoteInput` oficial de Android.
- **`:core:data`**: Persistencia local con migraciones verificadas y respaldo previo:
  - `AppDatabase` (`version = 6`, migraciones secuenciales `MIGRATION_1_2` hasta `MIGRATION_5_6` sin borrado destructivo, esquemas JSON exportados en `core/data/schemas/`) + `DatabaseBackupManager` (respaldos SQLite automáticos pre-migración).
  - `LocalBackupManager`: Exportación/importación local cifrada con **AES-256-GCM**, derivación `PBKDF2WithHmacSHA256` (65,536 iteraciones) y verificación `SHA-256`.
  - `TemplateBundleCodec`: Exportación/importación portable de plantillas de biblioteca (límite 2 MB, cero contactos ni teléfonos incluidos).
  - Repositorios Room (`HistoryRepository`, `SavedConfigRepository`, `ScheduledTaskRepository`, `AutoReplyRepository`) y DataStore (`SettingsRepository`, `AutoReplySettingsRepository`, `ContactsRepository`).
- **`:core:updater`**: Comparador semántico, parser de GitHub Releases, validación estricta de URLs HTTPS de GitHub, verificador criptográfico `SHA-256` y validador de compatibilidad (`applicationId`, `versionCode`, firma permanente obligatoria y `minSdk`).
- **`:app`**: UI Jetpack Compose Material 3 con **Tema Oscuro OLED / Claro / Sistema**, **Panel de Inicio**, **Programador Visual** (`DatePicker`, `TimePicker`, frecuencias y finalización), **Centro de Notificaciones de Programaciones Activas**, **Centro de Actividad**, **Biblioteca Inteligente**, **Editor Avanzado con selección múltiple**, **Centro de Seguridad y Privacidad** (con `AndroidKeystoreVault`), **Diagnóstico**, **Analíticas Locales Privadas** y **Actualizador integrado**.

Consulta los detalles técnicos completos en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) y el historial de versiones en [docs/CHANGELOG.md](docs/CHANGELOG.md).

---

## Seguridad, privacidad y DevSecOps

- **Procesamiento 100% local**: No existen servidores analíticos, telemetría ni rastreadores. Todos los datos permanecen en Room y DataStore locales.
- **Bóveda criptográfica local**: `AndroidKeystoreVault` genera y verifica claves `AES-256-GCM` no exportables respaldadas por hardware/TEE.
- **Respaldo seguro**:
  - Copias manuales cifradas con `AES-256-GCM` + `PBKDF2-HMAC-SHA256` + verificación `SHA-256`.
  - Respaldo automático Android restringido mediante `disableIfNoEncryptionCapabilities="true"`.
- **Alcance mínimo**: El Servicio de Accesibilidad se declara exclusivamente para `com.whatsapp` y `com.whatsapp.w4b`, exige activación manual, verificación estricta de destinatario y notificación persistente con botón **DETENER**.
- **Sin evasión ni credenciales**: Nunca solicita contraseñas/sesiones de WhatsApp ni intenta evadir CAPTCHAs, bloqueos o verificaciones.
- **Seguridad del repositorio**: Workflow continuo `.github/workflows/security.yml` (Gitleaks, verificación de archivos prohibidos, Android Lint y CodeQL `java-kotlin`), Dependabot semanal y hook local `.githooks/pre-commit`. Consulta [SECURITY.md](SECURITY.md).

---

## Identidad permanente y actualizaciones sin perder datos

- **`applicationId`**: `com.whatsapprepeater.android` (fijo entre todas las versiones).
- **`namespace`**: `com.whatsapprepeater.app`.
- **Certificado permanente SHA-256**: `0e582e7eec8712c582f7a48f1ad1d473e2ba149f4ed997914574a4daeb13c109`.
- **Actualizador integrado**: Consulta GitHub Releases bajo demanda únicamente por HTTPS, valida SHA-256 del APK, `applicationId`, `versionCode` creciente y coincidencia exacta de firma permanente antes de invocar el instalador oficial de Android, conservando plantillas, programaciones, historial y preferencias.
- **Publicación automática**: Cada push a `main` validado en CI compila y publica automáticamente una **Pre-release** firmada en GitHub Releases con `versionCode` creciente (`v1.0.1-build.N`). Los tags estables (`vX.Y.Z`) se crean únicamente bajo decisión explícita. Consulta [docs/RELEASE_AND_SIGNING.md](docs/RELEASE_AND_SIGNING.md).

---

## Requisitos y compilación local

- **Android 9.0 (API 28) o superior** (`minSdk = 28`, `targetSdk = 35`, `compileSdk = 36`).
- **JDK 17** y **Android SDK 36**.
- WhatsApp (`com.whatsapp`) o WhatsApp Business (`com.whatsapp.w4b`) instalado y con sesión iniciada por el usuario.

```bash
# Ejecutar todas las pruebas unitarias (JVM)
./gradlew test

# Ejecutar Android Lint
./gradlew lintDebug

# Compilar APK Debug y compilar pruebas instrumentadas Compose
./gradlew assembleDebug compileDebugAndroidTestKotlin

# Verificar ausencia de secretos localmente (Windows PowerShell)
./scripts/secret-scan.ps1
```

---

## Recomendaciones para POCO / Xiaomi / HyperOS

1. **Ajustes restringidos (Android 13–15)**: Si instalaste el APK manualmente, abre **Ajustes > Aplicaciones > WhatsApp Repeater > menú (⋮) > Permitir ajustes restringidos** antes de activar Accesibilidad o Acceso a Notificaciones.
2. **Batería y segundo plano**: Configura **Ahorro de batería → Sin restricciones**, activa **Inicio automático** y fija la app con el candado en la vista de aplicaciones recientes para evitar que HyperOS desconecte Accesibilidad o retrase alarmas del Programador.
3. **Alarmas exactas y notificaciones**: Concede **Alarmas y recordatorios** y **Notificaciones** para recibir la cuenta regresiva en vivo y los controles rápidos (Pausar / Reanudar / Cancelar).

---

## Limitaciones conocidas de Android y WhatsApp

- **Pantalla bloqueada**: Android impide automatizar la UI mientras el dispositivo está bloqueado con PIN/huella; si una programación dispara con pantalla bloqueada, el sistema muestra una notificación de intervención con botón de ejecución inmediata.
- **Cambios de UI en WhatsApp**: Si una actualización de WhatsApp altera significativamente los nodos de accesibilidad o la cabecera no coincide con el destinatario configurado, Motor 1 se detiene inmediatamente por seguridad y muestra un diálogo/notificación de intervención en lugar de enviar al chat equivocado.
- **Validación en dispositivo físico**: Todas las pruebas unitarias, migraciones Room, Lint, CodeQL y firma Release se validan automáticamente en CI; la interacción en vivo con WhatsApp sobre hardware real (p. ej. POCO X6 Pro 5G / HyperOS) debe verificarse siguiendo [docs/TESTING_PROTOCOL_AND_RELEASE_CHECKLIST.md](docs/TESTING_PROTOCOL_AND_RELEASE_CHECKLIST.md).
