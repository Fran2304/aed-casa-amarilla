# Spec — Issue #27: Motor de vencimientos global

Fuente: issue #27 ([DIFÍCIL] Motor de vencimientos global) + grilling con Jaim (2026-10-11).
Spec del proyecto: `docs/flujo_matricula_casa_amarilla_2027_ia.md` §4.8, §4.2, §4.4, §4.7, §5.

## Decisiones (grilling)

1. **Reloj global inyectable**: una única fuente de hora en la app. Se crea `Reloj` sobrescribible en pruebas (`now()`); los call sites de producción (`LocalDateTime.now()`/`LocalDate.now()` en `PanelPagos`, `TarjetasPago`, `ArregloSolicitudes.registrar`, `Matricula` ctor, `Alumno`) migran a obtener la hora del `Reloj`. `PruebaPagos` ya inyecta fechas por parámetro/reflexión; los casos de integración UI migran al `Reloj`.
2. **Cancelación 72h**: método nuevo de coordinación en `negocio` (p. ej. `Vencimientos`) que recorre matrículas vencidas y aplica la reversión existente de `Cobros` (`prepararAnulacionMatricula` + `aplicarReversionAnulacion`) como paso interno — sin duplicar reglas. Responsable de auditoría fijo: `"Motor de vencimientos"`.
3. **Sin UI de panel**: ya no habrá panel de inicio ni panel de operaciones. La lista de «próximos vencimientos» (24 h) queda como **consulta de negocio** (`Vencimientos.proximos(...)`); la UI que la muestre se definirá después.
4. **Quitar `PanelInicio`**: se elimina el panel placeholder y su botón/navegación de `PrincipalUI` (parte de este issue).
5. **Puntos de disparo**: motor al construir `PrincipalUI` (apertura de la app) y reutilizado en los bordes ya existentes — `PanelPagos.buscar/confirmar` y `Turnos.ordenPorPrioridad` — cubriendo así el 72h de matrículas antes de cualquier cálculo de vacantes.
6. Constante nombrada `HORAS_PAGO_MATRICULA = 72` en `Matricula` (hoy hardcodeado `plusHours(72)`), alineada con `Solicitud.HORAS_HABILITACION`.

## Reglas del motor

- **Vencimientos administrados** (todos contra fecha/hora real del `Reloj`, no tiempo de sesión):
  - Habilitación/invitación 48h vencida → solicitud al final de `EN_ESPERA_SIN_PAGO` (lógica existente `vencerHabilitaciones`).
  - Plazo documental 7d (entrega o corrección) vencido → solicitud `CANCELADA` (lógica existente `cancelarDocumentacionVencida`).
  - Matrícula 72h sin pago registrado → `CANCELADA` + liberar vacante (cálculo capacidad − PENDIENTE_PAGO − ACTIVA) + solicitud al final de `EN_ESPERA_FAVORABLE`; conservar inscripción pagada.
- El instante exacto de vencimiento cuenta como vencido (`!ahora.isBefore(vencimiento)`).
- Operación y registro de pago de matrícula deben ser anteriores al vencimiento original (reglas ya existentes).
- **Próximos vencimientos**: consulta que devuelve, para las próximas 24h, habilitaciones, plazos documentales (entrega/corrección) y vencimientos de matrícula, con código de solicitud/alumno y tipo de vencimiento.

## Criterios de aceptación

- [ ] Al abrir la app, toda invitación/plazo/matrícula vencida se procesa antes de cualquier operación.
- [ ] Antes de crear matrícula u ofrecer vacante (bordes existentes), se reevalúan los tres tipos de vencimiento, incluido 72h de matrículas.
- [ ] El `Reloj` inyectable permite probar vencimientos sin esperar 48/72h reales (todas las aserciones headless lo usan; cero `LocalDateTime.now()` suelto en src de producción).
- [ ] `Vencimientos.proximos(...)` devuelve los vencimientos de las próximas 24h, con los casos del ejemplo del diseño (matrícula 72h, invitación 48h, corrección 7d).
- [ ] `PanelInicio` eliminado; `PrincipalUI` arranca sin panel de inicio.
- [ ] Pruebas de regresión en `PruebaPagos`: casos nombrados con fechas de dominio fijas para el barrido de arranque, el borde pre-vacantes, el 72h (cancelación + liberación + cola favorable al final), próximos 24h, y estado intacto tras rechazos.

## Fuera de alcance

- Persistencia, autenticación, nuevas dependencias, cambios de arquitectura más allá del `Reloj` y `Vencimientos`.
- UI para mostrar próximos vencimientos (sin panel de inicio/operaciones).
- Vencimientos de entrevistas (no modelados en la spec).
