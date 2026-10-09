# AGENTS.md — aed-casa-amarilla

## Proyecto

Aplicación de escritorio en Java 21 para la admisión y matrícula del Nido La Casa Amarilla (2027). Usa Swing y el JDK directamente, sin Maven, Gradle, dependencias externas ni un framework de pruebas.

## Spec del proyecto y fuentes de consulta

- **`docs/flujo_matricula_casa_amarilla_2027_ia.md` es la spec del proyecto.** Antes de implementar y al revisar cualquier cambio, comprueba que el comportamiento respete ese documento. Las pruebas deben cubrir las reglas aplicables de la spec.
- **Si una regla es ambigua, está pendiente de definición o entra en conflicto con otra fuente, pregunta antes de implementar.** No inventes criterios ni resuelvas contradicciones por tu cuenta.
- Lee `CONTEXT.md` para usar la terminología del dominio: Alumno, Apoderado principal/adicional y Personal. Sigue su glosario en lugar de la terminología genérica del curso.
- Consulta el issue y sus comentarios para conocer el alcance y los acuerdos. Una especificación de tarea o un comentario no autoriza a contradecir la spec del proyecto; ante diferencias, consulta y deja explícito el acuerdo.
- `docs/flujo_para_diseño.md` aporta contexto de interfaz. `docs/requerimientos_proyecto.md` y `docs/resumen_temas_manual_ia.md` aportan requisitos y temas del curso; no sustituyen la spec del proyecto.

## Mapa del código

- `src/modelo/`: entidades, enums, excepciones de dominio y `Validaciones`.
- `src/datos/`: colecciones `Arreglo*` respaldadas por `ArrayList`.
- `src/negocio/`: coordinación y reglas de negocio (`Cobros`, `Turnos`, `Vacantes`, `Transiciones`, `ExpedienteDocumentos`).
- `src/ui/`: ventana, paneles y estilos compartidos de Swing. `src/util/`: constantes y utilidades de fechas de admisión.
- `src/Main.java`: entrada de la aplicación. `src/PruebaPagos.java`: archivo temporal de pruebas de regresión y demo gráfica de pagos.

Los datos de la aplicación se mantienen actualmente en memoria. Algunas colecciones tienen métodos de lectura y escritura de archivos, pero no asumas persistencia completa ni una base de datos. La demo carga datos de ejemplo y los recrea al reiniciarse. Personal es el actor que usa el sistema; el nombre del responsable es un dato de auditoría, no una autorización autenticada.

## Compilación y pruebas

Ejecuta los comandos desde la raíz del proyecto. Configura `JAVA_HOME` con un **JDK 21** y comprueba las versiones de Java y javac; no asumas que el Java del PATH corresponde al requerido. No se necesita un harness ni herramientas propias de un integrante del equipo para compilar o ejecutar las pruebas.

- CI (`.github/workflows/compilar.yml`) usa Temurin 21: genera la lista de fuentes con `find src -name "*.java" > sources.txt` y compila con `javac -encoding UTF-8 -Xlint:none -d out @sources.txt`.
- Eclipse también está configurado para Java 21, con fuentes en `src` y salida en `bin`.
- Ejecuta `PruebaPagos` con `-Djava.awt.headless=true` para las pruebas funcionales. Los casos de interfaz necesitan `-Dopencode.test.logs=<directorio absoluto existente>` para guardar el comprobante de prueba; puede ser cualquier directorio local destinado a esos archivos, sin un harness.
- Conserva la salida completa y el resultado de las comprobaciones. No confundas ausencia de un framework de pruebas con pruebas ejecutadas o aprobadas.
- Ejecuta `Main` para abrir la aplicación o `PruebaPagos` sin el modo headless para abrir la demo con datos de ejemplo. Las comprobaciones headless no verifican la apariencia ni toda la interacción manual; informa las pruebas gráficas por separado.

`PruebaPagos` está marcado para eliminarse más adelante. Mientras sea la entrada de las pruebas de regresión, no lo elimines sin acordar una alternativa que conserve esa cobertura.

## Convenciones de implementación

- Mantén los identificadores y mensajes de dominio en español y respeta la separación de paquetes. La interfaz delega las decisiones de negocio a `negocio` y `modelo`; no dupliques reglas en los botones.
- Usa las subclases existentes de `ReglaDominioException` y `Validaciones` para errores de dominio esperados. No ocultes errores inesperados ni los presentes como operaciones exitosas.
- Valida relaciones, transiciones, fechas y cálculos de cola antes de modificar objetos. Un rechazo no debe dejar cambios parciales en los datos relacionados.
- Los cambios de estado deben respetar `Transiciones`. Mantén métodos con responsabilidades claras y prefiere métodos privados con nombres descriptivos a nuevas capas o abstracciones innecesarias.
- Amplía las pruebas cuando cambie el comportamiento: casos nombrados, fechas de dominio fijas, errores esperados específicos y comprobaciones de estado intacto tras un rechazo. Identifica por separado los casos de interfaz que dependan del reloj. No añadas dependencias ni frameworks sin aprobación.

## Invariantes de negocio

- Un error de registro no se corrige editando un pago confirmado: se anula y se conserva el registro. `ANULADO` es terminal; fecha, motivo, responsable y comprobante existente no deben cambiar. Las consultas de pagos vigentes excluyen los anulados.
- No reinicies los plazos originales: 48 horas de habilitación de pago y 72 horas desde la creación de matrícula. El instante exacto de vencimiento cuenta como vencido. Operación y registro del pago de matrícula deben ser anteriores al vencimiento original.
- La anulación debe mantener la coherencia del pago con su solicitud y matrícula. No dejes una matrícula pendiente o activa sin la inscripción pagada requerida; valida las relaciones antes de revertir estados.
- Las vacantes se calculan como capacidad menos matrículas `PENDIENTE_PAGO` y `ACTIVA`. Cancelar libera capacidad mediante ese cálculo, no mediante un contador almacenado aparte.
- Conserva las validaciones de solicitud activa única y apoderado principal. Los pagos mantienen el importe registrado al crearse; cambiar una cuota no reescribe pagos existentes.

## Alcance y archivos generados

- Trabaja en una rama de tarea, no directamente en `main`. Si usas un worktree, no alteres otros worktrees, referencias, archivos ni evidencias ajenos al trabajo.
- Mantén los cambios dentro del alcance aprobado. No añadas persistencia, autenticación, dependencias ni cambios de arquitectura como arreglos incidentales.
- No incluyas clases compiladas, listas de fuentes generadas, logs, comprobantes de prueba ni pruebas auxiliares temporales en los commits. Añade explícitamente los archivos que correspondan.
- `.gitignore` excluye `/bin/`, `/reviews/` y `.opencode/`; no asumas que `out/` u otros directorios de salida están ignorados.
- Revisa `.github/workflows/opencode-review.yml` para conocer las comprobaciones automáticas. Antes de dar un cambio por terminado, contrástalo siempre con la spec del proyecto; si hay ambigüedad, pregunta. No afirmes fidelidad visual sin comprobar el diseño y la interfaz ejecutada.

## Nota temporal del review automático

La exclusión exacta de `src/PruebaPagos.java` del review automático es temporal. No elimina la obligación de ejecutar y mantener las pruebas funcionales. Cuando se retire la exclusión, conserva las instrucciones del proyecto de este archivo al integrar los cambios.
