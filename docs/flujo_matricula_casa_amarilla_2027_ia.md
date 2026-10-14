# Flujo de matrícula 2027 - Nido La Casa Amarilla

> **Tipo:** especificación funcional estructurada para consulta por IA y desarrollo de software.  
> **Fuente:** `flujo_completo_matricula_casa_amarilla_2027 (1).pdf`, 6 páginas, proporcionado por el usuario.  
> **Alcance:** primera entrega del sistema institucional de escritorio para 2027.  
> **Criterio de lectura:** las reglas de este archivo describen el proceso del negocio. Las secciones **Pendiente de definición** no autorizan a inventar una regla.

## 1. Resumen operativo

1. Un apoderado solicita una vacante para un alumno en un aula.
2. El sistema valida datos, edad, aula y solicitudes activas duplicadas.
3. Consulta vacantes y prioridad. Si no puede iniciar la inscripción, la solicitud espera sin pago.
4. El personal verifica el pago de inscripción. Su confirmación inicia el plazo de documentos.
5. El apoderado entrega cuatro documentos y el personal los valida.
6. Se agenda y realiza la entrevista.
7. Si la entrevista es favorable, el sistema vuelve a consultar vacantes. Puede haber espera favorable.
8. Se crea una matrícula `PENDIENTE_PAGO`, que reserva una vacante.
9. El pago de matrícula válido y oportuno activa la matrícula.

## 2. Actores y registros mencionados

| Elemento | Función o relación descrita |
|---|---|
| Apoderado | Solicita la vacante, paga, entrega documentos y responde a ofertas. Debe haber al menos uno por solicitud y exactamente uno principal. |
| Alumno | Postulante. Su edad se calcula al 31/03/2027 para verificar el aula. |
| Personal | Registra datos, verifica pagos y documentos, agenda y registra entrevistas, comunica observaciones y confirma ofertas. |
| Aula | Tiene una capacidad utilizada para calcular vacantes. |
| Solicitud | Vincula al alumno con el proceso de admisión de 2027. Puede entrar en las colas descritas abajo. |
| Invitación | Ofrece a una solicitud en espera sin pago iniciar la inscripción. Dura 48 horas y no reserva vacante. |
| Pago | Corresponde a inscripción o matrícula. Todo pago se vincula a una solicitud; el de matrícula también a su matrícula. |
| Documento | Evidencia requerida para continuar a entrevista. |
| Entrevista | Registra asistencia, resultado y personal participante. |
| Matrícula | Se crea tras documentos validados, entrevista favorable y vacante disponible. `PENDIENTE_PAGO` y `ACTIVA` ocupan vacante. |

## 3. Estados expresamente nombrados

### Solicitud

| Estado | Significado en el flujo |
|---|---|
| `EN_ESPERA_SIN_PAGO` | Aún no puede comenzar la inscripción; tiene fecha de ingreso a la cola. |
| `HABILITADA_PARA_PAGO` | **Decisión del grupo, no está en el PDF.** Solicitud nueva con vacante y sin nadie delante, o a la que le llegó el turno en la cola sin pago al liberarse una vacante; tiene 48 h desde la habilitación para pagar la inscripción. No reserva vacante. |
| `EN_DOCUMENTACION` | Inscripción pagada y confirmada; corre el plazo inicial de documentos. |
| `EN_ESPERA_FAVORABLE` | Documentos validados y entrevista favorable, pero sin vacante para crear matrícula. Conserva la inscripción pagada. **Decisión del grupo, no está en el PDF:** con matrícula `PENDIENTE_PAGO` o `ACTIVA` vigente, la solicitud permanece en este estado; no se le ofrece otra vacante y las consultas de candidatos la excluyen (§6, ítem 8). |
| `CANCELADA` | Incumplimiento de entrega o corrección de documentos, segunda inasistencia u otra cancelación indicada por el flujo. Se conserva en el historial. |
| `RECHAZADA` | Entrevista realizada con resultado `NO_FAVORABLE`. Se conserva en el historial. |

El PDF no nombra todos los estados intermedios de la solicitud. No asignarles nombres adicionales como si fueran parte de la fuente. La única excepción es `HABILITADA_PARA_PAGO`, que el grupo agregó para distinguir a la solicitud que puede pagar de las que solo esperan en la cola.

### Matrícula y pago

| Entidad | Estado | Efecto descrito |
|---|---|---|
| Matrícula | `PENDIENTE_PAGO` | Reserva una vacante desde su creación. |
| Matrícula | `ACTIVA` | Pago de matrícula válido y confirmado dentro del plazo; mantiene la vacante ya reservada. |
| Matrícula | `CANCELADA` | Libera la vacante y queda en el historial. |
| Pago | `CONFIRMADO` | Pago verificado. Un pago confirmado no se edita. |

## 4. Reglas del proceso

### 4.1. Solicitud y validaciones iniciales

**Entrada:** alumno, apoderados y aula solicitada.

1. Registrar los datos del alumno y sus apoderados.
2. Exigir **al menos un apoderado** y **exactamente uno principal**.
3. Antes de crear la solicitud o pedir un pago, validar los datos obligatorios.
4. Calcular la edad al **31 de marzo de 2027** y comprobar que corresponde al aula.
5. Buscar otra solicitud activa del mismo alumno para 2027. Si existe, mostrar esa solicitud y evitar el duplicado.
6. Con datos válidos, guardar la solicitud y consultar disponibilidad y prioridad.

**Unicidad:** solo una solicitud activa por alumno en 2027. Las canceladas y rechazadas permanecen como historial.

### 4.2. Vacantes, prioridad y espera sin pago

~~Las vacantes y prioridades solo se revisan durante la operación que las solicita.~~
**Resuelto (issue #27):** el motor global `Vencimientos` barre habilitaciones,
documentación y matrículas vencidas al arrancar y antes de calcular turnos o vacantes.

```text
vacantes = capacidad_del_aula
         - cantidad_de_matriculas_del_aula_en(PENDIENTE_PAGO, ACTIVA)
```

- No almacenar las vacantes en un contador mutable; calcularlas con la capacidad y las matrículas vigentes.
- La consulta de disponibilidad, la solicitud y el pago de inscripción **no reservan cupo**.
- Si hay disponibilidad y ninguna solicitud de mayor prioridad debe atenderse antes, la solicitud pasa de frente a `HABILITADA_PARA_PAGO` y el personal comunica la cuota (decisión del grupo).
- Si hay cola delante y no quedan vacantes, colocar la solicitud en `EN_ESPERA_SIN_PAGO` con fecha de ingreso a esa cola. Mientras esté allí, no cobrar, revisar documentos ni agendar entrevista.
- Cuando se libera una vacante, atender primero la cola de entrevista favorable e inscripción pagada; después la cola sin pago. Dentro de cada cola, respetar la antigüedad.
- Identificar la solicitud concreta a la que corresponde el turno. Una vacante no habilita simultáneamente a toda la cola.

**Habilitación desde la cola sin pago (decisión del grupo):**

1. Al liberarse una vacante, la solicitud a la que le toca el turno pasa a `HABILITADA_PARA_PAGO` sin acción del personal. El colegio avisa al apoderado por teléfono, fuera del sistema.
2. La habilitación dura **48 horas desde que se habilita** y no reserva la vacante.
3. Durante ese plazo, el apoderado paga y presenta los datos o comprobante.
4. Las 48 horas no se pausan ni se reinician mientras se recibe o verifica el pago. Si el pago se rechaza, el apoderado puede volver a pagar solo dentro del mismo plazo.
5. Si vence sin inscripción confirmada, la solicitud vuelve de `HABILITADA_PARA_PAGO` **al final** de `EN_ESPERA_SIN_PAGO` con nueva fecha de ingreso. Reevaluar prioridades.
6. **Decisión:** una solicitud con inscripción pagada en `EN_DOCUMENTACION` conserva su turno hasta que obtiene matrícula, se rechaza o se cancela. Mientras tanto, la vacante no se ofrece a otra solicitud de la cola sin pago ni a una solicitud nueva.
7. **Decisión:** una solicitud habilitada ocupa su turno frente a la cola sin pago y a las solicitudes nuevas hasta que paga o vence, pero no frente a la cola favorable. Si vence, reingresa con la fecha en que venció la habilitación.

**Decisión del grupo:** la regla de 48 horas también se aplica a la solicitud nueva que se habilita de frente; el PDF no define un plazo para esa vía.

### 4.3. Pago de inscripción

- Verificar concepto, monto, medio de pago, fecha real de la operación y número de operación cuando corresponda.
- La fecha real de la operación no puede ser futura ni anterior a la habilitación ni al registro de la solicitud: el plazo de pago empieza en la más tardía de las dos y termina al vencer la habilitación.
- Si el pago no cumple alguna validación, se rechaza y no se registra. No existe estado de observación para pagos; el apoderado debe realizar y presentar un pago nuevo y válido.
- El pago solo se aprueba cuando se confirma la operación (por ejemplo, que la transferencia llegó).
- Un pago puede confirmarse antes de tener número de comprobante: el número se emite después y se registra una sola vez.
- Antes de confirmar se comprueba, en este orden, que la inscripción no esté ya pagada, que la habilitación siga vigente y que a la solicitud le corresponda el turno (§4.2, regla 7); recién después se validan los datos del pago. Si algo falla, el pago no se registra y la solicitud no cambia: nunca queda un pago confirmado con la solicitud fuera de `EN_DOCUMENTACION`, ni se cobra una cuota no reembolsable sin turno.
- Si la habilitación ya venció al intentar pagar, la solicitud vuelve en ese momento al final de `EN_ESPERA_SIN_PAGO` y el rechazo indica cuándo venció.
- Si el pago es válido, conservar el monto aplicado, marcarlo `CONFIRMADO`, cambiar la solicitud a `EN_DOCUMENTACION` e iniciar el plazo de entrega de documentos.
- La cuota incluye gestión y revisión de documentos y **no es reembolsable**, incluso ante inelegibilidad posterior, entrevista no favorable, falta de vacante o cancelación posterior.

### 4.4. Documentos

~~El vencimiento documental depende de la sesión abierta.~~ **Resuelto (issue #27):**
se usa el `Reloj` global inyectable y la fecha exacta de entrega o corrección es el límite.

**Requeridos:** DNI del alumno, DNI del apoderado principal, partida de nacimiento y carné de vacunas.

- Desde la confirmación de la inscripción, el apoderado tiene **7 días calendario** para entregar los cuatro documentos.
- El plazo inicial exige **entrega**, no validación terminada. Si la entrega fue oportuna, una revisión institucional pendiente no convierte la solicitud en incumplida.
- El personal recibe y valida los documentos.
- Si hay una observación, la comunica y concede **7 días calendario para corregirla**. La documentación corregida vuelve a revisión.
- Si no se entregan los cuatro dentro del plazo inicial, o no se corrige una observación dentro del plazo concedido, la solicitud pasa a `CANCELADA`. Conservar historial y no reembolsar inscripción.
- Agendar entrevista solo cuando los cuatro documentos estén validados.

### 4.5. Entrevista

- Registrar agenda, realización y resultado.
- Debe participar **al menos un miembro del personal** y haber **exactamente uno responsable**.
- Por inasistencia del apoderado, permitir **una sola reprogramación**. La segunda inasistencia cancela la solicitud.
- Si la entrevista se realiza con resultado `NO_FAVORABLE`, la solicitud queda `RECHAZADA`.
- Un resultado `FAVORABLE` permite continuar, pero no garantiza vacante. Consultar disponibilidad de nuevo.
- Una solicitud nueva después de `RECHAZADA` o `CANCELADA` exige un nuevo pago de inscripción.

### 4.6. Vacante final y creación de matrícula

1. Tras la entrevista favorable, recalcular las vacantes del aula.
2. Si no hay vacante, colocar la solicitud en `EN_ESPERA_FAVORABLE`, conservando la inscripción pagada.
3. Cuando se libera una vacante, proponer al primer candidato de esa cola por fecha de ingreso. El personal confirma la oferta; si no se acepta, **no crear matrícula**.
4. Con cuatro documentos validados, entrevista favorable y vacante disponible, crear la matrícula `PENDIENTE_PAGO`.
5. Su creación reserva **una** vacante.

Un alumno solo puede tener una matrícula `PENDIENTE_PAGO` o `ACTIVA` a la vez. Las matrículas canceladas quedan en el historial. La cuota de matrícula es diferente de la cuota de inscripción; ambas se configuran por separado para 2027.

### 4.7. Pago de matrícula y cierre

~~Una matrícula sin pago se revisa únicamente al intentar pagar.~~ **Resuelto (issue #27):**
`Vencimientos` cancela las matrículas `PENDIENTE_PAGO` vencidas al arrancar y en los
bordes pre-vacantes, con responsable de auditoría `Motor de vencimientos`.

- El apoderado debe realizar el pago dentro de las primeras **72 horas corridas desde la creación** de la matrícula.
- Verificar concepto, monto, medio de pago, fecha real de la operación y número de operación cuando corresponda.
- Si el pago es válido y la operación ocurrió dentro de las primeras 72 horas, pasar la matrícula a `ACTIVA`. No descontar otra vacante: ya estaba reservada.
- Si transcurren más de 72 horas **sin pago registrado**, pasar la matrícula a `CANCELADA`, liberar la vacante y devolver la misma solicitud al **final de `EN_ESPERA_FAVORABLE`** con nueva fecha de ingreso. Conservar su inscripción pagada.
- Un pago `CONFIRMADO` no se edita. Si se anula por error de registro, devolver la matrícula a `PENDIENTE_PAGO` y reevaluar los plazos originales antes de mantener la reserva.

### 4.8. Reglas comunes de pagos y vencimientos

~~Cada pantalla obtiene la hora del sistema por separado.~~ **Resuelto (issue #27):**
`Reloj` es la única fuente global de hora; `Vencimientos.proximos` ofrece una consulta
de negocio de los vencimientos dentro de las próximas 24 horas, sin panel de inicio.

- Medios admitidos para ambos pagos: **efectivo, transferencia, tarjeta, Yape y Plin**.
- El número de operación es obligatorio excepto para efectivo.
- Cada pago conserva el monto aplicado al registrarse, aunque luego cambie la configuración.
- Todo pago guarda el comprobante que presenta el apoderado (captura o voucher de la operación) y no se confirma sin él. No es el número de comprobante que el nido emite después de confirmar (§4.3). Se guarda la ruta del archivo; copiarlo a una carpeta propia queda para la persistencia.
  **Resuelto (issue #82, solo demo):** el generador escribe PNG ficticios «DEMO — SIN VALOR»
  dentro de cada carpeta de demo y conserva sus rutas absolutas. No usa archivos personales
  ni `deleteOnExit`; esto no cambia la gestión de comprobantes de producción.
- Todo pago se relaciona con una solicitud. El pago de matrícula también se relaciona con su matrícula; el de inscripción ocurre antes de crearla.
- Revisar vencimientos al abrir la aplicación de escritorio y antes de operaciones que dependen de vacantes. El cierre de la aplicación no suspende los plazos.
  **Resuelto (issue #33):** al abrir, se cargan primero todas las entidades desde archivos
  (`Almacen`) y recién después corre el motor de vencimientos; tras cada operación que
  modifica datos se graba el estado. El historial son los campos de auditoría embebidos en
  solicitudes, matrículas y pagos (más las ofertas), que persisten con esas entidades: no se
  agrega una entidad `Evento`/`Historial`.

## 5. Eventos y resultados esperados

| Evento o condición | Resultado |
|---|---|
| Ya existe solicitud activa del alumno en 2027 | Mostrar la existente; no crear duplicado. |
| No puede comenzar la inscripción | `EN_ESPERA_SIN_PAGO`; sin cobro, revisión documental ni entrevista. |
| Vence la habilitación de 48 h sin inscripción confirmada | De `HABILITADA_PARA_PAGO` al final de `EN_ESPERA_SIN_PAGO`. |
| Pago de inscripción válido | Pago `CONFIRMADO`; solicitud `EN_DOCUMENTACION`; inicia plazo documental. |
| Faltan documentos al vencer 7 días calendario | Solicitud `CANCELADA`. |
| Vence plazo de corrección de una observación | Solicitud `CANCELADA`. |
| Segunda inasistencia a entrevista | Solicitud `CANCELADA`. |
| Entrevista `NO_FAVORABLE` | Solicitud `RECHAZADA`. |
| Entrevista `FAVORABLE`, sin vacante | `EN_ESPERA_FAVORABLE`; conserva inscripción pagada. |
| Documentos validados, entrevista favorable y vacante | Crear matrícula `PENDIENTE_PAGO`; reservar una vacante. |
| Pago de matrícula válido dentro de 72 h | Matrícula `ACTIVA`; sin descuento adicional de vacante. |
| Más de 72 h sin pago de matrícula registrado | Matrícula `CANCELADA`; liberar vacante; solicitud al final de espera favorable. |

**Resuelto (issue #27):** la consulta de próximos vencimientos devuelve código de solicitud,
alumno, tipo y fecha para habilitaciones, entrega/corrección documental y matrícula; no se
añade una pantalla de operaciones.

## 6. Pendiente de definición o aclaración

Estos puntos **no están resueltos en el PDF**. Una IA debe señalarlos antes de implementar comportamientos que dependan de ellos:

1. ~~Plazo de pago de inscripción para la **vía directa**.~~ **Resuelto (decisión del grupo):** la solicitud nueva se habilita de frente y tiene las mismas 48 h.
2. Lista completa de datos obligatorios, rangos de edad por aula y reglas de elegibilidad. **Decisión (2026-10-10):** se mantiene provisorio (igualdad exacta de edad al corte, aulas de ejemplo, celular del apoderado obligatorio «a confirmar») hasta que el nido entregue la lista real; los placeholders no se bendicen como definitivos.
3. ~~Cómo proceder cuando una oferta de la cola favorable **no es aceptada**: el PDF solo indica que no se crea matrícula.~~ **Resuelto (decisión del grupo, 2026-10-10):** no se crea matrícula; la solicitud reingresa al final de `EN_ESPERA_FAVORABLE` con nueva fecha de ingreso y la vacante queda libre para ofrecerse al siguiente candidato.
4. ~~Tratamiento de un pago de matrícula registrado pero observado al vencer el plazo.~~ **Resuelto (decisión del grupo):** los pagos no tienen estado de observación; un pago que no cumple las validaciones se rechaza y cuenta como ausencia de pago registrado. Al vencer las 72 h, la matrícula pasa a `CANCELADA`.
5. ~~Resultado de anular un pago confirmado cuando, al reevaluar el plazo original, ya no corresponde mantener la reserva.~~ **Resuelto (decisión del grupo, 2026-10-10):** al anular se reevalúa el plazo original, sin reiniciarlo. Inscripción: con remanente de las 48 h originales la solicitud vuelve a `HABILITADA_PARA_PAGO`; si ya venció, pasa al final de `EN_ESPERA_SIN_PAGO`. Matrícula: con las 72 h originales vencidas pasa a `CANCELADA`, libera la vacante y la solicitud va al final de `EN_ESPERA_FAVORABLE` conservando la inscripción pagada; vigente y `ACTIVA`, vuelve a `PENDIENTE_PAGO` manteniendo la reserva y el vencimiento original.
6. Nombres de estados intermedios que el PDF no enumera. **Resuelto en parte (decisión del grupo):** se agrega `HABILITADA_PARA_PAGO` para la solicitud de la cola sin pago a la que le llegó el turno. Los demás estados intermedios siguen sin nombre.
7. ~~Si el **número de operación** debe ser único (por medio de pago o en general). Hoy nada impide registrar dos pagos con el mismo número, incluso en solicitudes distintas.~~ **Resuelto (decisión del grupo, 2026-10-10):** el número de operación es único global entre todos los pagos registrados; el valor vacío (efectivo) queda fuera del chequeo y los pagos anulados cuentan como usados.
8. ~~Cómo se representa una solicitud con matrícula vigente (`PENDIENTE_PAGO` o `ACTIVA`): la spec no decía qué pasaba con ella en la cola favorable al crearse su matrícula.~~ **Resuelto (decisión del grupo, 2026-10-10):** la solicitud permanece en `EN_ESPERA_FAVORABLE`; no se le ofrece otra vacante y toda consulta de candidatos o cálculo de turnos la excluye por matrícula vigente. No se agrega ningún estado nuevo (ítem 6). Si la matrícula pasa a `CANCELADA` (72 h sin pago o anulación, ítem 5), la solicitud reingresa al final de la cola favorable con nueva fecha de ingreso, conservando la inscripción pagada.
9. ~~Qué estado toma la solicitud tras una entrevista `FAVORABLE` cuando **sí** hay vacante: §4.6 solo dice que sin vacante pasa a `EN_ESPERA_FAVORABLE`, pero la matrícula se crea desde una oferta aceptada y la oferta solo se hace a solicitudes de esa cola.~~ **Resuelto (decisión del grupo, 2026-10-10):** tras una entrevista `FAVORABLE` la solicitud pasa **siempre** a `EN_ESPERA_FAVORABLE`, haya vacante o no, con la fecha y hora en que se registra el resultado como fecha de ingreso. Si hay vacante y le corresponde turno, se le ofrece por el camino normal de la cola favorable (ítem 3). Un único camino hacia la matrícula; no se agrega ningún estado nuevo (ítem 6).

## 7. Referencia de páginas

- **Página 1:** diagrama resumido del recorrido principal.
- **Página 3:** solicitud, vacantes, prioridad, espera sin pago, invitación y pago de inscripción.
- **Página 4:** cierre de la regla de invitación de 48 horas.
- **Página 5:** documentos, entrevista, vacante final y creación de matrícula.
- **Página 6:** pago de matrícula, reglas compartidas y vencimientos.

El PDF menciona como fuente previa `reglas_matricula_casa_amarilla_2027.md`, pero ese archivo no fue usado para añadir reglas a esta conversión.

## 8. Notas de implementación: pantalla Nueva solicitud (issue #10)

Razones de diseño de `PanelSolicitudes` y del código que usa. No agregan reglas al proceso: explican cómo se aplican las de §4.1 y §4.2.

### 8.1. Reglas aplicadas en la pantalla

- **Orden de validaciones.** `ArregloSolicitudes.registrar` valida en el orden del flujo (§4.1): datos obligatorios, apoderados, edad y solicitud activa duplicada. Si fallan varias, el personal ve primero la que corresponde corregir antes. La franja de comprobaciones de la pantalla usa el mismo orden (primero la edad, después el duplicado) para anticipar exactamente el error que daría al guardar.
- **Comprobación previa al guardado.** `ArregloSolicitudes.exigirAdmisible` reúne todo lo que `registrar` exige salvo los apoderados. Sirve para comprobarlo antes de vincular apoderados nuevos a un alumno que ya existe, y no después.
- **Edad del aula.** Supuesto: edad exacta = edad del aula. Los rangos de edad por aula siguen pendientes de definición (§6, ítem 2). Si el nido acepta rangos, se cambia solo en `Aula.admiteEdad`, que usan tanto el registro de la solicitud como la validación en vivo de la pantalla. La pantalla valida la edad antes de que exista el `Alumno`, por eso `FechasAdmision.edadAlCorte` calcula la edad a partir de la fecha suelta. `ArregloSolicitudes.exigirAdmisible` usa esa misma función al guardar, para que la franja y el guardado nunca discrepen sobre la edad.
- **Fecha de nacimiento.** Se interpreta en modo estricto para que un 31/02 se rechace en vez de convertirse en silencio en 28/02.
- **Alumno ya registrado.** Si el DNI ya es de un alumno, se reutiliza ese alumno: sus datos quedan en solo lectura y sus apoderados ya vinculados aparecen en la lista. Lo que el personal ya había escrito se guarda como borrador, porque el DNI puede coincidir a mitad de tipeo; si el siguiente dígito deshace la coincidencia, esos datos vuelven en vez de perderse. Lo mismo ocurre si un guardado registra un alumno nuevo y un paso posterior falla: si el personal corrige el DNI, lo escrito vuelve al formulario.
- **Apoderados.** Nunca se crea un `Apoderado` suelto. Si el DNI ya está en el padrón se usa esa misma instancia (hermanos que comparten apoderado); si no, sus datos quedan pendientes y se registran en el padrón recién al guardar, para no dejar apoderados huérfanos. Mientras está pendiente, la fila no tiene instancia del padrón.
- **Apoderado principal.** El primero de la lista queda como principal mientras no se elija otro. Cambiar el principal de un alumno ya registrado afecta a las demás pantallas, así que la fila lo indica antes de guardar en vez de hacerlo en silencio.
- **Quitar apoderados.** Solo se quita lo que aún no se guardó: un vínculo ya registrado es un dato del alumno y no se deshace desde el formulario de una solicitud.
- **Qué se conserva si el guardado falla.** Lo que ya quedó registrado (alumno, apoderados, vínculos) se conserva aunque falle un paso posterior: son personas reales y los padrones no tienen eliminar. Por eso la admisión se comprueba antes de tocar los vínculos y el principal del alumno.
- **Ubicación en la cola.** `Turnos.ubicarNueva` hace entrar la solicitud nueva a la cola sin pago antes de evaluar turnos: si habilitar falla, la solicitud queda esperando turno, y no activa pero fuera de toda cola. Devuelve solo las otras solicitudes habilitadas, es decir, a quién hay que avisar; el estado de la nueva se consulta en ella misma.
- **Aviso de habilitadas.** Guardar revisa la cola del aula y puede habilitar a quienes esperaban antes. Su plazo de 48 h ya corre, así que la ficha muestra a quién avisar. Mientras el personal estuvo en otra pantalla, una habilitada pudo pagar o vencer: al volver, el aviso solo pide llamar a quienes todavía pueden pagar.
- **Datos que cambian en otras pantallas.** Las vacantes y las solicitudes activas se vuelven a evaluar cada vez que el personal entra a la pantalla. El error "no se pudo evaluar su turno" solo describe a una solicitud que sigue esperando; si otra pantalla ya la movió, la ficha muestra su estado real.
- **Título de la ficha.** La ficha sirve para dos casos opuestos (recién creada o ya existía): el título los distingue solo, sin depender del color del aviso.

### 8.2. Detalles técnicos de Swing

- Los `setText` que hace el propio panel disparan los `DocumentListener`; la bandera `actualizando` evita que el recálculo se vuelva a llamar a sí mismo a mitad de camino.
- En un `BoxLayout` vertical, los `JPanel` se estiran a lo alto si sobra espacio. La clase `Fila` conserva su alto preferido aunque cambie el contenido (franja, lista de apoderados).
- En un `BoxLayout` vertical, componentes con alineaciones distintas se desplazan entre sí: `Estilos.alineado` alinea todos los de un formulario a la izquierda.
- `Estilos.rellenarTarjeta` suma relleno interior sin reemplazar el borde redondeado que la tarjeta ya trae.
- Los textos largos usan HTML con ancho fijo para que el `JLabel` haga salto de línea dentro de la columna.
- `PruebaPagos` usa los padrones de la ventana y no unos propios: con dos padrones los códigos de alumno se repiten y la pantalla de solicitudes confunde alumnos distintos.

## 9. Notas de implementación: pantalla Colas y disponibilidad (issue #25)

Decisiones de `PanelColas` acordadas en la revisión del issue (2026-10-11). No agregan reglas al proceso: explican cómo se muestran las de §4.2 y §4.6. Criterio usado: cuando el diseño de Figma (frame `04 · Colas y cálculo de aula`) y el código chocan, gana el código.

### 9.1. Qué muestra la pantalla

- **Orden de atención.** Las filas salen de `Turnos.ordenPorPrioridad`, tal cual: favorable, en documentación, habilitada y sin pago. El diseño solo muestra favorable y sin pago, pero una solicitud en documentación o habilitada ocupa turno (§4.2); si se ocultara, el «turno 1» de la tabla no coincidiría con a quién le toca de verdad. El grupo va dentro de la columna TURNO («1 · Favorable», «3 · En documentación»), sin columna extra.
- **Un solo turno actual.** Solo la primera fila es el turno actual, resaltada en amarillo espera. Aunque `Turnos.exigirTurno` permite ofertar a cualquier favorable dentro de las N vacantes, la pantalla las atiende en secuencia: al confirmar, la tabla se recalcula y la siguiente pasa a ser la primera. El resultado es el mismo y respeta el diseño.
- **Columna Acción.** Solo informa: «Ofertar →» en la primera fila si es favorable y hay vacantes, «Esperar» en el resto. No navega a otras pantallas.
- **Ingreso a cola.** Las solicitudes habilitadas o en documentación muestran «—»: al salir de la cola, `Solicitud` borra su fecha de ingreso.
- **Turno actual no favorable.** Si la primera fila está en documentación, habilitada, sin pago o no hay vacantes, el panel la muestra igual con su estado y una línea que explica por qué no se le oferta; los botones quedan deshabilitados. Con la cola vacía se muestra «No hay solicitudes en cola para <aula>».
- **Comprobaciones.** Son informativas; la validación real la hace `Turnos.confirmarOferta`. Documentos: los 4 del expediente en `VALIDADO`, con `ExpedienteDocumentos.todosValidados`, la misma condición que habilita agendar la entrevista. Entrevista: `ArregloEntrevistas.buscarDe` con resultado `FAVORABLE`. Cupo: `Vacantes.calcular > 0`; la parte «sin matrícula vigente» ya la garantiza `ordenPorPrioridad`, que descarta a quien la tiene. Confirmación de oferta: siempre sin marcar, porque se cumple recién al confirmar.
- **Selector de aula.** Un combo en el encabezado, no dentro de la tarjeta del aula, porque cambia toda la pantalla. El diseño no lo dibuja, pero lo pide el criterio de aceptación.

### 9.2. Oferta

- **Oferta no aceptada.** El diseño tiene un solo botón, pero §6 ítem 3 exige registrar el rechazo para que la solicitud vuelva al final de la cola favorable; sin él, un candidato que no acepta bloquearía la cola. Por eso hay un botón secundario «Oferta no aceptada» que llama a `confirmarOferta` con `aceptada = false`. No pide confirmación.
- **Personal que confirma.** `Oferta` exige el personal y no hay usuario con sesión: se escribe en un campo obligatorio, igual que en el expediente.
- **Errores.** Si `confirmarOferta` rechaza la operación, su mensaje se muestra en un diálogo.

### 9.3. Recálculo y avisos

- **Cuándo se recalcula.** Al entrar a la pantalla, al cambiar de aula y tras cada oferta. Primero se revisan vencimientos y turnos (`Turnos.revisarTurnos`, que habilita a las solicitudes sin pago con turno) y después se redibuja; así la tabla muestra el mismo estado que verá `confirmarOferta`. El desglose y las vacantes nunca se guardan.
- **Desglose del aula.** Los sumandos (`PENDIENTE_PAGO` y `ACTIVA`) y el total salen juntos de `Vacantes.desglose`, y `Vacantes.calcular` usa ese mismo desglose. Así ninguna pantalla rearma la fórmula por su cuenta ni olvida uno de los dos estados que reservan vacante, y la tarjeta y el botón de oferta nunca discrepan.
- **Avisos de vencimiento.** Van en el encabezado y no en un diálogo: el mismo aviso vuelve en cada revisión mientras los datos no se corrijan, y se borra solo cuando la revisión ya no lo devuelve. Usan una etiqueta propia, separada del mensaje de la oferta, porque `ofertar` escribe ahí su confirmación y justo después vuelve a revisar. Si `Turnos.revisarTurnos` falla, su error se muestra junto con los avisos de `Vencimientos.revisar`.
- **Habilitadas por avisar.** Las que habilita la revisión de turnos, de cualquier aula, se acumulan en una franja para llamar a sus familias (§4.2: el aviso es telefónico y fuera del sistema) hasta que el personal pulsa «Ya se avisó». Se acumulan porque la siguiente revisión ya no las devuelve, y cambiar de aula o volver a la pantalla no debe borrar a quién avisar. Es una franja dentro del panel y no un diálogo, para que siga a la vista mientras el personal llama. Una habilitada que entretanto pagó o venció sale sola de la lista.

### 9.4. Cambios en pantallas vecinas

- **`AvisoHabilitadas`.** Solicitudes (§8.1, «Aviso de habilitadas») y Colas muestran a quién llamar con la misma clase: el filtro de las que todavía pueden pagar y el formato de cada línea (código, alumno, apoderado principal con celular y vencimiento). Colas añade el aula, porque reúne habilitadas de todas las aulas.
- **Borrador del pago.** Pagos recarga la lista de solicitudes cada vez que el personal vuelve a la pantalla, y la tarjeta se repinta. `TarjetasPago.prepararPago` recibe la solicitud o matrícula que se paga: si es la misma, con la misma cuota y plazo, conserva lo que el personal ya escribió. Si cambió, o si en medio la tarjeta pasó a bloqueada o a pago confirmado, el formulario se limpia.
- **Posición en Pagos.** La pantalla solo vuelve al inicio si cambió la solicitud elegida, tras anular un pago o tras un rechazo por vencimiento; al volver a la misma solicitud no se pierde la posición en el pago que se estaba llenando.

### 9.5. Fuera de alcance

- ~~**Sin datos precargados.** La app arranca vacía. Precargar un ejemplo con plazos vigentes obliga a usar fechas relativas al reloj, choca con la carga desde archivo de #33 y se rompe al cambiar las reglas provisorias de §6 ítem 2. Si se quiere, va en un issue propio de `datos`.~~
  **Resuelto (issues #33 y #82):** `Main` carga el almacenamiento elegido, sin generar
  ejemplos. La demo opcional se genera aparte y se abre explícitamente según §10.
- **Sin pruebas automáticas.** La pantalla se verifica a mano en la GUI. `java -cp bin PruebaPagos` abre la ventana con casos cargados (SOL-1001 a SOL-1011) que también se ven en Colas.

## 10. Demo opcional para el profesor (issue #82)

**Resuelto:** `GenerarDatosDemo` prepara cinco casos ficticios para la aplicación real;
no se llama desde `Main` ni modifica pantallas, cuotas, capacidades o reglas de negocio.
Cada ejecución crea una carpeta exclusiva bajo `datos/demo-profesor/`, con los once CSV
existentes y seis comprobantes PNG persistentes. No borra ni sobrescribe otras carpetas.
Las fechas son relativas a la generación, con cronología válida y plazos originales de
48 horas, 72 horas y 7 días. El reloj de `Main` no se congela: cerrar la app no pausa plazos.

### 10.1. Generar y abrir

Desde la raíz, configurar `JAVA_HOME` y `PATH` con JDK 21 y compilar:

```sh
find src -name "*.java" > /tmp/casaamarilla-sources.txt
javac -encoding UTF-8 -Xlint:none -d bin @/tmp/casaamarilla-sources.txt
java -Djava.awt.headless=true -cp bin GenerarDatosDemo
```

El generador imprime la ruta absoluta y el comando para abrirla:
`java -Dcasaamarilla.datos="<carpeta-generada>" -cp bin Main`.
Cerrar y reabrir con **esa misma ruta** conserva los datos y comprobantes; mover o borrar
la carpeta invalida las rutas de los comprobantes. Generar de nuevo antes de una exposición
posterior si ya vencieron los plazos; no modificar ni reiniciar los originales.

### 10.2. Recorrido de exposición

Todos los casos preparados están en Tulipanes (3 años, capacidad 18). Identificarlos por
el nombre «Alumno DEMO N», no por el orden de códigos de solicitud.

| Caso | Mostrar en la aplicación |
|---|---|
| 1 | Habilitada para pago vigente: intentar inscripción por importe distinto de 180 y una solicitud activa duplicada; ambos rechazos conservan el estado. |
| 2 | Inscripción confirmada, tres documentos validados y carné de vacunas observado con corrección vigente; mostrar el pendiente y el bloqueo de entrevista. |
| 3 | Cuatro documentos validados, entrevista favorable y oferta aceptada; matrícula pendiente vigente, con vacante reservada y pago de 850 aún pendiente. |
| 4 y 5 | Dos alumnos distintos con matrículas activas, ambos pagos confirmados y comprobantes ficticios legibles; documentación, entrevistas y ofertas completas. |

Girasoles queda sin solicitudes ni matrículas (4 años, capacidad 20). Para el happy path
manual, registrar un alumno nuevo de 4 años al 31/03/2027 (por ejemplo, nacimiento
15/06/2022) y su apoderado principal; crear y habilitar solicitud, confirmar inscripción
por 180 con comprobante ficticio, entregar y validar los cuatro documentos, registrar
entrevista realizada favorable, aceptar oferta y confirmar matrícula por 850. No se
precarga este recorrido. Usar identidades y números de operación distintos de los casos.

### 10.3. Comprobaciones

`java -Djava.awt.headless=true -Dopencode.test.logs=<directorio-absoluto-existente> -cp bin PruebaPagos`
incluye generación con fechas fijas,
recarga sin pérdida, relaciones, estados, plazos, comprobantes e aislamiento de carpetas.
Estas pruebas no acreditan apariencia ni interacción gráfica: comprobar manualmente
el recorrido anterior, cierre y reapertura con `Main`, e informar esa comprobación aparte.
