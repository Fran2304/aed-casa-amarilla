# Nido · La Casa Amarilla 2027

> Convertido desde `Nido-La-Casa-Amarilla-2027-gpt6-sol.fig`. Contiene 6 pantallas de diseño; el texto se organizó por bloques de cada pantalla.

---

## 01 · Panel de operaciones

*Cabecera:* La Casa Amarilla   /   Matrícula 2027 · Sede San Borja · Personal

**Navegación lateral:** OPERACIÓN 2027 · Inicio · Solicitudes · Colas y vacantes · Documentos · Entrevistas · Pagos y matrículas · Historial

### Centro de operaciones

+ Nueva solicitud

Lun 28 sep 2026 · Vencimientos recalculados al abrir y antes de asignar vacantes.

VACANTES CALCULADAS

### 12

en 3 aulas

COLAS POR ATENDER

### 08

3 favorables

DOCUMENTOS A REVISAR

### 06

2 observados

VENCEN EN 24 HORAS

### 03

**Turnos por prioridad**

Primero favorables; luego sin pago. En cada grupo manda el ingreso más antiguo a su cola.

- PRIORIDAD · ALUMNO / AULA · INGRESO · ACCIÓN

1 · Favorable

Lucía Vega · Girasoles

25 sep · 09:15

Ofertar →

2 · Favorable

Mateo Rojas · Girasoles

27 sep · 11:20

En espera

3 · Sin pago

Alma Torres · Tulipanes

22 sep · 14:05

Invitar 48 h →

4 · Sin pago

Emilia Paz · Tulipanes

24 sep · 10:40

En espera

Ver todas las colas →

**Próximos vencimientos**

Hoy 16:30 · Matrícula

Mía León · 72 h desde creación

Mañana 09:00 · Invitación

Alma Torres · 48 h desde emisión

30 sep · Corrección

Noa Ruiz · 7 días calendario

Vacantes = capacidad − PENDIENTE_PAGO − ACTIVA. Inscripción y solicitud no reservan cupo.

---

## 02 · Solicitud y admisión

*Cabecera:* La Casa Amarilla   /   Matrícula 2027 · Sede San Borja · Personal

**Navegación lateral:** OPERACIÓN 2027 · Inicio · Solicitudes · Colas y vacantes · Documentos · Entrevistas · Pagos y matrículas · Historial

### Nueva solicitud · comprobaciones

Validar antes de guardar o pedir pago · 2027 · Una solicitud activa por alumno.

**Datos del alumno y apoderados**

DNI ALUMNO *

NACIMIENTO *

78451238

18 jun 2022

✓ 4 años al 31 mar 2027 · Aula Girasoles compatible · Sin solicitud activa duplicada

AULA SOLICITADA *

VACANTES CALCULADAS

Girasoles · 4 años

2 disponibles · no se reservan

**Apoderados · uno o más; exactamente uno principal**

● Principal · Carolina Soto · DNI 45623017 · 999 213 654

○ Adicional · Marco Vega · DNI 44900128 · 977 553 204

Si existe una solicitud activa, abrir la ficha existente; las canceladas y rechazadas siguen visibles en historial.

Guardar y evaluar turno

**Ruta de inscripción**

EN_ESPERA_SIN_PAGO

Fecha de ingreso a cola. Sin cobro, revisión documental ni entrevista.

Invitación al llegar su turno

48 h desde emisión, sin reserva. Corre incluso durante recepción y corrección del comprobante.

Pago de inscripción

Registrar monto aplicado, concepto, medio (efectivo, transferencia, tarjeta, Yape o Plin), fecha real, n° de operación salvo efectivo y comprobante. Recibido no es confirmado.

CONFIRMADO → EN_DOCUMENTACION

Abre 7 días calendario; no reserva cupo.

Si vence la invitación, cerrar y mover al final de su cola con nuevo ingreso. Pago directo: plazo aún no definido.

Inscripción no reembolsable. Una nueva solicitud tras rechazo o cancelación requiere otro pago de inscripción.

---

## 03 · Expediente, documentos y entrevista

*Cabecera:* La Casa Amarilla   /   Matrícula 2027 · Sede San Borja · Personal

**Navegación lateral:** OPERACIÓN 2027 · Inicio · Solicitudes · Colas y vacantes · Documentos · Entrevistas · Pagos y matrículas · Historial

### Expediente · Noa Ruiz

SOL-1038 · Tulipanes (3 años) · Apoderada principal: Ana Ruiz

ESTADO: EN_DOCUMENTACION

Corrección: 30 sep · 15:00

Inscripción confirmada 20 sep · 10:30 → entrega hasta 27 sep · 10:30

**Documentos · 3 de 4 validados**

Los cuatro deben entregarse dentro de 7 días calendario desde el pago confirmado. La validación puede concluir después.

- DOCUMENTO · ENTREGADO · REVISIÓN

DNI del alumno

22 sep · 09:00

VALIDADO

DNI apoderada principal

22 sep · 09:00

VALIDADO

Partida de nacimiento

23 sep · 12:10

VALIDADO

- Carné de vacunas · 23 sep · 13:20 · OBSERVADO

Falta página de refuerzo · comunicado 23 sep · 15:00

7 días calendario para corregir; vuelve a revisión. Si vence sin corrección, se cancela la solicitud.

**Entrevista**

BLOQUEADA · 1 observado

Agendable al validar los 4 documentos.

Personal y cita

Fecha y hora: —

Integrantes del personal (al menos 1): —

Responsable (exactamente 1): —

Asistencia y resultado

Pendiente · FAVORABLE / NO_FAVORABLE

Inasistencias: 0 de 2

Una reprogramación por inasistencia; segunda ausencia → CANCELADA. NO_FAVORABLE → RECHAZADA.

Entrevista FAVORABLE habilita evaluar matrícula, pero no crea una matrícula ni garantiza cupo.

---

## 04 · Colas y cálculo de aula

*Cabecera:* La Casa Amarilla   /   Matrícula 2027 · Sede San Borja · Personal

**Navegación lateral:** OPERACIÓN 2027 · Inicio · Solicitudes · Colas y vacantes · Documentos · Entrevistas · Pagos y matrículas · Historial

### Colas y disponibilidad

La vacante habilita una selección concreta; no a todos los integrantes de la cola.

VACANTES

### 02

**Aula Girasoles · 4 años**

Edad al 31 mar 2027 · única sede · cupo calculado en tiempo real

Capacidad 20   −   PENDIENTE_PAGO 3   −   ACTIVA 15   =   2 disponibles

**Orden de atención · Girasoles**

FAVORABLE primero; luego SIN PAGO. Antigüedad dentro de cada cola.

| TURNO | SOLICITUD | INGRESO COLA | ACCIÓN |
|---|---|---|---|
| 1 · Favorable | Lucía Vega · SOL-1042 | 25 sep · 09:15 | Ofertar → |

2 · Favorable

Mateo Rojas · SOL-1048

27 sep · 11:20

Esperar

3 · Sin pago

Eva Díaz · SOL-1051

21 sep · 08:40

Esperar

4 · Sin pago

Leo Silva · SOL-1056

26 sep · 17:30

Esperar

**Turno actual · Lucía**

EN_ESPERA_FAVORABLE

Inscripción pagada, entrevista favorable.

Antes de crear matrícula

✓ Cuatro documentos validados

✓ Entrevista FAVORABLE

✓ Recalcular cupo y matrícula vigente

□ Confirmación de oferta por personal

Confirmar oferta y crear matrícula

Si no acepta, no se crea. Matrícula PENDIENTE_PAGO es el primer momento que reserva vacante.

Invitación SIN PAGO: 48 h desde emisión; si vence, vuelve al final de esa cola con nuevo ingreso.

---

## 05 · Matrícula y pago

*Cabecera:* La Casa Amarilla   /   Matrícula 2027 · Sede San Borja · Personal

**Navegación lateral:** OPERACIÓN 2027 · Inicio · Solicitudes · Colas y vacantes · Documentos · Entrevistas · Pagos y matrículas · Historial

### Matrícula · Mía León

MAT-2071 · SOL-1029 · Aula Girasoles · Responsable de pago: Carlos León

**MATRÍCULA: PENDIENTE_PAGO**

**Reserva 1 vacante ahora**

Creada 25 sep · 16:30 → pago hasta 28 sep · 16:30 (72 horas corridas)

**Pago de matrícula · comprobante recibido**

EN REVISIÓN · Recibir no equivale a confirmar

Fecha real de operación dentro de las 72 h originales.

CONCEPTO

MONTO APLICADO (S/)

Matrícula 2027

850.00

MEDIO DE PAGO

N° OPERACIÓN

Yape

YP-8291457

FECHA REAL DE OPERACIÓN

COMPROBANTE

28 sep · 12:42

Abrir comprobante →

Número obligatorio salvo efectivo. Inscripción y matrícula son conceptos separados; cada pago conserva su monto aplicado.

**Validación y cierre**

✓ Concepto y monto aplicado

✓ Medio y número de operación

✓ Fecha real dentro de 72 h

✓ Comprobante verificable

Confirmar pago → ACTIVA

Registrar observación

SI VENCE SIN PAGO REGISTRADO

Matrícula CANCELADA, libera vacante. Solicitud vuelve al final de EN_ESPERA_FAVORABLE con nuevo ingreso; conserva inscripción pagada.

Un pago confirmado no se edita: su anulación retorna a PENDIENTE_PAGO y reevalúa el plazo original, sin reiniciarlo.

---

## 06 · Historial y estados

*Cabecera:* La Casa Amarilla   /   Matrícula 2027 · Sede San Borja · Personal

**Navegación lateral:** OPERACIÓN 2027 · Inicio · Solicitudes · Colas y vacantes · Documentos · Entrevistas · Pagos y matrículas · Historial

### Trazabilidad · Lucía Vega

SOL-1042 · Aula Girasoles · Eventos fechados con responsable y motivo.

SOLICITUD: EN_ESPERA_FAVORABLE

Inscripción confirmada

Ingreso: 25 sep · 09:15 · Puesto 1 en su aula · Matrícula vigente: ninguna

**Línea de tiempo de la solicitud**

- FECHA / HORA · EVENTO · PERSONAL

25 sep · 09:15

Sin cupo → EN_ESPERA_FAVORABLE

Sistema

25 sep · 08:40

Entrevista FAVORABLE · asistió

R. Salazar

24 sep · 12:00

4 documentos validados

M. López

20 sep · 10:30

Inscripción confirmada · S/ 180

A. Cruz

19 sep · 16:42

Pago recibido · comprobante revisado

A. Cruz

19 sep · 09:05

Solicitud registrada · Girasoles

A. Cruz

Ver historial completo y matrículas canceladas →

**Pagos y comprobantes**

INSCRIPCIÓN · CONFIRMADO

S/ 180 · Transferencia

TR-904118 · 19 sep · 16:40

Abrir comprobante →

Matrícula

No creada · Inscripción no ocupa vacante.

Estados del proceso

Solicitud: registrada, EN_ESPERA_SIN_PAGO, EN_DOCUMENTACION, entrevista, EN_ESPERA_FAVORABLE, RECHAZADA, CANCELADA.

Matrícula: PENDIENTE_PAGO, ACTIVA o CANCELADA.

Solo PENDIENTE_PAGO y ACTIVA ocupan vacante.

Cambios, pagos y matrículas canceladas permanecen en historial; montos aplicados no cambian retroactivamente.
