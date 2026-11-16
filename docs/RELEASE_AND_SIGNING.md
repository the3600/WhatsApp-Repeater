# Guía de Firma de Producción, Actualizaciones y Publicación

Este documento detalla cómo mantener la identidad única de **WhatsApp Repeater** (`com.whatsapprepeater.android`), proteger la clave de firma y publicar nuevas versiones en **GitHub Releases** sin que los usuarios pierdan sus datos ni necesiten desinstalar la app.

---

## 1. Reglas de oro para actualizar sin desinstalar

Android permite instalar una actualización sobre una versión previa **únicamente** si se cumplen las 3 condiciones siguientes:
1. **`applicationId` idéntico**: Siempre debe ser `com.whatsapprepeater.android` (definido en [app/build.gradle.kts](../app/build.gradle.kts)).
2. **Misma clave de firma**: Todas las versiones públicas deben firmarse con **el mismo archivo `.jks`**. Si pierdes o cambias el keystore, Android rechazará la actualización.
3. **`versionCode` estrictamente superior**: Cada versión publicada debe incrementar `VERSION_CODE` en [version.properties](../version.properties).

---

## 2. Generar y resguardar la clave de firma de producción (una sola vez)

Ejecuta este comando **una sola vez** en un equipo seguro (fuera del repositorio):

```bash
keytool -genkeypair -v \
  -keystore whatsapp-repeater-release.jks \
  -alias whatsapp_repeater \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

> **IMPORTANTE**:
> - Guarda `whatsapp-repeater-release.jks` y sus contraseñas en un gestor de contraseñas o bóveda cifrada con respaldo offline.
> - **Nunca** subas `.jks`, `.keystore` ni `keystore.properties` al repositorio Git (están bloqueados en `.gitignore`).

---

## 3. Configurar GitHub Secrets para CI/CD

Codifica el keystore en Base64:
- **Linux / macOS**:
  ```bash
  base64 -w 0 whatsapp-repeater-release.jks > keystore.b64.txt
  ```
- **Windows PowerShell**:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("whatsapp-repeater-release.jks")) | Set-Content keystore.b64.txt
  ```

En tu repositorio de GitHub ve a **Settings > Secrets and variables > Actions > New repository secret** y crea estos 4 secretos:

| Secret | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Contenido completo de `keystore.b64.txt` |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del archivo `.jks` |
| `ANDROID_KEY_ALIAS` | Alias usado al crear la clave (ej. `whatsapp_repeater`) |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave privada del alias |

Luego elimina `keystore.b64.txt` de tu disco local.

---

## 4. Compilar un APK de producción localmente (opcional)

Crea un archivo `keystore.properties` en la raíz del proyecto (**no lo subas a Git**):

```properties
storeFile=C:/ruta/segura/whatsapp-repeater-release.jks
storePassword=TU_PASSWORD
keyAlias=whatsapp_repeater
keyPassword=TU_PASSWORD_ALIAS
```

Luego ejecuta:
```bash
./gradlew assembleRelease
```

Si `keystore.properties` o las variables `ANDROID_KEYSTORE_*` no están configuradas, Gradle bloqueará `assembleRelease` intencionalmente para impedir que se publique una versión firmada con la clave de depuración.

---

## 5. Checklist antes de publicar una nueva versión

1. **Incrementar versión** en [version.properties](../version.properties):
   - `1.0.0` → `VERSION_CODE=1`
   - `1.1.0` → `VERSION_CODE=2`
   - `1.2.0` → `VERSION_CODE=3`
   - `2.0.0` → `VERSION_CODE=4`
2. **Si cambiaste entidades Room**:
   - Incrementa `AppDatabase.CURRENT_VERSION`.
   - Añade una `Migration(old, new)` explícita en `AppDatabase` (nunca uses `fallbackToDestructiveMigration()`).
   - Agrega un caso de prueba en `DatabaseMigrationTest.kt` verificando conservación de registros previos.
3. **Ejecutar pruebas**:
   ```bash
   ./gradlew test
   ```
4. **Publicar etiqueta en GitHub**:
   ```bash
   git add version.properties
   git commit -m "chore: release v1.1.0"
   git tag v1.1.0
   git push origin main --tags
   ```

El workflow [android-ci.yml](../.github/workflows/android-ci.yml) ejecutará todas las pruebas, compilará y firmará el APK con tus secretos, validará `applicationId`, `versionCode` y firma, calculará el `SHA-256`, generará `version-metadata.json` y publicará todo en **GitHub Releases**.

