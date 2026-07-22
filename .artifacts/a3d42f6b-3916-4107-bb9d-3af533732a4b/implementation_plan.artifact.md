# Plan de Implementación - Mejora Agresiva contra Restricciones de Edad

Este plan implementa medidas más profundas para saltar las restricciones de edad que persisten en algunos contenidos, incluso con sesión iniciada.

## Problema Identificado

A pesar de las mejoras anteriores, algunos videos siguen bloqueados porque:
1.  **Falta de Flags de Integridad**: Las peticiones de InnerTube no incluyen flags como `contentCheckOk` o `racyCheckOk`, que YouTube usa para validar que el cliente ha "aceptado" ver contenido sensible.
2.  **Orden de Clientes**: Si todos los clientes fallan, el error que se muestra al usuario es el del último cliente intentado, que suele ser el más restrictivo (Web).
3.  **Identificación de Errores**: Vamos a ampliar aún más la lista de frases de error para cubrir variaciones sutiles.

## Cambios Propuestos

### Componente: `:innertube` (Models)

#### [MODIFY] [PlayerBody.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/innertube/src/main/kotlin/com/arturo254/opentune/innertube/models/body/PlayerBody.kt)
- Añadir `contentCheckOk = true` y `racyCheckOk = true` a la estructura de `ContentPlaybackContext`. Estos flags son señales para YouTube de que el cliente permite contenido "racy" o restringido.

### Componente: `:innertube` (Core)

#### [MODIFY] [InnerTube.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/innertube/src/main/kotlin/com/arturo254/opentune/innertube/InnerTube.kt)
- Actualizar la construcción de `PlayerBody` para incluir estos nuevos flags de forma predeterminada en todas las peticiones de reproducción.

### Componente: `:app` (Utils)

#### [MODIFY] [YTPlayerUtils.kt](file:///C:/Users/siryo/StudioProjects/OpenTune/app/src/main/kotlin/com/arturo254/opentune/utils/YTPlayerUtils.kt)
- **Reordenar Fallbacks**: Priorizar los clientes `TVHTML5` y `ANDROID_VR` en la lista de fallbacks generales, ya que tienen mayor probabilidad de éxito con restricciones de edad.
- **Ampliar Detección**: Añadir más variantes de mensajes de error en español.
- **Mejorar Lógica de Fallback**: Si se detecta un error de edad, forzar el uso de clientes que ignoran la firma de seguridad o que tienen perfiles de TV, que son históricamente más permisivos.

## Plan de Verificación

### Verificación Manual
1.  Reproducir el video que fallaba anteriormente.
2.  Observar en Logcat si las peticiones ahora incluyen los flags `contentCheckOk`.
3.  Verificar si el cambio de orden de clientes permite encontrar un stream válido antes de llegar al error fatal.

### Verificación de Compilación
- Ejecutar análisis estático para asegurar que los cambios en `PlayerBody` no rompen la serialización.
