# Walkthrough - Mejora Agresiva contra Restricción de Edad

Se han implementado medidas técnicas avanzadas para superar los bloqueos de "confirmación de edad" que YouTube impone incluso en cuentas con sesión iniciada.

## Cambios realizados

### `:innertube` (Núcleo de comunicación)

#### [PlayerBody.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/innertube/src/main/kotlin/com/arturo254/opentune/innertube/models/body/PlayerBody.kt) e [InnerTube.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/innertube/src/main/kotlin/com/arturo254/opentune/innertube/InnerTube.kt)
- **Integridad de Contenido**: Se han añadido los flags `contentCheckOk` y `racyCheckOk` a todas las peticiones de reproducción. Estos flags actúan como una declaración del cliente indicando que el contenido sensible ha sido "pre-aprobado", lo que ayuda a evitar el gate de edad en los servidores de YouTube.
- **Robustez de Firma**: Se ha flexibilizado la petición para que siempre envíe el contexto de reproducción (con los flags de edad) incluso si el `signatureTimestamp` no está disponible en ese momento.

#### [YouTubeClient.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/innertube/src/main/kotlin/com/arturo254/opentune/innertube/models/YouTubeClient.kt)
- **Expansión de Firmas**: Se ha habilitado el uso de firmas de seguridad (`useSignatureTimestamp`) en más clientes: `iPadOS`, `Mobile Web` y `Web Embedded Player`. Esto asegura que los flags de integridad se envíen correctamente desde estos perfiles.

### `:app` (Lógica de reproducción)

#### [YTPlayerUtils.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/app/src/main/kotlin/com/arturo254/opentune/utils/YTPlayerUtils.kt)
- **Prioridad de Respaldo**: Se ha reordenado la lista de clientes de fallback para intentar primero los de **Android TV** y **Android VR**. Estos perfiles son conocidos por tener políticas de restricción de edad mucho más laxas que el cliente Web o el de Android estándar.
- **Diccionario de Errores Ampliado**: Se han añadido múltiples variaciones de mensajes de error en español para asegurar que el sistema detecte la restricción de edad instantáneamente y pase al siguiente fallback sin quedarse "pensando".

## Verificación realizada
- **Compilación Exitosa**: El módulo `:innertube` compila correctamente tras los cambios en los modelos de datos.
- **Análisis de Código**: Se ha verificado que la lógica de fallback en `YTPlayerUtils.kt` sea coherente con el nuevo orden de clientes.

> [!TIP]
> Al priorizar ahora los clientes de TV y VR, la aplicación intentará "hacerse pasar" por un dispositivo donde YouTube suele pedir menos verificaciones interactivas de edad.
