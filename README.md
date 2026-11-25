# AST Campo

App Android nativa (Kotlin, Jetpack Compose, Material 3) de **Arrieta Soluciones Técnicas (A.S.T.)** para tomar fotos de trabajos de campo con marca de agua (ruta, fecha y hora, coordenadas y temperatura) y dejarlas organizadas por proyecto.

Sin anuncios, sin cuentas, sin analítica, sin servidor propio. Las fotos nunca salen del teléfono. A internet salen solo coordenadas: redondeadas a ~1 km a Open-Meteo (temperatura) y, si dejas activo "Lugar", exactas al servicio de ubicación de Google (nombre del sitio).

> Este repositorio también contiene el PWA "Analizador Térmico" (`index.html`, `simulador.js`, `sw.js`, `manifest.json`). No se tocó. La app Android vive en `ast-campo/`.

## Instalar en el celular

1. En el celular abre este enlace en Chrome y descarga el APK:
   `https://raw.githubusercontent.com/arrietasolucionestecnologicas-oss/caldera/main/releases/AST-Campo-v1.1.0.apk`
2. Al abrir el archivo Android pedirá permiso para instalar apps desconocidas desde el navegador: acepta en **Ajustes > Permitir de esta fuente**.
3. Instala y abre **AST Campo**. Concede el permiso de **ubicación** (mientras usas la app).

Si Play Protect avisa de "app no verificada" es normal: el APK está firmado con la clave propia de A.S.T., no con una de Google Play.

## Actualizar

Descarga el APK de la versión nueva (v1.1.0 es compatible con v1.0.0: conserva tus proyectos; las fotos viejas quedan sin línea de lugar) (`releases/AST-Campo-vX.Y.Z.apk`) y ábrelo: se instala **encima** de la anterior y conserva proyectos y fotos. Esto solo funciona porque todas las versiones se firman con el mismo keystore (ver abajo). Para comprobar que el archivo no se dañó, compara su SHA-256 con `releases/AST-Campo-v1.1.0.apk.sha256`.

## Qué hace

- **Proyectos y carpetas** con profundidad ilimitada (Proyecto > subcarpeta > subcarpeta...). Crear, renombrar, mover y eliminar en cualquier nivel (al eliminar pregunta qué hacer con las fotos). Plantilla editable para "Subestación" (Transformadores, Tableros, Generadores, Banco de condensadores). Recuerda el último destino por proyecto.
- **Captura**: abre la cámara de Samsung por intent, aplica la marca al volver y queda lista para la siguiente foto. Chips de ruta destino, precisión GPS y temperatura (tocable para escribirla a mano). Deshacer la última foto.
- **Marca de agua** (ruta, fecha y hora, coordenadas con temperatura y, opcional, el lugar; esquina inferior izquierda, azul marino translúcido con barra naranja). Tamaños Pequeño, Mediano (predeterminado) y Grande, configurable en Ajustes con vista previa en vivo.
- **Lugar**: nombre aproximado del sitio (dirección, barrio, ciudad) obtenido del servicio de ubicación de Google. Es una dirección, **no** la lista de lugares cercanos de WhatsApp o Google Maps (esa usa otra base de datos de pago/con clave). Necesita internet y le envía las coordenadas a Google; se puede apagar en Ajustes.
- **Revisar la foto al instante**: en la captura, toca la miniatura de la última foto para verla a pantalla completa con zoom.
- **Galería**: las fotos se guardan con MediaStore en `Pictures/AST Campo/<Proyecto>/<Sub>/.../` y aparecen solas en la Galería, sin permisos de almacenamiento. Nombre de archivo `Proyecto_Sub_..._yyyyMMdd_HHmmss_NNN.jpg`.
- **Copia original sin marca** (activada por defecto) en el almacenamiento privado de la app, visible desde el visor ("Original").
- **EXIF** de la foto final: GPS, fecha y hora originales, orientación y datos técnicos de la cámara.
- **Visor** con zoom, detalles, mover, eliminar y compartir.
- Tema oscuro por defecto y tema claro de alto contraste para el sol.

## Lo que debes saber (límites reales)

- **La temperatura es de referencia, no medida en el sitio.** Viene de un modelo meteorológico (celdas de 1 a 11 km). El S25 Ultra no tiene sensor de temperatura ambiente. No la uses para factores de corrección de ensayos (p. ej. transformadores): mide con termómetro y escríbela a mano en la pantalla de captura.
- **Las carpetas vacías no existen en el almacenamiento.** Solo viven en la base de datos de la app hasta que se guarda la primera foto; "Mis archivos" las mostrará desde entonces.
- **Galería de Samsung**: no pude comprobar con fuentes oficiales cómo muestra las subcarpetas. Los foros dan versiones contradictorias (álbumes planos, uno por carpeta final, o vista sin jerarquía). Asume que **no** muestra el árbol: por eso la app es el navegador principal, el nombre del archivo lleva la ruta y el árbol completo se ve en "Mis archivos > Imágenes > AST Campo". Puede que veas muchos álbumes pequeños en la Galería.
- **Cámara Samsung por intent**: el intent no permite pedir resolución ni modo. Lo que no pude verificar: qué modos (Pro, 50 MP, etc.) quedan disponibles al abrirla así y en qué resolución devuelve la foto. Hallazgos de foros (anecdóticos, no oficiales): las resoluciones altas del modo Pro/200 MP no se exponen a apps de terceros. La Fase 2 (importar fotos hechas con la app completa de Cámara) cubre ese caso.
- **La marca queda grabada en los píxeles** y no se puede quitar después; para eso existe la copia original. Una marca visible **no es prueba de autenticidad**: cualquier app puede escribir lo mismo. Lo que respalda una foto es la copia original con su EXIF (fecha y GPS de la cámara). La marca de AST Campo es de la app, no la nativa de Samsung; no la presentes como si lo fuera. Esa copia vive en `Android/data/co.ast.campo/files/originales/`, que **Android 13 o superior no deja abrir desde "Mis archivos"**, y **se borra si desinstalas la app**. Respáldala (compartiéndola desde el visor) si es importante.
- Mover una foto o renombrar una carpeta cambia el archivo y la carpeta en la Galería, pero el texto de ruta ya impreso en la marca **no cambia**.
- Si desinstalas la app o la reinstalas con otra firma, Android deja de permitirle modificar o borrar las fotos que creó (siguen en la Galería).
- Procesar una foto de 50 MP necesita memoria: si falla, la foto se guarda sin marca (la app te avisa) o a menor resolución.

## Temperatura: Open-Meteo y su licencia

La consulta está detrás de la interfaz `WeatherProvider` (`app/src/main/kotlin/co/ast/campo/weather/`). Se elige en **Ajustes > Temperatura**: gratuito, plan comercial con clave, o solo manual.

Lo que comprobé en el sitio de Open-Meteo (nov. 2026) y debes revisar tú, porque **A.S.T. es un negocio**:

- El acceso gratuito (`api.open-meteo.com`) es **solo para uso no comercial**, con límites de 10.000 llamadas/día, 5.000/hora y 600/minuto, y sin garantía de disponibilidad.
- El uso comercial requiere una suscripción: da una clave API y el endpoint `customer-api.open-meteo.com` (misma sintaxis, con `&apikey=`). Ver https://open-meteo.com/en/pricing y https://open-meteo.com/en/terms.
- Los datos son CC BY 4.0: exigen atribución (la app la muestra en Ajustes).
- Pendiente tuyo: decidir si el uso de A.S.T. cuenta como comercial. Es mi lectura, no asesoría legal. Si lo es, contrata el plan y pega la clave en Ajustes, o usa "Solo manual".

## Firma y respaldo del keystore (importante)

`ast-campo/keystore/ast-campo.p12` es la clave de firma. **Haz respaldo del keystore y de su contraseña fuera de este repositorio** (gestor de contraseñas + copia en tu nube privada). Si se pierde, no podrás actualizar la app encima de la instalada: habría que desinstalarla y se perdería la capacidad de modificar las fotos ya creadas.

- Este repositorio es **público**. Por eso la contraseña del keystore **no** está aquí: se entregó aparte. El keystore sin contraseña no sirve, pero la contraseña debe protegerse; si crees que se filtró, genera otro keystore (implica reinstalar la app).
- Para compilar firmado: `AST_KEYSTORE_PASSWORD=<contraseña> ./gradlew :app:assembleRelease` dentro de `ast-campo/`. Alias: `ast-campo`.
- Para que GitHub Actions firme: crea el secreto `AST_KEYSTORE_PASSWORD` en *Settings > Secrets and variables > Actions*. Sin él, el flujo corre pruebas y lint pero no genera el APK firmado.

## Compilar y probar

Requisitos: JDK 17+ (se probó con 21) y Android SDK con plataforma 37.2 y build-tools 36.0.0. Gradle viene en el wrapper (9.6.0). En `ast-campo/`:

```
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

- Pruebas unitarias JVM (91): sanitización de nombres, rutas y nombres de archivo, formato de la marca, caché de temperatura, orientación EXIF, fechas EXIF, árbol de carpetas, parser y resolutor de Open-Meteo.
- Pruebas de integración con Robolectric (`app/src/testRobolectric`, activar con `-PwithRobolectric`): renderizado real de la marca en las 8 orientaciones EXIF, procesamiento de fotos con EXIF, operaciones de carpetas con Room y arranque de la UI. Descargan archivos grandes de Maven Central que a veces devuelven 429, así que corren en GitHub Actions como paso opcional que no bloquea el APK. En la sesión de desarrollo no pudieron correr localmente.
- Si Android cierra la app mientras procesa fotos, las pendientes se reanudan al abrirla (cola en `cache/captures/*.json`, hasta 3 intentos).
- Versiones: AGP 9.5.2, Kotlin 2.4.20, Compose BOM 2026.11.01, Room 2.8.5. `compileSdk` 37.2, `targetSdk` 37, `minSdk` 29.

## Cambiar el logo

El logo actual es **provisional**. Reemplaza el único recurso `ast-campo/app/src/main/res/drawable/ast_logo.xml` (vector de 108 x 108 dp con el contenido dentro del círculo central de 66 dp, o un `ast_logo.png` de 432 x 432 px). Se usa como ícono de la app y en la barra de Proyectos. Los colores propuestos están en `ui/Theme.kt` (`AstColors`); no son un Brand Kit oficial.

## Vistas previas de diseño

`docs/preview/` tiene la marca de agua y las pantallas (réplica en Python, ver `generate_previews.py`; los íconos son aproximados).
