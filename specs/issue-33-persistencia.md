# Spec — Issue #33: Integración de persistencia (cargar al iniciar y grabar en cada cambio)

Fuente: issue #33 ([MEDIO] Integración: cargar al iniciar y grabar en cada cambio) + grilling con Jaim (2026-10-11).
Spec del proyecto: `docs/flujo_matricula_casa_amarilla_2027_ia.md` §4.8, §5. Temas del manual: Unidad 5 (Archivos: `PrintWriter`/`FileWriter`, `String.split`, `File` integrados con `ArrayList`).
Precedentes: `specs/issue-27-motor-vencimientos.md` (motor al arrancar), `specs/issue-23-24-26-matricula-pago.md:75` (el load debe ir antes del barrido).

## Objetivo

Integrar la persistencia con la aplicación completa: al abrir, cargar todas las entidades y **después** ejecutar el motor de vencimientos (los plazos corren con la app cerrada); tras cada operación que modifica datos, grabar; el historial (auditoría embebida) también persiste.

## Decisiones (grilling)

1. **Disparador de grabado: llamada explícita en los bordes.** Cada operación que muta llama a un único `Almacen.grabar()` tras el éxito. Sin callbacks genéricos ni `Thread`.
2. **Ubicación: directorio único resoluble.** `Almacen` recibe un directorio base; por defecto un `datos/` relativo al working dir, sobrescribible por la propiedad de sistema `casaamarilla.datos`. Las pruebas apuntan a un directorio temporal.
3. **Alcance del historial: sin entidad `Evento`/`Historial`.** El historial son campos de auditoría embebidos (`Pago.fechaAnulacion/motivo/responsable`, `Matricula.fechaCancelacion/motivo/responsable`, `Solicitud.fechaCancelacion/motivo/responsable`, `Solicitud.ofertas`). Persistir esas entidades persiste el historial. Consistente con `specs/issue-17:44` (que excluyó `Historial`) y con la remoción de `PanelHistorial` en #28 (sin consumidor, sin infraestructura de eventos).
4. **Formato: CSV `;` UTF-8** con `PrintWriter`/`BufferedReader`, el mismo idioma ya usado por `ArregloAlumnos`/`ArregloApoderados`. Relaciones por clave de referencia (código/DNI), no anidadas.
5. **Reconstrucción: métodos explícitos en el modelo.** Cada entidad gana una fábrica `reconstruir(...)` (o un `registrar(...)` en su `Arreglo*`) que asigna el estado persistido sin exigir el orden de las transiciones. Sin reflexión.
6. **Alumnos y apoderados entran a la app.** `PrincipalUI` retiene `ArregloAlumnos` y `ArregloApoderados`; se cargan/graban junto al resto y se reconstruyen los vínculos alumno–apoderado. No se crea pantalla de mantenimiento (fuera de alcance): `PruebaPagos` sigue poblando alumnos/apoderados en pruebas y demo.
7. **Prueba de punta a punta en `PruebaPagos`.** Casos nombrados, semilla determinista, directorio temporal inyectado; "cerrar y reabrir" = descartar las colecciones y volver a cargar en objetos nuevos.
8. **Cuotas también persisten.** `ConfiguracionCuotas` (inscripción y matrícula) se graba tras `PanelCuotas.guardar`; si el archivo no existe al arrancar, quedan los defaults 180/850.
9. **Campos con caracteres peligrosos: escape, no recorte.** Un valor puede contener `;`, `,` o saltos de línea (`Validaciones` los permite en textos libres). Cada campo se escribe con escape (`\` → `\\`, `;` → `\;`, salto de línea → `\n`) y se desescapa al leer, de modo que el valor sobrevive la ida y vuelta. Un helper compartido en `datos` centraliza el escape/desescape.
10. **Grabar el barrido de vencimientos aunque la acción se rechace.** El barrido (`barrer`/`revisar`) puede mutar estado (habilitaciones vencidas, matrículas canceladas, solicitudes reingresadas). Esas mutaciones **sí** deben grabarse aunque la operación del Personal termine en rechazo. Por tanto, todo borde que ejecute `barrer`/`revisar` graba tras el barrido, antes de decidir el resultado de la acción.
11. **Fallo de `grabar()`: se informa, no se revierte ni se oculta.** Un error de escritura se muestra al personal; el estado en memoria ya aplicado se conserva y la siguiente grabación exitosa corrige el archivo. No se presenta la operación como totalmente exitosa sin advertir el fallo de grabación. Si `cargar()` falla al arrancar, se informa y no se arranca a medias (no se sobrescribe nada con estado parcial).

## Arquitectura

- **`datos.Almacen` (nuevo, coordinador de persistencia).** Retiene las 7 colecciones (`ArregloAulas`, `ArregloSolicitudes`, `ArregloPagos`, `ArregloMatriculas`, `ArregloEntrevistas`, `ArregloAlumnos`, `ArregloApoderados`) y la `ConfiguracionCuotas`, más el directorio base.
  - `cargar()`: carga todo en orden de dependencias y resuelve referencias. Si un archivo no existe, deja la colección vacía (o los seeds/defaults de su ctor).
  - `grabar()`: graba todo. Es el único punto de escritura.
  - No se duplica I/O: el `Almacen` reutiliza los `grabar/cargar` ya existentes de `ArregloAlumnos`/`ArregloApoderados` (pasándoles las rutas del directorio base) y aporta el I/O nuevo de las demás colecciones.
- **I/O por colección.** Cada `Arreglo*` gana `grabar(String ruta)` y `cargar(String ruta, ...)` (con las dependencias que necesite para resolver referencias), o equivalentes claros. Se mantienen los contratos actuales de `ArregloAlumnos` (dos rutas) y `ArregloApoderados`.
- **Orden de reconstrucción (dependencias):** apoderados → alumnos → vínculos alumno–apoderado → aulas → cuotas → solicitudes (con su expediente/documentos y ofertas) → matrículas → entrevistas → pagos. Las referencias se resuelven por código/DNI contra las colecciones ya cargadas.
- **Arranque.** `PrincipalUI` crea el `Almacen`, llama `almacen.cargar()` y **luego** ejecuta `Vencimientos.barrer(...)` (y `revisarVencimientos()`), antes de crear los paneles. Si la carga falla, se informa y no se arranca a medias.
- **Bordes de grabado (tras el barrido y tras el éxito de la acción, antes de refrescar/avisar):** todo borde que ejecute `barrer`/`revisar` graba tras el barrido (decisión 10), aunque la acción luego se rechace.
  - `PanelExpediente.ejecutar(Accion, String)` (`:821`): un único punto cubre entrega/validación/observación de documentos, agendar/asistencia/inasistencia/resultado de entrevista, y las cancelaciones que la propia revisión de vencimientos produce. Graba tras `revisarVencimientos()` y tras el camino exitoso (y también cuando el barrido mutó estado aunque la acción se rechace).
  - `PanelPagos.confirmar` (`:364`) y `confirmarMatricula` (`:383`): graban tras `Vencimientos.barrer` y tras el éxito de `Cobros.*`.
  - `PanelPagos.anular` (`:402`): graba tras `Cobros.anularPago`.
  - `PanelPagos.buscar` (`:229`) y `PanelExpediente.abrir` (`:432`): si su `revisar`/`barrer` muta estado, graban.
  - `TarjetasPago.registrarBoleta` (`:551`): muta un `Pago` fuera del flujo `PanelPagos`; `PanelPagos` le pasa un hook de grabado (callback `Runnable` o método equivalente) y graba tras registrar la boleta.
  - `PanelCuotas.guardar` (`:84`): graba tras `setCuotas`.
  - `PrincipalUI` (arranque): tras `cargar()` barre y graba (`barrerYGrabar`), para no perder las cancelaciones/reingresos que corren con la app cerrada.
  - `PanelSolicitudes` (constructor, `componentShown` e inicio de `guardar`): barre y graba (`barrerVencimientosYGrabar`) en los bordes no-tecla; `recalcular()` (por tecla) no barre ni graba. La rama `catch` con la solicitud ya registrada también graba.
  - `PanelColas.revisarTurnos`/`ofertar`: barre y graba tras la promoción de turnos y la oferta.
  - Se pasa el `Almacen` (o un hook mínimo) a los constructores afectados; `PrincipalUI.crearCentro` (`:168`) lo inyecta.
- **Manejo de error al grabar (decisión 11).** `grabar()` lanza `IOException`; los bordes la capturan y la muestran al personal con `JOptionPane` (no puede quedar una operación presentada como exitosa si no se grabó). El estado en memoria se conserva y la siguiente grabación corrige el archivo. No se ocultan errores. Al reutilizar los `grabar` de `ArregloAlumnos`/`ArregloApoderados`, se añade `checkError()` al `PrintWriter` (hoy no comprueba el error retenido) para que un fallo de escritura no pase silencioso.

## Formato de archivos (uno por entidad, en el directorio base)

Todos `;`-separados, UTF-8, una línea por registro. `null` → campo vacío; se reconstruye a `null` al leer. Fechas `ISO-8601` (`LocalDate`/`LocalDateTime`). Booleanos `true`/`false`. Enums por nombre.

| Archivo | Colección | Campos |
|---|---|---|
| `apoderados.csv` | ArregloApoderados | dni;nombres;apellidos;celular |
| `alumnos.csv` | ArregloAlumnos | codAlumno;dni;nombres;apellidos;fechaNacimiento;celular |
| `vinculos.csv` | ArregloAlumnos | codAlumno;dniApoderado;esPrincipal |
| `aulas.csv` | ArregloAulas | codigo;nombre;edadRequerida;capacidad |
| `cuotas.csv` | ConfiguracionCuotas | cuotaInscripcion;cuotaMatricula |
| `solicitudes.csv` | ArregloSolicitudes | codigo;codAlumno;codAula;fechaRegistro;estado;fechaIngresoCola;fechaHabilitacion;vencimientoHabilitacionOriginal;fechaConfirmacionInscripcion;fechaCancelacion;motivoCancelacion;responsableCancelacion;inscripcionConfirmadaEl;inicioCicloOfertas |
| `documentos.csv` | (por solicitud, expediente) | codSolicitud;tipo;fechaHoraEntrega;fechaHoraCorreccion;estado;observacion;fechaHoraComunicacion |
| `ofertas.csv` | (por solicitud) | codSolicitud;fechaHora;personal;aceptada |
| `matriculas.csv` | ArregloMatriculas | numMatricula;codSolicitud;estado;fechaHoraCreacion;vencimientoPagoOriginal;fechaCancelacion;motivoCancelacion;responsableCancelacion |
| `entrevistas.csv` | ArregloEntrevistas | codSolicitud;fechaHora;personal;responsable;asistioApoderado;realizada;inasistencias;resultado |
| `pagos.csv` | ArregloPagos | codSolicitud;concepto;numMatricula;montoPagado;medio;numeroOperacion;fechaHoraOperacion;fechaHoraRegistro;rutaComprobantePago;comprobante;estado;fechaAnulacion;motivoAnulacion;responsableAnulacion |

Detalles del formato:
- **Escape de campos (decisión 9):** antes de unir con `;`, cada campo se escapa (`\` → `\\`, `;` → `\;`, `\n` → `\n` literal escapado); al leer, se divide respetando el escape y se desescapa. Esto preserva motivos, observaciones, nombres y rutas que contengan `;`, `,` o saltos de línea.
- `solicitudes.csv:inscripcionConfirmadaEl` = `expediente.getInscripcionConfirmadaEl()` (vacío si no hay expediente). Los 4 `documentos` se leen contra ese expediente.
- `solicitudes.csv:inicioCicloOfertas` requiere un getter nuevo en `Solicitud` (hoy no existe); se restaura tal cual para conservar el ciclo de ofertas vigente tras reabrir.
- `entrevistas.csv:personal` = los participantes unidos por `,`; cada nombre se escapa con la regla de la decisión 9 (`\`, `;` y saltos de línea). **Limitación:** la decisión 9 no escapa la coma, y aquí la coma es el separador de la lista, de modo que un nombre de participante que contenga una coma no sobrevive la ida y vuelta (se partiría en dos participantes).
- `entrevistas.csv` identifica la solicitud por `codSolicitud`; al reconstruir, la entrevista se liga al expediente **actual** de esa solicitud. **Limitación documentada:** una entrevista de un ciclo de inscripción ya superado no se distingue de la vigente; el flujo no permite conservar entrevistas de una inscripción reemplazada (anular inscripción con matrícula vigente está bloqueado), así que no se modela ese caso.
- `matriculas.csv:numMatricula` = `Matricula.getNumMatricula()`; `codSolicitud` referencia la solicitud.
- `pagos.csv:numMatricula` vacío para concepto `INSCRIPCION`; presente para `MATRICULA`.
- `siguienteCodigo`/`siguienteNumero` **no se persisten**: se recalculan como `max+1` al cargar (el `ArregloAlumnos` ya lo hace).
- **Integridad de carga:** un archivo inexistente deja su colección vacía (o los seeds/defaults de su ctor); una línea malformada, una referencia a un código/DNI inexistente o una clave duplicada lanzan `IOException` con la línea, sin sobrescribir datos existentes. No se acepta una carga aparentemente válida pero incompleta.
- **Semillas y demo:** `PruebaPagos` (prueba y demo gráfica) apunta el directorio de persistencia a un directorio temporal antes de construir `PrincipalUI`, de modo que la demo no toca `datos/`. El directorio `datos/` de producción se agrega a `.gitignore` junto a las salidas.

## Criterios de aceptación

- [ ] Al abrir la app, `Almacen.cargar()` corre **antes** del barrido de `Vencimientos`; los paneles se crean después.
- [ ] Cerrar y reabrir conserva TODO el estado: aulas, alumnos, vínculos, apoderados, cuotas, solicitudes (con expediente/documentos y ofertas, incluidas las fechas de ingreso a cola), matrículas, entrevistas y pagos — incluidas las auditorías (anulación/cancelación).
- [ ] Una solicitud registrada conserva su cola y su fecha de ingreso original tras reabrir (ejemplo del issue).
- [ ] Un vencimiento ocurrido con la app cerrada se procesa al reabrir (avanzando el `Reloj` en la prueba): invitación 48 h y matrícula 72 h.
- [ ] Ninguna operación queda sin grabar: mantenimiento de datos, pagos (inscripción y matrícula), anulación, documentos, entrevistas, matrículas, cuotas — **y los cambios que el barrido de vencimientos aplica aunque la operación del Personal se rechace**.
- [ ] Estado intacto ante rechazos: una operación fallida no deja datos parciales; si el barrido mutó estado antes del rechazo, esa mutación sí queda grabada.
- [ ] Campos con `;`, `,` o saltos de línea sobreviven la ida y vuelta (escape/desescape).
- [ ] Pruebas de regresión en `PruebaPagos`: casos nombrados con directorio temporal y semilla determinista que (a) graban, "cierran", recargan en objetos nuevos y comparan el estado, (b) verifican el barrido al reabrir con el `Reloj` avanzado, (c) confirman que un rechazo sin mutaciones no graba, (d) confirman que un rechazo con vencimientos aplicados sí graba.

## Fuera de alcance

- Pantalla de mantenimiento de alumnos/apoderados (no existe hoy; el issue no la pide).
- Nueva entidad `Evento`/`Historial` o pantalla de historial.
- Copiar los archivos de comprobante de pago a una carpeta propia (solo se persiste la **ruta**).
- Autenticación, dependencias externas, base de datos, cambios de arquitectura más allá del coordinador `Almacen`.
- Migrar los seeds de `ArregloAulas`/`ConfiguracionCuotas` a archivos versionados (siguen siendo los defaults; el archivo, si existe, reemplaza).
