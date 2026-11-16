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

Ejecuta estos pasos **una sola vez** fuera del repositorio (`C:\Users\Yojan\whatsapp-signing`, nunca dentro de `C:\Desarrollos\Whatsapp`):

### Windows PowerShell
```powershell
New-Item -ItemType Directory -Path "$env:USERPROFILE\whatsapp-signing" -Force
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot\bin\keytool.exe" -genkeypair -v `
  -keystore "$env:USERPROFILE\whatsapp-signing\whatsapp-repeater-release.jks" `
  -alias whatsapp_repeater `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

> **Copia de seguridad obligatoria (2 ubicaciones protegidas)**:
> 1. Copia `whatsapp-repeater-release.jks` a una unidad USB cifrada o disco externo desconectado.
> 2. Guarda una segunda copia + las dos contraseñas (`storePassword` y `keyPassword`) y el alias (`whatsapp_repeater`) en un gestor de contraseñas cifrado (Bitwarden, 1Password, KeePassXC).
> - **Nunca** subas `.jks`, `.keystore`, `.b64.txt` ni `keystore.properties` a Git (están bloqueados en [.gitignore](../.gitignore)).

---

## 3. Configurar los 4 GitHub Secrets para CI/CD

Codifica el keystore en Base64 **sin BOM ni saltos de línea** (Windows PowerShell):

```powershell
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\whatsapp-signing\whatsapp-repeater-release.jks"))
[IO.File]::WriteAllText("$env:USERPROFILE\whatsapp-signing\keystore.b64.txt", $b64)
Get-Content "$env:USERPROFILE\whatsapp-signing\keystore.b64.txt" | Set-Clipboard
```

En GitHub (`https://github.com/the3600/WhatsApp-Repeater/settings/secrets/actions`) pulsa **New repository secret** y crea estos 4 secretos:

| Secret | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Contenido exacto de `keystore.b64.txt` (ya copiado al portapapeles) |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del almacén `.jks` que ingresaste en `keytool` |
| `ANDROID_KEY_ALIAS` | `whatsapp_repeater` |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave del alias (o la misma si pulsaste Enter) |

Después elimina el archivo temporal `keystore.b64.txt`:
```powershell
Remove-Item "$env:USERPROFILE\whatsapp-signing\keystore.b64.txt" -Force
```

---

## 4. Verificar la firma en GitHub Actions (sin publicar Release todavía)

1. Abre **Actions → Android CI & Signed Release** en GitHub.
2. Pulsa **Run workflow** (`workflow_dispatch`) sobre `main`.
3. Confirma que el job `release-signed` decodifica el keystore, compila `./gradlew assembleRelease`, verifica `apksigner verify` y `com.whatsapprepeater.android`, y genera el artefacto `WhatsAppRepeater-release-signed` **sin crear ningún Release público**.

---

## 5. Compilar un APK Release localmente (opcional)

Crea `keystore.properties` en la raíz del proyecto (**ignorado por `.gitignore`, nunca subir a Git**):

```properties
storeFile=C:/Users/Yojan/whatsapp-signing/whatsapp-repeater-release.jks
storePassword=TU_CONTRASEÑA_DEL_ALMACEN
keyAlias=whatsapp_repeater
keyPassword=TU_CONTRASEÑA_DE_LA_CLAVE
```

Ejecuta:
```powershell
.\gradlew.bat assembleRelease
```

Si `keystore.properties` o las variables `ANDROID_KEYSTORE_*` faltan, Gradle detendrá `assembleRelease` intencionalmente para evitar publicar con clave de depuración.

---

## 6. Checklist para publicar futuras versiones

1. Incrementar `VERSION_CODE` y `VERSION_NAME` en [version.properties](../version.properties):
   - `1.0.0` → `VERSION_CODE=1`
   - `1.1.0` → `VERSION_CODE=2`
   - `1.2.0` → `VERSION_CODE=3`
   - `2.0.0` → `VERSION_CODE=4`
2. Si cambian entidades Room, incrementa `AppDatabase.CURRENT_VERSION`, agrega `Migration(old, new)` explícita y pruébala en `DatabaseMigrationTest.kt`.
3. Ejecuta `./gradlew.bat test` y valida en teléfono físico.
4. Cuando decidas publicar:
   ```bash
   git add version.properties
   git commit -m "chore: release v1.0.0"
   git tag v1.0.0
   git push origin main --tags
   ```
