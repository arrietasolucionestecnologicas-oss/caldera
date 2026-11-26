# AST Campo: contexto completo del proyecto

Documento para que otra persona, otra sesión de Claude o la app de presupuesto de A.S.T. puedan entender, reutilizar o integrar esta app sin haber visto la conversación en la que se construyó. Cuando algo **no está verificado**, se dice. No incluye ninguna contraseña.

- Repositorio: `arrietasolucionestecnologicas-oss/caldera` (público). La app está en `ast-campo/`. El repositorio también contiene un PWA ajeno a esta app (`index.html`, `simulador.js`, `sw.js`, `manifest.json`) que no se tocó y que se publica con GitHub Pages desde `main`.
- Versión actual: **1.3.3** (`versionCode` 7). APK firmado en `releases/AST-Campo-v1.3.3.apk`.
- Descarga: `https://raw.githubusercontent.com/arrietasolucionestecnologicas-oss/caldera/main/releases/AST-Campo-v1.3.3.apk`
- Ramas: trabajo en `ccr-ee567c0a-q1wdt0`; lo publicado está también en `main`.

---

## 1. Para qué es y quién la usa

Gerson Arrieta, de **Arrieta Soluciones Técnicas (A.S.T.)**, Barranquilla, Colombia. Hace trabajos de campo: pruebas eléctricas a transformadores, granjas solares, mantenimiento de subestaciones, tableros, generadores, bancos de condensadores, CCTV y otros. Necesita muchas fotos por trabajo para **informes técnicos**, con datos impresos (ruta, fecha y hora, coordenadas, temperatura, lugar), organizadas por proyecto y fáciles de reconocer en el computador.

Equipo del usuario: Samsung Galaxy S25 Ultra. No usa apps de Play Store por la publicidad. La app se usa en el celular, en campo, con una mano y muchas veces con las manos ocupadas.

Principios pedidos:
- Android nativo (Kotlin, Jetpack Compose, Material 3). Sin anuncios, sin cuentas, sin analítica, sin servidor propio.
- **Las fotos nunca salen del teléfono.** A internet solo salen coordenadas (ver sección 9).
- Estética de herramienta de ingeniería, tema oscuro por defecto, botones grandes (mínimo 56 dp en acciones principales).
- Textos en español de Colombia.

## 2. Qué hace (resumen funcional)

1. **Proyectos y carpetas** de profundidad ilimitada (Proyecto > subcarpeta > subcarpeta...). Crear, renombrar, mover y eliminar en cualquier nivel. Al eliminar, pregunta qué hacer con las fotos. Plantilla editable para "Subestación" (Transformadores, Tableros, Generadores, Banco de condensadores). Recuerda el último destino por proyecto. Sugerencias rápidas y numeración siguiente ("CT 5" sugiere "CT 6").
2. **Captura**: abre la cámara de Samsung por intent, aplica la marca al volver y queda lista para la siguiente. Chips de ruta destino, precisión GPS y temperatura (tocable para escribirla a mano). Contador de sesión, miniatura de la última foto (tocable para revisarla) y "Deshacer". Estado de guardado siempre visible junto al botón.
3. **Marca de agua**: solo letras blancas con sombra suave, esquina inferior izquierda, sin fondo ni barra (el panel azul marino translúcido con barra naranja es opcional en Ajustes). Líneas: ruta, fecha y hora, coordenadas con temperatura, lugar. Tamaños Pequeño, Mediano (predeterminado) y Grande. Vista previa en vivo en Ajustes.
4. **Galería**: guarda con MediaStore en `Pictures/AST Campo/<Proyecto>/<Sub>/.../` y aparece sola en la Galería, sin permisos de almacenamiento.
5. **Nombre de archivo** con la ruta y el número de foto de esa carpeta: `Granja Solar X_Pruebas eléctricas_CT 5_001.jpg`.
6. **Copia original sin marca** (activada por defecto) en el almacenamiento privado de la app.
7. **EXIF** de la foto final: GPS, fecha y hora originales, orientación, datos técnicos de la cámara y descripción (ruta en ASCII).
8. **Visor** con zoom, detalles, mover, eliminar, compartir y "Original".
9. **Ajustes**: marca de agua, plantilla de subestación, proveedor de temperatura, copia original, tema (oscuro o claro de alto contraste).

No hay: Fase 2 (importar fotos de la Cámara Samsung, exportar ZIP/PDF, notas por foto, búsqueda por fecha), sincronización, copia de seguridad, multiusuario ni nube.

## 3. Pila tecnológica y versiones

| Pieza | Versión |
|---|---|
| Android Gradle Plugin | 9.5.2 |
| Gradle (wrapper) | 9.6.0 |
| Kotlin | 2.4.20 |
| KSP | 2.3.14 |
| Compose BOM | 2026.11.01 (Material 3, icons-extended) |
| Navigation Compose | 2.10.2 |
| Room | 2.8.5 |
| ExifInterface | 1.4.2 |
| Play Services Location | 21.4.0 |
| Coroutines | 1.11.0 |
| JDK | 21 (AGP exige 17 o más) |
| `compileSdk` / `targetSdk` / `minSdk` | 37.2 / 37 / 29 |
| Build-tools | 36.0.0 |

- Paquete y `applicationId`: `co.ast.campo`. Nombre visible: "AST Campo".
- Sin librerías de imágenes de terceros: se quitó Coil (ver sección 12).
- Sin inyección de dependencias externa: `AstApp` es el contenedor.
- Persistencia: Room (carpetas y fotos) y SharedPreferences (ajustes, caché, estado pendiente).

## 4. Arquitectura y mapa del código

Todo está en `ast-campo/app/src/main/kotlin/co/ast/campo/`.

**`core/`: lógica pura de Kotlin, sin Android, probada en la JVM.**
- `NameSanitizer.kt`: limpia nombres de carpeta (quita `/ \ : * ? " < > |`, controles; colapsa espacios; recorta espacios y puntos finales e iniciales; máximo 60; conserva tildes y ñ con NFC). `key()` y `isDuplicate()` comparan sin distinguir mayúsculas. `searchKey()` ignora tildes. `asciiFold()` para campos EXIF.
- `MediaPaths.kt`: `relativePath()` (`Pictures/AST Campo/.../`), `originalRelativePath()`, `fileName()` (ruta + `_NNN.jpg`, máximo 120 caracteres, recorta por el medio con `...`), `sequenceOf()`, `nextSequence()`, `freeSequence()`.
- `FolderTree.kt`: vista del árbol desde la lista plana (`pathOf`, `segments`, `subtreeIds`, `isSelfOrDescendant`, `aggregate` de conteos y última actividad). Protegido contra ciclos.
- `FolderTemplates.kt`: detección de "Subestación", plantilla por defecto, sugerencias, `nextNumbered`.
- `WatermarkFormatter.kt`: fecha `dd/MM/yyyy HH:mm`, coordenadas con 6 decimales y punto, temperatura redondeada con ` °C` o `--`, línea de datos, abreviatura de ruta con `…` conservando proyecto y últimos niveles.
- `OrientationMatrix.kt`: matriz imagen vista a píxeles crudos para las 8 orientaciones EXIF.
- `TemperatureCache.kt`: reglas de caché de temperatura y distancia haversine.
- `ExifDates.kt`: lectura y escritura de fechas EXIF con desfase.
- `Bounded.kt`: `awaitBounded()` espera un máximo una consulta de red y la deja terminar en segundo plano.

**`data/`**
- `Database.kt`: entidades `FolderEntity` y `PhotoEntity`, DAOs, `AstDatabase` versión 2 con migración 1→2.
- `Settings.kt`: `SettingsRepository` (SharedPreferences `ast_settings` con `StateFlow`), también caché de temperatura y de lugar, último destino por proyecto y temperatura manual con caducidad de 3 h.
- `AstRepository.kt`: operaciones de carpetas y fotos; la ruta lógica es la ruta física, así que renombrar o mover reubica cada foto en MediaStore y en la copia original.

**`media/`**
- `GalleryStore.kt`: interfaz `Gallery` y su implementación con MediaStore (`insert` con `IS_PENDING`, `relocate`, `delete`, `exists`, `existing`).
- `LocationTracker.kt`: interfaz `LocationSource` y su implementación con `FusedLocationProviderClient`; `LocationPolicy` decide qué ubicación vale para una foto.
- `PhotoProcessor.kt`: el corazón (sección 6).
- `WatermarkRenderer.kt`: dibuja la marca con `Canvas`. `SamplePhoto.kt` genera la foto de ejemplo de Ajustes.
- `CaptureManager.kt` y `PendingStore.kt`: orquestan la captura y la cola persistente.

**`weather/`**: interfaz `WeatherProvider`, `OpenMeteoProvider`, `TemperatureResolver`.
**`place/`**: interfaz `PlaceProvider`, `AndroidGeocoderProvider`, `PlaceFormatter`, `PlaceResolver`.
**`ui/`**: `Theme.kt`, `Components.kt` (diálogos, selector de carpeta en árbol), `Images.kt` (carga de imágenes con APIs de Android), `ProjectsScreen`, `FolderScreen`, `CaptureScreen`, `ViewerScreen`, `SettingsScreen`, `ViewModels.kt`.
**Raíz**: `AstApp.kt` (dependencias), `AstNav.kt` (rutas), `MainActivity.kt`.

Rutas de navegación: `projects`, `folder/{id}`, `capture/{id}`, `viewer/{folderId}/{photoId}`, `settings`.

Las interfaces `Gallery`, `LocationSource`, `PlaceProvider` y `WeatherProvider` existen para poder sustituir piezas y probar sin Android real.

## 5. Modelo de datos

**Room `ast_campo.db`, versión 2.**

`folders`: `id` (autogenerado), `parentId` (nulo = proyecto), `name` (ya sanitizado), `createdAt`. Índice en `parentId`.

`photos`: `id`, `folderId`, `mediaUri` (content://media/...), `displayName`, `takenAt`, `latitude`, `longitude`, `accuracyM`, `temperatureC`, `temperatureSource` (`MANUAL`, `NETWORK`, `CACHE`, `NONE`), `originalPath` (ruta absoluta de la copia sin marca, si hay), `width`, `height`, `sizeBytes`, `place` (agregada en la v2). Índice en `folderId`. Sin claves foráneas: los borrados de carpeta se hacen a mano en `AstRepository`.

Migración 1→2: `ALTER TABLE photos ADD COLUMN place TEXT`. **No verificada en un dispositivo con datos reales de la v1.0**; en las pruebas de integración solo se usa la base en memoria.

**SharedPreferences.**
- `ast_settings`: ajustes de marca (`wm_route`, `wm_dt`, `wm_coords`, `wm_temp`, `wm_place`, `wm_bg`, `wm_corner`, `wm_size`, `wm_opacity`), `save_original`, `theme`, `weather_mode`, `weather_key`, `substation_template`, último destino `dest_<projectId>`, temperatura manual con hora, caché de temperatura (`tc_*`) y de lugar (`pc_*`).
- `ast_pending_capture`: la foto que la cámara está tomando (archivo, carpeta, ubicación al abrir, temperatura manual, tiempos).

**Archivos.**
- Galería: `Pictures/AST Campo/<Proyecto>/<Sub>/.../<nombre>.jpg`.
- Originales: `getExternalFilesDir("originales")/<Proyecto>/<Sub>/.../<nombre>.jpg`.
- Temporales: `cache/captures/cap_*.jpg` y `cap_*.json` (cola persistente), `out_*.jpg` (foto marcada antes de insertar). Se borran los de más de 3 días.

## 6. Flujo de captura, paso a paso

1. **Pantalla de captura** (`CaptureScreen`): pide permiso de ubicación, arranca `LocationTracker` solo mientras la pantalla está visible, precarga temperatura y lugar en un bucle (cada 20 s) para que estén en caché.
2. **TOMAR FOTO** (`CaptureVM.prepareCapture` y `CaptureManager.prepare`): crea `cache/captures/cap_<ms>.jpg`, guarda el estado pendiente en SharedPreferences con `commit()` (síncrono, por si Android mata la app) y devuelve una URI de `FileProvider`. Se abre la cámara con `ActivityResultContracts.TakePicture`. Un guardia `cameraBusy` evita abrir dos cámaras con un doble toque.
3. **Al volver** (`CaptureManager.onCameraResult`): lee y borra el estado pendiente, escribe el sidecar `cap_*.json` y encola el procesamiento en el `appScope` (no depende de la pantalla). El resultado de la cámara se entrega aunque la Activity se haya recreado, siempre que la pantalla de captura se restaure.
4. **Procesamiento** (`PhotoProcessor.process`):
   1. Elige la ubicación (`LocationPolicy.pick`): la mejor entre la guardada al abrir la cámara y la actual, solo si es de hasta 60 s antes de abrir la cámara. Si no hay, pide una nueva (`currentOnce`, 5 s).
   2. Pide en paralelo temperatura y lugar, cada una con un máximo de 5 s (`awaitBounded`). Si la temperatura no llega, usa la última guardada si es de menos de 60 min y menos de 10 km.
   3. Dentro de un `Mutex` (una foto a la vez, para acotar memoria y fijar el consecutivo): lee EXIF, decodifica el JPEG, dibuja la marca, recomprime en JPEG calidad 95 con la **resolución original**, escribe EXIF, guarda la copia original, inserta en MediaStore y en Room, borra el sidecar y el temporal.
5. **Eventos** (`CaptureEvent.Saved` o `Failed`) llegan a la pantalla de captura: avisos discretos (sin coordenadas, precisión peor de 30 m, marca fallida, EXIF fallido) y confirmación "Foto guardada: n.º N".
6. **Recuperación**: al abrir la app (`AstApp.onCreate`), `CaptureManager.recoverPending()` reprocesa los sidecars de fotos que quedaron a medias (hasta 3 intentos).

Si la marca falla (por ejemplo por memoria), se guarda la foto **sin marca** antes que perderla y se avisa. Si el EXIF falla, se conserva la foto marcada.

### 6.1 Decisión de diseño importante: la marca se dibuja sin rotar los píxeles

Una foto de 50 MP pesa unos 200 MB como bitmap. Rotarla duplicaría la memoria. Por eso `WatermarkRenderer` dibuja sobre los **píxeles crudos** aplicando `OrientationMatrix` (para que el texto quede abajo a la izquierda de la imagen **ya orientada**) y la orientación EXIF se conserva en el archivo. Está probado con las 8 orientaciones.
Consecuencia: el JPEG puede tener píxeles horizontales con EXIF orientación 6. Los visores modernos lo respetan.
Con `OutOfMemoryError` reintenta con `inSampleSize` 2 y luego 4 (reduce resolución y avisa).

### 6.2 Marca de agua: especificación actual

- Tamaño del texto de la ruta como fracción del lado corto: Pequeño 2,2 %, Mediano 3,0 % (por defecto), Grande 4,0 %. Margen 2 %. Líneas de datos al 92 % del tamaño. Fuente sans-serif, ruta en peso 600.
- Sin fondo: todo en blanco puro (datos en peso 500), sombra negra suave (radio 0,20 de la altura de letra, desplazamiento vertical 0,06) y el texto se dibuja dos veces para densificar la sombra.
- Con fondo (opcional): panel azul marino `#0A1F44` con opacidad 30 a 90 % (60 por defecto), esquinas apenas redondeadas, barra naranja `#F58220`, datos en gris `#CDD6E4`.
- Esquinas configurables: inferior izquierda (por defecto), inferior derecha, superior izquierda, superior derecha.
- Las líneas largas se cortan con `…` para no salirse de la foto. La ruta se abrevia conservando el proyecto y los últimos niveles (`Proyecto › … › Sub › Último`).
- Contenido: ruta, fecha y hora local `dd/MM/yyyy HH:mm`, `lat, lon` con 6 decimales y punto más temperatura (`28 °C` o `--`), y lugar. Sin ubicación: "Sin ubicación".
- **No hay ningún texto de A.S.T. ni de la app en la imagen ni en el EXIF** (el usuario lo pidió expresamente: las fotos van a informes de clientes). El campo "Software" de EXIF no se escribe.

### 6.3 Nombre de archivo y numeración

`<Proyecto>_<Sub>_..._NNN.jpg`, donde NNN es el número de foto **dentro de esa carpeta**: el mayor existente más uno (no reutiliza números de fotos borradas), consecutivo entre días, tres cifras como mínimo para ordenar bien en Windows (001, 002, ... 1000). Máximo 120 caracteres: si la ruta no cabe, se recorta por el medio con `...` y el número siempre se conserva. Al mover una foto a otra carpeta toma el siguiente número de esa carpeta; al renombrar una carpeta conserva su número si está libre. La fecha y hora ya **no** van en el nombre (siguen en EXIF y en la fecha de captura). Los nombres anteriores con fecha se siguen entendiendo (`sequenceOf` lee el último grupo de dígitos).

### 6.4 Ubicación

`FusedLocationProviderClient` con `PRIORITY_HIGH_ACCURACY` (intervalo 2 s) mientras la pantalla de captura está visible. Chip de precisión en metros. Aviso discreto si es peor de 30 m, pero la foto se guarda igual. Sin permiso o sin GPS: se guarda sin coordenadas y se avisa. El permiso se relee al volver a la pantalla. Tras dos denegaciones, el chip abre los ajustes de la app.

### 6.5 Temperatura

- La temperatura **viene de internet**: el S25 Ultra no trae sensor de temperatura ambiente (no aparece en sus fichas técnicas; no se probó en el equipo). **Es temperatura de referencia de la zona (modelo meteorológico de 1 a 11 km), no medida en el sitio.** No usarla para factores de corrección de ensayos.
- Proveedor: Open-Meteo (`current=temperature_2m`), detrás de `WeatherProvider`. Modos en Ajustes: gratuito (solo uso **no comercial**), comercial con clave (`customer-api.open-meteo.com` con `&apikey=`) o solo manual. Atribución CC BY 4.0 mostrada en Ajustes. Se envían las coordenadas redondeadas a 2 decimales (~1 km).
- Prioridad: manual (caduca a las 3 h) > lectura reciente cercana (menos de 10 min y 1 km, evita llamadas repetidas) > internet > última lectura (menos de 60 min y 10 km) > `--`.
- **Pendiente del usuario**: decidir si su uso cuenta como comercial; si lo es, contratar el plan o usar "solo manual". Límites del gratuito comprobados en nov. 2026: 10.000 llamadas/día, 5.000/hora, 600/minuto.

### 6.6 Lugar (nombre del sitio)

`Geocoder` de Android (en los Samsung lo atiende el servicio de Google, así que **las coordenadas exactas se envían a Google**; se avisa en Ajustes y se puede apagar). Resultado tipo "Calle 72 #45-20, Prado, Barranquilla". **Es una dirección, no la lista de lugares cercanos de WhatsApp o Google Maps** (esa usa otra base de datos con clave). Caché de 24 h y 500 m.

## 7. Carpetas: reglas

- La carpeta lógica es la carpeta física. Renombrar o mover una carpeta reubica sus fotos en MediaStore (cambia `RELATIVE_PATH` y `DISPLAY_NAME`) y mueve la copia original.
- Nombres: sanitizados, sin duplicados en el mismo nivel (sin distinguir mayúsculas; las tildes sí distinguen).
- No se puede mover una carpeta dentro de sí misma ni de un descendiente.
- Eliminar: con "eliminar fotos" borra Galería, copia original y registros. Sin eso, las fotos pasan a la carpeta superior; si era un proyecto, las fotos se quedan en la Galería pero dejan de aparecer en la app. Si alguna foto falla, la carpeta se conserva para no dejar filas huérfanas.
- Una operación larga corre en el `appScope` para terminar aunque se cierre la pantalla.
- **Las carpetas vacías no existen en el almacenamiento**, solo en Room, hasta que se guarda la primera foto.
- Al abrir una carpeta, `pruneMissing` olvida las fotos que ya no existen en la Galería (con doble comprobación individual).

## 8. Permisos

Declarados: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`. **No se declara `CAMERA` a propósito**: si una app lo declara, el intent de cámara exige tenerlo concedido. Verificado en el APK con `aapt2 dump permissions`. No se necesitan permisos de almacenamiento (la app es dueña de lo que inserta en MediaStore). `allowBackup=false` y reglas de extracción vacías: nada sale por copia en la nube.

## 9. Privacidad: qué sale a internet

- Temperatura: coordenadas redondeadas (~1 km) a Open-Meteo.
- Lugar (si está activo): coordenadas exactas al servicio de ubicación de Google.
- Nada más. Las fotos, los nombres de proyectos y los datos de la app no se envían a ningún lado.

## 10. Compilar, firmar, publicar

- En `ast-campo/`: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`.
- Se compila en este entorno con `ANDROID_HOME=/opt/android-sdk`.
- **Firma**: keystore PKCS12 en `ast-campo/keystore/ast-campo.p12`, alias `ast-campo`. **La contraseña NO está en el repositorio** (es público). Se pasa por la variable de entorno `AST_KEYSTORE_PASSWORD` o la propiedad Gradle `astKeystorePassword`. Si se pierde, no se puede actualizar la app encima de la instalada (habría que desinstalarla). Huella SHA-256 del certificado: `59:77:45:51:98:22:5D:A7:4C:00:82:37:1B:88:62:22:01:A9:42:AA:96:D2:26:AD:87:44:B3:91:B6:A9:CD:E6`.
- R8 activado (`minify` y recorte de recursos). El APK pesa unos 3 MB. Solo esquema de firma v2.
- GitHub Actions (`.github/workflows/build-apk.yml`): corre pruebas unitarias, lint y las pruebas de integración (Robolectric); si existe el secreto `AST_KEYSTORE_PASSWORD`, compila y sube el APK firmado. **El secreto aún no está creado** (el acceso de la sesión no permite crearlo), así que el flujo no entrega APK firmado hasta que el usuario lo agregue. Un paso final marca la corrida como fallida si las pruebas de integración fallan.
- Cada versión nueva debe subir `versionCode` y publicar `releases/AST-Campo-vX.Y.Z.apk` con su `.sha256`.

## 11. Pruebas

- **Unitarias en la JVM** (unas 100): sanitización, rutas y nombres, árbol de carpetas, formato de la marca, caché de temperatura, orientación y fechas EXIF, política de ubicación, cola persistente, resolutor de temperatura y de lugar, `awaitBounded`. Se ejecutan con `:app:testDebugUnitTest`.
- **De integración con Robolectric** (`app/src/testRobolectric`, solo con `-PwithRobolectric`; unas 25): renderizado real de la marca en las 8 orientaciones, procesamiento de fotos con EXIF y GPS, numeración, operaciones de carpetas con Room en memoria, internet lento que no bloquea el guardado, y un recorrido de la UI completa. Generan capturas en `app/build/test-artifacts/`.
- Estas últimas descargan archivos grandes de Maven Central que a veces devuelven **HTTP 429**; en la sesión de desarrollo solo se pudieron ejecutar en GitHub Actions, no localmente.
- **No cubierto**: nada se probó en un teléfono real. Las pruebas corren sobre el código de **debug**, sin R8; un fallo que solo exista en el APK de release no lo detectan (ya ocurrió, ver sección 12).

## 12. Lecciones aprendidas y trampas conocidas

1. **R8 rompió la carga de fotos.** En el APK de release, R8 quitó de Coil las clases que leen `content://` (`ContentUriFetcher`, `BitmapFactoryDecoder`) y no se veía ninguna foto. Se reemplazó Coil por `ContentResolver.loadThumbnail` e `ImageDecoder` (`ui/Images.kt`). Regla: no depender de librerías con carga por reflexión o `ServiceLoader` sin probar el APK de release.
2. `ExifInterface.getDateTimeOriginal()` es API restringida (lint `RestrictedApi`); se usa `ExifDates` propio.
3. **Maven Central devuelve 429** por IP compartida; hay que reintentar con pausas. No se usó ningún espejo alternativo (un intento de redirigir descargas globalmente fue rechazado por el sistema de permisos).
4. Compose 1.13 exige `compileSdk` 37.1 o superior; AGP 9.5 admite hasta API 37.
5. En GitHub Actions, `continue-on-error` muestra el paso como "success" aunque falle. Por eso hay un paso final que deja el resultado real a la vista. Antes de eso hubo una prueba mal ubicada que no compilaba y nadie lo notó.
6. Las zonas horarias: el runner de CI usa UTC; las pruebas fijan `America/Bogota`.
7. Una llamada de red bloqueante no se interrumpe con `withTimeout`; se usa `awaitBounded` (espera acotada y la consulta sigue en segundo plano).
8. El nombre `Pictures/AST Campo/` es el de la carpeta física en el celular. Solo se ve en la ruta del archivo, no en la imagen. Cambiarlo dejaría las fotos existentes en la carpeta anterior.
9. Avisos de compilación pendientes: `rememberModalBottomSheetState` y la sobrecarga de `Slider` aparecen como obsoletos en Material 3 (siguen funcionando).

## 13. Límites conocidos (honestos)

- **Cámara Samsung por intent**: el intent no permite pedir resolución ni modo. **No se verificó** qué modos (Pro, 50 MP, etc.) quedan disponibles ni en qué resolución devuelve la foto. Hallazgos de foros (anecdóticos, no oficiales): las resoluciones altas del modo Pro/200 MP no se exponen a apps de terceros.
- **Galería de Samsung**: no se pudo confirmar con fuentes oficiales cómo muestra las subcarpetas; los foros se contradicen. Se asume que muestra álbumes planos; por eso la app es el navegador principal y el árbol completo se ve en "Mis archivos".
- La copia original vive en `Android/data/co.ast.campo/files/originales/`, que Android 13 o superior no deja abrir desde "Mis archivos", y se borra al desinstalar.
- La marca queda grabada en los píxeles. Una marca visible no es prueba de autenticidad; lo que respalda una foto es la copia original con su EXIF.
- Mover o renombrar una carpeta no cambia el texto de ruta ya impreso en las fotos.
- Si la app se reinstala con otra firma, Android deja de permitirle modificar o borrar las fotos que creó.
- Renombrar o mover una carpeta cambia primero Room y luego el MediaStore foto por foto, sin transacción. Si Android mata el proceso a la mitad, la ruta física y la lógica pueden quedar desfasadas.
- Los avisos de eventos (por ejemplo "no se pudo guardar") solo se ven si la pantalla de captura sigue abierta.
- Procesar fotos de 50 MP es el caso más pesado: **no se probó en el equipo**.
- La migración de Room v1 a v2 no se probó con datos reales.
- Sobre fondos totalmente blancos y brillantes, la marca sin panel se lee menos (se mitigó con sombra y texto blanco más grueso).

## 14. Historial de versiones

| Versión | Cambio |
|---|---|
| 1.0.0 | App completa: carpetas, captura, marca con panel, visor, ajustes |
| 1.1.0 | Arregla fotos invisibles en el APK de release (se quita Coil); marca más grande (Pequeño/Mediano/Grande); línea de lugar; revisar la foto desde la captura; Room v2 |
| 1.2.0 | Nombre de archivo `Ruta_NNN.jpg` con numeración por carpeta |
| 1.3.0 | Marca solo con letras (sin panel ni barra), fondo opcional |
| 1.3.1 | Sin ningún texto de A.S.T. en la foto (se quita el campo de empresa y el EXIF "Software") |
| 1.3.2 | Ícono de cámara con el rayo de A.S.T. en el lente |
| 1.3.3 | Guardado sin esperas (consultas acotadas y precargadas) y estado claro junto al botón |

Revisión independiente del código (11 hallazgos) en la v1.1: se corrigieron la pérdida de fotos si el proceso muere (cola persistente), coordenadas viejas (ventana de 60 s), carpetas huérfanas al eliminar, permiso de ubicación obsoleto, caducidad de la temperatura manual, fallo de EXIF que descartaba la marca, doble toque en la cámara, excepciones de MediaStore, avisos que se encolaban y zoom bloqueado en el visor.

## 15. Integrar esto en la app de presupuesto de A.S.T.

**Qué no sé** (no se vio la otra app): su lenguaje, framework, si es Android nativo, su modelo de datos ni cómo guarda "servicios". Todo lo de esta sección son recomendaciones, no verificadas contra esa app.

### Mapa conceptual

| AST Campo | App de presupuesto (propuesta) |
|---|---|
| Proyecto (carpeta raíz) | Cliente o proyecto/obra |
| Subcarpeta | Servicio, equipo o actividad |
| Foto | Evidencia del servicio |
| `FolderEntity.id` | Debe guardar además el **id del servicio** de la app de presupuesto (`externalId`) |

La idea natural: al abrir un servicio en la app de presupuesto, un botón "Fotos" lleva a la captura con la carpeta de ese servicio como destino, y la galería de ese servicio se ve ahí mismo.

### Tres caminos

**A. Integrar como módulo dentro de la app de presupuesto (solo si esa app es Android nativo en Kotlin).** Recomendado en ese caso: una sola app, una sola base de datos, sin duplicar pantallas.
- Copiar tal cual: `core/`, `media/` (menos `SamplePhoto` si no se quiere la vista previa), `place/`, `weather/`.
- Adaptar: `data/Database.kt` y `AstRepository.kt` (unir `folders` con el modelo de servicios o agregar `externalId`), `Settings.kt` (llevar los ajustes de marca a los ajustes de esa app), y las pantallas de `ui/` al sistema de navegación y al tema de esa app.
- Interfaces ya listas para enchufar: `Gallery`, `LocationSource`, `PlaceProvider`, `WeatherProvider`.
- Punto de entrada: `CaptureManager.prepare(folderId, manualTemp)` devuelve la URI para la cámara; `onCameraResult(success)` procesa. El resto es automático.
- Declarar en el manifiesto: permisos de ubicación e internet, el `FileProvider` con `file_paths.xml` y **no declarar `CAMERA`**.
- Cuidado: `largeHeap` en el manifiesto, `minSdk` 29 (MediaStore con `RELATIVE_PATH`), `targetSdk` 37.
- Firma: la otra app tiene su propio `applicationId` y firma; no se actualizan una encima de otra. Las fotos ya tomadas con AST Campo quedarían "huérfanas" para la nueva app (Android solo deja modificar lo que esa app creó).

**B. Mantener las dos apps separadas y conectarlas.** Recomendado si la otra app **no** es Android nativo (web, PWA, Flutter, React Native), porque esta lógica (MediaStore, EXIF, cámara por intent, procesamiento de 50 MP) es muy específica de Android nativo y portarla es más costoso que conectarla. Opciones de conexión (ninguna existe aún, habría que construirla):
- *Enlace profundo (deep link) o intent*: la app de presupuesto abre AST Campo con una ruta tipo "Cliente > Servicio"; AST Campo crea la carpeta si no existe y abre la captura. Requiere agregar un `intent-filter` en `MainActivity` y resolver la ruta en `AstRepository`.
- *Carpeta compartida*: la app de presupuesto lee las fotos de `Pictures/AST Campo/<Cliente>/<Servicio>/` (requiere permiso de lectura de medios y no ve las de otro dueño sin permiso).
- *Exportar un manifiesto*: AST Campo escribe un `.json` o CSV por carpeta con los nombres y metadatos de las fotos para importarlo (parte de la Fase 2 pendiente).

**C. Reescribir la captura en la otra tecnología.** No recomendado salvo que la otra app sea multiplataforma y se acepte perder la marca de agua sobre píxeles crudos y el manejo de memoria de fotos grandes.

### Riesgos a decidir antes de integrar
- **Identidad de la carpeta**: hoy el árbol es libre (el usuario escribe nombres). En una app de presupuesto probablemente los nombres vienen de clientes y servicios; hay que decidir si el usuario puede seguir creando carpetas libres o solo las derivadas de servicios.
- **Cambio de nombre de un servicio**: renombrar la carpeta renombra todas las fotos (ya soportado), pero el texto impreso en la marca no cambia.
- **Datos de clientes en las fotos**: el nombre del cliente y la ubicación quedan impresos en la imagen y en el nombre del archivo. Revisar con el usuario qué puede verse en un informe.
- **Respaldo**: hoy no hay copia de seguridad de la base de Room ni de las copias originales. Una app de presupuesto probablemente ya tenga una estrategia; las fotos en sí sí están en la Galería.
- **Licencia de Open-Meteo**: si la app de presupuesto es comercial, aplicar la decisión de la sección 6.5.

### Preguntas que habría que contestar para diseñar la integración
1. ¿La app de presupuesto es Android nativo? ¿En Kotlin o Java? ¿Usa Room u otra base de datos?
2. ¿Cómo se identifica un servicio (id numérico, texto, jerarquía)?
3. ¿Quieren una sola app o seguir con dos?
4. ¿Qué datos de cada servicio deben salir en la marca (cliente, número de orden, equipo)?
5. ¿Debe haber informe o exportación (PDF o ZIP) por servicio? (Es la Fase 2 de AST Campo, no construida.)

### Texto sugerido para otra sesión de Claude
> Lee `docs/CONTEXTO.md` del repositorio `arrietasolucionestecnologicas-oss/caldera` (carpeta `ast-campo/`). Quiero integrar la gestión de fotos de servicios de esa app en mi app de presupuesto. Mi app es [lenguaje, framework, base de datos]. Cada servicio tiene [identificador y jerarquía]. Quiero [una sola app / dos apps conectadas]. Propón primero el diseño y dime qué riesgos ves; no construyas hasta que yo lo apruebe.

## 16. Pendiente y siguientes pasos

- **Pendiente del usuario**: guardar la contraseña del keystore fuera del repositorio; crear el secreto `AST_KEYSTORE_PASSWORD` en GitHub si quiere compilar firmado desde Actions; decidir el plan de Open-Meteo.
- **Fase 2** (no construida; el usuario la condicionó a que la Fase 1 funcione): importar fotos tomadas con la app completa de Cámara Samsung (modo Pro) con la marca desde el EXIF; exportar carpeta o proyecto a ZIP y PDF; nota de texto por foto; búsqueda por proyecto y fecha.
- **Probar en el celular** (lo único que nunca se pudo verificar desde el entorno de desarrollo): la cámara por intent y sus modos, la Galería de Samsung, fotos de 50 MP, el GPS real, y mover o renombrar carpetas con fotos.
- Migrar las dos APIs de Material 3 que aparecen como obsoletas.
- Logo oficial: reemplazar `ast-campo/app/src/main/res/drawable/ast_logo.xml` (hoy una cámara con el rayo de A.S.T., hecha por código). La paleta (`AstColors` en `ui/Theme.kt`) es una propuesta, no un Brand Kit oficial.
