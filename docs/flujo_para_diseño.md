# Contexto funcional para diseñar la aplicación en OpenPencil

**Proyecto:** sistema institucional de matrícula 2027 del Nido La Casa Amarilla, sede San Borja.  
**Estado:** versión de trabajo aceptada por el equipo para avanzar; algunos casos se revisarán después.  
**Fuente principal:** `flujo_completo_matricula_casa_amarilla_2027.pdf`, versión modificada por el equipo y revisada el 28 de septiembre de 2026.  
**Propósito de este archivo:** permitir que otro modelo entienda el proceso y, a partir de él, proponga especificaciones de interfaz en openPencio. Este archivo describe comportamiento y datos, no define todavía el aspecto visual ni una lista definitiva de pantallas.

> Si otra documentación del proyecto menciona 96 horas para pagar la matrícula o 24 horas adicionales para presentar comprobantes, **no usar esos plazos**. En la versión de trabajo actual se indican 72 horas desde la creación de la matrícula y no se contempla expresamente un periodo adicional. Los casos ambiguos se enumeran al final.

## 1. Contexto y actores

La aplicación es un **software institucional de escritorio** que utilizará el personal del nido. No se ha definido un portal web ni una interfaz de autoservicio para apoderados. El apoderado entrega información, documentos y pagos; el personal los registra, atiende, revisa y confirma en el sistema. El sistema valida reglas, calcula vacantes, controla estados, fechas y prioridades, y conserva el historial.

Hay una sola sede y el proceso descrito corresponde al año 2027. El alumno puede tener varios apoderados, pero debe existir al menos uno y exactamente uno debe marcarse como principal. La edad para determinar el aula se calcula usando la fecha de nacimiento del alumno al **31 de marzo de 2027**.

## 2. Regla central de vacantes

Las vacantes disponibles de un aula se calculan así:

`vacantes disponibles = capacidad del aula - matrículas PENDIENTE_PAGO - matrículas ACTIVA`

La consulta de disponibilidad, la solicitud y el pago de inscripción **no reservan** vacante. La vacante se reserva únicamente cuando se crea una matrícula en estado `PENDIENTE_PAGO`. Al pasar esa matrícula a `ACTIVA`, no se descuenta otra vacante. Al cancelarla, deja de ocuparla. Las vacantes no se almacenan como un contador editable independiente.

## 3. Flujo principal

### 3.1. Solicitud y comprobaciones iniciales

El apoderado solicita una vacante para un alumno y un aula. El personal registra los datos del alumno, sus apoderados y el aula solicitada. Antes de crear la solicitud o pedir un pago, el sistema comprueba los datos obligatorios, la edad del alumno para el aula y la existencia de otra solicitud activa del mismo alumno para 2027. Si ya existe una, se muestra la solicitud existente para evitar duplicados. Solo puede haber una solicitud activa por alumno durante 2027; las solicitudes canceladas o rechazadas permanecen en el historial.

Cuando los datos son válidos, el sistema guarda la solicitud y consulta las vacantes y el orden de atención. Si hay disponibilidad y no corresponde atender antes a otra solicitud, la solicitud pasa de frente a `HABILITADA_PARA_PAGO` y el personal comunica la cuota de inscripción. **Decisión del grupo:** el plazo es el mismo de la habilitación desde la cola, 48 horas desde que se habilita (ver §3.2).

Si hay cola delante y no quedan vacantes, la solicitud pasa a `EN_ESPERA_SIN_PAGO` y se registra su fecha de ingreso a esa cola. Mientras permanezca ahí, no se cobra la inscripción, no se revisan documentos y no se agenda una entrevista.

### 3.2. Prioridad de las listas de espera

Cuando aparece una vacante, tienen prioridad las solicitudes con inscripción pagada y entrevista favorable que esperan matrícula. Después se atiende a las solicitudes sin pago. Dentro de cada grupo se respeta la antigüedad de ingreso a la cola correspondiente. El sistema selecciona una solicitud concreta para atender; la vacante no habilita a todos los integrantes de la cola a la vez.

Cuando se libera una vacante y le llega el turno a una solicitud `EN_ESPERA_SIN_PAGO`, esta pasa a `HABILITADA_PARA_PAGO` sin acción del personal; el colegio avisa al apoderado por teléfono, fuera del sistema. Este estado es una decisión del grupo y no aparece en el PDF. La habilitación dura **48 horas desde que se habilita** y no reserva la vacante. Si vence sin que la inscripción quede confirmada, la solicitud vuelve al final de `EN_ESPERA_SIN_PAGO` con una nueva fecha de ingreso. Las prioridades se evalúan otra vez. Las 48 horas continúan corriendo durante la recepción y verificación del pago; un pago rechazado no reinicia ese plazo.

**Decisión del grupo:** una solicitud con inscripción pagada en `EN_DOCUMENTACION` conserva su turno hasta que obtiene matrícula, se rechaza o se cancela. Mientras tanto, la vacante no se ofrece a otra solicitud de la cola sin pago ni a una solicitud nueva. Así, una sola vacante no lleva a cobrar inscripciones a toda la cola, una familia tras otra.

**Decisión del grupo:** una solicitud habilitada también ocupa su turno, frente a la cola sin pago y a las solicitudes nuevas, hasta que paga o vence; frente a la cola favorable no, porque esta va primero. Si vence, reingresa a la cola sin pago con la fecha en que venció la habilitación.

### 3.3. Pago de inscripción

El apoderado paga la cuota de inscripción y presenta la información o el comprobante correspondiente. El personal registra y verifica concepto, monto, medio de pago, fecha real de la operación y número de operación cuando corresponda. Si el pago no cumple alguna validación, se rechaza y el apoderado debe presentar un pago nuevo; los pagos no tienen estado de observación. El pago solo se aprueba cuando se confirma la operación. Recibir un comprobante no equivale a confirmar el pago.

La cuota de inscripción incluye la gestión y revisión documental del postulante. Es **no reembolsable** aunque posteriormente no haya vacante, la entrevista sea no favorable o la solicitud termine cancelada. Su monto se configura para 2027 y cada pago conserva el monto que se le aplicó. Cuando el personal confirma el pago, el sistema lo registra como `CONFIRMADO`, cambia la solicitud a `EN_DOCUMENTACION` y abre el plazo de entrega de documentos. En ese momento todavía no se ha creado una matrícula ni reservado una vacante.

### 3.4. Entrega y validación de documentos

El apoderado dispone de **7 días calendario desde la confirmación de la inscripción** para entregar estos cuatro documentos: DNI del alumno, DNI del apoderado principal, partida de nacimiento y carné de vacunas. El plazo se cumple con la entrega; la validación del personal puede terminar después. Si no se entregan los cuatro dentro del plazo, la solicitud se cancela y se conserva su historial.

El personal recibe y valida cada documento. Si alguno tiene observaciones, se comunican al apoderado y se conceden **7 días calendario para corregirlas**. La documentación corregida vuelve a revisión. Si no se corrige dentro del plazo, la solicitud se cancela. La entrevista solo puede agendarse cuando los cuatro documentos están validados.

### 3.5. Entrevista

El personal agenda la entrevista después de validar todos los documentos. Una entrevista debe tener al menos un integrante del personal y exactamente uno identificado como responsable. Se registra la asistencia y el resultado.

Si el apoderado no asiste, se permite **una sola reprogramación por inasistencia**. Una segunda inasistencia cancela la solicitud. Si se realiza la entrevista, el resultado puede ser `FAVORABLE` o `NO_FAVORABLE`. Un resultado no favorable deja la solicitud `RECHAZADA`. Un resultado favorable habilita la evaluación de matrícula, pero no crea una matrícula ni garantiza un cupo. Una nueva solicitud después de un rechazo o de la cancelación de la solicitud requiere pagar otra cuota de inscripción.

### 3.6. Comprobación final y creación de matrícula

Después de la entrevista favorable, el sistema vuelve a calcular la disponibilidad del aula porque la inscripción no reservó vacante. Si no hay cupo, la solicitud pasa a `EN_ESPERA_FAVORABLE` y conserva el pago de inscripción. Cuando se libera un cupo, el sistema propone al primer candidato de esa cola según su fecha de ingreso; el personal confirma la oferta antes de crear la matrícula. Si la oferta no se acepta, no se crea la matrícula.

La matrícula se crea únicamente si los cuatro documentos están validados, la entrevista es favorable y se verifica que hay vacante. Se crea en estado `PENDIENTE_PAGO`, con su fecha y hora de creación. **Desde ese instante reserva una vacante.** Un alumno puede tener solo una matrícula vigente (`PENDIENTE_PAGO` o `ACTIVA`) a la vez. Las matrículas canceladas se conservan como historial.

### 3.7. Pago de matrícula y cierre

La cuota de matrícula es distinta de la cuota de inscripción y ambas se configuran por separado para 2027. El apoderado debe realizar el pago de matrícula dentro de las **72 horas corridas desde la creación** de la matrícula. El personal revisa concepto, monto, medio, fecha real de la operación y número de operación cuando corresponda.

Cuando el pago es válido, está confirmado y la operación ocurrió dentro de las primeras 72 horas, la matrícula pasa a `ACTIVA`. Si transcurren más de 72 horas sin pago registrado, la versión de trabajo indica que la matrícula pasa a `CANCELADA`, libera su vacante y la solicitud vuelve al final de `EN_ESPERA_FAVORABLE` con nueva fecha de ingreso. La solicitud y su inscripción pagada se conservan; no se inicia una solicitud nueva por esa cancelación de matrícula.

## 4. Reglas compartidas y datos que la interfaz debe poder mostrar

Los medios de pago admitidos para ambos conceptos son `EFECTIVO`, `TRANSFERENCIA`, `TARJETA`, `YAPE` y `PLIN`. El número de operación es obligatorio excepto para efectivo. Cada registro de pago conserva el monto aplicado, incluso si después cambia el monto configurado. Todo pago está vinculado a una solicitud; el pago de matrícula también se vincula a la matrícula que liquida.

Un pago confirmado no se edita directamente. Si se anula por un error de registro, la matrícula vuelve a `PENDIENTE_PAGO` y el sistema reevalúa los plazos originales antes de mantener la reserva. La anulación no inicia un nuevo plazo de 72 horas.

Los vencimientos deben comprobarse al abrir la aplicación y antes de operaciones que dependen de vacantes. Como la aplicación puede permanecer cerrada, el cálculo utiliza las fechas guardadas y no depende de que un temporizador esté siempre ejecutándose.

Para representar correctamente el proceso en el diseño, la interfaz debe permitir comprender el estado actual de cada solicitud y matrícula, las fechas de inicio y vencimiento, el aula solicitada, la disponibilidad calculada, la posición o prioridad de espera, los documentos recibidos y validados, las observaciones, la entrevista, los pagos y sus comprobantes, y el historial de cambios relevantes. Estas son **necesidades de información y acciones**, no una decisión sobre cómo dividirlas en pantallas.
