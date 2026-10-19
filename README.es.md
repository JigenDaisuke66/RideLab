<div align="center">

# RideLab

**Convierte tu Android en un panel de ciclismo indoor**

Rodillos BLE · Entrenamiento ERG · Grabación en segundo plano · Análisis local

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md)

</div>

RideLab es una aplicación nativa de Android desarrollada con Kotlin y Jetpack Compose. Conecta un rodillo y un sensor de frecuencia cardíaca, ajusta la potencia, sigue un entrenamiento y consulta tus recorridos en el teléfono. No requiere cuenta ni servicios en la nube.

## Funciones

- **Panel en vivo:** potencia, frecuencia cardíaca, cadencia, tiempo y gráficos de los últimos cinco minutos.
- **ERG manual:** entrada numérica, ajustes de ±5 W, aplicación explícita y objetivos guardados por rodillo. Restaurar un valor no envía ninguna orden.
- **Entrenamiento:** sesiones integradas, prueba FTP de rampa y prueba inicial sin FTP previo. El ERG automático está limitado actualmente a ThinkRider X2 MAX reconocidos.
- **Grabación en segundo plano:** un servicio en primer plano mantiene las conexiones y registra en Room aproximadamente a 1 Hz al cambiar de aplicación, bloquear o girar la pantalla.
- **Control del recorrido:** iniciar, pausar, reanudar y finalizar. La pausa detiene el tiempo activo, el muestreo y el entrenamiento, conservando conexiones y resistencia aplicada. Finalizar conserva las conexiones hasta desconectarlas manualmente.
- **Historial y análisis:** grupos plegables por año/mes, eliminación individual, gráficos, continuidad, medias/máximos, NP/IF/TSS, mejores potencias, zonas, W/kg y comparaciones históricas.
- **Energía estimada:** calorías derivadas de la potencia y equivalencias alimentarias; no es una medición del metabolismo individual.
- **Datos portátiles:** CSV por recorrido, ZIP con todos los CSV y copia completa restaurable.
- **Ventana flotante transparente:** tiempo, potencia, pulso y cadencia; gráficos plegables, arrastre y ajustes de tamaño, fuente y opacidad.
- **Aspecto:** cinco paletas, modos claro/oscuro/sistema, tres diseños flotantes y siete idiomas.

Los valores ausentes no se convierten en cero; los ceros reales se conservan. FTP y peso son opcionales y se guardan al iniciar cada recorrido: cambiarlos después no modifica sus valores históricos. NP necesita potencia suficientemente continua; IF/TSS requieren el FTP guardado. Las mejores potencias cubren 5/15/30 segundos y 1/5/20/60 minutos.

## Compatibilidad

Las capacidades se detectan mediante servicios y características GATT reales. Medir potencia no implica admitir ERG.

| Dispositivo / protocolo | Comportamiento | Verificación |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power + FE-C over BLE ERG, 50–2000 W y entrenamientos automáticos existentes | Rodillo principal probado físicamente |
| Rodillo FTMS | Indoor Bike Data; ERG manual si dispone de características y rango válidos | Pruebas de protocolo/unitarias; pendiente de hardware; entrenamientos automáticos desactivados |
| Otros FE-C over BLE | ERG manual conservador de 50–200 W con FEC1/FEC2/FEC3 completos | Modelos no verificados; automatización desactivada |
| Solo Cycling Power | Potencia y cadencia si hay revoluciones de biela | No habilita ERG; el rol separado de potenciómetro externo sigue incompleto |
| Sensor cardíaco BLE | Heart Rate Service, BPM UINT8/UINT16 | Conexión simultánea con X2 MAX verificada; otros modelos requieren pruebas |

El X2 MAX probado utiliza Cycling Power y FE-C, sin FTMS. Si existen ambos protocolos de control, se elige FE-C y no se duplica el objetivo. FTMS exige autorización y respuesta de éxito; completar la escritura GATT no equivale a aceptar la orden. Reconectar no reenvía objetivos. No se garantiza compatibilidad universal. Consulta la [guía](TRAINER_COMPATIBILITY.md).

## Primeros pasos

1. Usa Android 12+ (API 31) con BLE. Instala el APK si está disponible en [Releases](https://github.com/JigenDaisuke66/RideLab/releases), o ejecuta el código desde Android Studio. Un AAB no se instala directamente como un APK.
2. Activa Bluetooth y concede permiso para dispositivos cercanos. Selecciona rodillo y sensor cardíaco por separado.
3. Comprueba los datos y configura FTP/peso si los conoces. Puedes dejarlos vacíos. La prueba inicial está disponible en rodillos compatibles y guardar el resultado requiere confirmación.
4. Inicia el recorrido y permite notificaciones. Aplica explícitamente el objetivo ERG manual; al terminar, abre Historial.

La ventana flotante requiere permiso para mostrarse sobre otras aplicaciones; se ajusta desde Recorrido o Ajustes. Haz primero una prueba breve y otra con pantalla bloqueada: las políticas del teléfono también afectan al segundo plano. [Procedimiento](FOREGROUND_SERVICE_VERIFICATION.md).

## Exportación y restauración

| Archivo | Uso | Restaurable |
| --- | --- | --- |
| `.csv` individual | Muestras de un recorrido para Excel/WPS | No |
| CSV `.zip` | `summary.csv` y un CSV por recorrido terminado | No |
| `.ridelab` | Recorridos completos/interrumpidos, muestras y FTP/peso históricos | Sí |

La exportación masiva y restauración están arriba del Historial; el CSV individual está en el detalle. Restaurar combina registros sin sustituir los existentes y omite duplicados idénticos. No incluye recorridos activos ni ajustes de la aplicación. Los blancos CSV indican datos ausentes. Las copias están comprimidas y verificadas, pero no cifradas, y pueden contener direcciones Bluetooth. [Formato y límites](HISTORY_BACKUP.md).

## Idiomas

Chino simplificado, inglés, japonés, chino tradicional (Taiwán), español, portugués de Brasil y francés. La aplicación sigue el idioma del sistema por defecto y usa chino simplificado si no está admitido. Las elecciones manuales persisten y se admite el idioma por aplicación de Android.

## Desarrollo y pruebas

Usa Android Studio compatible con el AGP del proyecto, preferiblemente con JDK 21 integrado. SDK de compilación 37.1, destino 36, mínimo API 31. Consulta [versiones](gradle/libs.versions.toml) y [Wrapper](gradle/wrapper/gradle-wrapper.properties).

```bash
git clone https://github.com/JigenDaisuke66/RideLab.git
cd RideLab
# macOS / Linux
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Abre el proyecto, sincroniza Gradle y ejecuta `app` en un teléfono real. Los comandos no generan APK. Compilar pruebas de dispositivo no las ejecuta; las pruebas unitarias no sustituyen al hardware. El servicio posee BLE y grabación; Compose presenta estado y envía eventos. Lee [proyecto](PROJECT.md), [arquitectura UI](UI_ARCHITECTURE.md) y [hoja de ruta](ROADMAP.md).

## Comentarios y contribuciones

En [Issues](https://github.com/JigenDaisuke66/RideLab/issues), incluye versión de la aplicación, teléfono/Android, modelo y firmware del rodillo/sensor, pasos, servicios/protocolo/rango y registros: `RideLabGatt`, `RideLabWorkout`, `RideLabOverlay`. Elimina direcciones o datos personales antes de compartirlos si lo deseas. Aporta pruebas relevantes y traducciones UI en los siete idiomas.

Actualmente no hay sincronización en la nube, cuentas, FIT/TCX, PowerMatch ni simulación de pendientes. Los documentos de verificación distinguen implementación y aceptación física.
