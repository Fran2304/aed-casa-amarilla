# Spec: Plazos documentales y cancelación por incumplimiento (issue #17)

**Date:** 2026-10-10 · **Status:** Settled (grilling session)
**Context:** Cablear la cancelación documental de la issue #17: la lógica de plazos ya existe (`ExpedienteDocumentos.debeCancelarse`, `Documento.correccionVencida`) pero nadie la invoca. Falta la operación de cancelación con evento de historial, el barrido en los bordes existentes y las pruebas de regresión.

## Decisions

### Evento de historial
- Campos de auditoría en `Solicitud` (`fechaCancelacion`, `motivoCancelacion`, `responsableCancelacion`), espejo del patrón `Pago.anular` — el issue pide "evento en historial" y es el precedente de auditoría del repo; sin nueva entidad ni capa.

### Disparo de la cancelación
- Ambos: método de dominio reutilizable (`Solicitud.cancelarPorDocumentacion(...)`) + barrido automático `ArregloSolicitudes.cancelarDocumentacionVencida(hoy, responsable)`, espejo de `vencerHabilitaciones`, invocado desde `Turnos.ordenPorPrioridad` y `PanelPagos.buscar` — cubre "ya no participa en colas" y "no recibe pagos" en los bordes que ya usan la habilitación 48h.
- Responsable: parámetro en cada borde; en la demo, el nombre del Personal hardcodeado como el resto de la UI.

### Estados y plazos
- Cancelación solo desde `EN_DOCUMENTACION` — único estado con plazo documental vivo (en `EN_ESPERA_FAVORABLE` los 4 ya están validados).
- Plazo de corrección por documento observado (cada uno desde su propia `fechaComunicacion`); una re-observación abre ventana nueva de 7 días — comportamiento existente, la spec no lo restringe.
- Semántica de límite: `LocalDate.plusDays(7)`, día límite inclusive (entrega/corrección el último día, hasta 23:59, es oportuna); el día siguiente cancela. Ejemplo del issue: confirmación 20/sep → límite 27/sep.

### Errores y no reembolso
- Cancelación inoportuna (plazo vigente): `ReglaDominioException` directa con mensaje claro; sin subclase nueva — el repo solo crea subclases para errores con múltiples usos.
- Pago de inscripción tras cancelar: basta el rechazo existente de `Cobros.anularPago` (exige `EN_DOCUMENTACION`), cubierto con prueba; el pago queda `CONFIRMADO` para siempre (no reembolso, spec §110).

### Demo
- `cargarCasos` agrega una solicitud `EN_DOCUMENTACION` con plazo vencido — hace visible el barrido en la demo sin tocar UI.

## Acceptance criteria

- Plazo inicial vencido sin los 4 entregados → `CANCELADA` con evento registrado (fecha, motivo, responsable).
- Plazo de corrección vencido sin corregir → `CANCELADA` con evento registrado.
- Entrega de los 4 el último día del plazo (hasta 23:59) → oportuna; no cancelable ese día; el día siguiente sí.
- Corrección el último día del plazo de corrección → oportuna (día límite inclusive).
- Una solicitud `CANCELADA`: no transiciona (terminal en `Transiciones`), no recibe pagos (`confirmarInscripcion` y pago de matrícula rechazados), no aparece en colas.
- Anular el pago de inscripción de una solicitud `CANCELADA` → rechazo existente; el pago permanece `CONFIRMADO` (no reembolso).
- Intento de cancelación con plazo vigente → `ReglaDominioException`; estado intacto (snapshot antes/después).
- El barrido en `Turnos.ordenPorPrioridad` y `PanelPagos.buscar` cancela las vencidas que encuentre.
- Demo no-headless: `cargarCasos` incluye el caso `EN_DOCUMENTACION` vencido.
- Pruebas en `PruebaPagos` con fechas de dominio fijas, casos nombrados, errores esperados específicos y comprobación de estado intacto tras rechazo.

## Non-goals / Deferred

- UI de `PanelDocumentos` (entrega, observación, cancelación manual) — otro issue.
- Hook de barrido al abrir la aplicación (`PrincipalUI`) — diferido al issue de UI; este issue cubre `ordenPorPrioridad` + `buscar`.
- Nueva entidad `Historial`/`EventoCancelacion` y nueva subclase de excepción.
- Persistencia, reloj inyectable, cambios de arquitectura.
