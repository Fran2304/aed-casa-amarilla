# Spec: Matrícula PENDIENTE_PAGO, ciclo de 72 h y UI de matrícula y pago (issues #23, #24, #26)

**Date:** 2026-10-11 · **Status:** Implementado (rama feature/issue-23-matricula-pago)
**Origen:**
- [#23](https://github.com/Fran2304/aed-casa-amarilla/issues/23) — [DIFÍCIL] Creación de matrícula PENDIENTE_PAGO · `dificil`, `negocio`
- [#24](https://github.com/Fran2304/aed-casa-amarilla/issues/24) — [DIFÍCIL] Ciclo de 72 horas: ACTIVA o CANCELADA · `dificil`, `negocio`
- [#26](https://github.com/Fran2304/aed-casa-amarilla/issues/26) — [MEDIO] UI: Matrícula · pago y activación · `medio`, `ui`

**Flujo:** `docs/flujo_matricula_casa_amarilla_2027_ia.md` — §4.6, §4.7 y §6.4 · **Manual:** `docs/resumen_temas_manual_ia.md` — Unidad 3
**Diseño:** [Figma · Nido La Casa Amarilla 2027](https://www.figma.com/design/IkwxtdIlJIiat4jhOURfiC/Nido-La-Casa-Amarilla-2027-gpt6-sol?node-id=0-2&p=f&t=7CZfiVNmyifhiVd2-0) — frame **`05 · Matrícula y pago`** (título «Matrícula · Mía León»)

**Orden de implementación:** #23 → #24 (depende de #23 y de las validaciones de pago de #12) → #26 (pantalla sobre la lógica de #23 y #24).

## Especificación

### 1. Creación de matrícula PENDIENTE_PAGO (#23)

- Crear la matrícula cuando se cumplen **las tres**: 4 documentos validados + entrevista favorable + vacante disponible (con oferta aceptada).
- Reglas:
  1. `numMatricula` correlativo desde **100001**; fecha/hora del sistema.
  2. La creación reserva **exactamente una** vacante.
  3. Un alumno solo puede tener una matrícula `PENDIENTE_PAGO` o `ACTIVA` a la vez; las canceladas quedan en historial.
  4. Vence a las **72 horas corridas desde su creación**.
- La solicitud sale de la cola favorable al crearse su matrícula.

### 2. Ciclo de 72 horas: ACTIVA o CANCELADA (#24)

- Verificación del pago de matrícula contra el plazo:
  1. Pago **válido** y operación dentro de las 72 h desde creación → matrícula `ACTIVA`. No descuenta otra vacante (ya estaba reservada).
  2. **Más de 72 h sin pago registrado** → matrícula `CANCELADA`: liberar la vacante y devolver la solicitud **al final de `EN_ESPERA_FAVORABLE`** con nueva fecha de ingreso, conservando su inscripción pagada.
  3. **Pendiente §6.4 decidido:** no existe pago observado. Un pago que no pasa las validaciones de #12 se rechaza y no se registra, así que al vencer el plazo solo cuenta si hay un pago registrado o no. Un pago rechazado equivale a ausencia de pago.
- Una solicitud devuelta a la cola favorable puede recibir una nueva oferta → nueva matrícula con nuevo plazo de 72 h.

### 3. UI: Matrícula · pago y activación (#26)

- Pantalla de detalle de matrícula según el diseño:
  - Identificación: MAT-xxxx, SOL-xxxx, aula, responsable de pago + franja «MATRÍCULA: PENDIENTE_PAGO» con fecha de creación, vencimiento 72 h y aviso «Reserva 1 vacante ahora».
  - Panel de pago: campos del comprobante + alerta «EN REVISIÓN · Recibir no equivale a confirmar».
  - Panel «Validación y cierre»: comprobaciones (concepto/monto, medio/n° operación, fecha real dentro de 72 h, comprobante verificable) + botón «Confirmar pago → ACTIVA».
  - Tarjeta de vencimiento sin pago registrado: matrícula `CANCELADA`, liberación de vacante y retorno de la solicitud al final de la espera favorable.
  - Estado `ACTIVA` en verde del tema con la matrícula confirmada.

## Decisions

### Ya existente (no se reimplementa)
- `Matricula` nace `PENDIENTE_PAGO` con `vencimientoPagoOriginal = creación + 72 h`; `Vacantes.calcular` ya cuenta `PENDIENTE_PAGO` + `ACTIVA`; `Pago` ya rechaza un pago de matrícula operado o registrado en o después del vencimiento.
- Con matrícula vigente la solicitud **permanece** en `EN_ESPERA_FAVORABLE` y `Turnos` la excluye (§6.8): "sale de la cola favorable" se cumple sin estado nuevo.
- §6.4 (pago observado): resuelto en #24 — ver punto 3 de la sección 2.

### Numeración y almacenamiento
- `numMatricula` es `int` correlativo desde **100001**, como `codAlumno` — lo fija la rúbrica (`docs/requerimientos_proyecto.md:18`). La UI lo muestra con prefijo `MAT-100001` para conservar la etiqueta del diseño; el «MAT-2071» de los ejemplos es ilustrativo.
- Nueva clase `datos.ArregloMatriculas` con `PRIMER_NUMERO = 100001` y `siguienteNumero`, espejo de `ArregloSolicitudes`. Reemplaza a `ArrayList<Matricula>` en todas las firmas (`Turnos`, `Cobros`, `PanelPagos`, `PrincipalUI`, `PruebaPagos`).
- `ArregloMatriculas` es solo almacenamiento y consultas (`registrar`, `buscar`, `vigenteDe`, `ultimaDe`, `contiene`, `listar`). El barrido de 72 h vive en `negocio.Vencimientos`: es una regla de plazos y colas, y dejarlo en `datos` creaba la dependencia circular `datos` → `negocio.Turnos` → `datos`.

### Creación (#23)
- La matrícula se crea dentro de `Turnos.confirmarOferta` cuando `aceptada = true`, en la misma operación — no existe el estado "oferta aceptada sin matrícula".
- Sin re-verificación defensiva de documentos ni entrevista: `EN_ESPERA_FAVORABLE` (§6.9) y `exigirTurno` (vacante + turno) ya lo garantizan. `ArregloMatriculas.registrar` agrega el control explícito "un alumno, una sola matrícula `PENDIENTE_PAGO`/`ACTIVA`".

### Pago y ciclo de 72 h (#24)
- Pago de matrícula en **un paso**, simétrico a `confirmarInscripcion`: nuevo `Cobros.confirmarMatricula` valida, confirma el pago y activa la matrícula. La alerta «EN REVISIÓN · Recibir no equivale a confirmar» del diseño es solo texto informativo.
- Si `confirmarMatricula` encuentra la matrícula vencida: ejecuta el barrido (matrícula `CANCELADA`, solicitud al final de la cola favorable) y lanza `ReglaDominioException`, para que el personal vea el resultado final y no una `PENDIENTE_PAGO` ya muerta. Solo barre en ese caso: el pago de matrícula no depende de vacantes. Si tras el barrido la matrícula sigue `PENDIENTE_PAGO`, el mensaje dice «venció, pero no se pudo cancelar: …» con el motivo, en vez de afirmar que quedó cancelada.
- Un pago de matrícula no puede tener operación anterior a la creación de la matrícula (`Pago.exigirEstadoPagable`), espejo del chequeo de habilitación de la inscripción: las 72 h corren desde la creación, así que un pago anterior no puede liquidar una matrícula que no existía. Antes solo lo impedía la validación de la tarjeta.
- Fecha de reingreso tras cancelar: `ahora` pasado por `fechaAlFinalFavorable` (no el instante del vencimiento: el barrido puede correr días después y la solicitud debe quedar realmente al final). Mismo criterio que `Cobros.anularPago`.
- La cancelación por vencimiento solo ocurre si la solicitud está en `EN_ESPERA_FAVORABLE`. La matrícula solo nace en la cola favorable y ese estado no tiene salidas, así que siempre se cumple; si algún día no, el barrido lo rechaza antes de tocar nada (cancelar sin reencolar dejaría la solicitud a medias y sin aviso) y la matrícula sigue `PENDIENTE_PAGO`.

### Barridos de vencimientos
- **Un único punto de entrada:** `negocio.Vencimientos.revisar(ahora, solicitudes, matriculas)` aplica los tres barridos siempre en el mismo orden (matrículas → habilitaciones → documentación), para que el estado final no dependa de qué pantalla barrió primero.
- **Las consultas no barren.** `Turnos.ordenPorPrioridad`, `siguiente` y `exigirTurno` solo leen: mirar la cola de un aula no cancela nada (lo necesitará la pantalla de colas de #25). Barren las operaciones (`confirmarOferta`, `ubicarNueva`, `revisarTurnos`, `Cobros.confirmarInscripcion`) y las pantallas al abrirse o buscar (`PrincipalUI`, `PanelPagos.buscar`, `PanelExpediente`).
- **Responsable fijo `Vencimientos.RESPONSABLE = "Sistema"`.** Un vencimiento no lo decide nadie del personal; registrar a quien abrió la pantalla haría depender el historial de qué pantalla barrió primero. Es el mismo valor que ya usaba `PanelExpediente`.
- **Los fallos se devuelven como avisos, no se lanzan.** Un ítem que no se pudo barrer no debe frenar el pago o la oferta de otra solicitud. Quien depende de un ítem concreto comprueba después su estado: `Cobros` responde «venció, pero no se pudo cancelar / devolver a la cola: …»; `confirmarOferta` añade los avisos al mensaje si la oferta se rechaza (un «no hay vacante» causado por una matrícula que no se pudo cancelar explica su causa); las pantallas los muestran en la tarjeta visible o en su línea de mensajes; `PrincipalUI` en un diálogo.
- **Matrículas una por una:** si una falla, las demás se procesan igual (si no, las siguientes vencidas seguirían reservando vacante por un problema ajeno). Se recorre una copia de la lista porque cancelar cambia lo que se recorre.
- **`confirmarInscripcion` barre una sola vez, al inicio.** Antes anota si la habilitación de la solicitud ya venció, porque después del barrido ya no queda rastro para explicar el rechazo; `PanelPagos.buscar` hace lo mismo para pintar la tarjeta.
- **`confirmarOferta` barre antes de cualquier comprobación:** cancelar una matrícula vencida reinicia el ciclo de ofertas de su solicitud; sin barrer, se la rechazaría por una oferta aceptada que ya no vale.
- **`exigirTurno` comprueba primero «ya tiene matrícula vigente»** y después arma el orden de la cola, que es lo caro.
- **Arranque (`PrincipalUI`):** barre antes de crear los paneles, para que nazcan con el estado ya vencido; cuando exista la carga desde archivos tiene que ir antes de ese barrido. El diálogo de avisos se muestra con `SwingUtilities.invokeLater`: el constructor corre antes del `setVisible`, y un modal con la ventana dueña oculta aparece suelto y frena la aplicación.
- `revisarTurnos` y `ubicarNueva` no devuelven los avisos: no fallan por falta de vacante, solo dejan de habilitar. Hoy nadie las llama; la pantalla que las use tendrá que mostrar los suyos al barrer.

### Auditoría de cancelación
- `Matricula.cancelar(fecha, motivo, responsable)` guarda `fechaCancelacion`, `motivoCancelacion`, `responsableCancelacion` — espejo de `Solicitud.cancelarPorDocumentacion` (#17). Un único método para los dos caminos que cancelan:
  - Barrido: «Vencimiento de 72 h sin pago registrado».
  - `Cobros.anularPago` con plazo vencido: «Anulación de pago con plazo de 72 h vencido».
- Cubre "el historial muestra la matrícula cancelada" a nivel de datos; la UI de `PanelHistorial` queda fuera.

### UI (#26)
- La vista vive **dentro de «Pagos y matrículas»** (card `PAGOS`), como dibuja el menú del diseño — sin card nueva. Al buscar: si la solicitud tiene matrícula se muestra la vista del diseño 05; si no, la vista de inscripción actual.
- El buscador de `PanelPagos` acepta `SOL-1029` y `MAT-100001`; muestra la matrícula vigente o, si no hay, la última cancelada (hace visible la tarjeta de vencimiento).
- Buscar por `MAT-` una matrícula cancelada la muestra (es historial), pero si la solicitud ya tiene otra vigente el bloqueo la nombra: «La solicitud tiene ahora MAT-100004 (PENDIENTE_PAGO); búscala para registrar el pago» (o «para ver su pago» si está `ACTIVA`). Sin eso parecía que no quedaba nada que cobrar.
- Panel de pago y «Validación y cierre»: se reutiliza `TarjetasPago` con la cuota de matrícula y el plazo creación → +72 h; sus cuatro comprobaciones son las del diseño. `AccionConfirmar` → `Cobros.confirmarMatricula`.
- Anulación: `TarjetasPago.configurarAnulacion` → `Cobros.anularPago` existente (§6.5), sin lógica nueva. El pago queda `ANULADO` y registrado; no existe ni se agrega borrado de pagos.
- Sin botón «Registrar observación»: el diseño 05 lo trae, pero contradice §6.4.
- Colores de la franja por estado (`PENDIENTE_PAGO` amarillo espera, `ACTIVA` verde, `CANCELADA` rojo) en `Estilos`, con un `chip(EstadoMatricula)` junto al de `EstadoSolicitud`.
- Las tres fábricas de chip comparten un `chip(texto, fondo, frente)` privado: solo eligen texto y colores, así un cambio de aspecto no se olvida en ninguna. Los dos chips de estado usan el mismo texto (`_` → espacio: «PENDIENTE PAGO»); la franja y los mensajes siguen con el nombre crudo del estado.
- Sin fuentes ni bordes literales en `PanelPagos` ni `TarjetasPago` (regla de CLAUDE.md): usan las fábricas de `Estilos` (`dato()`, `datoDestacado()`, `detalle()`, `etiqueta()`, `contenidoDestacado()`, la nueva `nota()`) y los márgenes `MARGEN_PANTALLA`, `MARGEN_FRANJA` y `MARGEN_TEXTO_AJUSTADO`.
- El hueco `cierre` de `TarjetasPago` solo cuenta como visible si su aviso lo está: `GridBagLayout` salta los componentes invisibles con sus márgenes, así no queda una fila vacía en la tarjeta de inscripción (sin aviso) ni con la matrícula `ACTIVA` (aviso oculto). Se calcula en `isVisible()` y no con un listener porque los eventos de visibilidad llegan después.
- `TarjetasPago.avisar(texto)` se llama después de pintar la tarjeta, porque pintarla limpia el mensaje.
- Fechas: `util.FormatoFecha.FECHA_HORA` (`dd/MM/yyyy HH:mm`) es el único formato de fecha y hora de pagos; `TarjetasPago.FORMATO_FECHA` apunta a él y los mensajes de `Cobros` y `Pago` usan `FormatoFecha.fechaHora`, para que el mensaje muestre la fecha igual que la tarjeta de al lado.
- La operación de un pago se escribe sin segundos, así que se compara contra el inicio del plazo llevado al minuto (`FormatoFecha.alMinuto`), en `Pago` y en `TarjetasPago`. Si no, el valor que propone el formulario («10:15») se rechazaba durante el primer minuto con «Es anterior al inicio del plazo (10:15)».
- `ArregloPagos.inscripcionConfirmadaDe` / `matriculaConfirmadaDe` devuelven el pago (o `null`): la pantalla lo necesita para pintarlo, y así la regla de «confirmado» vive en un solo sitio para `Cobros` y la UI.

### Demo y pruebas
- `cargarCasos` crea con `confirmarOferta` tres matrículas: `PENDIENTE_PAGO` vigente, `ACTIVA`, y una con 72 h vencidas que cancela el barrido. El caso pendiente usa a Rosa León: Mía León ya es el caso de inscripción habilitada (SOL-1001) de la demo.
- Pruebas en `PruebaPagos` con fechas de dominio fijas, casos nombrados, errores esperados específicos y comprobación de estado intacto tras rechazo.

## Acceptance criteria

### #23
- [x] Primera matrícula = 100001, con fecha/hora del sistema; la UI la muestra como `MAT-100001`.
- [x] Confirmar una oferta aceptada crea la matrícula `PENDIENTE_PAGO` en la misma operación; una oferta no aceptada no crea matrícula.
- [x] Crear matrícula con alumno ya matriculado (`PENDIENTE_PAGO`/`ACTIVA`) → rechazado; estado intacto.
- [x] La vacante queda reservada inmediatamente (visible en `Vacantes.calcular`).
- [x] La fecha de vencimiento 72 h queda registrada.
- [x] La solicitud con matrícula vigente sigue en `EN_ESPERA_FAVORABLE` pero no aparece en turnos ni recibe otra oferta.

### #24
- [x] Pago válido en plazo → pago `CONFIRMADO` + matrícula `ACTIVA` sin doble descuento de vacante.
- [x] Pago tras el vencimiento → rechazado; la matrícula queda `CANCELADA` por el barrido y la solicitud al final de la cola favorable.
- [x] Sin pago registrado a las 72 h → el barrido deja `CANCELADA` + vacante liberada + solicitud al final de la cola favorable con fecha = momento del barrido, inscripción conservada.
- [x] El barrido corre desde `Vencimientos.revisar` en las operaciones que dependen de vacantes, en la búsqueda de «Pagos y matrículas», en Expediente y al abrir la aplicación; consultar el orden de la cola no cancela nada.
- [x] Toda matrícula `CANCELADA` (barrido o anulación con plazo vencido) guarda fecha, motivo y responsable.
- [x] Una solicitud devuelta a la cola favorable puede recibir nueva oferta → nueva matrícula con nuevo número y nuevo plazo de 72 h.
- [x] El criterio del pendiente §6.4 está decidido y documentado.

### #26
- [x] Buscar por `SOL-xxxx` o `MAT-xxxxxx` en «Pagos y matrículas» abre la vista de matrícula cuando existe; sin matrícula, la vista de inscripción.
- [x] La franja y la tarjeta de vencimiento reflejan el estado real y la fecha límite de 72 h.
- [x] «Confirmar pago → ACTIVA» aplica `Cobros.confirmarMatricula` y refresca la pantalla.
- [x] Con matrícula `ACTIVA` la pantalla muestra el estado final y bloquea la edición del pago; anular el pago aplica §6.5 y refresca.
- [x] No hay botón «Registrar observación».
- [x] Demo no-headless: `cargarCasos` muestra una matrícula `PENDIENTE_PAGO`, una `ACTIVA` y una cancelada por vencimiento.

## Non-goals / Deferred

- UI de #25 (colas y botón «Confirmar oferta y crear matrícula»): en esta entrega la matrícula solo nace desde `cargarCasos`.
- UI de `PanelHistorial`.
- Pago de matrícula en dos pasos (`RECIBIDO` → `CONFIRMADO`) y cualquier estado de pago observado.
- Borrado físico de pagos o matrículas.
- Persistencia, reloj inyectable.

### Sin arreglar a propósito (revisión de código)
Criterio: no se arregla lo que no pasa en la práctica.

- **Barridos de `ArregloSolicitudes` sin tolerancia por ítem.** `vencerHabilitaciones` y `cancelarDocumentacionVencida` (código de `main`) se cortan en la primera solicitud que falla y vuelven como un solo aviso. Hoy no pasa: cada bucle filtra con la misma condición que valida el método. Retomar si cambia `Transiciones` o la carga desde archivos trae datos inconsistentes (hacer tolerantes los bucles o moverlos a `Vencimientos`).
- **Posición al volver a la cola sin pago.** La habilitación vencida reingresa con la fecha del vencimiento; la matrícula vencida, con la del barrido. El flujo dice «al final ... con nueva fecha de ingreso». En la práctica queda al final porque registrar una solicitud barre antes; el único caso distinto es anular un pago de inscripción con la habilitación original vencida mientras otra habilitación vencida sigue sin barrer.
- **Avisos del barrido que se pisan:** en Expediente y al anular un pago, el mensaje de éxito reemplaza el aviso de barrido incompleto. Solo se nota si falla un ítem del barrido.
- **`Pago` y `Cobros.prepararAnulacionMatricula` aceptan `EN_DOCUMENTACION`** para una matrícula, aunque ese estado no puede darse con matrícula; el barrido ya no lo acepta.
- **«Guardar el vencimiento antes de barrer» duplicado** en `PanelPagos.buscar` y `Cobros.confirmarInscripcion`. Quitarlo exigiría que `revisar` devuelva qué venció, cambiando código de `main`.
- **Doble barrido** entre buscar y confirmar: justificado, porque entre una acción y otra pasa tiempo.
- **`Vacantes.calcular` copia la lista de matrículas dos veces:** sin costo observable con el volumen de un nido.
- **Búsqueda por `SOL-` con una matrícula cancelada** abre la vista de matrícula y oculta la tarjeta de la inscripción confirmada. No se pierde ninguna acción: la inscripción no se puede anular fuera de `EN_DOCUMENTACION`.
- **Fechas sin formato** en `Solicitud.java:120` y `:159`, `Documento.java:75` y `ExpedienteDocumentos.java:62` (código de `main`). Las de `Solicitud` no llegan a verse porque `Cobros` y `Pago` validan antes.
- **Ninguna pantalla crea matrículas** hasta #25 (ver arriba): hoy solo nacen en la demo de `PruebaPagos`.

## Ejemplos

- **#23:** MAT-2071 · SOL-1029 · Girasoles · creada 28/sep → vence 01/oct (72 h) · «Reserva 1 vacante ahora» (diseño 05).
- **#24:** MAT-2071 creada 28/sep 12:00 → pago Yape 29/sep 09:00 → `ACTIVA`.
- **#24:** MAT-2071 sin pago al 01/oct 12:00 → `CANCELADA` → vacante liberada → solicitud de Mía León al final de `EN_ESPERA_FAVORABLE`.
  - Nota: el issue dice «01/sep»; por la fecha de creación (28/sep 12:00) corresponde **01/oct 12:00**.
- **#26:** MAT-2071 · SOL-1029 · Girasoles · Carlos León · `PENDIENTE_PAGO` · vence en 72 h · pago Yape YP-8291457 S/ 850 (diseño 05).
