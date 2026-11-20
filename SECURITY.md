# Política de Seguridad — WhatsApp Repeater

## Arquitectura de Seguridad y Privacidad

WhatsApp Repeater está diseñado bajo el principio **100 % local y sin servidores propios**:

- **Sin transmisión de contenido**: los mensajes, listas, plantillas, contactos, programaciones e historial se almacenan únicamente en el dispositivo del usuario.
- **Cifrado local y respaldos**:
  - Los respaldos portables se cifran con **AES-256-GCM** derivado mediante **PBKDF2WithHmacSHA256** (120 000 iteraciones, salt de 16 bytes, IV de 12 bytes) e integridad SHA-256.
  - Los secretos de verificación local usan **Android Keystore** (`AES/GCM/NoPadding`, clave no exportable almacenada en el almacén seguro del sistema).
- **Verificación de actualizaciones**:
  - Solo se descargan artefactos mediante **HTTPS** desde dominios oficiales de GitHub (`github.com`, `*.githubusercontent.com`).
  - El actualizador comprueba independientemente la integridad **SHA-256**, `applicationId`, `versionCode` ascendente y **huella SHA-256 del certificado de firma del APK**.
- **Identidad permanente de producción**:
  - `applicationId`: `com.whatsapprepeater.android`
  - Certificado público SHA-256 esperado (producción): `0e582e7eec8712c582f7a48f1ad1d473e2ba149f4ed997914574a4daeb13c109`
  - El keystore privado nunca se versiona en Git; reside únicamente en almacenamiento seguro del desarrollador y en GitHub Actions Secrets cifrados.

## Controles del Repositorio

- `.gitignore` bloquea keystores (`.jks`, `.keystore`, `.p12`), propiedades de firma, bases SQLite, respaldos y archivos `.env`.
- Pre-commit local disponible en `.githooks/pre-commit` (`git config core.hooksPath .githooks`) y `scripts/secret-scan.ps1`.
- Workflow automático `.github/workflows/security.yml` con **Gitleaks**, **Android Lint** y **CodeQL (Java/Kotlin)** con permisos mínimos.
- **Dependabot** configurado en `.github/dependabot.yml`.

## Divulgación Responsable de Vulnerabilidades

Si descubres una vulnerabilidad de seguridad (fuga de datos, fallo en verificación de destinatario, fallo en verificación de actualizaciones o exposición involuntaria de credenciales):

1. **No publiques** el exploit ni datos sensibles en Issues públicos.
2. Reporta el hallazgo mediante **GitHub Security Advisories** privados del repositorio (`Security → Advisories → Report a vulnerability`) o contactando directamente al propietario del repositorio.
3. Incluye versión afectada (`versionName` y `versionCode`), pasos reproducibles y alcance estimado.
4. Si una credencial llegara a filtrarse, debe revocarse/rotarse inmediatamente antes de cualquier otra acción; nunca se debe reemplazar silenciosamente la clave de firma de producción sin aviso explícito.
