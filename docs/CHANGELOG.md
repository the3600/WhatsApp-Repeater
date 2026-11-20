# Changelog — WhatsApp Repeater

Todas las compilaciones de desarrollo en `main` se publican automáticamente como **Pre-release** firmadas con el certificado permanente de producción (`com.whatsapprepeater.android`, `0e582e7e...109`).

---

## [1.0.1-build.10] — Evolución 2.0 (Etapa B: Inteligencia Local, Centro de Seguridad, Analíticas, Tema Claro/Oscuro y Documentación)
### Añadido
- **Motor de Inteligencia Local (`LocalIntelligenceEngine`)**: Análisis determinista 100% en dispositivo que detecta programaciones bloqueadas sin Accesibilidad, duplicados cercanos, solapamientos (< 3 min), variables dinámicas sin resolver, restricciones de batería en HyperOS/POCO, alarmas exactas o notificaciones faltantes, y fallos recientes de verificación; integrado en el Panel de Inicio con acciones directas.
- **Centro de Seguridad y Privacidad (`SecurityPrivacyScreen` / `SecurityPrivacyViewModel`)**: Auditoría en vivo de `AndroidKeystoreVault` (AES-256-GCM), firma SHA-256 instalada con copia rápida, transparencia de permisos activos, política de cifrado/red/retención, limpieza de caché APK, borrado de historiales y restablecimiento total con confirmación de consecuencias.
- **Sistema de Tema Adaptable (`ThemeMode`: Oscuro OLED, Claro y Sistema)**: Paleta clara de alto contraste (`LightAppPalette`) propagada a toda la aplicación mediante `LocalAppPalette`, persistida en DataStore, sincronizada con barras del sistema (`enableEdgeToEdge`) e incluida en respaldos cifrados.
- **Analíticas Locales Ampliadas**: Contador de programaciones creadas, estimación local de tiempo ahorrado (claramente etiquetada como estimación orientativa) y agrupación de errores frecuentes registrados.
- **Documentación 2.0**: Actualización integral de `README.md`, nueva guía técnica `docs/ARCHITECTURE.md` y este `docs/CHANGELOG.md`.
- **Pruebas nuevas**: `LocalIntelligenceEngineTest`, `LocalBackupManagerTest` y pruebas Compose para `SecurityPrivacyScreen` y `ThemeMode.LIGHT`.

---

## [1.0.1-build.9] — Evolución 2.0 (Etapa A: Hardening DevSecOps, Android Keystore y Actualizador Endurecido)
### Añadido
- **Workflow DevSecOps** (`.github/workflows/security.yml`): escaneo de secretos con Gitleaks (`.gitleaks.toml`), validación de archivos prohibidos en Git, Android Lint y análisis estático **CodeQL (`java-kotlin`)**.
- **Dependabot** (`.github/dependabot.yml`), política `SECURITY.md`, `.gitignore` endurecido, hook local `.githooks/pre-commit` y script `scripts/secret-scan.ps1`.
- **`AndroidKeystoreVault`**: Bóveda criptográfica local respaldada por `AndroidKeyStore` (`AES/GCM/NoPadding`, 256 bits no exportable) con `verifyHealth()`.
### Corregido / Endurecido
- **`UpdateCompatibilityChecker` y `UpdateManager`**: Validación estricta de URLs `https://` exclusivamente hacia hosts oficiales de GitHub (incluyendo destino post-redirección) y rechazo obligatorio si falta o no coincide la huella SHA-256 del certificado de firma del APK descargado.
- **`data_extraction_rules.xml`**: Exigencia de cifrado extremo a extremo (`disableIfNoEncryptionCapabilities="true"`) en respaldos en la nube.

---

## [1.0.1-build.8] — Editor Avanzado, Biblioteca Portable y Diagnóstico Ampliado
- Selección múltiple con acciones en lote y copia/compartición por mensaje en el Editor.
- Ordenamiento inteligente y exportación/importación JSON portable libre de contactos en Biblioteca Inteligente (`TemplateBundleCodec`).
- Diagnóstico ampliado con estado de alarmas exactas, Motor 1 y verificación en vivo de la huella SHA-256 instalada.

---

## [1.0.1-build.7] — Centro de Notificaciones Activas, Inicio Renovado y Centro de Actividad
- Notificación consolidada de programaciones activas con cronómetro nativo eficiente en batería (`ScheduleCountdownFormatter`) y acciones rápidas (Pausar / Reanudar / Cancelar / Abrir).
- Panel de Inicio renovado con cuenta regresiva en vivo, KPIs y accesos directos.
- Centro de Actividad unificado (`ActivityCenterScreen`).

---

## [1.0.1-build.6] — Programador Visual Material 3 y Endurecimiento de `ContactVerifier`
- Reconstrucción del Programador con `DatePicker`, `TimePicker`, días personalizados de la semana, fecha/hora de fin y migración Room v6 (`MIGRATION_5_6`).
- Endurecimiento de `ContactVerifier` (BiDi/NBSP, `+521`/`+52`, exclusión del `RecyclerView`, guardia contra contactos similares) y de `AndroidAccessibilityDriver` (límite de 1,500 nodos y `node.refresh()`).

---

## [1.0.1-build.1 – build.5] — Base Modular, Motores 1 y 2 y CI/CD Firmado
- Arquitectura modular multi-módulo, Motor 1 (Accesibilidad), Motor 2 (Autorespuestas), Room v1→v5, respaldo cifrado AES-256-GCM y publicación automática firmada en GitHub Releases con verificación SHA-256 del certificado permanente.
