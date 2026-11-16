# Protocolo de Pruebas en Teléfono Físico, Firma y Actualización

## 1. Protocolo de pruebas en teléfono Android físico (Android 9+)

Antes de declarar la app lista para distribución pública, ejecuta cada escenario en un dispositivo físico con **WhatsApp** instalado (usa un contacto de prueba propio):

| # | Caso de prueba | Pasos en el teléfono | Resultado esperado |
|---|---|---|---|
| 1 | **Instalación limpia y cero datos falsos** | Instalar APK y abrir por primera vez. | Inicio, Historial y Lista de mensajes completamente vacíos. |
| 2 | **Permisos bajo demanda** | Entrar en *Seleccionar Destinatario*. | Solo en ese momento solicita `READ_CONTACTS`; en *Inicio* no se pide. |
| 3 | **Selección de contacto real e ingreso E.164** | Buscar contacto real de agenda y confirmar; probar también ingreso manual `+código...`. | Muestra nombre y número exacto; exige confirmación antes de avanzar. |
| 4 | **Activación de Accesibilidad** | Ir a *Permisos y Accesibilidad* → *Abrir ajustes de Android* → activar servicio. | En Android 13–15 habilitar primero *Permitir ajustes restringidos* en Info de app. Insignia pasa a **Activo**. |
| 5 | **Lista de mensajes diferentes** | Elegir *Secuencia de mensajes múltiples*, agregar 3 mensajes distintos, reordenar y editar uno. | Envía los 3 en el orden exacto una sola vez y verifica vaciado de caja tras pulsar Enviar. |
| 6 | **Repetición de un mismo mensaje** | Elegir *Repetir mensaje único*, escribir texto y fijar 3 repeticiones. | Envía exactamente 3 veces respetando la cantidad configurada. |
| 7 | **Intervalos aleatorios** | Configurar mínimo `15s` y máximo `35s`. | La cuenta regresiva entre envíos varía dentro de `[15s, 35s]`. |
| 8 | **Verificación de destinatario** | Abrir otro chat distinto manualmente mientras inicia o cambia la ventana. | El motor detecta discrepancia en la cabecera y **detiene inmediatamente** sin escribir ni enviar. |
| 9 | **Detención de emergencia** | Pulsar *DETENER INMEDIATAMENTE* → *DETENER AHORA* (o botón *DETENER* de la notificación). | Cancela corrutina, purga pendientes y registra lote como `Interrumpido` en Historial. |
| 10 | **Interrupción segura ante pantallas inesperadas** | Abrir pantalla ajena, diálogo de número sin WhatsApp o bloqueo/verificación. | Detiene con `FAILED_SAFETY_CHECK` sin intentar eludir controles. |
| 11 | **Persistencia tras cierre** | Cerrar por completo la app (recientes) y reabrir. | Historial, trazas, listas guardadas, borrador e intervalos siguen intactos. |
| 12 | **Copia de seguridad cifrada SAF** | Ir a *Ajustes* → *Exportar copia (.json cifrado)*, borrar historial y *Restaurar*. | Verifica SHA-256, descifra con la clave y restaura historial, listas y preferencias. |

---

## 2. Cómo configurar los 4 secretos de firma y publicar un Release firmado

1. **Generar keystore estable de producción** (una única vez, fuera del repo):
   ```bash
   keytool -genkeypair -v -keystore whatsapp-repeater-release.jks -alias whatsapp_repeater -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Codificar a Base64** (Windows PowerShell):
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("whatsapp-repeater-release.jks")) | Set-Content keystore.b64.txt
   ```
3. **Configurar los 4 secretos en GitHub** (`https://github.com/the3600/WhatsApp-Repeater/settings/secrets/actions`):
   - `ANDROID_KEYSTORE_BASE64`: contenido de `keystore.b64.txt`
   - `ANDROID_KEYSTORE_PASSWORD`: contraseña del `.jks`
   - `ANDROID_KEY_ALIAS`: `whatsapp_repeater`
   - `ANDROID_KEY_PASSWORD`: contraseña del alias
4. **Ejecutar el workflow y publicar una versión**:
   - Cada `push` a `main` ejecuta pruebas unitarias, migraciones Room y `assembleDebug`.
   - Para generar el APK release firmado, su `.sha256`, `version-metadata.json` y publicarlos en **GitHub Releases**:
     1. Actualiza `version.properties` (`VERSION_CODE` incremental, `VERSION_NAME` semántico).
     2. Crea y sube la etiqueta:
        ```bash
        git add version.properties
        git commit -m "chore: release v1.0.0"
        git tag v1.0.0
        git push origin main --tags
        ```

---

## 3. Protocolo controlado de prueba de actualización (`v1.0.0` → `v1.1.0`)

> **Nota sobre repositorio privado**: La API pública de GitHub (`https://api.github.com/repos/the3600/WhatsApp-Repeater/releases/latest`) devuelve `HTTP 404` si el repositorio es **privado** sin token. Para que el actualizador integrado en el teléfono descargue directamente sin credenciales privadas, publica los Releases desde un repositorio público de distribución (o cambia temporalmente el origen en *Actualizaciones*).

1. Instala el APK **release firmado** de `v1.0.0` (`versionCode = 1`) en un teléfono físico.
2. Crea una lista de mensajes guardada, ejecuta un envío de prueba (o registra datos reales) y ajusta tus intervalos predeterminados en *Ajustes*.
3. Incrementa `version.properties` a `VERSION_CODE=2`, `VERSION_NAME=1.1.0`, haz commit y publica el tag `v1.1.0` para que GitHub Actions firme y publique el Release con la **misma clave de producción**.
4. En el teléfono con `v1.0.0`, abre **Ajustes → Actualizaciones → Buscar actualizaciones**.
5. Verifica que muestre versión actual `1.0.0`, nueva versión `1.1.0` y sus novedades.
6. Pulsa **Descargar APK y verificar SHA-256** (comprueba automáticamente SHA-256, `com.whatsapprepeater.android`, `versionCode = 2 > 1` y coincidencia de firma).
7. Habilita *Instalar aplicaciones desconocidas* si Android lo solicita y confirma en el instalador oficial de Android (**sin desinstalar** `v1.0.0`).
8. Abre `v1.1.0` y confirma que se conservaron intactos: historial, listas guardadas, borrador activo, contacto de referencia y preferencias DataStore.

