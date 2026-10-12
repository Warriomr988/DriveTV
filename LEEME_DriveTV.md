# DriveTV v0.4.1 — Etapas 1 a 6 (LEEME para Termux)

App Google TV: login con Google → explorar Google Drive → reproducir vídeos
directo desde Drive con Media3/ExoPlayer, sin descargar el archivo completo.

Paquete: `com.panda.drivetv` · Kotlin + Compose material3 con foco TV propio · Media3 1.4.1

## Novedades v0.4.1
- **Foco D-pad en los botones del reproductor**: al mostrarse los controles, el
  foco va al botón play/pausa (borde blanco de 4dp) y las flechas navegan
  entre los botones sin adelantar el vídeo. Con los controles ocultos, las
  flechas siguen adelantando/atrasando ±10 s. También los botones de la
  pantalla de error toman foco.
## Novedades v0.4.0
- **Icono propio de la app**: TV con botón play verde sobre fondo azul oscuro
  (icono adaptativo + PNGs clásicos para Android 5–7).
## Novedades v0.3.1
- **Buffer ampliado para remuxes 4K de alto bitrate**: el reproductor ahora
  acumula hasta 90 s de vídeo (tope 384 MB de RAM) para pasar los picos de
  bitrate sin pausarse. El arranque tarda unos segundos más. Si el bitrate
  *promedio* supera lo que entrega la conexión, ningún buffer lo salva: ahí
  la solución es más ancho de banda o un archivo más liviano.
## Novedades v0.3.0
- **Botón de proporción** 🖥 en el reproductor: alterna Ajustar (con barras) →
  Llenar (ocupa toda la pantalla, recorta) → Zoom. El botón muestra el modo
  activo.
## Novedades v0.2.0
- **Portada con vídeos**: en la página principal, además de las carpetas, hay
  sección "🎬 Todos los vídeos" con todos los vídeos del Drive (búsqueda por
  `mimeType`, sin entrar carpeta por carpeta). En subcarpetas se sigue viendo
  el contenido normal.
- **Indicador de foco más visible**: borde blanco de 4dp + escala al enfocar
  con el D-pad.
- **Panel de audio/subtítulos**: con el panel abierto, el D-pad navega el
  panel (arriba/abajo + OK para elegir); ya no adelanta el vídeo al pulsar
  derecha. El foco entra solo al primer botón y vuelve al reproductor al
  cerrar. Back cierra el panel.
- El error del botón "Continuar con Google" ahora muestra mensaje en vez de
  quedarse en silencio.

> **Nota técnica:** la guía pide `androidx.tv:tv-material`, pero Google republicó
> todas las librerías `androidx.tv` exigiendo `compileSdk 35` (verificado en sus
> AAR, hasta el alpha12) y el aapt2 de Termux no puede con API 35. La UI de TV
> usa material3 estándar + `ui/tv/TvComponents.kt` (borde y escala al enfocar
> con el D-pad). El resto de la arquitectura de la guía se mantiene intacta.

---

## 1. Análisis de la guía (qué se implementó de cada etapa)

| Etapa | Guía | Implementado en v0.1.0 |
|---|---|---|
| 1. Proyecto base | App Android TV mínima instalable | ✅ `MainActivity` + Compose TV, manifest con `LEANBACK_LAUNCHER`, tema oscuro |
| 2. Google Login/OAuth | AuthManager + TokenStore, sin tokens en Activities | ✅ `AuthManager` (Google Sign-In + `GoogleAuthUtil`, scope `drive.readonly`) + `PrefsTokenStore`. Extra: **token manual** para probar sin Google Cloud |
| 3. Drive API | DriveApi + DriveRepository + modelos | ✅ `HttpDriveApi` (Drive v3 con `HttpURLConnection` + `org.json`, sin dependencias extra), `DriveFile`, `DriveRepository`. Errores 401/403/404/429/5xx tipados |
| 4. Drive Explorer | UI TV con D-pad | ✅ `FilesScreen` (TvLazyColumn), `FileCard` (📁/🎬), `FilesViewModel` con pila de carpetas, Back para subir |
| 5. Streaming real | `AuthenticatingDataSource` + Media3 + Range/seek | ✅ `AuthenticatingDataSource` (inyecta `Bearer` en cada `open`, el seek reabre con el header), `Media3Player` con buffers 20s/60s de la guía |
| 6. Player avanzado | Controles D-pad, reanudación, subtítulos, audio, capacidades, MPV | ✅ Controles D-pad (OK=play/pausa, ←/→=∓10s, ↑/↓=controles, Back=salir), `ResumeManager` (fileId+posición), `TrackManager` (audio/subtítulos), `DeviceCapabilities` (MediaCodecList+HDR), `MpvPlayer` como stub documentado |

## 2. Decisiones que difieren de la guía (y por qué)

1. **compileSdk/targetSdk 34 en vez de 35**: el aapt2 de Termux es v2.19 y no lee el
   `android.jar` de API 35 (misma limitación que FiltroLlamadas/PS4Bridge).
2. **Drive API con `HttpURLConnection` + `org.json`** en vez del cliente
   `google-api-services-drive`: cero dependencias extra, menos peso y menos
   puntos de fallo en Termux. La interfaz `DriveApi` queda lista para cambiar
   la implementación sin tocar la UI.
3. **Token manual (modo prueba)**: en `SignInScreen` puedes pegar un access token
   y probar las etapas 3–6 sin crear proyecto en Google Cloud.
4. **Sin Hilt**: DI manual en `DriveTVApp` (singletons). Menos dependencias.
5. **401 en reproducción**: se muestra "sesión expirada" con botones Reintentar /
   "Iniciar sesión de nuevo" (la política refresh+retry de la guía, §5.7).

## 3. Compilar en Termux (Panda)

```bash
cd ~
# Descarga drivetv-v0.4.1.zip del Drive (carpeta DRIVETV) y luego:
rm -rf ~/drivetv-tmp && mkdir -p ~/drivetv-tmp
unzip ~/storage/downloads/drivetv-v0.4.1.zip -d ~/drivetv-tmp
rm -rf ~/panda-code-agent/projects/DriveTV
mv ~/drivetv-tmp/DriveTV ~/panda-code-agent/projects/DriveTV
rm -rf ~/drivetv-tmp
cd ~/panda-code-agent/projects/DriveTV
echo "sdk.dir=/data/data/com.termux/files/home/android-sdk" > local.properties
cd ~/panda-code-agent
python3 panda.py projects/DriveTV
```

Dentro de Panda:

```
compilar
```

La primera compilación descarga Kotlin, Compose y Media3 (~200–300 MB);
tarda varios minutos. Al final debe decir `BUILD SUCCESSFUL` y dejar el APK en:

```
projects/DriveTV/app/build/outputs/apk/debug/app-debug.apk
```

> Si Panda pide reparar algo, deja que lo intente; si falla, mándame el error.

## 4. Exportar el APK a Descargas e instalar

```bash
APK=~/panda-code-agent/projects/DriveTV/app/build/outputs/apk/debug/app-debug.apk
cp "$APK" ~/storage/downloads/DriveTV-v0.4.1.apk
ls -lh ~/storage/downloads/DriveTV-v0.4.1.apk
```

Instala `DriveTV-v0.4.1.apk` en el Google TV (o en el teléfono para probar la UI).

## 5. Probar sin Google Cloud (token manual) — etapas 3, 4 y 5

1. En el navegador ve a **OAuth 2.0 Playground**: `https://developers.google.com/oauthplayground`
2. En "Select & authorize APIs" busca `drive.readonly`, márcalo y pulsa **Authorize APIs**.
3. Inicia sesión con tu Google y acepta. Pulsa **Exchange authorization code for tokens**.
4. Copia el **Access token** (empieza por `ya29.`).
5. En DriveTV → pégalo en el campo de token manual → **Usar este token**.
6. Ya puedes explorar tu Drive y reproducir un vídeo.

El token dura ~1 hora; cuando caduque, repite los pasos o usa el login de Google.

## 6. Login con Google "de verdad" (opcional, etapa 2 completa)

1. Crea un proyecto en **Google Cloud Console** y habilita **Google Drive API**.
2. Pantalla de consentimiento OAuth: tipo Externa, scope `.../auth/drive.readonly`.
3. Credenciales → **ID de cliente OAuth** → tipo **Web** → copia el client ID.
4. En Termux, crea `~/panda-code-agent/projects/DriveTV/local.properties`:
   ```
   drivetv.webClientId=TU_CLIENT_ID_WEB
   ```
   (Nunca va en el repo; ya está en `.gitignore`.)
5. Recompila. El botón "Continuar con Google" pedirá la cuenta y obtendrá el
   token solo.

## 7. Criterios de aceptación (de la guía)

- [ ] Etapa 1: compila, se instala en Google TV, abre y muestra la navegación.
- [ ] Etapa 2: login con Google o token manual; reiniciar no pide login de nuevo.
- [ ] Etapa 3: la raíz de Drive se lista (carpetas y vídeos con nombre/tamaño).
- [ ] Etapa 4: entrar/salir de carpetas solo con el mando (D-pad + Back).
- [ ] Etapa 5: un vídeo empieza sin descargar todo; seek adelante/atrás funciona.
- [ ] Etapa 6: pausa con OK, reanuda donde quedó, subtítulos/audio seleccionables,
      errores con mensaje claro (sin cierres bruscos).

## 8. Límites conocidos de v0.4.1

- 4K/HDR: depende del TV (ver Ajustes → capacidades). Probar en el TV real.
- MPV: solo está la frontera (`MpvPlayer` stub); Media3 es el reproductor único.
- Subtítulos externos SRT/VTT: pendientes (`SubtitleManager` los validará).
- El token se guarda en SharedPreferences privadas (endurecer con
  EncryptedSharedPreferences en una versión posterior).
