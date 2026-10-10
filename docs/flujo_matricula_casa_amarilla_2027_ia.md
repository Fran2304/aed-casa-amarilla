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
| `EN_ESPERA_FAVORABLE` | Documentos validados y entrevista favorable, pero sin vacante para crear matrícula. Conserva la inscripción pagada. |
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

- El apoderado debe realizar el pago dentro de las primeras **72 horas corridas desde la creación** de la matrícula.
- Verificar concepto, monto, medio de pago, fecha real de la operación y número de operación cuando corresponda.
- Si el pago es válido y la operación ocurrió dentro de las primeras 72 horas, pasar la matrícula a `ACTIVA`. No descontar otra vacante: ya estaba reservada.
- Si transcurren más de 72 horas **sin pago registrado**, pasar la matrícula a `CANCELADA`, liberar la vacante y devolver la misma solicitud al **final de `EN_ESPERA_FAVORABLE`** con nueva fecha de ingreso. Conservar su inscripción pagada.
- Un pago `CONFIRMADO` no se edita. Si se anula por error de registro, devolver la matrícula a `PENDIENTE_PAGO` y reevaluar los plazos originales antes de mantener la reserva.

### 4.8. Reglas comunes de pagos y vencimientos

- Medios admitidos para ambos pagos: **efectivo, transferencia, tarjeta, Yape y Plin**.
- El número de operación es obligatorio excepto para efectivo.
- Cada pago conserva el monto aplicado al registrarse, aunque luego cambie la configuración.
- Todo pago guarda el comprobante que presenta el apoderado (captura o voucher de la operación) y no se confirma sin él. No es el número de comprobante que el nido emite después de confirmar (§4.3). Se guarda la ruta del archivo; copiarlo a una carpeta propia queda para la persistencia.
- Todo pago se relaciona con una solicitud. El pago de matrícula también se relaciona con su matrícula; el de inscripción ocurre antes de crearla.
- Revisar vencimientos al abrir la aplicación de escritorio y antes de operaciones que dependen de vacantes. El cierre de la aplicación no suspende los plazos.

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

## 6. Pendiente de definición o aclaración

Estos puntos **no están resueltos en el PDF**. Una IA debe señalarlos antes de implementar comportamientos que dependan de ellos:

1. ~~Plazo de pago de inscripción para la **vía directa**.~~ **Resuelto (decisión del grupo):** la solicitud nueva se habilita de frente y tiene las mismas 48 h.
2. Lista completa de datos obligatorios, rangos de edad por aula y reglas de elegibilidad.
3. Cómo proceder cuando una oferta de la cola favorable **no es aceptada**: el PDF solo indica que no se crea matrícula.
4. ~~Tratamiento de un pago de matrícula registrado pero observado al vencer el plazo.~~ **Resuelto (decisión del grupo):** los pagos no tienen estado de observación; un pago que no cumple las validaciones se rechaza y cuenta como ausencia de pago registrado. Al vencer las 72 h, la matrícula pasa a `CANCELADA`.
5. Resultado de anular un pago confirmado cuando, al reevaluar el plazo original, ya no corresponde mantener la reserva.
6. Nombres de estados intermedios que el PDF no enumera. **Resuelto en parte (decisión del grupo):** se agrega `HABILITADA_PARA_PAGO` para la solicitud de la cola sin pago a la que le llegó el turno. Los demás estados intermedios siguen sin nombre.
7. Si el **número de operación** debe ser único (por medio de pago o en general). Hoy nada impide registrar dos pagos con el mismo número, incluso en solicitudes distintas.

## 7. Referencia de páginas

- **Página 1:** diagrama resumido del recorrido principal.
- **Página 3:** solicitud, vacantes, prioridad, espera sin pago, invitación y pago de inscripción.
- **Página 4:** cierre de la regla de invitación de 48 horas.
- **Página 5:** documentos, entrevista, vacante final y creación de matrícula.
- **Página 6:** pago de matrícula, reglas compartidas y vencimientos.

El PDF menciona como fuente previa `reglas_matricula_casa_amarilla_2027.md`, pero ese archivo no fue usado para añadir reglas a esta conversión.
