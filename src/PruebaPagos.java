import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import datos.ArregloAlumnos;
import datos.ArregloApoderados;
import modelo.Alumno;
import modelo.Aula;
import modelo.Apoderado;
import modelo.ConfiguracionCuotas;
import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.EstadoMatricula;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.Pago;
import modelo.MedioPago;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.TransicionInvalidaException;
import negocio.Cobros;
import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import ui.PrincipalUI;

// TODO: Archivo temporal solo para pruebas; se eliminará más adelante.
public class PruebaPagos {

    private static int siguienteDni = 70000001;

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            try {
                ejecutarAserciones();
                System.out.println("Pruebas headless de pagos: OK");
                return;
            } catch (Exception e) {
                throw new IllegalStateException("Fallaron las pruebas headless de pagos.", e);
            }
        }
        SwingUtilities.invokeLater(() -> {
            PrincipalUI ventana = new PrincipalUI();
            try {
                cargarCasos(ventana);
            } catch (ReglaDominioException e) {
                throw new IllegalStateException("No se pudieron cargar los casos: "
                        + e.getMessage(), e);
            }
            ventana.mostrar(PrincipalUI.PAGOS);
            ventana.setVisible(true);
        });
    }

    private static void ejecutarAserciones() throws Exception {
        probarMatriculaActivaVigenteYVencida();
        probarColasConEmpates();
        probarRechazosAtomicos();
        probarAuditoriaInmutable();
        probarPlazoOriginalDeMatriculaParaAmbasFechas();
        probarProteccionDeInscripcionConMatriculaVigente();
        probarComprobanteDePagoAnulado();
        probarIntegracionUiTrasAnulacionExitosa();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();
        Aula aula = new Aula("AUL-T", "Prueba", 4, 2);
        Solicitud inscripcion = solicitud("INS", base.minusDays(2), aula);
        inscripcion.habilitarParaPago(base.minusHours(2));
        LocalDateTime inscripcionDeadline = inscripcion.getVencimientoHabilitacionOriginal();
        Pago pagoInscripcion = Cobros.confirmarInscripcion(inscripcion,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1),
                "voucher", base, solicitudes, matriculas, pagos, cuotas);
        comprobar(inscripcion.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                "la inscripción se confirma");
        Cobros.anularPago(pagoInscripcion, "error de registro", "Personal de caja", base,
                solicitudes, matriculas, pagos);
        comprobar(pagoInscripcion.getEstado() == modelo.EstadoPago.ANULADO,
                "el pago queda anulado");
        comprobar(inscripcion.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO,
                "la inscripción vigente vuelve a habilitada");
        comprobar(pagos.listar().size() == 1 && !pagos.inscripcionConfirmada(inscripcion),
                "la consulta ignora pagos anulados");
        comprobar(inscripcion.getVencimientoHabilitacionOriginal().equals(inscripcionDeadline)
                && pagoInscripcion.getFechaAnulacion().equals(base)
                && pagoInscripcion.getMotivoAnulacion().equals("error de registro")
                && pagoInscripcion.getResponsableAnulacion().equals("Personal de caja"),
                "plazo original y auditoría se conservan");
        esperarFallo(ReglaDominioException.class, () -> Cobros.anularPago(pagoInscripcion, "otro", "Personal", base,
                 solicitudes, matriculas, pagos), "pago anulado no se puede repetir");
        assertAuditoria(pagoInscripcion, base, "error de registro", "Personal de caja");
        comprobar(pagos.listar().contains(pagoInscripcion),
                "el pago anulado permanece registrado tras reintento de Cobros");
        Pago reemplazo = Cobros.confirmarInscripcion(inscripcion,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.plusHours(1),
                "voucher-reemplazo", base.plusHours(1), solicitudes, matriculas, pagos, cuotas);
        comprobar(reemplazo.estaConfirmado() && pagos.listar().contains(pagoInscripcion)
                && pagos.listar().size() == 2, "nuevo pago confirmado conserva el anulado");
        LocalDateTime directAuditDate = base.plusHours(2);
        reemplazo.anular("anulación directa", "Personal responsable", directAuditDate);
        comprobar(reemplazo.getFechaAnulacion().equals(directAuditDate)
                && reemplazo.getMotivoAnulacion().equals("anulación directa")
                && reemplazo.getResponsableAnulacion().equals("Personal responsable"),
                "Pago.anular conserva auditoría completa");
        esperarFallo(TransicionInvalidaException.class,
                 () -> reemplazo.anular("cambio", "Otro", base.plusHours(3)),
                 "Pago.anular repetido rechaza modificación de auditoría");
        assertAuditoria(reemplazo, directAuditDate, "anulación directa", "Personal responsable");
        comprobar(pagos.listar().contains(reemplazo),
                "el pago anulado permanece registrado tras reintento directo");

        LocalDateTime exactInscriptionBase = LocalDateTime.of(2026, 10, 8, 12, 0);
        Solicitud inscripcionExacta = registrarSolicitud(solicitudes, aula, "INS-48",
                exactInscriptionBase.minusHours(48));
        inscripcionExacta.habilitarParaPago(exactInscriptionBase.minusHours(48));
        Pago pagoInscripcionExacta = new Pago(inscripcionExacta, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "",
                exactInscriptionBase.minusHours(47), exactInscriptionBase.minusHours(46),
                "voucher-48", cuotas);
        pagoInscripcionExacta.confirmar();
        pagos.agregar(pagoInscripcionExacta);
        inscripcionExacta.confirmarInscripcion(exactInscriptionBase.minusHours(47));
        Solicitud colaInscripcion = registrarSolicitud(solicitudes, aula, "COLA-48-A",
                exactInscriptionBase.minusHours(47));
        Solicitud colaInscripcionEmpatada = registrarSolicitud(solicitudes, aula, "COLA-48-B",
                exactInscriptionBase.minusHours(46));
        colaInscripcion.ingresarAColaSinPago(exactInscriptionBase.plusSeconds(2));
        colaInscripcionEmpatada.ingresarAColaSinPago(exactInscriptionBase.plusSeconds(2));
        Cobros.anularPago(pagoInscripcionExacta, "venció", "Personal", exactInscriptionBase,
                solicitudes, matriculas, pagos);
        comprobar(inscripcionExacta.getEstado() == EstadoSolicitud.EN_ESPERA_SIN_PAGO
                && solicitudes.colaSinPago(aula).get(solicitudes.colaSinPago(aula).size() - 1)
                        == inscripcionExacta
                && inscripcionExacta.getFechaIngresoCola().equals(exactInscriptionBase.plusSeconds(3)),
                "habilitación exactamente 48 horas termina después de dos empates");

        Solicitud solicitudDuplicada = solicitud("DUP", base.minusDays(2), aula);
        solicitudDuplicada.habilitarParaPago(base.minusHours(2));
        Pago primero = new Pago(solicitudDuplicada, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-dup-1", cuotas);
        Pago segundo = new Pago(solicitudDuplicada, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-dup-2", cuotas);
        primero.confirmar();
        segundo.confirmar();
        pagos.agregar(primero);
        pagos.agregar(segundo);
        solicitudDuplicada.confirmarInscripcion(base.minusHours(1));
        Snapshot snapshotDuplicado = snapshot(primero, solicitudDuplicada, null, solicitudes,
                matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(primero, "duplicado", "Personal", base,
                solicitudes, matriculas, pagos), "otro pago confirmado rechaza anulación atómicamente");
        assertSnapshot(snapshotDuplicado, primero, solicitudDuplicada, null, solicitudes, matriculas,
                pagos, "rechazo por otro pago no muta relaciones");
        comprobar(segundo.estaConfirmado(), "el segundo pago confirmado también permanece intacto");
        Solicitud solicitudInvalida = solicitud("ESTADO", base.minusDays(2), aula);
        solicitudInvalida.habilitarParaPago(base.minusHours(2));
        Pago pagoSolicitudInvalida = new Pago(solicitudInvalida, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-estado", cuotas);
        pagoSolicitudInvalida.confirmar();
        pagos.agregar(pagoSolicitudInvalida);
        solicitudInvalida.confirmarInscripcion(base.minusHours(1));
        solicitudInvalida.cambiarEstado(EstadoSolicitud.EN_ESPERA_SIN_PAGO);
        Snapshot snapshotEstadoGiant = snapshot(pagoSolicitudInvalida, solicitudInvalida, null,
                solicitudes, matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(pagoSolicitudInvalida, "estado", "Personal", base,
                solicitudes, matriculas, pagos), "solicitud en estado inválido rechazada");
        assertSnapshot(snapshotEstadoGiant, pagoSolicitudInvalida, solicitudInvalida, null,
                solicitudes, matriculas, pagos, "estado inválido no muta solicitud ni auditoría");
        Solicitud matriculaSolicitud = solicitud("MAT", base.minusDays(3), aula);
        matriculaSolicitud.habilitarParaPago(base.minusHours(3));
        matriculaSolicitud.confirmarInscripcion(base.minusHours(2));
        Matricula matricula = new Matricula(matriculaSolicitud, base.minusHours(72));
        matriculas.add(matricula);
        Pago pagoMatricula = new Pago(matriculaSolicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(1),
                base.minusMinutes(30),
                "voucher-matricula", cuotas);
        pagoMatricula.confirmar();
        pagos.agregar(pagoMatricula);
        Cobros.anularPago(pagoMatricula, "registro equivocado", "Personal de caja", base,
                solicitudes, matriculas, pagos);
        comprobar(matricula.getEstado() == EstadoMatricula.CANCELADA,
                "la matrícula vencida se cancela");
        comprobar(matriculaSolicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE,
                "la solicitud vuelve a cola favorable");
        Solicitud activaSolicitud = solicitud("ACT", base.minusDays(4), aula);
        activaSolicitud.habilitarParaPago(base.minusHours(4));
        activaSolicitud.confirmarInscripcion(base.minusHours(3));
        Matricula activa = new Matricula(activaSolicitud, base.minusHours(2));
        matriculas.add(activa);
        Pago pagoActivo = new Pago(activaSolicitud, ConceptoPago.MATRICULA, activa,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-activa", cuotas);
        pagoActivo.confirmar();
        pagos.agregar(pagoActivo);
        activa.activar();
        Cobros.anularPago(pagoActivo, "corrección", "Personal de caja", base,
                solicitudes, matriculas, pagos);
        comprobar(activa.getEstado() == EstadoMatricula.PENDIENTE_PAGO,
                "la matrícula activa vigente vuelve a pendiente sin reiniciar fecha");
        comprobar(pagos.listar().contains(pagoActivo) && !pagos.matriculaConfirmada(activa),
                "la consulta de matrícula conserva y excluye el pago anulado");
        LocalDateTime pendienteDeadline = base.minusHours(71).plusHours(72);
        Solicitud pendienteSolicitud = solicitud("PEND", base.minusDays(4), aula);
        pendienteSolicitud.habilitarParaPago(base.minusHours(4));
        pendienteSolicitud.confirmarInscripcion(base.minusHours(3));
        Matricula pendienteVigente = new Matricula(pendienteSolicitud, base.minusHours(71));
        matriculas.add(pendienteVigente);
        Pago pagoPendienteVigente = new Pago(pendienteSolicitud, ConceptoPago.MATRICULA,
                pendienteVigente, cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "",
                base.minusHours(1), base, "voucher-pendiente", cuotas);
        pagoPendienteVigente.confirmar();
        pagos.agregar(pagoPendienteVigente);
        Cobros.anularPago(pagoPendienteVigente, "antes del vencimiento", "Personal", base,
                solicitudes, matriculas, pagos);
        comprobar(pendienteVigente.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                && pendienteVigente.getVencimientoPagoOriginal().equals(pendienteDeadline),
                "matrícula pendiente justo antes de 72 horas conserva plazo");

        LocalDateTime exactMatriculaBase = LocalDateTime.of(2026, 10, 9, 10, 0);
        Solicitud activaVencida = registrarSolicitud(solicitudes, aula, "ACT-72",
                exactMatriculaBase.minusHours(72));
        activaVencida.habilitarParaPago(exactMatriculaBase.minusHours(6));
        activaVencida.confirmarInscripcion(exactMatriculaBase.minusHours(5));
        LocalDateTime creacion72h = exactMatriculaBase.minusHours(72);
        Matricula matriculaActivaVencida = new Matricula(activaVencida, creacion72h);
        LocalDateTime deadline72h = matriculaActivaVencida.getVencimientoPagoOriginal();
        matriculas.add(matriculaActivaVencida);
        Pago pagoActivoVencido = new Pago(activaVencida, ConceptoPago.MATRICULA,
                matriculaActivaVencida, cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "",
                 exactMatriculaBase.minusHours(1), exactMatriculaBase.minusMinutes(30),
                 "voucher-activa-72h", cuotas);
        pagoActivoVencido.confirmar();
        pagos.agregar(pagoActivoVencido);
        matriculaActivaVencida.activar();
        Solicitud colaFavorableA = registrarSolicitud(solicitudes, aula, "COLA-FAV-A",
                exactMatriculaBase.minusHours(71));
        Solicitud colaFavorableB = registrarSolicitud(solicitudes, aula, "COLA-FAV-B",
                exactMatriculaBase.minusHours(70));
        colaFavorableA.habilitarParaPago(exactMatriculaBase.minusHours(2));
        colaFavorableA.confirmarInscripcion(exactMatriculaBase.minusHours(1));
        colaFavorableB.habilitarParaPago(exactMatriculaBase.minusHours(2));
        colaFavorableB.confirmarInscripcion(exactMatriculaBase.minusHours(1));
        colaFavorableA.ingresarAColaFavorable(exactMatriculaBase);
        colaFavorableB.ingresarAColaFavorable(exactMatriculaBase);
        int vacantesAntes = negocio.Vacantes.calcular(aula, matriculas);
        Cobros.anularPago(pagoActivoVencido, "vencimiento exacto", "Personal de caja",
                exactMatriculaBase,
                solicitudes, matriculas, pagos);
        comprobar(matriculaActivaVencida.getEstado() == EstadoMatricula.CANCELADA,
                "la matrícula activa vence exactamente a las 72 horas y se cancela");
        comprobar(pagoActivoVencido.getEstado() == modelo.EstadoPago.ANULADO,
                "el pago de matrícula activa vencida queda anulado");
        comprobar(activaVencida.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE,
                "la solicitud vuelve a cola favorable y conserva la inscripción");
        comprobar(matriculaActivaVencida.getFechaHoraCreacion().equals(creacion72h)
                && matriculaActivaVencida.getVencimientoPagoOriginal().equals(deadline72h)
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesAntes + 1,
                "creación y deadline originales se conservan y la vacante se libera");
        java.util.ArrayList<Solicitud> colaFavorable = solicitudes.colaFavorable(aula);
        comprobar(colaFavorable.get(0) == colaFavorableA
                && colaFavorable.get(1) == colaFavorableB
                && colaFavorable.get(2) == activaVencida
                && activaVencida.getFechaIngresoCola().equals(exactMatriculaBase.plusSeconds(1)),
                "cola favorable conserva empate y agrega al final con fecha exacta");

        Solicitud invalida = solicitud("INV", base.minusDays(5), aula);
        invalida.habilitarParaPago(base.minusHours(5));
        invalida.confirmarInscripcion(base.minusHours(4));
        Matricula pendiente = new Matricula(invalida, base.minusHours(2));
        matriculas.add(pendiente);
        Pago pagoPendiente = new Pago(invalida, ConceptoPago.MATRICULA, pendiente,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-invalido", cuotas);
        pagoPendiente.confirmar();
        pagos.agregar(pagoPendiente);
        Snapshot snapshotMotivoGiant = snapshot(pagoPendiente, invalida, pendiente, solicitudes,
                matriculas, pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoPendiente, " ", "Personal", base,
                solicitudes, matriculas, pagos), "motivo vacío rechazado sin mutación");
        assertSnapshot(snapshotMotivoGiant, pagoPendiente, invalida, pendiente, solicitudes, matriculas,
                pagos, "motivo vacío rechazado sin mutación");
        Snapshot snapshotFechaGiant = snapshot(pagoPendiente, invalida, pendiente, solicitudes,
                matriculas, pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoPendiente, "motivo", "Personal", null,
                solicitudes, matriculas, pagos), "fecha nula rechazada sin mutación");
        assertSnapshot(snapshotFechaGiant, pagoPendiente, invalida, pendiente, solicitudes,
                matriculas, pagos, "fecha nula rechazada sin mutación");
        Snapshot snapshotResponsableNuloGiant = snapshot(pagoPendiente, invalida, pendiente,
                solicitudes, matriculas, pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoPendiente, "motivo", null, base,
                solicitudes, matriculas, pagos), "responsable nulo rechazado sin mutación");
        assertSnapshot(snapshotResponsableNuloGiant, pagoPendiente, invalida, pendiente, solicitudes,
                matriculas, pagos, "responsable nulo rechazado sin mutación");
        Snapshot snapshotResponsableVacioGiant = snapshot(pagoPendiente, invalida, pendiente,
                solicitudes, matriculas, pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoPendiente, "motivo", " ", base,
                solicitudes, matriculas, pagos), "responsable vacío rechazado sin mutación");
        assertSnapshot(snapshotResponsableVacioGiant, pagoPendiente, invalida, pendiente, solicitudes,
                matriculas, pagos, "responsable vacío rechazado sin mutación");
        Pago recibido = new Pago(invalida, ConceptoPago.MATRICULA, pendiente,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-recibido", cuotas);
        pagos.agregar(recibido);
        Snapshot snapshotRecibidoGiant = snapshot(recibido, invalida, pendiente, solicitudes,
                matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(recibido, "motivo", "Personal", base,
                solicitudes, matriculas, pagos), "pago RECIBIDO rechazado");
        assertSnapshot(snapshotRecibidoGiant, recibido, invalida, pendiente, solicitudes,
                matriculas, pagos, "pago RECIBIDO rechazado sin mutación");
        Pago noRegistrado = new Pago(invalida, ConceptoPago.MATRICULA, pendiente,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-no-registrado", cuotas);
        noRegistrado.confirmar();
        Snapshot snapshotNoRegistradoGiant = snapshot(noRegistrado, invalida, pendiente, solicitudes,
                matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(noRegistrado, "motivo", "Personal", base,
                solicitudes, matriculas, pagos), "pago no registrado rechazado");
        assertSnapshot(snapshotNoRegistradoGiant, noRegistrado, invalida, pendiente, solicitudes,
                matriculas, pagos, "pago no registrado rechazado sin mutación");
        Matricula matriculaAusente = new Matricula(invalida, base.minusHours(1));
        Pago pagoMatriculaAusente = new Pago(invalida, ConceptoPago.MATRICULA, matriculaAusente,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "voucher-ausente", cuotas);
        pagoMatriculaAusente.confirmar();
        pagos.agregar(pagoMatriculaAusente);
        Snapshot snapshotAusenteGiant = snapshot(pagoMatriculaAusente, invalida, matriculaAusente,
                solicitudes, matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(pagoMatriculaAusente, "ausente", "Personal", base,
                solicitudes, matriculas, pagos), "matrícula ausente de colección rechazada");
        assertSnapshot(snapshotAusenteGiant, pagoMatriculaAusente, invalida, matriculaAusente,
                solicitudes, matriculas, pagos, "relación de matrícula ausente no muta");
        Solicitud solicitudIncompatible = solicitud("INCOMP", base.minusDays(2), aula);
        solicitudIncompatible.habilitarParaPago(base.minusHours(2));
        solicitudIncompatible.confirmarInscripcion(base.minusHours(1));
        Matricula matriculaIncompatible = new Matricula(solicitudIncompatible, base);
        int pagosAntesIncompatible = pagos.listar().size();
        Snapshot snapshotIncompatibleRequest = snapshot(null, invalida, matriculaIncompatible,
                solicitudes, matriculas, pagos);
        Snapshot snapshotIncompatibleOwner = snapshot(null, solicitudIncompatible,
                matriculaIncompatible, solicitudes, matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> new Pago(invalida, ConceptoPago.MATRICULA, matriculaIncompatible,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base, base,
                "voucher-incompatible", cuotas), "solicitud y matrícula incompatibles rechazadas");
        assertSnapshot(snapshotIncompatibleRequest, null, invalida, matriculaIncompatible,
                solicitudes, matriculas, pagos, "solicitud incompatible no muta relación");
        assertSnapshot(snapshotIncompatibleOwner, null, solicitudIncompatible, matriculaIncompatible,
                solicitudes, matriculas, pagos, "matrícula incompatible no muta relación");
        comprobar(pagos.listar().size() == pagosAntesIncompatible,
                "relación incompatible no modifica pagos");
        Snapshot snapshotDirectRetry = snapshot(pagoInscripcion, inscripcion, null, solicitudes,
                matriculas, pagos);
        esperarFallo(TransicionInvalidaException.class,
                () -> pagoInscripcion.anular("otra", "Personal", base),
                "un pago anulado no se puede anular otra vez");
        assertSnapshot(snapshotDirectRetry, pagoInscripcion, inscripcion, null, solicitudes,
                matriculas, pagos, "reintento directo conserva todos los datos");

        Solicitud overflow = solicitud("OVER", base.minusDays(7), aula);
        overflow.habilitarParaPago(base.minusHours(50));
        Pago pagoOverflow = new Pago(overflow, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(49),
                base.minusHours(48),
                "voucher-overflow", cuotas);
        pagoOverflow.confirmar();
        pagos.agregar(pagoOverflow);
        overflow.confirmarInscripcion(base.minusHours(48));
        Solicitud colaMaxima = registrarSolicitud(solicitudes, aula, "COLA-MAX", base.minusDays(8));
        colaMaxima.ingresarAColaSinPago(LocalDateTime.MAX);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(pagoOverflow, "overflow", "Personal", LocalDateTime.MAX,
                solicitudes, matriculas, pagos), "overflow de cola rechazado atómicamente");
        comprobar(pagoOverflow.estaConfirmado() && overflow.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                "overflow no muta el pago ni la solicitud");

        javafxSafeUiAssertion(pagoInscripcion);
    }

    private static void probarMatriculaActivaVigenteYVencida() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-A", "Nombrada", 4, 2);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();

        Solicitud vigente = registrarSolicitud(solicitudes, aula, "NAMED-V", base.minusDays(2));
        vigente.habilitarParaPago(base.minusHours(2));
        vigente.confirmarInscripcion(base.minusHours(1));
        Matricula activa = new Matricula(vigente, base.minusHours(2));
        LocalDateTime creacion = activa.getFechaHoraCreacion();
        LocalDateTime deadline = activa.getVencimientoPagoOriginal();
        matriculas.add(activa);
        Pago pago = pagoMatricula(vigente, activa, base, cuotas, "named-vigente");
        pago.confirmar();
        pagos.agregar(pago);
        activa.activar();
        int vacantesAntes = negocio.Vacantes.calcular(aula, matriculas);
        Cobros.anularPago(pago, "vigente", "Personal", base, solicitudes, matriculas, pagos);
        comprobar(activa.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                && activa.getFechaHoraCreacion().equals(creacion)
                && activa.getVencimientoPagoOriginal().equals(deadline)
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesAntes,
                "named matrícula activa vigente conserva plazo y vacante");

        Solicitud vencida = registrarSolicitud(solicitudes, aula, "NAMED-E", base.minusDays(3));
        vencida.habilitarParaPago(base.minusHours(3));
        Pago inscripcion = new Pago(vencida, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(2), base,
                "named-inscripcion", cuotas);
        inscripcion.confirmar();
        pagos.agregar(inscripcion);
        vencida.confirmarInscripcion(base.minusHours(2));
        Matricula activaVencida = new Matricula(vencida, base.minusHours(72));
        LocalDateTime creacionVencida = activaVencida.getFechaHoraCreacion();
        LocalDateTime deadlineVencida = activaVencida.getVencimientoPagoOriginal();
        matriculas.add(activaVencida);
        Pago pagoVencido = pagoMatricula(vencida, activaVencida, base, cuotas, "named-vencida");
        pagoVencido.confirmar();
        pagos.agregar(pagoVencido);
        activaVencida.activar();
        int vacantesAntesVencida = negocio.Vacantes.calcular(aula, matriculas);
        Cobros.anularPago(pagoVencido, "vencida", "Personal", base,
                solicitudes, matriculas, pagos);
        comprobar(activaVencida.getEstado() == EstadoMatricula.CANCELADA
                && pagoVencido.getEstado() == modelo.EstadoPago.ANULADO
                && pagos.listar().contains(inscripcion)
                && inscripcion.getEstado() == modelo.EstadoPago.CONFIRMADO
                && inscripcion.getFechaAnulacion() == null
                && pagos.inscripcionConfirmada(vencida)
                && activaVencida.getFechaHoraCreacion().equals(creacionVencida)
                && activaVencida.getVencimientoPagoOriginal().equals(deadlineVencida)
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesAntesVencida + 1
                && vencida.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE,
                "named matrícula activa exacta 72 horas libera vacante y conserva inscripción");
        java.util.ArrayList<Solicitud> colaVencida = solicitudes.colaFavorable(aula);
        comprobar(colaVencida.contains(vencida)
                && colaVencida.get(colaVencida.size() - 1) == vencida
                && vencida.getFechaIngresoCola().equals(base)
                && vencida.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE,
                "matrícula vencida queda en la cola favorable real con fecha asignada");

        Solicitud pendienteSolicitud = registrarSolicitud(solicitudes, aula, "NAMED-P",
                base.minusDays(3));
        pendienteSolicitud.habilitarParaPago(base.minusHours(3));
        Pago inscripcionPendiente = new Pago(pendienteSolicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(2), base,
                "named-pendiente-inscripcion", cuotas);
        inscripcionPendiente.confirmar();
        pagos.agregar(inscripcionPendiente);
        pendienteSolicitud.confirmarInscripcion(base.minusHours(2));
        Matricula pendiente = new Matricula(pendienteSolicitud, base.minusHours(72));
        matriculas.add(pendiente);
        Pago pagoPendiente = pagoMatricula(pendienteSolicitud, pendiente, base, cuotas,
                "named-pendiente");
        pagoPendiente.confirmar();
        pagos.agregar(pagoPendiente);
        int vacantesAntesPendiente = negocio.Vacantes.calcular(aula, matriculas);
        Cobros.anularPago(pagoPendiente, "pendiente vencida", "Personal", base,
                solicitudes, matriculas, pagos);
        comprobar(pendiente.getEstado() == EstadoMatricula.CANCELADA,
                "matrícula pendiente vencida se cancela");
        comprobar(pendiente.getFechaHoraCreacion().equals(base.minusHours(72))
                && pendiente.getVencimientoPagoOriginal().equals(base),
                "matrícula pendiente vencida conserva creación y deadline");
        comprobar(pendienteSolicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && pagos.listar().contains(inscripcionPendiente)
                && inscripcionPendiente.getEstado() == modelo.EstadoPago.CONFIRMADO
                && inscripcionPendiente.getFechaAnulacion() == null
                && pagos.inscripcionConfirmada(pendienteSolicitud),
                "matrícula pendiente vencida conserva inscripción confirmada");
        comprobar(negocio.Vacantes.calcular(aula, matriculas) == vacantesAntesPendiente + 1,
                "matrícula pendiente vencida libera una vacante");
        comprobar(solicitudes.colaFavorable(aula).contains(pendienteSolicitud)
                && solicitudes.colaFavorable(aula).get(solicitudes.colaFavorable(aula).size() - 1)
                        == pendienteSolicitud
                && pendienteSolicitud.getFechaIngresoCola().equals(base.plusSeconds(1)),
                "matrícula pendiente vencida conserva cola real y fecha asignada");
    }

    private static void probarColasConEmpates() throws Exception {
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-Q", "Colas", 4, 4);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();

        LocalDateTime baseSinPago = LocalDateTime.of(2026, 10, 8, 10, 0);
        Solicitud objetivoSinPago = registrarSolicitud(solicitudes, aula, "TARGET-SIN",
                baseSinPago.minusHours(48));
        objetivoSinPago.habilitarParaPago(baseSinPago.minusHours(48));
        Pago pagoSinPago = new Pago(objetivoSinPago, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", baseSinPago.minusHours(47),
                baseSinPago.minusHours(46), "queue-sin", cuotas);
        pagoSinPago.confirmar();
        pagos.agregar(pagoSinPago);
        objetivoSinPago.confirmarInscripcion(baseSinPago.minusHours(47));
        Solicitud sinA = registrarSolicitud(solicitudes, aula, "SIN-A", baseSinPago.minusHours(47));
        Solicitud sinB = registrarSolicitud(solicitudes, aula, "SIN-B", baseSinPago.minusHours(46));
        sinA.ingresarAColaSinPago(baseSinPago.plusSeconds(2));
        sinB.ingresarAColaSinPago(baseSinPago.plusSeconds(2));
        Cobros.anularPago(pagoSinPago, "cola sin pago", "Personal", baseSinPago,
                solicitudes, matriculas, pagos);
        java.util.ArrayList<Solicitud> colaSinPago = solicitudes.colaSinPago(aula);
        comprobar(colaSinPago.size() == 3 && colaSinPago.get(0) == sinA
                && colaSinPago.get(1) == sinB && colaSinPago.get(2) == objetivoSinPago
                && sinA.getFechaIngresoCola().equals(baseSinPago.plusSeconds(2))
                && sinB.getFechaIngresoCola().equals(baseSinPago.plusSeconds(2))
                && objetivoSinPago.getFechaIngresoCola().equals(baseSinPago.plusSeconds(3)),
                "colaSinPagoConEmpates conserva ambos empates y agrega al final");

        LocalDateTime baseFavorable = LocalDateTime.of(2026, 10, 8, 14, 0);
        Solicitud objetivoFavorable = registrarSolicitud(solicitudes, aula, "TARGET-FAV",
                baseFavorable.minusHours(72));
        objetivoFavorable.habilitarParaPago(baseFavorable.minusHours(6));
        Pago inscripcion = new Pago(objetivoFavorable, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", baseFavorable.minusHours(4),
                baseFavorable.minusHours(3), "queue-fav-ins", cuotas);
        inscripcion.confirmar();
        pagos.agregar(inscripcion);
        objetivoFavorable.confirmarInscripcion(baseFavorable.minusHours(5));
        Matricula matricula = new Matricula(objetivoFavorable, baseFavorable.minusHours(72));
        matriculas.add(matricula);
        Pago pagoFavorable = pagoMatricula(objetivoFavorable, matricula, baseFavorable, cuotas,
                "queue-fav");
        pagoFavorable.confirmar();
        pagos.agregar(pagoFavorable);
        Solicitud favA = registrarSolicitud(solicitudes, aula, "FAV-A", baseFavorable.minusHours(71));
        Solicitud favB = registrarSolicitud(solicitudes, aula, "FAV-B", baseFavorable.minusHours(70));
        favA.habilitarParaPago(baseFavorable.minusHours(2));
        favA.confirmarInscripcion(baseFavorable.minusHours(1));
        favB.habilitarParaPago(baseFavorable.minusHours(2));
        favB.confirmarInscripcion(baseFavorable.minusHours(1));
        favA.ingresarAColaFavorable(baseFavorable);
        favB.ingresarAColaFavorable(baseFavorable);
        Cobros.anularPago(pagoFavorable, "cola favorable", "Personal", baseFavorable,
                solicitudes, matriculas, pagos);
        java.util.ArrayList<Solicitud> colaFavorable = solicitudes.colaFavorable(aula);
        comprobar(colaFavorable.size() == 3 && colaFavorable.get(0) == favA
                && colaFavorable.get(1) == favB && colaFavorable.get(2) == objetivoFavorable
                && favA.getFechaIngresoCola().equals(baseFavorable)
                && favB.getFechaIngresoCola().equals(baseFavorable)
                && objetivoFavorable.getFechaIngresoCola().equals(baseFavorable.plusSeconds(1)),
                "colaFavorableConEmpates conserva ambos empates y agrega al final");
    }

    private static void probarRechazosAtomicos() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-R", "Rechazos", 4, 2);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-R-S", base.minusDays(2));
        solicitud.habilitarParaPago(base.minusHours(2));
        Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-rechazo", cuotas);
        pago.confirmar();
        pagos.agregar(pago);
        solicitud.confirmarInscripcion(base.minusHours(1));
        solicitud.cambiarEstado(EstadoSolicitud.EN_ESPERA_SIN_PAGO);
        Snapshot snapshotEstado = snapshot(pago, solicitud, null, solicitudes, matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(pago, "estado", "Personal", base,
                        solicitudes, matriculas, pagos), "solicitud inválida");
        assertSnapshot(snapshotEstado, pago, solicitud, null, solicitudes, matriculas, pagos,
                "rechazo de solicitud inválida es atómico");

        Solicitud otra = registrarSolicitud(solicitudes, aula, "NAMED-R-M", base.minusDays(2));
        otra.habilitarParaPago(base.minusHours(2));
        otra.confirmarInscripcion(base.minusHours(1));
        Matricula ausente = new Matricula(otra, base.minusHours(1));
        Pago pagoAusente = pagoMatricula(otra, ausente, base, cuotas, "named-ausente");
        pagoAusente.confirmar();
        pagos.agregar(pagoAusente);
        Snapshot snapshotAusente = snapshot(pagoAusente, otra, ausente, solicitudes, matriculas,
                pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(pagoAusente, "ausente", "Personal", base,
                        solicitudes, matriculas, pagos), "matrícula ausente");
        assertSnapshot(snapshotAusente, pagoAusente, otra, ausente, solicitudes, matriculas, pagos,
                "rechazo de matrícula ausente conserva todos los datos");

        Solicitud incompatible = registrarSolicitud(solicitudes, aula, "NAMED-R-I", base.minusDays(2));
        incompatible.habilitarParaPago(base.minusHours(2));
        incompatible.confirmarInscripcion(base.minusHours(1));
        Matricula otraMatricula = new Matricula(incompatible, base);
        int registrosAntes = pagos.listar().size();
        Snapshot snapshotIncompatible = snapshot(null, otra, otraMatricula, solicitudes, matriculas,
                pagos);
        esperarFallo(ReglaDominioException.class,
                () -> new Pago(otra, ConceptoPago.MATRICULA, otraMatricula,
                        cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base, base,
                        "named-incompatible", cuotas), "solicitud y matrícula incompatibles");
        assertSnapshot(snapshotIncompatible, null, otra, otraMatricula, solicitudes, matriculas,
                pagos, "relación incompatible no modifica colecciones ni matrícula");
        comprobar(pagos.listar().size() == registrosAntes, "relación incompatible no agrega pagos");

        Snapshot snapshotMotivo = snapshot(pagoAusente, otra, ausente, solicitudes, matriculas, pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoAusente, " ", "Personal", base,
                        solicitudes, matriculas, pagos), "motivo vacío");
        assertSnapshot(snapshotMotivo, pagoAusente, otra, ausente, solicitudes, matriculas, pagos,
                "motivo vacío conserva estado, auditoría, tiempos y colecciones");
        Snapshot snapshotResponsable = snapshot(pagoAusente, otra, ausente, solicitudes, matriculas,
                pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoAusente, "motivo", " ", base,
                        solicitudes, matriculas, pagos), "responsable vacío");
        assertSnapshot(snapshotResponsable, pagoAusente, otra, ausente, solicitudes, matriculas,
                pagos, "responsable vacío conserva estado, auditoría, tiempos y colecciones");
        Snapshot snapshotFecha = snapshot(pagoAusente, otra, ausente, solicitudes, matriculas, pagos);
        esperarFallo(DatoInvalidoException.class,
                () -> Cobros.anularPago(pagoAusente, "motivo", "Personal", null,
                        solicitudes, matriculas, pagos), "fecha nula");
        assertSnapshot(snapshotFecha, pagoAusente, otra, ausente, solicitudes, matriculas, pagos,
                "fecha nula conserva estado, auditoría, tiempos y colecciones");

        Pago recibido = pagoMatricula(otra, ausente, base, cuotas, "named-recibido");
        pagos.agregar(recibido);
        Snapshot snapshotRecibido = snapshot(recibido, otra, ausente, solicitudes, matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(recibido, "motivo", "Personal", base,
                        solicitudes, matriculas, pagos), "pago RECIBIDO");
        assertSnapshot(snapshotRecibido, recibido, otra, ausente, solicitudes, matriculas, pagos,
                "pago RECIBIDO conserva estado y colección registrada");

        Pago noRegistrado = pagoMatricula(otra, ausente, base, cuotas, "named-no-registrado");
        noRegistrado.confirmar();
        Snapshot snapshotNoRegistrado = snapshot(noRegistrado, otra, ausente, solicitudes,
                matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(noRegistrado, "motivo", "Personal", base,
                        solicitudes, matriculas, pagos), "pago confirmado no registrado");
        assertSnapshot(snapshotNoRegistrado, noRegistrado, otra, ausente, solicitudes, matriculas,
                pagos, "pago confirmado no registrado no muta nada");

        Solicitud overflowSolicitud = registrarSolicitud(solicitudes, aula, "NAMED-OVERFLOW",
                base.minusDays(7));
        overflowSolicitud.habilitarParaPago(base.minusHours(50));
        Pago overflow = new Pago(overflowSolicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(49),
                base.minusHours(48), "named-overflow", cuotas);
        overflow.confirmar();
        pagos.agregar(overflow);
        overflowSolicitud.confirmarInscripcion(base.minusHours(48));
        Solicitud colaMaxima = registrarSolicitud(solicitudes, aula, "NAMED-COLA-MAX",
                base.minusDays(8));
        colaMaxima.ingresarAColaSinPago(LocalDateTime.MAX);
        Snapshot snapshotOverflow = snapshot(overflow, overflowSolicitud, null, solicitudes,
                matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(overflow, "overflow", "Personal", LocalDateTime.MAX,
                        solicitudes, matriculas, pagos), "overflow de fecha de cola");
        assertSnapshot(snapshotOverflow, overflow, overflowSolicitud, null, solicitudes, matriculas,
                pagos,
                "overflow conserva estado, auditoría, tiempos y colecciones");

        probarHelperNoAceptaExcepcionInesperada();
    }

    private static void probarAuditoriaInmutable() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-H", "Auditoría", 4, 1);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-H-S", base.minusDays(2));
        solicitud.habilitarParaPago(base.minusHours(2));
        Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-audit", cuotas);
        pago.confirmar();
        ArregloPagos pagosDirecto = new ArregloPagos();
        pagosDirecto.agregar(pago);
        pago.anular("motivo inicial", "Personal inicial", base);
        assertAuditoria(pago, base, "motivo inicial", "Personal inicial");
        esperarFallo(TransicionInvalidaException.class,
                () -> pago.anular("motivo cambiado", "Personal cambiado", base.plusHours(1)),
                "Pago.anular repetido");
        assertAuditoria(pago, base, "motivo inicial", "Personal inicial");
        comprobar(pagosDirecto.listar().contains(pago) && pagosDirecto.listar().size() == 1,
                "Pago.anular conserva el registro después del reintento");

        ArregloPagos pagos = new ArregloPagos();
        pagos.agregar(pago);
        ArregloSolicitudes cobrosSolicitudes = new ArregloSolicitudes();
        Solicitud cobrosSolicitud = registrarSolicitud(cobrosSolicitudes, aula, "NAMED-H-C",
                base.minusDays(2));
        cobrosSolicitud.habilitarParaPago(base.minusHours(2));
        Pago cobrosPago = new Pago(cobrosSolicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-cobros-audit", cuotas);
        cobrosPago.confirmar();
        cobrosSolicitud.confirmarInscripcion(base.minusHours(1));
        pagos.agregar(cobrosPago);
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();
        Cobros.anularPago(cobrosPago, "cobros inicial", "Personal cobros", base,
                cobrosSolicitudes, matriculas, pagos);
        assertAuditoria(cobrosPago, base, "cobros inicial", "Personal cobros");
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(cobrosPago, "cobros cambiado", "Otro", base.plusHours(1),
                        cobrosSolicitudes, matriculas, pagos), "Cobros.anularPago repetido");
        assertAuditoria(cobrosPago, base, "cobros inicial", "Personal cobros");
        comprobar(pagos.listar().contains(cobrosPago) && pagos.listar().size() == 2,
                "Cobros.anularPago conserva el registro después del reintento");
    }

    private static void probarPlazoOriginalDeMatriculaParaAmbasFechas() throws Exception {
        LocalDateTime creacion = LocalDateTime.of(2026, 10, 10, 10, 0);
        LocalDateTime vencimiento = creacion.plusHours(72);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-D", "Fechas", 4, 1);
        Solicitud solicitud = solicitud("NAMED-D-S", creacion.minusDays(2), aula);
        solicitud.habilitarParaPago(creacion.minusHours(2));
        solicitud.confirmarInscripcion(creacion.minusHours(1));
        Matricula matricula = new Matricula(solicitud, creacion);
        Pago valido = new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", vencimiento.minusMinutes(2),
                vencimiento.minusMinutes(1), "named-deadline-ok", cuotas);
        comprobar(valido.getEstado() == modelo.EstadoPago.RECIBIDO,
                "pago de matrícula antes del deadline acepta ambas fechas");
        Snapshot snapshot = snapshot(valido, solicitud, matricula, new ArregloSolicitudes(),
                new java.util.ArrayList<Matricula>(), new ArregloPagos());
        esperarFallo(ReglaDominioException.class,
                () -> new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                        cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", vencimiento,
                        vencimiento, "named-deadline-operation", cuotas),
                "operación exactamente en deadline rechazada");
        esperarFallo(ReglaDominioException.class,
                () -> new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                        cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", vencimiento.minusMinutes(2),
                        vencimiento, "named-deadline-registration", cuotas),
                "registro exactamente en deadline rechazado");
        comprobar(valido.getFechaHoraOperacion().equals(vencimiento.minusMinutes(2))
                && valido.getFechaHoraRegistro().equals(vencimiento.minusMinutes(1)),
                "los pagos registrados conservan sus fechas originales");
        assertSnapshot(snapshot, valido, solicitud, matricula, new ArregloSolicitudes(),
                new java.util.ArrayList<Matricula>(), new ArregloPagos(),
                "rechazos de deadline no mutan el pago válido");

        comprobar(valido.getFechaHoraOperacion().isBefore(valido.getFechaHoraRegistro())
                && !valido.getFechaHoraOperacion().isBefore(solicitud.getFechaRegistro())
                && valido.getFechaHoraRegistro().isBefore(vencimiento),
                "caso válido respeta registro de solicitud, orden y deadline");

        Pago operacionAntes = pagoMatriculaConFechas(creacion, vencimiento.minusMinutes(2),
                vencimiento.minusMinutes(1), "deadline-operacion-antes");
        comprobar(operacionAntes.getFechaHoraOperacion().isBefore(vencimiento)
                && operacionAntes.getFechaHoraRegistro().equals(vencimiento.minusMinutes(1)),
                "fixture de operación antes conserva registro previo al deadline");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> pagoMatriculaConFechas(creacion, vencimiento, vencimiento.plusMinutes(1),
                        "deadline-operacion-exacto"),
                "operación exactamente en deadline identifica la guardia de operación",
                "La operación del pago de matrícula ocurre en o después");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> pagoMatriculaConFechas(creacion, vencimiento.plusMinutes(1),
                        vencimiento.plusMinutes(2), "deadline-operacion-despues"),
                "operación después de deadline identifica la guardia de operación",
                "La operación del pago de matrícula ocurre en o después");

        esperarFalloConMensaje(ReglaDominioException.class,
                () -> pagoMatriculaConFechas(creacion, vencimiento.minusMinutes(1), vencimiento,
                        "deadline-registro-exacto"),
                "registro exactamente en deadline identifica la guardia de registro",
                "El registro del pago de matrícula ocurre en o después");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> pagoMatriculaConFechas(creacion, vencimiento.minusMinutes(1),
                        vencimiento.plusMinutes(1), "deadline-registro-despues"),
                "registro después de deadline identifica la guardia de registro",
                "El registro del pago de matrícula ocurre en o después");

        LocalDateTime reemplazoBase = LocalDateTime.of(2026, 10, 11, 10, 0);
        LocalDateTime reemplazoCreacion = reemplazoBase.minusHours(72);
        LocalDateTime reemplazoDeadline = reemplazoCreacion.plusHours(72);
        ConfiguracionCuotas reemplazoCuotas = new ConfiguracionCuotas();
        Aula reemplazoAula = new Aula("NAMED-D-R", "Reemplazos", 4, 1);
        ArregloSolicitudes reemplazoSolicitudes = new ArregloSolicitudes();
        Solicitud reemplazoSolicitud = registrarSolicitud(reemplazoSolicitudes, reemplazoAula,
                "NAMED-D-R-S", reemplazoCreacion.minusDays(2));
        reemplazoSolicitud.habilitarParaPago(reemplazoCreacion.minusHours(2));
        reemplazoSolicitud.confirmarInscripcion(reemplazoCreacion.minusHours(1));
        Matricula reemplazoMatricula = new Matricula(reemplazoSolicitud, reemplazoCreacion);
        java.util.ArrayList<Matricula> reemplazoMatriculas =
                new java.util.ArrayList<Matricula>();
        reemplazoMatriculas.add(reemplazoMatricula);
        Pago original = new Pago(reemplazoSolicitud, ConceptoPago.MATRICULA, reemplazoMatricula,
                reemplazoCuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "",
                reemplazoBase.minusHours(2), reemplazoBase.minusHours(1),
                "replacement-original", reemplazoCuotas);
        original.confirmar();
        ArregloPagos reemplazoPagos = new ArregloPagos();
        reemplazoPagos.agregar(original);
        reemplazoMatricula.activar();
        Cobros.anularPago(original, "error de registro", "Personal", reemplazoBase.minusHours(1),
                reemplazoSolicitudes, reemplazoMatriculas, reemplazoPagos);
        comprobar(reemplazoMatricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO,
                "anulación vigente deja matrícula pendiente para reemplazo");
        Pago reemplazoAntes = new Pago(reemplazoSolicitud, ConceptoPago.MATRICULA,
                reemplazoMatricula, reemplazoCuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "",
                reemplazoDeadline.minusMinutes(2), reemplazoDeadline.minusMinutes(1),
                "replacement-before", reemplazoCuotas);
        reemplazoAntes.confirmar();
        reemplazoPagos.agregar(reemplazoAntes);
        comprobar(reemplazoAntes.estaConfirmado()
                && reemplazoAntes.getFechaHoraOperacion().isBefore(reemplazoDeadline),
                "reemplazo antes del deadline original es confirmado");
        comprobar(original.getEstado() == modelo.EstadoPago.ANULADO
                && reemplazoMatricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                && original.getFechaHoraOperacion().equals(reemplazoBase.minusHours(2))
                && original.getFechaHoraRegistro().equals(reemplazoBase.minusHours(1)),
                "reemplazo rechazado conserva pago original anulado y sus fechas");
        comprobar(reemplazoPagos.listar().size() == 2
                && reemplazoPagos.listar().contains(original)
                && reemplazoPagos.listar().contains(reemplazoAntes),
                "colección real conserva original anulado y reemplazo confirmado");

        probarReemplazoFueraDePlazo(reemplazoBase, reemplazoDeadline,
                reemplazoDeadline.plusMinutes(1), "operation-exact",
                "La operación del pago de matrícula ocurre en o después");
        probarReemplazoFueraDePlazo(reemplazoBase, reemplazoDeadline.plusMinutes(1),
                reemplazoDeadline.plusMinutes(2), "operation-after",
                "La operación del pago de matrícula ocurre en o después");
        probarReemplazoFueraDePlazo(reemplazoBase, reemplazoDeadline.minusMinutes(1),
                reemplazoDeadline, "registration-exact",
                "El registro del pago de matrícula ocurre en o después");
        probarReemplazoFueraDePlazo(reemplazoBase, reemplazoDeadline.minusMinutes(1),
                reemplazoDeadline.plusMinutes(1), "registration-after",
                "El registro del pago de matrícula ocurre en o después");
    }

    private static void probarReemplazoFueraDePlazo(LocalDateTime base,
            LocalDateTime operacionReemplazo, LocalDateTime registroReemplazo,
            String caso, String mensajeEsperado) throws Exception {
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("AUL-RP-" + caso, "Reemplazo " + caso, 4, 1);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();
        LocalDateTime creacion = base.minusHours(72);
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "RP-" + caso,
                creacion.minusDays(2));
        solicitud.habilitarParaPago(creacion.minusHours(2));
        solicitud.confirmarInscripcion(creacion.minusHours(1));
        Matricula matricula = new Matricula(solicitud, creacion);
        matriculas.add(matricula);
        Pago original = new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", base.minusHours(2),
                base.minusHours(1), "original-" + caso, cuotas);
        original.confirmar();
        pagos.agregar(original);
        matricula.activar();
        Cobros.anularPago(original, "error de registro", "Personal", base.minusHours(1),
                solicitudes, matriculas, pagos);
        comprobar(matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO,
                "fixture " + caso + " deja la matrícula pendiente tras anulación válida");
        Snapshot despuesAnulacion = snapshot(original, solicitud, matricula, solicitudes,
                matriculas, pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                        cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", operacionReemplazo,
                        registroReemplazo, "replacement-" + caso, cuotas),
                "reemplazo " + caso + " rechaza las fechas originales expiradas",
                mensajeEsperado);
        assertSnapshot(despuesAnulacion, original, solicitud, matricula, solicitudes, matriculas,
                pagos, "reemplazo " + caso + " no muta original, auditoría ni colecciones");
        assertAuditoria(original, base.minusHours(1), "error de registro", "Personal");
        comprobar(original.getFechaHoraOperacion().equals(base.minusHours(2))
                && original.getFechaHoraRegistro().equals(base.minusHours(1))
                && pagos.listar().size() == 1,
                "reemplazo " + caso + " conserva fechas y registro original");
    }

    private static void probarProteccionDeInscripcionConMatriculaVigente() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 12, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-G", "Guardia", 4, 1);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-G-S", base.minusDays(2));
        solicitud.habilitarParaPago(base.minusHours(2));
        Pago inscripcion = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-guard", cuotas);
        inscripcion.confirmar();
        pagos.agregar(inscripcion);
        solicitud.confirmarInscripcion(base.minusHours(1));
        Matricula vigente = new Matricula(solicitud, base.minusHours(1));
        matriculas.add(vigente);
        Solicitud otraSolicitud = registrarSolicitud(solicitudes, aula, "NAMED-G-OTHER",
                base.minusDays(3));
        otraSolicitud.habilitarParaPago(base.minusHours(3));
        otraSolicitud.confirmarInscripcion(base.minusHours(2));
        Matricula otraVigente = new Matricula(otraSolicitud, base.minusHours(1));
        otraVigente.activar();
        matriculas.add(otraVigente);
        Snapshot antes = snapshot(inscripcion, solicitud, vigente, solicitudes, matriculas, pagos);
        esperarFallo(ReglaDominioException.class,
                () -> Cobros.anularPago(inscripcion, "error", "Personal", base,
                solicitudes, matriculas, pagos),
                "inscripción con matrícula vigente exige coordinación previa");
        assertSnapshot(antes, inscripcion, solicitud, vigente, solicitudes, matriculas, pagos,
                "guardia de matrícula vigente es atómica");
        vigente.activar();
        Snapshot antesActiva = snapshot(inscripcion, solicitud, vigente, solicitudes, matriculas,
                pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Cobros.anularPago(inscripcion, "error", "Personal", base,
                        solicitudes, matriculas, pagos),
                "inscripción con matrícula ACTIVA de la misma solicitud se rechaza",
                "No se puede anular la inscripción mientras la matrícula vigente");
        assertSnapshot(antesActiva, inscripcion, solicitud, vigente, solicitudes, matriculas,
                pagos, "guardia de matrícula ACTIVA de la misma solicitud es atómica");
        Snapshot otraAntes = snapshot(null, otraSolicitud, otraVigente, solicitudes, matriculas,
                pagos);
        vigente.cancelar();
        Cobros.anularPago(inscripcion, "error", "Personal", base,
                solicitudes, matriculas, pagos);
        comprobar(inscripcion.getEstado() == modelo.EstadoPago.ANULADO,
                "matrícula de la misma solicitud cancelada permite anular inscripción");
        comprobar(otraVigente.getEstado() == EstadoMatricula.ACTIVA,
                "matrícula activa de otra solicitud no bloquea la anulación");
        assertSnapshot(otraAntes, null, otraSolicitud, otraVigente, solicitudes, matriculas, pagos,
                "matrícula y solicitud de otra operación quedan intactas");
    }

    private static void probarComprobanteDePagoAnulado() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 13, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-C", "Comprobantes", 4, 1);
        Solicitud solicitud = solicitud("NAMED-C-S", base.minusDays(2), aula);
        solicitud.habilitarParaPago(base.minusHours(2));
        Pago anulado = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-cancelled", cuotas);
        anulado.confirmar();
        ArregloPagos pagosSinBoleta = new ArregloPagos();
        pagosSinBoleta.agregar(anulado);
        anulado.anular("error", "Personal", base);
        Snapshot antes = snapshot(anulado, solicitud, null, new ArregloSolicitudes(),
                new java.util.ArrayList<Matricula>(), pagosSinBoleta);
        esperarFallo(ReglaDominioException.class, () -> anulado.registrarComprobante("12345"),
                "pago anulado sin boleta rechaza comprobante");
        assertSnapshot(antes, anulado, solicitud, null, new ArregloSolicitudes(),
                new java.util.ArrayList<Matricula>(), pagosSinBoleta,
                "rechazo de comprobante anulado no altera auditoría");
        comprobar(pagosSinBoleta.listar().size() == 1 && anulado.getComprobante().isEmpty(),
                "pago anulado sin boleta conserva colección y comprobante vacío");

        Solicitud registradoSolicitud = solicitud("NAMED-C-REAL", base.minusDays(2), aula);
        registradoSolicitud.habilitarParaPago(base.minusHours(2));
        Pago registrado = new Pago(registradoSolicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-real-receipt", cuotas);
        registrado.confirmar();
        registrado.registrarComprobante("987654");
        ArregloPagos pagosRegistrados = new ArregloPagos();
        pagosRegistrados.agregar(registrado);
        registrado.anular("error de registro", "Personal", base.plusMinutes(1));
        Snapshot registradoDespues = snapshot(registrado, registradoSolicitud, null,
                new ArregloSolicitudes(), new java.util.ArrayList<Matricula>(), pagosRegistrados);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> registrado.registrarComprobante("123456"),
                "pago anulado con boleta conserva el guardia de estado",
                "No se puede registrar un comprobante en un pago anulado");
        assertSnapshot(registradoDespues, registrado, registradoSolicitud, null,
                new ArregloSolicitudes(), new java.util.ArrayList<Matricula>(), pagosRegistrados,
                "boleta existente, auditoría y colección permanecen inmutables");
        comprobar(registrado.getComprobante().equals("987654")
                && registrado.getFechaAnulacion().equals(base.plusMinutes(1)),
                "anulación conserva la boleta original y su fecha");

        Pago confirmado = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-confirmed", cuotas);
        confirmado.confirmar();
        confirmado.registrarComprobante("456");
        comprobar(confirmado.getComprobante().equals("456"),
                "pago confirmado conserva registro de comprobante");
        Pago recibido = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-received", cuotas);
        recibido.registrarComprobante("789");
        comprobar(recibido.getComprobante().equals("789"),
                "pago recibido conserva registro de comprobante");
    }

    private static void probarIntegracionUiTrasAnulacionExitosa() throws Exception {
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDateTime ahora = LocalDateTime.now();
                ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
                Aula aula = new Aula("NAMED-UI", "UI", 4, 1);
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                ArregloPagos pagos = new ArregloPagos();
                java.util.ArrayList<Matricula> matriculas = new java.util.ArrayList<Matricula>();
                Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-UI-S",
                        ahora.minusDays(2));
                solicitud.habilitarParaPago(ahora.minusHours(1));
                Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                        cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", ahora.minusMinutes(10),
                        ahora.minusMinutes(5), "named-ui", cuotas);
                pago.confirmar();
                pagos.agregar(pago);
                solicitud.confirmarInscripcion(ahora.minusMinutes(10));
                ui.PanelPagos panel = new ui.PanelPagos(solicitudes, pagos, matriculas, cuotas);
                ((javax.swing.JTextField) campoPrivado(panel, "campoCodigo")).setText(
                        solicitud.getCodigo());
                java.lang.reflect.Method buscar = panel.getClass().getDeclaredMethod("buscar");
                buscar.setAccessible(true);
                buscar.invoke(panel);
                ui.TarjetasPago tarjetas = (ui.TarjetasPago) campoPrivado(panel, "tarjetas");
                ((javax.swing.JTextField) campoPrivado(tarjetas, "campoMotivoAnulacion"))
                        .setText("error de registro");
                ((javax.swing.JTextField) campoPrivado(tarjetas, "campoResponsableAnulacion"))
                        .setText("Personal");
                ((javax.swing.JButton) campoPrivado(tarjetas, "botonAnular")).doClick();
                javax.swing.JTextField monto = (javax.swing.JTextField) campoPrivado(tarjetas,
                        "campoMonto");
                javax.swing.JComboBox<?> medio = (javax.swing.JComboBox<?>) campoPrivado(tarjetas,
                        "comboMedio");
                javax.swing.JTextField operacion = (javax.swing.JTextField) campoPrivado(tarjetas,
                        "campoOperacion");
                javax.swing.JTextField fecha = (javax.swing.JTextField) campoPrivado(tarjetas,
                        "campoFecha");
                javax.swing.JCheckBox revisado = (javax.swing.JCheckBox) campoPrivado(tarjetas,
                        "revisado");
                javax.swing.JButton confirmar = (javax.swing.JButton) campoPrivado(tarjetas,
                        "botonConfirmar");
                comprobar(monto.getText().equals(String.format(java.util.Locale.ROOT, "%.2f",
                        cuotas.getCuotaInscripcion())),
                        "UI conserva el monto actualizado tras anulación");
                comprobar(monto.isEditable() && medio.isEnabled() && operacion.isEditable()
                        && fecha.isEditable(),
                        "callback real deja editable el formulario de reemplazo");
                medio.setSelectedItem(MedioPago.YAPE);
                operacion.setText("UI-REEMPLAZO");
                fecha.setText(ahora.minusMinutes(2).format(ui.TarjetasPago.FORMATO_FECHA));
                File evidencia = new File(System.getProperty("opencode.test.logs", ""));
                comprobar(evidencia.isAbsolute() && evidencia.isDirectory(),
                        "la evidencia UI usa el directorio absoluto de logs");
                File comprobante = File.createTempFile("prueba-ui-reemplazo-", ".png", evidencia);
                BufferedImage imagen = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                ImageIO.write(imagen, "png", comprobante);
                java.lang.reflect.Method mostrarComprobante = tarjetas.getClass()
                        .getDeclaredMethod("mostrarComprobante", String.class);
                mostrarComprobante.setAccessible(true);
                mostrarComprobante.invoke(tarjetas, comprobante.getAbsolutePath());
                revisado.setSelected(true);
                java.lang.reflect.Method validar = tarjetas.getClass().getDeclaredMethod("validar");
                validar.setAccessible(true);
                validar.invoke(tarjetas);
                comprobar(confirmar.isVisible() && confirmar.isEnabled(),
                        "UI habilita confirmar después de completar el reemplazo: "
                                + motivosUi(tarjetas));
                confirmar.doClick();
                comprobar(pagos.listar().size() == 2 && pago.getEstado() == modelo.EstadoPago.ANULADO
                        && pagos.inscripcionConfirmada(solicitud)
                        && solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                        "UI confirma reemplazo y conserva el pago anulado");

                LocalDateTime expiradaAhora = LocalDateTime.now();
                Aula aulaExpirada = new Aula("NAMED-UI-EXP", "UI expirada", 4, 1);
                ArregloSolicitudes solicitudesExpiradas = new ArregloSolicitudes();
                ArregloPagos pagosExpirados = new ArregloPagos();
                java.util.ArrayList<Matricula> matriculasExpiradas =
                        new java.util.ArrayList<Matricula>();
                Solicitud expirada = registrarSolicitud(solicitudesExpiradas, aulaExpirada,
                        "NAMED-UI-EXP-S", expiradaAhora.minusDays(3));
                expirada.habilitarParaPago(expiradaAhora.minusHours(49));
                Pago pagoExpirado = new Pago(expirada, ConceptoPago.INSCRIPCION, null,
                        cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "",
                        expiradaAhora.minusHours(2), expiradaAhora.minusMinutes(90),
                        "ui-expirada", cuotas);
                pagoExpirado.confirmar();
                pagosExpirados.agregar(pagoExpirado);
                expirada.confirmarInscripcion(expiradaAhora.minusHours(2));
                ui.PanelPagos panelExpirado = new ui.PanelPagos(solicitudesExpiradas,
                        pagosExpirados, matriculasExpiradas, cuotas);
                ((javax.swing.JTextField) campoPrivado(panelExpirado, "campoCodigo")).setText(
                        expirada.getCodigo());
                java.lang.reflect.Method buscarExpirado = panelExpirado.getClass()
                        .getDeclaredMethod("buscar");
                buscarExpirado.setAccessible(true);
                buscarExpirado.invoke(panelExpirado);
                ui.TarjetasPago tarjetasExpirada = (ui.TarjetasPago) campoPrivado(panelExpirado,
                        "tarjetas");
                ((javax.swing.JTextField) campoPrivado(tarjetasExpirada,
                        "campoMotivoAnulacion")).setText("error expirado");
                ((javax.swing.JTextField) campoPrivado(tarjetasExpirada,
                        "campoResponsableAnulacion")).setText("Personal");
                ((javax.swing.JButton) campoPrivado(tarjetasExpirada, "botonAnular")).doClick();
                comprobar(expirada.getEstado() == EstadoSolicitud.EN_ESPERA_SIN_PAGO
                        && expirada.estaEnColaSinPago()
                        && solicitudesExpiradas.listar().contains(expirada),
                        "UI expirada devuelve solicitud a la cola sin pago");
                comprobar(!((javax.swing.JButton) campoPrivado(tarjetasExpirada,
                                "botonConfirmar")).isVisible()
                        && !((javax.swing.JTextField) campoPrivado(tarjetasExpirada,
                                "campoMonto")).isEditable()
                        && !((javax.swing.JPanel) campoPrivado(tarjetasExpirada,
                                "panelAnulacion")).isVisible(),
                        "UI expirada bloquea controles de reemplazo");
                comprobar(((javax.swing.JTextArea) campoPrivado(tarjetasExpirada, "mensaje"))
                                .getText().contains("Pago anulado"),
                        "UI expirada conserva mensaje de anulación");
                comprobar(((javax.swing.JLabel) campoPrivado(tarjetasExpirada, "tituloAviso"))
                        .getText().equals("NO SE PUEDE REGISTRAR EL PAGO")
                        && ((javax.swing.JTextArea) campoPrivado(tarjetasExpirada, "detalleAviso"))
                                .getText().contains("EN_ESPERA_SIN_PAGO"),
                        "UI expirada muestra aviso de cola sin pago: título="
                                + ((javax.swing.JLabel) campoPrivado(tarjetasExpirada, "tituloAviso"))
                                        .getText()
                                + " detalle="
                                + ((javax.swing.JTextArea) campoPrivado(tarjetasExpirada, "detalleAviso"))
                                        .getText());

                LocalDateTime falloAhora = LocalDateTime.now();
                Aula aulaFallo = new Aula("NAMED-UI-FAIL", "UI fallo", 4, 1);
                ArregloSolicitudes solicitudesFallo = new ArregloSolicitudes();
                ArregloPagos pagosFallo = new ArregloPagos();
                java.util.ArrayList<Matricula> matriculasFallo =
                        new java.util.ArrayList<Matricula>();
                Solicitud solicitudFallo = registrarSolicitud(solicitudesFallo, aulaFallo,
                        "NAMED-UI-FAIL-S", falloAhora.minusDays(2));
                solicitudFallo.habilitarParaPago(falloAhora.minusHours(1));
                Pago pagoFallo = new Pago(solicitudFallo, ConceptoPago.INSCRIPCION, null,
                        cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "",
                        falloAhora.minusMinutes(20), falloAhora.minusMinutes(10),
                        "ui-fallo", cuotas);
                pagoFallo.confirmar();
                pagosFallo.agregar(pagoFallo);
                solicitudFallo.confirmarInscripcion(falloAhora.minusMinutes(20));
                Matricula matriculaFallo = new Matricula(solicitudFallo,
                        falloAhora.minusMinutes(5));
                matriculaFallo.activar();
                matriculasFallo.add(matriculaFallo);
                Snapshot falloAntes = snapshot(pagoFallo, solicitudFallo, matriculaFallo,
                        solicitudesFallo, matriculasFallo, pagosFallo);
                ui.PanelPagos panelFallo = new ui.PanelPagos(solicitudesFallo, pagosFallo,
                        matriculasFallo, cuotas);
                ((javax.swing.JTextField) campoPrivado(panelFallo, "campoCodigo")).setText(
                        solicitudFallo.getCodigo());
                java.lang.reflect.Method buscarFallo = panelFallo.getClass()
                        .getDeclaredMethod("buscar");
                buscarFallo.setAccessible(true);
                buscarFallo.invoke(panelFallo);
                ui.TarjetasPago tarjetasFallo = (ui.TarjetasPago) campoPrivado(panelFallo,
                        "tarjetas");
                ((javax.swing.JTextField) campoPrivado(tarjetasFallo,
                        "campoMotivoAnulacion")).setText("error con matrícula");
                ((javax.swing.JTextField) campoPrivado(tarjetasFallo,
                        "campoResponsableAnulacion")).setText("Personal");
                ((javax.swing.JButton) campoPrivado(tarjetasFallo, "botonAnular")).doClick();
                comprobar(pagoFallo.estaConfirmado()
                        && solicitudFallo.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                        && ((javax.swing.JTextArea) campoPrivado(tarjetasFallo, "mensaje"))
                                .getText().contains("No se puede anular la inscripción mientras")
                        && ((javax.swing.JPanel) campoPrivado(tarjetasFallo, "panelAnulacion"))
                                .isVisible()
                        && !((javax.swing.JButton) campoPrivado(tarjetasFallo,
                                "botonConfirmar")).isVisible(),
                        "UI muestra rechazo de dominio y mantiene pago confirmado");
                assertSnapshot(falloAntes, pagoFallo, solicitudFallo, matriculaFallo,
                        solicitudesFallo, matriculasFallo, pagosFallo,
                        "rechazo de UI por matrícula vigente es atómico");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static Pago pagoMatricula(Solicitud solicitud, Matricula matricula,
            LocalDateTime base, ConfiguracionCuotas cuotas, String comprobante)
            throws ReglaDominioException {
        LocalDateTime operacion = base.minusHours(1);
        LocalDateTime registro = base.minusMinutes(30);
        comprobar(!matricula.getFechaHoraCreacion().isAfter(operacion)
                && !operacion.isBefore(solicitud.getFechaRegistro())
                && !operacion.isAfter(registro)
                && !registro.isAfter(base)
                && operacion.isBefore(matricula.getVencimientoPagoOriginal())
                && registro.isBefore(matricula.getVencimientoPagoOriginal()),
                "fixture de matrícula respeta cronología real y plazo original");
        return new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", operacion, registro,
                comprobante, cuotas);
    }

    private static Pago pagoMatriculaConFechas(LocalDateTime creacion,
            LocalDateTime operacion, LocalDateTime registro, String comprobante)
            throws Exception {
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("AUL-DL-" + comprobante, "Deadline", 4, 1);
        Solicitud solicitud = solicitud("SOL-" + comprobante, creacion.minusDays(2), aula);
        solicitud.habilitarParaPago(creacion.minusHours(2));
        solicitud.confirmarInscripcion(creacion.minusHours(1));
        Matricula matricula = new Matricula(solicitud, creacion);
        comprobar(!operacion.isBefore(solicitud.getFechaRegistro())
                && !operacion.isAfter(registro),
                "fixture de deadline conserva orden solicitud-operación-registro");
        return new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", operacion, registro,
                comprobante, cuotas);
    }

    private static final class Snapshot {
        private final modelo.EstadoPago estadoPago;
        private final LocalDateTime fechaAnulacion;
        private final String motivoAnulacion;
        private final String responsableAnulacion;
        private final String comprobante;
        private final EstadoSolicitud estadoSolicitud;
        private final LocalDateTime fechaIngresoCola;
        private final LocalDateTime fechaHabilitacion;
        private final LocalDateTime vencimientoHabilitacion;
        private final LocalDateTime fechaConfirmacion;
        private final EstadoMatricula estadoMatricula;
        private final LocalDateTime creacionMatricula;
        private final LocalDateTime vencimientoMatricula;
        private final java.util.ArrayList<Solicitud> solicitudes;
        private final java.util.ArrayList<Matricula> matriculas;
        private final java.util.ArrayList<Pago> pagos;

        private Snapshot(Pago pago, Solicitud solicitud, Matricula matricula,
                ArregloSolicitudes arregloSolicitudes,
                java.util.ArrayList<Matricula> arregloMatriculas, ArregloPagos arregloPagos) {
            estadoPago = pago == null ? null : pago.getEstado();
            fechaAnulacion = pago == null ? null : pago.getFechaAnulacion();
            motivoAnulacion = pago == null ? null : pago.getMotivoAnulacion();
            responsableAnulacion = pago == null ? null : pago.getResponsableAnulacion();
            comprobante = pago == null ? null : pago.getComprobante();
            estadoSolicitud = solicitud == null ? null : solicitud.getEstado();
            fechaIngresoCola = solicitud == null ? null : solicitud.getFechaIngresoCola();
            fechaHabilitacion = solicitud == null ? null : solicitud.getFechaHabilitacion();
            vencimientoHabilitacion = solicitud == null
                    ? null : solicitud.getVencimientoHabilitacionOriginal();
            fechaConfirmacion = solicitud == null ? null : solicitud.getFechaConfirmacionInscripcion();
            estadoMatricula = matricula == null ? null : matricula.getEstado();
            creacionMatricula = matricula == null ? null : matricula.getFechaHoraCreacion();
            vencimientoMatricula = matricula == null ? null : matricula.getVencimientoPagoOriginal();
            solicitudes = arregloSolicitudes.listar();
            matriculas = new java.util.ArrayList<Matricula>(arregloMatriculas);
            pagos = arregloPagos.listar();
        }
    }

    private static Snapshot snapshot(Pago pago, Solicitud solicitud, Matricula matricula,
            ArregloSolicitudes solicitudes, java.util.ArrayList<Matricula> matriculas,
            ArregloPagos pagos) {
        return new Snapshot(pago, solicitud, matricula, solicitudes, matriculas, pagos);
    }

    private static void assertSnapshot(Snapshot antes, Pago pago, Solicitud solicitud,
            Matricula matricula, ArregloSolicitudes solicitudes,
            java.util.ArrayList<Matricula> matriculas, ArregloPagos pagos, String descripcion) {
        comprobar(antes.estadoPago == (pago == null ? null : pago.getEstado())
                && java.util.Objects.equals(antes.fechaAnulacion,
                        pago == null ? null : pago.getFechaAnulacion())
                && java.util.Objects.equals(antes.motivoAnulacion,
                        pago == null ? null : pago.getMotivoAnulacion())
                && java.util.Objects.equals(antes.responsableAnulacion,
                        pago == null ? null : pago.getResponsableAnulacion())
                && java.util.Objects.equals(antes.comprobante,
                        pago == null ? null : pago.getComprobante())
                && antes.estadoSolicitud == (solicitud == null ? null : solicitud.getEstado())
                && java.util.Objects.equals(antes.fechaIngresoCola,
                        solicitud == null ? null : solicitud.getFechaIngresoCola())
                && java.util.Objects.equals(antes.fechaHabilitacion,
                        solicitud == null ? null : solicitud.getFechaHabilitacion())
                && java.util.Objects.equals(antes.vencimientoHabilitacion,
                        solicitud == null ? null : solicitud.getVencimientoHabilitacionOriginal())
                && java.util.Objects.equals(antes.fechaConfirmacion,
                        solicitud == null ? null : solicitud.getFechaConfirmacionInscripcion())
                && antes.estadoMatricula == (matricula == null ? null : matricula.getEstado())
                && java.util.Objects.equals(antes.creacionMatricula,
                        matricula == null ? null : matricula.getFechaHoraCreacion())
                && java.util.Objects.equals(antes.vencimientoMatricula,
                        matricula == null ? null : matricula.getVencimientoPagoOriginal())
                && antes.solicitudes.equals(solicitudes.listar())
                && antes.matriculas.equals(matriculas)
                && antes.pagos.equals(pagos.listar()), descripcion);
    }

    private static void assertAuditoria(Pago pago, LocalDateTime fecha, String motivo,
            String responsable) {
        comprobar(pago.getEstado() == modelo.EstadoPago.ANULADO
                && pago.getFechaAnulacion().equals(fecha)
                && pago.getMotivoAnulacion().equals(motivo)
                && pago.getResponsableAnulacion().equals(responsable),
                "auditoría completa permanece inmutable");
    }

    private static void probarHelperNoAceptaExcepcionInesperada() throws Exception {
        boolean rechazo = false;
        try {
            esperarFallo(ReglaDominioException.class,
                    () -> { throw new IllegalStateException("unexpected"); },
                    "unexpected debe fallar el helper");
        } catch (IllegalStateException e) {
            rechazo = true;
        }
        comprobar(rechazo, "esperarFallo no acepta excepciones inesperadas");
    }

    private static Solicitud solicitud(String codigo, LocalDateTime registro, Aula aula)
            throws Exception {
        Alumno alumno = new Alumno(90000000 + codigo.hashCode() % 1000000,
                "70000001", "Prueba", codigo, LocalDate.of(2022, 6, 18), null);
        alumno.agregarApoderado(new Apoderado("80000001", "Apoderado", codigo, "999111222"),
                true);
        return new Solicitud(codigo, alumno, aula, registro);
    }

    @SuppressWarnings("unchecked")
    private static Solicitud registrarSolicitud(ArregloSolicitudes solicitudes, Aula aula,
            String codigo, LocalDateTime registro) throws Exception {
        Alumno alumno = new Alumno(91000000 + Math.abs(codigo.hashCode() % 900000),
                "71000001", "Cola", codigo, LocalDate.of(2022, 6, 18), null);
        alumno.agregarApoderado(new Apoderado("81000001", "Apoderado", codigo, "999111222"),
                true);
        Solicitud solicitud = new Solicitud(codigo, alumno, aula, registro);
        // ArregloSolicitudes no expone fecha de registro; el fixture inyecta una fecha fija
        // para no depender del reloj de la máquina en las pruebas de orden de colas.
        java.util.ArrayList<Solicitud> almacen =
                (java.util.ArrayList<Solicitud>) campoPrivado(solicitudes, "solicitudes");
        almacen.add(solicitud);
        return solicitud;
    }

    private static void javafxSafeUiAssertion(Pago pago) throws Exception {
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                ui.TarjetasPago tarjetas = new ui.TarjetasPago("Pago", "Concepto", "Confirmar",
                        "Fecha", (monto, medio, operacion, fecha, ruta) -> { });
                tarjetas.mostrarConfirmado(pago, "confirmado");
                tarjetas.configurarAnulacion((p, motivo, responsable, fecha) -> {
                    throw new ReglaDominioException("rechazo de UI");
                });
                ((javax.swing.JButton) campoPrivado(tarjetas, "botonAnular")).doClick();
                comprobar(((javax.swing.JTextArea) campoPrivado(tarjetas, "mensaje")).getText()
                        .contains("rechazo de UI"), "UI contiene error de dominio");
                final boolean[] llamo = new boolean[1];
                tarjetas.configurarAnulacion((p, motivo, responsable, fecha) -> {
                    llamo[0] = p == pago && fecha != null;
                });
                ((javax.swing.JButton) campoPrivado(tarjetas, "botonAnular")).doClick();
                 comprobar(llamo[0] && ((javax.swing.JPanel) campoPrivado(tarjetas,
                         "panelAnulacion")).isVisible()
                         && ((javax.swing.JTextArea) campoPrivado(tarjetas, "mensaje")).getText()
                                 .contains("Pago anulado"),
                         "UI conserva el formulario hasta que el callback lo actualice");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) throw failure[0];
    }

    private static Object campoPrivado(Object objeto, String nombre) throws Exception {
        java.lang.reflect.Field campo = objeto.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        return campo.get(objeto);
    }

    private static String motivosUi(ui.TarjetasPago tarjetas) throws Exception {
        javax.swing.JLabel[] motivos = (javax.swing.JLabel[]) campoPrivado(tarjetas, "motivos");
        StringBuilder texto = new StringBuilder();
        for (javax.swing.JLabel motivo : motivos) {
            texto.append('[').append(motivo.getText()).append(']');
        }
        return texto.toString();
    }

    private static void comprobar(boolean condicion, String descripcion) {
        if (!condicion) throw new AssertionError(descripcion);
    }

    private interface AccionFalla { void ejecutar() throws Exception; }

    private static void esperarFallo(
            Class<? extends ReglaDominioException> esperado, AccionFalla accion,
            String descripcion) throws Exception {
        try {
            accion.ejecutar();
        } catch (ReglaDominioException e) {
            if (!esperado.isInstance(e)) {
                throw new AssertionError(descripcion + ": excepción inesperada "
                        + e.getClass().getSimpleName(), e);
            }
            return;
        }
        throw new AssertionError(descripcion + ": la operación fue aceptada");
    }

    private static void esperarFalloConMensaje(
            Class<? extends ReglaDominioException> esperado, AccionFalla accion,
            String descripcion, String fragmentoMensaje) throws Exception {
        try {
            accion.ejecutar();
        } catch (ReglaDominioException e) {
            if (!esperado.isInstance(e)) {
                throw new AssertionError(descripcion + ": excepción inesperada "
                        + e.getClass().getSimpleName(), e);
            }
            comprobar(e.getMessage() != null && e.getMessage().contains(fragmentoMensaje),
                    descripcion + ": mensaje inesperado: " + e.getMessage());
            return;
        }
        throw new AssertionError(descripcion + ": la operación fue aceptada");
    }

    private static void cargarCasos(PrincipalUI ventana) throws ReglaDominioException {
        ArregloAlumnos alumnos = new ArregloAlumnos();
        ArregloApoderados apoderados = new ArregloApoderados();
        Aula girasoles = ventana.getAulas().buscar("AUL-01");
        Aula tulipanes = ventana.getAulas().buscar("AUL-02");
        LocalDate cuatroAnios = LocalDate.of(2022, 6, 18);
        LocalDate tresAnios = LocalDate.of(2023, 5, 10);
        LocalDateTime ahora = LocalDateTime.now();

        Solicitud vigente = registrar(ventana, alumnos, apoderados, "Mía", "León",
                cuatroAnios, girasoles);
        vigente.habilitarParaPago(ahora.minusHours(2));

        Solicitud otraVigente = registrar(ventana, alumnos, apoderados, "Noa", "Ruiz",
                tresAnios, tulipanes);
        otraVigente.habilitarParaPago(ahora.minusHours(47));

        Solicitud enCola = registrar(ventana, alumnos, apoderados, "Eva", "Díaz",
                tresAnios, tulipanes);
        enCola.ingresarAColaSinPago(ahora);

        Solicitud pagada = registrar(ventana, alumnos, apoderados, "Lucía", "Vega",
                cuatroAnios, girasoles);
        pagada.habilitarParaPago(ahora.minusHours(1));
        String comprobante = crearComprobanteDeEjemplo();
        Cobros.confirmarInscripcion(pagada, ventana.getCuotas().getCuotaInscripcion(),
                MedioPago.YAPE, "YP-8291457", LocalDateTime.now(), comprobante,
                LocalDateTime.now(), ventana.getSolicitudes(), ventana.getMatriculas(),
                ventana.getPagos(), ventana.getCuotas());

        Solicitud vencida = registrar(ventana, alumnos, apoderados, "Leo", "Silva",
                cuatroAnios, girasoles);
        vencida.habilitarParaPago(ahora.minusHours(50));

        System.out.println("Casos de prueba de Pagos:");
        System.out.println("  " + vigente.getCodigo() + "  habilitada hace 2 h (pagar)");
        System.out.println("  " + otraVigente.getCodigo() + "  habilitada hace 47 h (vence en 1 h)");
        System.out.println("  " + vencida.getCodigo() + "  habilitación vencida");
        System.out.println("  " + enCola.getCodigo() + "  en cola sin pago (no habilitada)");
        System.out.println("  " + pagada.getCodigo() + "  inscripción ya pagada (solo lectura)");
        System.out.println("  SOL-9999  no existe");
        System.out.println("Comprobante de ejemplo para adjuntar: " + comprobante);
        System.out.println("Las solicitudes se registran al abrir: la fecha de operación (con minutos)"
                + " recién es válida desde el minuto siguiente.");
    }

    private static String crearComprobanteDeEjemplo() {
        try {
            File archivo = File.createTempFile("comprobante-yape-", ".png");
            archivo.deleteOnExit();
            BufferedImage imagen = new BufferedImage(360, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = imagen.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 360, 200);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Yape · S/ 180.00", 30, 70);
            g.drawString("Operación YP-8291457", 30, 100);
            g.dispose();
            ImageIO.write(imagen, "png", archivo);
            return archivo.getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el comprobante de ejemplo.", e);
        }
    }

    private static Solicitud registrar(PrincipalUI ventana, ArregloAlumnos alumnos,
            ArregloApoderados apoderados, String nombres, String apellidos,
            LocalDate nacimiento, Aula aula) throws ReglaDominioException {
        Alumno alumno = alumnos.registrar(String.valueOf(siguienteDni++), nombres, apellidos,
                nacimiento, null);
        alumno.agregarApoderado(apoderados.registrar(String.valueOf(siguienteDni++),
                "Apoderado de", nombres, "999111222"), true);
        return ventana.getSolicitudes().registrar(alumno, aula);
    }
}
