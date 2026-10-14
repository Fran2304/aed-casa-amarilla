import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import datos.ArregloAlumnos;
import datos.ArregloApoderados;
import datos.Almacen;
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
import modelo.Oferta;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.TransicionInvalidaException;
import util.Reloj;
import negocio.Vencimientos;

import negocio.Cobros;
import negocio.Turnos;
import negocio.Vacantes;
import datos.ArregloMatriculas;
import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import datos.EscrituraCsv;
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
            // La demo gráfica nunca toca el directorio datos/ de producción.
            apuntarADirectorioTemporal();
            PrincipalUI ventana = new PrincipalUI();
            Solicitud casoExpediente;
            try {
                casoExpediente = cargarCasos(ventana);
            } catch (ReglaDominioException e) {
                throw new IllegalStateException("No se pudieron cargar los casos: "
                        + e.getMessage(), e);
            }
            if (args.length > 0 && args[0].equals("expediente")) {
                ventana.mostrar(PrincipalUI.EXPEDIENTE);
                SwingUtilities.invokeLater(() -> seleccionarEnExpediente(ventana, casoExpediente));
            } else {
                ventana.mostrar(PrincipalUI.PAGOS);
            }
            ventana.setVisible(true);
        });
    }

    private static void ejecutarAserciones() throws Exception {
        probarGeneradorDemoProfesor();
        probarMatriculaActivaVigenteYVencida();
        probarColasConEmpates();
        probarRechazosAtomicos();
        probarAuditoriaInmutable();
        probarPlazoOriginalDeMatriculaParaAmbasFechas();
        probarPlazosDocumentalesYCancelacion();
        probarEntrevistaYExpediente();
        probarProteccionDeInscripcionConMatriculaVigente();
        probarComprobanteDePagoAnulado();
        probarIntegracionUiTrasAnulacionExitosa();
        probarIntegracionUiBarridoDocumental();
        probarNuevaOfertaTrasAnulacionDeMatricula();
        probarCicloDeOfertaRechazadaYRechazosAtomicos();
        probarCiclosIndependientesConFechasNoMonotonicas();
        probarMotorVencimientosConReloj();
        probarCreacionYCicloDeMatricula();
        probarIntegracionUiMatricula();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();
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
        Matricula matricula = matriculas.registrar(matriculaSolicitud, base.minusHours(72));
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
        Matricula activa = matriculas.registrar(activaSolicitud, base.minusHours(2));
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
        Matricula pendienteVigente = matriculas.registrar(pendienteSolicitud, base.minusHours(71));
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
        Matricula matriculaActivaVencida = matriculas.registrar(activaVencida, creacion72h);
        LocalDateTime deadline72h = matriculaActivaVencida.getVencimientoPagoOriginal();
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
        Matricula pendiente = matriculas.registrar(invalida, base.minusHours(2));
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
        Matricula matriculaAusente = new Matricula(0, invalida, base.minusHours(1));
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
        Matricula matriculaIncompatible = new Matricula(0, solicitudIncompatible, base);
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

        probarPersistenciaIdaYVuelta();
        probarVencimientoAlReabrir();
        probarRechazoSinMutacionNoCambiaArchivos();
        probarRechazoConBarridoPersiste();
        probarCargaRechazaMatriculaColgante();
        probarCargaRechazaMatriculaSinNumero();
        probarCargaRechazaOfertaHuerfana();
        probarCargaRechazaEntrevistaDuplicada();
        probarCargaRechazaDocumentoDuplicado();
        probarArranqueGrabaBarrido();
        probarPanelSolicitudesGrabaBarrido();
        probarPanelColasGrabaBarrido();
        probarRecalcularSinVencimientosNoEscribe();

        javafxSafeUiAssertion(pagoInscripcion);
    }

    private static void probarMatriculaActivaVigenteYVencida() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-A", "Nombrada", 4, 2);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();

        Solicitud vigente = registrarSolicitud(solicitudes, aula, "NAMED-V", base.minusDays(2));
        vigente.habilitarParaPago(base.minusHours(2));
        vigente.confirmarInscripcion(base.minusHours(1));
        Matricula activa = matriculas.registrar(vigente, base.minusHours(2));
        LocalDateTime creacion = activa.getFechaHoraCreacion();
        LocalDateTime deadline = activa.getVencimientoPagoOriginal();
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
        Matricula activaVencida = matriculas.registrar(vencida, base.minusHours(72));
        LocalDateTime creacionVencida = activaVencida.getFechaHoraCreacion();
        LocalDateTime deadlineVencida = activaVencida.getVencimientoPagoOriginal();
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
        Matricula pendiente = matriculas.registrar(pendienteSolicitud, base.minusHours(72));
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

    private static void probarNuevaOfertaTrasAnulacionDeMatricula() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-OFFER-NEW", "Ofertas nuevas", 4, 1);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();

        CicloFixture fixture = prepararCicloOferta(solicitudes, pagos, matriculas, aula,
                "OFFER-NEW", base);
        Oferta primera = Turnos.confirmarOferta(fixture.solicitud, true, "Personal inicial",
                fixture.fechaOferta, solicitudes, matriculas);
        comprobar(primera.isAceptada() && fixture.solicitud.tieneOfertaAceptada()
                && fixture.solicitud.getOfertas().size() == 1,
                "oferta inicial aceptada queda en el ciclo actual");
        comprobarCronologia(fixture, primera.getFechaHora(), "oferta inicial");
        comprobarDocumentosValidados(fixture.solicitud, "oferta inicial");

        Snapshot antesTrueDuplicado = snapshot(null, fixture.solicitud, null, solicitudes,
                matriculas, pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Turnos.confirmarOferta(fixture.solicitud, true, "Personal duplicado true",
                        fixture.fechaOferta.plusMinutes(1), solicitudes, matriculas),
                "segunda aceptación true del ciclo es atómica", "ya aceptó una oferta");
        assertSnapshot(antesTrueDuplicado, null, fixture.solicitud, null, solicitudes, matriculas,
                pagos, "segunda aceptación true conserva historial y ciclo");
        Snapshot antesFalseDuplicado = snapshot(null, fixture.solicitud, null, solicitudes,
                matriculas, pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Turnos.confirmarOferta(fixture.solicitud, false, "Personal duplicado false",
                        fixture.fechaOferta.plusMinutes(2), solicitudes, matriculas),
                "segunda respuesta false del ciclo es atómica", "ya aceptó una oferta");
        assertSnapshot(antesFalseDuplicado, null, fixture.solicitud, null, solicitudes, matriculas,
                pagos, "segunda respuesta false conserva historial y ciclo");

        // La oferta aceptada ya creó la matrícula PENDIENTE_PAGO en la misma operación.
        Matricula matricula = matriculas.vigenteDe(fixture.solicitud);
        LocalDateTime creacion = matricula.getFechaHoraCreacion();
        Pago pagoMatricula = pagoMatriculaConFechas(fixture.solicitud, matricula,
                creacion.plusHours(1), creacion.plusHours(2), "offer-new-matricula", cuotas);
        pagoMatricula.confirmar();
        pagos.agregar(pagoMatricula);
        matricula.activar();
        Oferta ofertaHistorica = fixture.solicitud.getOfertas().get(0);
        LocalDateTime anulacion = matricula.getVencimientoPagoOriginal();
        Cobros.anularPago(pagoMatricula, "vencimiento de prueba", "Personal de caja", anulacion,
                solicitudes, matriculas, pagos);
        comprobar(matricula.getEstado() == EstadoMatricula.CANCELADA
                && fixture.solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && fixture.solicitud.getOfertas().get(0) == ofertaHistorica
                && fixture.solicitud.getOfertas().size() == 1
                && matricula.getFechaHoraCreacion().equals(creacion)
                && pagoMatricula.getFechaHoraOperacion().isBefore(anulacion)
                && pagoMatricula.getFechaHoraRegistro().isBefore(anulacion),
                "anulación en deadline conserva matrícula, fechas y oferta aceptada");

        Oferta segunda = Turnos.confirmarOferta(fixture.solicitud, true, "Personal de reingreso",
                anulacion.plusMinutes(1), solicitudes, matriculas);
        comprobar(segunda != primera && segunda.isAceptada()
                && fixture.solicitud.getOfertas().size() == 2
                && fixture.solicitud.getOfertas().get(0) == primera
                && fixture.solicitud.getOfertas().get(1) == segunda
                && fixture.solicitud.tieneOfertaAceptada(),
                "reingreso favorable inicia ciclo y permite nueva oferta aceptada");

        Snapshot antesTrueSegundoCiclo = snapshot(null, fixture.solicitud, matricula, solicitudes,
                matriculas, pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Turnos.confirmarOferta(fixture.solicitud, true,
                        "Personal duplicado true segundo ciclo", anulacion.plusMinutes(2),
                        solicitudes, matriculas),
                "segunda aceptación true del segundo ciclo es atómica", "ya aceptó una oferta");
        assertSnapshot(antesTrueSegundoCiclo, null, fixture.solicitud, matricula, solicitudes,
                matriculas, pagos, "true repetido no inserta oferta ni modifica la cola");
        Snapshot antesFalseSegundoCiclo = snapshot(null, fixture.solicitud, matricula, solicitudes,
                matriculas, pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Turnos.confirmarOferta(fixture.solicitud, false,
                        "Personal duplicado false segundo ciclo", anulacion.plusMinutes(3),
                        solicitudes, matriculas),
                "segunda respuesta false del segundo ciclo es atómica", "ya aceptó una oferta");
        assertSnapshot(antesFalseSegundoCiclo, null, fixture.solicitud, matricula, solicitudes,
                matriculas, pagos, "false repetido no inserta oferta ni modifica la cola");
        comprobar(fixture.solicitud.tieneOfertaAceptada()
                && fixture.solicitud.getOfertas().size() == 2
                && fixture.solicitud.getOfertas().get(0) == primera
                && fixture.solicitud.getOfertas().get(1) == segunda,
                "segundo ciclo conserva ambas identidades y aceptación actual");

        Snapshot snapshotNulo = snapshot(null, fixture.solicitud, matricula, solicitudes,
                matriculas, pagos);
        comprobar(fixture.solicitud.tieneOfertaAceptada()
                && fixture.solicitud.getOfertas().size() == 2
                && fixture.solicitud.getOfertas().get(0) == primera
                && fixture.solicitud.getOfertas().get(1) == segunda,
                "antes de fecha nula existe aceptación actual e historial completo");
        esperarFallo(DatoInvalidoException.class,
                () -> fixture.solicitud.ingresarAColaFavorable(null),
                "reingreso con fecha nula rechaza antes de mutar");
        assertSnapshot(snapshotNulo, null, fixture.solicitud, matricula, solicitudes, matriculas,
                pagos, "reingreso nulo conserva historial y relaciones");
    }

    private static void probarCicloDeOfertaRechazadaYRechazosAtomicos() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 9, 10, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-OFFER-DECLINE", "Oferta rechazada", 4, 1);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();

        CicloFixture fixture = prepararCicloOferta(solicitudes, pagos, matriculas, aula,
                "OFFER-DECLINE", base);
        Solicitud objetivo = fixture.solicitud;
        Oferta aceptadaAnterior = Turnos.confirmarOferta(objetivo, true, "Personal anterior",
                fixture.fechaOferta, solicitudes, matriculas);

        Matricula matricula = matriculas.vigenteDe(objetivo);
        LocalDateTime creacion = matricula.getFechaHoraCreacion();
        Pago pagoMatricula = pagoMatriculaConFechas(objetivo, matricula, creacion.plusHours(1),
                creacion.plusHours(2), "offer-decline-matricula", cuotas);
        pagoMatricula.confirmar();
        pagos.agregar(pagoMatricula);
        matricula.activar();
        LocalDateTime anulacion = matricula.getVencimientoPagoOriginal();
        Cobros.anularPago(pagoMatricula, "matrícula vencida", "Personal de caja", anulacion,
                solicitudes, matriculas, pagos);

        CicloFixture otraFixture = prepararCicloOfertaSinTurno(solicitudes, pagos, matriculas, aula,
                "OFFER-AHEAD", anulacion.plusMinutes(4));
        Solicitud otra = otraFixture.solicitud;
        java.util.ArrayList<Matricula> matriculasAntesRechazo =
                matriculas.listar();
        int vacantesAntesRechazo = negocio.Vacantes.calcular(aula, matriculas);
        Oferta rechazada = Turnos.confirmarOferta(objetivo, false, "Personal de turno",
                anulacion.plusMinutes(3), solicitudes, matriculas);
        comprobar(rechazada != aceptadaAnterior && !rechazada.isAceptada()
                && solicitudOrdenadaPrimero(solicitudes, aula, otra)
                && !objetivo.tieneOfertaAceptada()
                && objetivo.getOfertas().size() == 2
                && objetivo.getOfertas().get(0) == aceptadaAnterior
                && objetivo.getOfertas().get(1) == rechazada
                && objetivo.getFechaIngresoCola().isAfter(otra.getFechaIngresoCola())
                && matricula.getEstado() == EstadoMatricula.CANCELADA
                && matriculas.listar().equals(matriculasAntesRechazo)
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesAntesRechazo
                && pagos.inscripcionConfirmada(objetivo),
                "rechazo inicia ciclo nuevo, conserva aceptación anterior y respeta cola real");

        Oferta ofertaDeOtra = Turnos.confirmarOferta(otra, true, "Personal para otra",
                anulacion.plusMinutes(4),
                solicitudes, matriculas);
        comprobar(ofertaDeOtra.isAceptada() && otra.tieneOfertaAceptada()
                && objetivo.getOfertas().get(0).isAceptada()
                && objetivo.getOfertas().get(1) == rechazada,
                "otra solicitud en cabeza puede aceptar sin borrar historial objetivo");

        Solicitud invalida = solicitud("OFFER-INVALID", base, aula);
        Oferta historialAceptado = new Oferta(base.plusHours(1), "Personal de fixture", true);
        invalida.registrarOferta(historialAceptado);
        Snapshot snapshotInvalida = snapshot(null, invalida, null, solicitudes, matriculas, pagos);
        comprobar(invalida.tieneOfertaAceptada() && invalida.getOfertas().size() == 1,
                "fixture unitario inválido inicia con aceptación histórica actual");
        esperarFalloConMensaje(TransicionInvalidaException.class,
                () -> invalida.ingresarAColaFavorable(base.plusMinutes(1)),
                "ingreso favorable inválido conserva aceptación histórica",
                "EN_ESPERA_SIN_PAGO a EN_ESPERA_FAVORABLE");
        assertSnapshot(snapshotInvalida, null, invalida, null, solicitudes, matriculas, pagos,
                "ingreso favorable inválido no muta aceptación histórica ni colecciones");
        comprobar(invalida.tieneOfertaAceptada() && invalida.getOfertas().size() == 1
                && invalida.getOfertas().get(0) == historialAceptado
                && invalida.getEstado() == EstadoSolicitud.EN_ESPERA_SIN_PAGO,
                "ingreso favorable inválido no cambia índice, identidad, cantidad ni estado");
    }

    private static boolean solicitudOrdenadaPrimero(ArregloSolicitudes solicitudes, Aula aula,
            Solicitud esperada) {
        return solicitudes.colaFavorable(aula).get(0) == esperada;
    }

    private static void probarCiclosIndependientesConFechasNoMonotonicas() throws Exception {
        LocalDateTime fechaOfertaAnterior = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime fechaReingreso = fechaOfertaAnterior;
        Aula aula = new Aula("NAMED-OFFER-DATES", "Fechas de ciclos", 4, 2);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloMatriculas matriculas = new ArregloMatriculas();
        Solicitud mismaFecha = registrarSolicitud(solicitudes, aula, "OFFER-SAME-DATE",
                fechaReingreso.minusDays(3));
        mismaFecha.habilitarParaPago(fechaReingreso.minusHours(1));
        mismaFecha.confirmarInscripcion(fechaReingreso);
        mismaFecha.ingresarAColaFavorable(fechaReingreso);
        Oferta historica = Turnos.confirmarOferta(mismaFecha, true, "Personal histórico",
                fechaOfertaAnterior, solicitudes, matriculas);
        matriculas.vigenteDe(mismaFecha).cancelar(fechaReingreso, "reingreso de prueba", "Personal");
        mismaFecha.ingresarAColaFavorable(fechaReingreso);
        Oferta actual = Turnos.confirmarOferta(mismaFecha, true, "Personal actual",
                fechaOfertaAnterior, solicitudes, matriculas);
        comprobar(historica != actual && historica.getFechaHora().equals(actual.getFechaHora())
                && mismaFecha.getOfertas().size() == 2 && mismaFecha.tieneOfertaAceptada(),
                "unitario de índice permite reingreso con misma fecha de oferta y conserva identidad");

        Solicitud fechaDecreciente = registrarSolicitud(solicitudes, aula, "OFFER-BACKWARD",
                fechaReingreso.minusDays(2));
        fechaDecreciente.habilitarParaPago(fechaReingreso.minusHours(1));
        fechaDecreciente.confirmarInscripcion(fechaReingreso);
        fechaDecreciente.ingresarAColaFavorable(fechaOfertaAnterior);
        Oferta ofertaFutura = Turnos.confirmarOferta(fechaDecreciente, true, "Personal futuro",
                fechaOfertaAnterior.plusDays(1), solicitudes, matriculas);
        matriculas.vigenteDe(fechaDecreciente).cancelar(fechaReingreso, "reingreso de prueba",
                "Personal");
        fechaDecreciente.ingresarAColaFavorable(fechaReingreso);
        Oferta ofertaAnterior = Turnos.confirmarOferta(fechaDecreciente, true, "Personal anterior",
                fechaReingreso, solicitudes, matriculas);
        comprobar(ofertaFutura.getFechaHora().isAfter(ofertaAnterior.getFechaHora())
                && fechaDecreciente.getOfertas().get(0) == ofertaFutura
                && fechaDecreciente.getOfertas().get(1) == ofertaAnterior,
                "unitario de índice conserva ofertas aunque la fecha asignada retroceda");

        LocalDateTime cicloBase = LocalDateTime.of(2026, 10, 22, 10, 0);
        Aula aulaVencida = new Aula("NAMED-OFFER-FUTURE", "Cola futura", 4, 1);
        ArregloSolicitudes solicitudesVencida = new ArregloSolicitudes();
        ArregloPagos pagosVencida = new ArregloPagos();
        ArregloMatriculas matriculasVencida = new ArregloMatriculas();
        CicloFixture objetivo = prepararCicloOferta(solicitudesVencida, pagosVencida,
                matriculasVencida, aulaVencida, "OFFER-FUTURE-TARGET", cicloBase);
        Oferta ofertaObjetivo = Turnos.confirmarOferta(objetivo.solicitud, true,
                "Personal objetivo futuro", objetivo.fechaOferta, solicitudesVencida,
                matriculasVencida);
        Matricula matricula = matriculasVencida.vigenteDe(objetivo.solicitud);
        LocalDateTime creacion = matricula.getFechaHoraCreacion();
        Pago pago = pagoMatriculaConFechas(objetivo.solicitud, matricula, creacion.plusHours(1),
                creacion.plusHours(2), "future-target-matricula", new ConfiguracionCuotas());
        pago.confirmar();
        pagosVencida.agregar(pago);
        matricula.activar();
        LocalDateTime anulacion = matricula.getVencimientoPagoOriginal();
        LocalDateTime fechaAsignadaFutura = anulacion.plusDays(1);
        Solicitud colaFutura = registrarSolicitud(solicitudesVencida, aulaVencida,
                "OFFER-FUTURE-OTHER", cicloBase.minusDays(2));
        colaFutura.habilitarParaPago(cicloBase.minusHours(6));
        colaFutura.confirmarInscripcion(cicloBase.minusHours(5));
        registrarYValidarDocumentos(colaFutura, cicloBase.minusHours(4), cicloBase.minusHours(3));
        colaFutura.ingresarAColaFavorable(fechaAsignadaFutura);
        Cobros.anularPago(pago, "vencimiento futuro", "Personal de caja", anulacion,
                solicitudesVencida, matriculasVencida, pagosVencida);
        comprobar(objetivo.solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && objetivo.solicitud.getFechaIngresoCola().equals(fechaAsignadaFutura.plusSeconds(1))
                && !objetivo.solicitud.tieneOfertaAceptada()
                && objetivo.solicitud.getOfertas().size() == 1
                && objetivo.solicitud.getOfertas().get(0) == ofertaObjetivo
                && colaFutura.getFechaIngresoCola().equals(fechaAsignadaFutura),
                "anulación real reingresa después de la fecha futura máxima y conserva historial");
        Oferta ofertaActual = new Oferta(anulacion.plusMinutes(1), "Personal índice", true);
        objetivo.solicitud.registrarOferta(ofertaActual);
        comprobar(objetivo.solicitud.tieneOfertaAceptada()
                && objetivo.solicitud.getOfertas().size() == 2
                && objetivo.solicitud.getOfertas().get(0) == ofertaObjetivo
                && objetivo.solicitud.getOfertas().get(1) == ofertaActual,
                "unitario de índice cuenta aceptación actual aunque su fecha sea anterior a cola futura");
        Snapshot snapshotOfertaActual = snapshot(null, objetivo.solicitud, matricula,
                solicitudesVencida, matriculasVencida, pagosVencida);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Turnos.confirmarOferta(objetivo.solicitud, false, "Personal duplicado futuro",
                        anulacion.plusMinutes(2), solicitudesVencida, matriculasVencida),
                "aceptación actual bloquea oferta con cola futura", "ya aceptó una oferta");
        assertSnapshot(snapshotOfertaActual, null, objetivo.solicitud, matricula,
                solicitudesVencida, matriculasVencida, pagosVencida,
                "cola futura no desplaza el índice de aceptación actual");
    }

    private static void probarCreacionYCicloDeMatricula() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 9, 25, 16, 0);
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-MAT", "Girasoles prueba", 4, 4);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();
        CicloFixture mia = prepararCicloOferta(solicitudes, pagos, matriculas, aula,
                "MAT-MIA", base);
        CicloFixture rechazo = prepararCicloOferta(solicitudes, pagos, matriculas, aula,
                "MAT-RECHAZO", base.plusMinutes(1));
        CicloFixture leo = prepararCicloOferta(solicitudes, pagos, matriculas, aula,
                "MAT-LEO", base.plusMinutes(2));
        CicloFixture ana = prepararCicloOferta(solicitudes, pagos, matriculas, aula,
                "MAT-ANA", base.plusMinutes(3));
        int vacantesIniciales = negocio.Vacantes.calcular(aula, matriculas);

        // #23 · creación dentro de confirmarOferta
        Turnos.confirmarOferta(mia.solicitud, true, "Personal de admisión", mia.fechaOferta,
                solicitudes, matriculas);
        Matricula primera = matriculas.vigenteDe(mia.solicitud);
        comprobar(primera != null && primera.getNumMatricula() == 100001
                && primera.getCodigo().equals("MAT-100001")
                && primera.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                && primera.getFechaHoraCreacion().equals(mia.fechaOferta)
                && primera.getVencimientoPagoOriginal().equals(mia.fechaOferta.plusHours(72)),
                "oferta aceptada crea MAT-100001 PENDIENTE_PAGO con vencimiento de 72 h");
        comprobar(negocio.Vacantes.calcular(aula, matriculas) == vacantesIniciales - 1,
                "la creación reserva exactamente una vacante");
        comprobar(mia.solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && !Turnos.ordenPorPrioridad(aula, solicitudes, matriculas)
                        .contains(mia.solicitud),
                "con matrícula vigente sigue EN_ESPERA_FAVORABLE pero sale de los turnos");

        Turnos.confirmarOferta(rechazo.solicitud, false, "Personal de admisión",
                rechazo.fechaOferta, solicitudes, matriculas);
        comprobar(matriculas.listar().size() == 1 && matriculas.vigenteDe(rechazo.solicitud) == null
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesIniciales - 1,
                "oferta no aceptada no crea matrícula ni reserva vacante");

        Snapshot antesDuplicada = snapshot(null, mia.solicitud, primera, solicitudes, matriculas,
                pagos);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> matriculas.registrar(mia.solicitud, mia.fechaOferta.plusMinutes(5)),
                "alumno con matrícula vigente no recibe otra", "ya tiene la matrícula MAT-100001");
        assertSnapshot(antesDuplicada, null, mia.solicitud, primera, solicitudes, matriculas,
                pagos, "matrícula duplicada rechazada sin mutación");

        // #24 · operación anterior a la creación de la matrícula
        LocalDateTime creacionPrimera = primera.getFechaHoraCreacion();
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Cobros.confirmarMatricula(primera, cuotas.getCuotaMatricula(),
                        MedioPago.YAPE, "YP-ANTES", creacionPrimera.minusMinutes(1),
                        "voucher-antes", creacionPrimera.plusHours(1), solicitudes,
                        matriculas, pagos, cuotas),
                "pago con operación anterior a la matrícula rechazado",
                "anterior a la creación de la matrícula MAT-100001");
        comprobar(primera.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                && !pagos.matriculaConfirmada(primera),
                "el pago anterior a la matrícula no la activa ni se registra");

        // #24 · pago válido dentro de 72 h
        LocalDateTime registroPago = primera.getFechaHoraCreacion().plusHours(18);
        Pago pagoPrimera = Cobros.confirmarMatricula(primera, cuotas.getCuotaMatricula(),
                MedioPago.YAPE, "YP-8291457", registroPago.minusHours(1), "voucher-mia",
                registroPago, solicitudes, matriculas, pagos, cuotas);
        comprobar(pagoPrimera.estaConfirmado() && primera.getEstado() == EstadoMatricula.ACTIVA
                && pagos.matriculaConfirmada(primera)
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesIniciales - 1,
                "pago válido en plazo activa sin descontar otra vacante");
        int pagosTrasActivar = pagos.listar().size();
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Cobros.confirmarMatricula(primera, cuotas.getCuotaMatricula(),
                        MedioPago.YAPE, "YP-REPETIDO", registroPago, "voucher-repetido",
                        registroPago.plusMinutes(5), solicitudes,
                        matriculas, pagos, cuotas),
                "segundo pago de una matrícula ACTIVA rechazado", "ya tiene el pago confirmado");
        comprobar(pagos.listar().size() == pagosTrasActivar,
                "el pago rechazado no se registra");

        // #24 · pago presentado después del vencimiento
        Turnos.confirmarOferta(leo.solicitud, true, "Personal de admisión", leo.fechaOferta,
                solicitudes, matriculas);
        Matricula segunda = matriculas.vigenteDe(leo.solicitud);
        comprobar(segunda.getNumMatricula() == 100002,
                "ni la oferta rechazada ni la duplicada consumen correlativo");
        LocalDateTime tardio = segunda.getVencimientoPagoOriginal().plusHours(1);
        int vacantesConSegunda = negocio.Vacantes.calcular(aula, matriculas);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Cobros.confirmarMatricula(segunda, cuotas.getCuotaMatricula(),
                        MedioPago.EFECTIVO, "", segunda.getVencimientoPagoOriginal()
                                .minusHours(1), "voucher-tardio", tardio,
                        solicitudes, matriculas, pagos, cuotas),
                "pago tras el vencimiento rechazado", "venció");
        comprobar(segunda.getEstado() == EstadoMatricula.CANCELADA
                && segunda.getFechaCancelacion().equals(tardio)
                && negocio.Vencimientos.MOTIVO_VENCIMIENTO.equals(segunda.getMotivoCancelacion())
                && negocio.Vencimientos.RESPONSABLE.equals(segunda.getResponsableCancelacion()),
                "el rechazo por vencimiento cancela la matrícula con auditoría");
        comprobar(leo.solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && !leo.solicitud.getFechaIngresoCola().isBefore(tardio)
                && leo.solicitud.getFechaIngresoCola()
                        .isAfter(rechazo.solicitud.getFechaIngresoCola())
                && pagos.inscripcionConfirmada(leo.solicitud)
                && pagos.listar().size() == pagosTrasActivar
                && negocio.Vacantes.calcular(aula, matriculas) == vacantesConSegunda + 1,
                "vencida: vacante liberada y solicitud al final de la cola favorable");

        // #24 · nueva oferta tras la cancelación: nuevo número y nuevo plazo
        LocalDateTime reoferta = tardio.plusMinutes(1);
        Turnos.confirmarOferta(leo.solicitud, true, "Personal de admisión", reoferta,
                solicitudes, matriculas);
        Matricula tercera = matriculas.vigenteDe(leo.solicitud);
        comprobar(tercera != segunda && tercera.getNumMatricula() == 100003
                && tercera.getVencimientoPagoOriginal().equals(reoferta.plusHours(72))
                && matriculas.ultimaDe(leo.solicitud) == tercera,
                "la solicitud devuelta recibe nueva matrícula con nuevo plazo");

        // #24 · barrido desde Vencimientos.revisar; ordenPorPrioridad solo consulta
        Turnos.confirmarOferta(ana.solicitud, true, "Personal de admisión", reoferta,
                solicitudes, matriculas);
        Matricula cuarta = matriculas.vigenteDe(ana.solicitud);
        LocalDateTime barrido = cuarta.getVencimientoPagoOriginal();
        comprobar(!Turnos.ordenPorPrioridad(aula, solicitudes, matriculas)
                        .contains(ana.solicitud)
                && cuarta.getEstado() == EstadoMatricula.PENDIENTE_PAGO,
                "ordenPorPrioridad no cancela la vencida: consultar no modifica");
        negocio.Vencimientos.revisar(barrido, solicitudes, matriculas);
        java.util.ArrayList<Solicitud> orden = Turnos.ordenPorPrioridad(aula, solicitudes,
                matriculas);
        comprobar(cuarta.getEstado() == EstadoMatricula.CANCELADA
                && negocio.Vencimientos.RESPONSABLE.equals(cuarta.getResponsableCancelacion())
                && negocio.Vencimientos.MOTIVO_VENCIMIENTO.equals(cuarta.getMotivoCancelacion())
                && orden.contains(ana.solicitud),
                "Vencimientos.revisar cancela la vencida y la devuelve a los turnos");
        comprobar(primera.getEstado() == EstadoMatricula.ACTIVA,
                "el barrido no toca una matrícula ACTIVA aunque pasen las 72 h");

        // §6.5 · anulación con plazo vencido: misma auditoría de cancelación
        LocalDateTime anulacion = primera.getVencimientoPagoOriginal().plusHours(1);
        Cobros.anularPago(pagoPrimera, "error de registro", "Personal de caja", anulacion,
                solicitudes, matriculas, pagos);
        comprobar(primera.getEstado() == EstadoMatricula.CANCELADA
                && primera.getFechaCancelacion().equals(anulacion)
                && Cobros.MOTIVO_ANULACION_VENCIDA.equals(primera.getMotivoCancelacion())
                && "Personal de caja".equals(primera.getResponsableCancelacion())
                && mia.solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE,
                "anular con plazo vencido cancela con auditoría y reingresa a la cola");

        // #23 · re-ofertar sin barrido previo: confirmarOferta barre antes de comprobar
        LocalDateTime antesDeOfertar = anulacion.plusMinutes(1);
        negocio.Vencimientos.revisar(antesDeOfertar, solicitudes, matriculas);
        Solicitud enTurno = Turnos.siguiente(aula, solicitudes, matriculas);
        Turnos.confirmarOferta(enTurno, true, "Personal de admisión", antesDeOfertar,
                solicitudes, matriculas);
        Matricula sinBarrer = matriculas.vigenteDe(enTurno);
        LocalDateTime trasVencer = sinBarrer.getVencimientoPagoOriginal().plusMinutes(1)
                .plusSeconds(42);
        Turnos.confirmarOferta(enTurno, true, "Personal de admisión", trasVencer,
                solicitudes, matriculas);
        Matricula conSegundos = matriculas.vigenteDe(enTurno);
        comprobar(sinBarrer.getEstado() == EstadoMatricula.CANCELADA
                && conSegundos != sinBarrer
                && conSegundos.getFechaHoraCreacion().equals(trasVencer),
                "la oferta barre primero: una aceptada con matrícula vencida no la bloquea");

        LocalDateTime mismoMinuto = trasVencer.withSecond(0);
        Cobros.confirmarMatricula(conSegundos, cuotas.getCuotaMatricula(), MedioPago.YAPE,
                "YP-MISMO-MINUTO", mismoMinuto, "voucher-mismo-minuto",
                trasVencer.plusSeconds(10), solicitudes, matriculas, pagos, cuotas);
        comprobar(conSegundos.getEstado() == EstadoMatricula.ACTIVA,
                "una operación del mismo minuto que la creación de la matrícula se acepta");
    }

    private static void probarIntegracionUiMatricula() throws Exception {
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDateTime ahora = Reloj.porDefecto().now().withSecond(0).withNano(0);
                ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                ArregloPagos pagos = new ArregloPagos();
                ArregloMatriculas matriculas = new ArregloMatriculas();
                Aula vigentes = new Aula("NAMED-UI-MAT", "UI matrícula", 4, 2);
                CicloFixture vigente = prepararCicloOferta(solicitudes, pagos, matriculas,
                        vigentes, "UI-MAT-VIGENTE", ahora.minusHours(5));
                Turnos.confirmarOferta(vigente.solicitud, true, "Personal de admisión",
                        vigente.fechaOferta, solicitudes, matriculas);
                Matricula matricula = matriculas.vigenteDe(vigente.solicitud);

                ui.PanelPagos panel = new ui.PanelPagos(solicitudes, pagos, matriculas, cuotas, null);
                ui.TarjetasPago tarjetas =
                        (ui.TarjetasPago) campoPrivado(panel, "tarjetasMatricula");
                comprobar(campoPrivado(panel, "actual") == null
                        && ((javax.swing.JTextArea) campoPrivado(campoPrivado(panel, "tarjetas"),
                                "detalleAviso")).getText()
                                        .startsWith("Elige una solicitud"),
                        "Pagos arranca sin solicitud elegida");

                elegirEnPagos(panel, vigente.solicitud);
                comprobar(campoPrivado(panel, "matriculaActual") == matricula
                        && ((javax.swing.JLabel) campoPrivado(panel, "tituloFranja")).getText()
                                .equals("MATRÍCULA: PENDIENTE_PAGO")
                        && ((javax.swing.JLabel) campoPrivado(panel, "reservaFranja")).getText()
                                .equals("Reserva 1 vacante ahora")
                        && ((javax.swing.JLabel) campoPrivado(panel, "tituloVencimiento"))
                                .getText().equals("SI VENCE SIN PAGO REGISTRADO")
                        && ((javax.swing.JButton) campoPrivado(tarjetas, "botonConfirmar"))
                                .isVisible(),
                        "elegir la solicitud abre la matrícula PENDIENTE_PAGO lista para cobrar");
                comprobar(buscarBoton(panel, "observación") == null,
                        "la pantalla no ofrece «Registrar observación»");

                java.lang.reflect.Method confirmar = panel.getClass().getDeclaredMethod(
                        "confirmarMatricula", double.class, MedioPago.class, String.class,
                        LocalDateTime.class, String.class);
                confirmar.setAccessible(true);
                confirmar.invoke(panel, cuotas.getCuotaMatricula(), MedioPago.YAPE,
                        "UI-MAT-1", ahora.minusMinutes(1), "voucher-ui-matricula");
                comprobar(matricula.getEstado() == EstadoMatricula.ACTIVA
                        && ((javax.swing.JLabel) campoPrivado(panel, "tituloFranja")).getText()
                                .equals("MATRÍCULA: ACTIVA")
                        && !((javax.swing.JButton) campoPrivado(tarjetas, "botonConfirmar"))
                                .isVisible()
                        && ((javax.swing.JPanel) campoPrivado(tarjetas, "panelAnulacion"))
                                .isVisible()
                        && !((javax.swing.JPanel) campoPrivado(panel, "vencimiento"))
                                .isVisible()
                        && !((javax.swing.JPanel) campoPrivado(tarjetas, "cierre"))
                                .isVisible(),
                        "confirmar activa, refresca y bloquea la edición del pago");
                comprobar(!((javax.swing.JPanel) campoPrivado(
                        campoPrivado(panel, "tarjetas"), "cierre")).isVisible(),
                        "la tarjeta de inscripción no deja una fila de cierre vacía");

                Aula vencidas = new Aula("NAMED-UI-MAT-V", "UI vencida", 4, 1);
                CicloFixture vencida = prepararCicloOferta(solicitudes, pagos, matriculas,
                        vencidas, "UI-MAT-VENCIDA", ahora.minusHours(80));
                Turnos.confirmarOferta(vencida.solicitud, true, "Personal de admisión",
                        vencida.fechaOferta, solicitudes, matriculas);
                Matricula matriculaVencida = matriculas.vigenteDe(vencida.solicitud);
                elegirEnPagos(panel, vencida.solicitud);
                comprobar(campoPrivado(panel, "matriculaActual") == matriculaVencida
                        && matriculaVencida.getEstado() == EstadoMatricula.CANCELADA
                        && negocio.Vencimientos.RESPONSABLE.equals(matriculaVencida.getResponsableCancelacion())
                        && ((javax.swing.JLabel) campoPrivado(panel, "tituloVencimiento"))
                                .getText().equals("MATRÍCULA CANCELADA")
                        && !((javax.swing.JButton) campoPrivado(tarjetas, "botonConfirmar"))
                                .isVisible()
                        && ((javax.swing.JPanel) campoPrivado(tarjetas, "cierre")).isVisible(),
                        "elegir la solicitud barre la matrícula vencida y muestra la tarjeta de"
                                + " cancelación");

                Turnos.confirmarOferta(vencida.solicitud, true, "Personal de admisión", ahora,
                        solicitudes, matriculas);
                Matricula nueva = matriculas.vigenteDe(vencida.solicitud);
                elegirEnPagos(panel, vencida.solicitud);
                comprobar(campoPrivado(panel, "matriculaActual") == nueva
                        && ((javax.swing.JButton) campoPrivado(tarjetas, "botonConfirmar"))
                                .isVisible(),
                        "con una matrícula nueva, elegir la solicitud abre la vigente");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static javax.swing.AbstractButton buscarBoton(java.awt.Container raiz,
            String fragmento) {
        for (java.awt.Component hijo : raiz.getComponents()) {
            if (hijo instanceof javax.swing.AbstractButton
                    && ((javax.swing.AbstractButton) hijo).getText().contains(fragmento)) {
                return (javax.swing.AbstractButton) hijo;
            }
            if (hijo instanceof java.awt.Container) {
                javax.swing.AbstractButton encontrado =
                        buscarBoton((java.awt.Container) hijo, fragmento);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private static void probarColasConEmpates() throws Exception {
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        Aula aula = new Aula("NAMED-Q", "Colas", 4, 4);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();

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
        Matricula matricula = matriculas.registrar(objetivoFavorable, baseFavorable.minusHours(72));
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
        ArregloMatriculas matriculas = new ArregloMatriculas();
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
        Matricula ausente = new Matricula(0, otra, base.minusHours(1));
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
        Matricula otraMatricula = new Matricula(0, incompatible, base);
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
        ArregloMatriculas matriculas = new ArregloMatriculas();
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
        Matricula matricula = new Matricula(0, solicitud, creacion);
        Pago valido = new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", vencimiento.minusMinutes(2),
                vencimiento.minusMinutes(1), "named-deadline-ok", cuotas);
        comprobar(valido.getEstado() == modelo.EstadoPago.RECIBIDO,
                "pago de matrícula antes del deadline acepta ambas fechas");
        Snapshot snapshot = snapshot(valido, solicitud, matricula, new ArregloSolicitudes(),
                new ArregloMatriculas(), new ArregloPagos());
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
                new ArregloMatriculas(), new ArregloPagos(),
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
        ArregloMatriculas reemplazoMatriculas =
                new ArregloMatriculas();
        Matricula reemplazoMatricula = reemplazoMatriculas.registrar(reemplazoSolicitud,
                reemplazoCreacion);
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
        ArregloMatriculas matriculas = new ArregloMatriculas();
        LocalDateTime creacion = base.minusHours(72);
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "RP-" + caso,
                creacion.minusDays(2));
        solicitud.habilitarParaPago(creacion.minusHours(2));
        solicitud.confirmarInscripcion(creacion.minusHours(1));
        Matricula matricula = matriculas.registrar(solicitud, creacion);
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
        ArregloMatriculas matriculas = new ArregloMatriculas();
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-G-S", base.minusDays(2));
        solicitud.habilitarParaPago(base.minusHours(2));
        Pago inscripcion = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", base.minusHours(1), base,
                "named-guard", cuotas);
        inscripcion.confirmar();
        pagos.agregar(inscripcion);
        solicitud.confirmarInscripcion(base.minusHours(1));
        Matricula vigente = matriculas.registrar(solicitud, base.minusHours(1));
        Solicitud otraSolicitud = registrarSolicitud(solicitudes, aula, "NAMED-G-OTHER",
                base.minusDays(3));
        otraSolicitud.habilitarParaPago(base.minusHours(3));
        otraSolicitud.confirmarInscripcion(base.minusHours(2));
        Matricula otraVigente = matriculas.registrar(otraSolicitud, base.minusHours(1));
        otraVigente.activar();
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
        vigente.cancelar(base, "cancelación de prueba", "Personal");
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
                new ArregloMatriculas(), pagosSinBoleta);
        esperarFallo(ReglaDominioException.class, () -> anulado.registrarComprobante("12345"),
                "pago anulado sin boleta rechaza comprobante");
        assertSnapshot(antes, anulado, solicitud, null, new ArregloSolicitudes(),
                new ArregloMatriculas(), pagosSinBoleta,
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
                new ArregloSolicitudes(), new ArregloMatriculas(), pagosRegistrados);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> registrado.registrarComprobante("123456"),
                "pago anulado con boleta conserva el guardia de estado",
                "No se puede registrar un comprobante en un pago anulado");
        assertSnapshot(registradoDespues, registrado, registradoSolicitud, null,
                new ArregloSolicitudes(), new ArregloMatriculas(), pagosRegistrados,
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
        Reloj.porDefecto().fijar(LocalDateTime.of(2026, 10, 8, 10, 0));
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 10, 0);
                ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
                Aula aula = new Aula("NAMED-UI", "UI", 4, 1);
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                ArregloPagos pagos = new ArregloPagos();
                ArregloMatriculas matriculas = new ArregloMatriculas();
                Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-UI-S",
                        ahora.minusDays(2));
                solicitud.habilitarParaPago(ahora.minusHours(1));
                Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                        cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", ahora.minusMinutes(10),
                        ahora.minusMinutes(5), "named-ui", cuotas);
                pago.confirmar();
                pagos.agregar(pago);
                solicitud.confirmarInscripcion(ahora.minusMinutes(10));
                ui.PanelPagos panel = new ui.PanelPagos(solicitudes, pagos, matriculas, cuotas, null);
                elegirEnPagos(panel, solicitud);
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

                LocalDateTime expiradaAhora = Reloj.porDefecto().now();
                Aula aulaExpirada = new Aula("NAMED-UI-EXP", "UI expirada", 4, 1);
                ArregloSolicitudes solicitudesExpiradas = new ArregloSolicitudes();
                ArregloPagos pagosExpirados = new ArregloPagos();
                ArregloMatriculas matriculasExpiradas =
                        new ArregloMatriculas();
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
                        pagosExpirados, matriculasExpiradas, cuotas, null);
                elegirEnPagos(panelExpirado, expirada);
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

                LocalDateTime falloAhora = Reloj.porDefecto().now();
                Aula aulaFallo = new Aula("NAMED-UI-FAIL", "UI fallo", 4, 1);
                ArregloSolicitudes solicitudesFallo = new ArregloSolicitudes();
                ArregloPagos pagosFallo = new ArregloPagos();
                ArregloMatriculas matriculasFallo =
                        new ArregloMatriculas();
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
                Matricula matriculaFallo = matriculasFallo.registrar(solicitudFallo,
                        falloAhora.minusMinutes(5));
                matriculaFallo.activar();
                Snapshot falloAntes = snapshot(pagoFallo, solicitudFallo, matriculaFallo,
                        solicitudesFallo, matriculasFallo, pagosFallo);
                ui.PanelPagos panelFallo = new ui.PanelPagos(solicitudesFallo, pagosFallo,
                        matriculasFallo, cuotas, null);
                elegirEnPagos(panelFallo, solicitudFallo);
                // Con matrícula vigente, elegir la solicitud abre la vista de matrícula: la
                // anulación de la inscripción ya no se ofrece ahí (la guarda de dominio se
                // prueba en probarProteccionDeInscripcionConMatriculaVigente).
                comprobar(campoPrivado(panelFallo, "matriculaActual") == matriculaFallo
                        && pagoFallo.estaConfirmado()
                        && solicitudFallo.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                        "UI con matrícula vigente abre la vista de matrícula");
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

    private static void probarIntegracionUiBarridoDocumental() throws Exception {
        Reloj.porDefecto().fijar(LocalDateTime.of(2026, 10, 8, 10, 0));
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 10, 0);
                Aula aula = new Aula("NAMED-UI-DOC", "UI documental", 4, 1);
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                ArregloPagos pagos = new ArregloPagos();
                ArregloMatriculas matriculas = new ArregloMatriculas();
                Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-UI-DOC-S",
                        ahora.minusDays(10));
                LocalDateTime confirmacion = ahora.minusDays(9);
                solicitud.habilitarParaPago(confirmacion.minusHours(1));
                solicitud.confirmarInscripcion(confirmacion);

                ui.PanelPagos panel = new ui.PanelPagos(solicitudes, pagos, matriculas,
                        new ConfiguracionCuotas(), null);
                elegirEnPagos(panel, solicitud);

                comprobar(solicitud.getEstado() == EstadoSolicitud.CANCELADA
                        && solicitud.getFechaCancelacion() != null
                        && solicitud.getMotivoCancelacion() != null
                        && !solicitud.getMotivoCancelacion().isEmpty()
                        && negocio.Vencimientos.RESPONSABLE_DOCUMENTACION.equals(
                                solicitud.getResponsableCancelacion()),
                        "UI buscar cancela documentación vencida con auditoría completa");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static CicloFixture prepararCicloOferta(ArregloSolicitudes solicitudes,
            ArregloPagos pagos, ArregloMatriculas matriculas, Aula aula,
            String codigo, LocalDateTime base) throws Exception {
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        LocalDateTime registroSolicitud = base.minusDays(3);
        LocalDateTime habilitacion = base.minusHours(6);
        LocalDateTime operacionInscripcion = base.minusHours(2);
        LocalDateTime registroInscripcion = base.minusHours(1);
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, codigo, registroSolicitud);
        solicitud.habilitarParaPago(habilitacion);
        Pago pagoInscripcion = Cobros.confirmarInscripcion(solicitud,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", operacionInscripcion,
                codigo + "-inscripcion", registroInscripcion, solicitudes, matriculas, pagos,
                cuotas);
        comprobar(pagoInscripcion.estaConfirmado()
                && solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                codigo + " confirma inscripción antes de documentos");
        LocalDateTime entregaDocumentos = base.minusMinutes(50);
        LocalDateTime validacionDocumentos = base.minusMinutes(40);
        registrarYValidarDocumentos(solicitud, entregaDocumentos, validacionDocumentos);
        comprobarDocumentosValidados(solicitud, codigo + " documentos");

        solicitud.ingresarAColaFavorable(base);
        return new CicloFixture(solicitud, pagoInscripcion, registroSolicitud, habilitacion,
                operacionInscripcion, registroInscripcion, entregaDocumentos,
                validacionDocumentos, base.plusMinutes(30));
    }

    private static CicloFixture prepararCicloOfertaSinTurno(ArregloSolicitudes solicitudes,
            ArregloPagos pagos, ArregloMatriculas matriculas, Aula aula,
            String codigo, LocalDateTime base) throws Exception {
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        LocalDateTime registroSolicitud = base.minusDays(3);
        LocalDateTime habilitacion = base.minusHours(6);
        LocalDateTime operacionInscripcion = base.minusHours(2);
        LocalDateTime registroInscripcion = base.minusHours(1);
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, codigo, registroSolicitud);
        solicitud.habilitarParaPago(habilitacion);
        Pago pagoInscripcion = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", operacionInscripcion,
                registroInscripcion, codigo + "-inscripcion", cuotas);
        pagoInscripcion.confirmar();
        pagos.agregar(pagoInscripcion);
        solicitud.confirmarInscripcion(registroInscripcion);
        LocalDateTime entregaDocumentos = base.minusMinutes(50);
        LocalDateTime validacionDocumentos = base.minusMinutes(40);
        registrarYValidarDocumentos(solicitud, entregaDocumentos, validacionDocumentos);
        comprobarDocumentosValidados(solicitud, codigo + " documentos");
        solicitud.ingresarAColaFavorable(base);
        return new CicloFixture(solicitud, pagoInscripcion, registroSolicitud, habilitacion,
                operacionInscripcion, registroInscripcion, entregaDocumentos,
                validacionDocumentos, base.plusMinutes(30));
    }

    private static void registrarYValidarDocumentos(Solicitud solicitud,
            LocalDateTime entrega, LocalDateTime validacion) throws Exception {
        for (modelo.Documento documento : solicitud.getExpediente().getDocumentos()) {
            documento.registrarEntrega(entrega);
            documento.validar();
        }
        comprobar(solicitud.getExpediente().puedeAgendarEntrevista(),
                solicitud.getCodigo() + " permite continuar solo tras validar los cuatro documentos");
        comprobar(solicitud.getExpediente().getDocumentos().size() == 4
                && solicitud.getExpediente().getDocumentos().stream()
                        .allMatch(modelo.Documento::estaValidado)
                && validacion.isAfter(entrega),
                solicitud.getCodigo() + " valida los cuatro documentos con fecha posterior a entrega");
    }

    private static void comprobarDocumentosValidados(Solicitud solicitud, String descripcion) {
        comprobar(solicitud.getExpediente() != null
                && solicitud.getExpediente().getDocumentos().size() == 4
                && solicitud.getExpediente().getDocumentos().stream()
                        .allMatch(modelo.Documento::estaValidado),
                descripcion + ": DNI alumno, DNI apoderado, partida y carné validados");
    }

    private static void comprobarCronologia(CicloFixture fixture, LocalDateTime oferta,
            String descripcion) {
        comprobar(fixture.solicitud.getFechaRegistro().equals(fixture.registroSolicitud)
                && fixture.registroSolicitud.isBefore(fixture.habilitacion)
                && fixture.habilitacion.isBefore(fixture.solicitud.getFechaConfirmacionInscripcion())
                && fixture.solicitud.getFechaConfirmacionInscripcion().isBefore(
                        fixture.entregaDocumentos)
                && fixture.entregaDocumentos.isBefore(fixture.validacionDocumentos)
                && fixture.validacionDocumentos.isBefore(oferta),
                descripcion + " respeta registro -> habilitación -> inscripción -> documentos -> oferta");
    }

    private static final class CicloFixture {
        private final Solicitud solicitud;
        private final Pago pagoInscripcion;
        private final LocalDateTime registroSolicitud;
        private final LocalDateTime habilitacion;
        private final LocalDateTime operacionInscripcion;
        private final LocalDateTime registroInscripcion;
        private final LocalDateTime entregaDocumentos;
        private final LocalDateTime validacionDocumentos;
        private final LocalDateTime fechaOferta;

        private CicloFixture(Solicitud solicitud, Pago pagoInscripcion,
                LocalDateTime registroSolicitud, LocalDateTime habilitacion,
                LocalDateTime operacionInscripcion, LocalDateTime registroInscripcion,
                LocalDateTime entregaDocumentos, LocalDateTime validacionDocumentos,
                LocalDateTime fechaOferta) {
            this.solicitud = solicitud;
            this.pagoInscripcion = pagoInscripcion;
            this.registroSolicitud = registroSolicitud;
            this.habilitacion = habilitacion;
            this.operacionInscripcion = operacionInscripcion;
            this.registroInscripcion = registroInscripcion;
            this.entregaDocumentos = entregaDocumentos;
            this.validacionDocumentos = validacionDocumentos;
            this.fechaOferta = fechaOferta;
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
        Matricula matricula = new Matricula(0, solicitud, creacion);
        comprobar(!operacion.isBefore(solicitud.getFechaRegistro())
                && !operacion.isAfter(registro),
                "fixture de deadline conserva orden solicitud-operación-registro");
        return new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "", operacion, registro,
                comprobante, cuotas);
    }

    private static Pago pagoMatriculaConFechas(Solicitud solicitud, Matricula matricula,
            LocalDateTime operacion, LocalDateTime registro, String comprobante,
            ConfiguracionCuotas cuotas) throws ReglaDominioException {
        comprobar(matricula.getFechaHoraCreacion().isBefore(operacion)
                && operacion.isBefore(registro)
                && !operacion.isBefore(solicitud.getFechaRegistro())
                && registro.isBefore(matricula.getVencimientoPagoOriginal()),
                "pago de matrícula respeta creación -> operación -> registro -> deadline");
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
        private final LocalDateTime fechaCancelacion;
        private final String motivoCancelacion;
        private final String responsableCancelacion;
        private final EstadoMatricula estadoMatricula;
        private final LocalDateTime creacionMatricula;
        private final LocalDateTime vencimientoMatricula;
        private final java.util.ArrayList<Oferta> ofertas;
        private final boolean tieneOfertaAceptada;
        private final java.util.ArrayList<Solicitud> solicitudes;
        private final java.util.ArrayList<Matricula> matriculas;
        private final java.util.ArrayList<Pago> pagos;

        private Snapshot(Pago pago, Solicitud solicitud, Matricula matricula,
                ArregloSolicitudes arregloSolicitudes,
                ArregloMatriculas arregloMatriculas, ArregloPagos arregloPagos) {
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
            fechaCancelacion = solicitud == null ? null : solicitud.getFechaCancelacion();
            motivoCancelacion = solicitud == null ? null : solicitud.getMotivoCancelacion();
            responsableCancelacion = solicitud == null ? null : solicitud.getResponsableCancelacion();
            estadoMatricula = matricula == null ? null : matricula.getEstado();
            creacionMatricula = matricula == null ? null : matricula.getFechaHoraCreacion();
            vencimientoMatricula = matricula == null ? null : matricula.getVencimientoPagoOriginal();
            ofertas = solicitud == null ? new java.util.ArrayList<Oferta>() : solicitud.getOfertas();
            tieneOfertaAceptada = solicitud != null && solicitud.tieneOfertaAceptada();
            solicitudes = arregloSolicitudes.listar();
            matriculas = arregloMatriculas.listar();
            pagos = arregloPagos.listar();
        }
    }

    private static Snapshot snapshot(Pago pago, Solicitud solicitud, Matricula matricula,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas,
            ArregloPagos pagos) {
        return new Snapshot(pago, solicitud, matricula, solicitudes, matriculas, pagos);
    }

    private static void assertSnapshot(Snapshot antes, Pago pago, Solicitud solicitud,
            Matricula matricula, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas, ArregloPagos pagos, String descripcion) {
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
                && java.util.Objects.equals(antes.fechaCancelacion,
                        solicitud == null ? null : solicitud.getFechaCancelacion())
                && java.util.Objects.equals(antes.motivoCancelacion,
                        solicitud == null ? null : solicitud.getMotivoCancelacion())
                && java.util.Objects.equals(antes.responsableCancelacion,
                        solicitud == null ? null : solicitud.getResponsableCancelacion())
                && antes.estadoMatricula == (matricula == null ? null : matricula.getEstado())
                && java.util.Objects.equals(antes.creacionMatricula,
                        matricula == null ? null : matricula.getFechaHoraCreacion())
                && java.util.Objects.equals(antes.vencimientoMatricula,
                        matricula == null ? null : matricula.getVencimientoPagoOriginal())
                && ofertasSemanticaIgual(antes.ofertas,
                        solicitud == null ? new java.util.ArrayList<Oferta>() : solicitud.getOfertas())
                && antes.tieneOfertaAceptada == (solicitud != null && solicitud.tieneOfertaAceptada())
                && antes.solicitudes.equals(solicitudes.listar())
                && antes.matriculas.equals(matriculas.listar())
                && antes.pagos.equals(pagos.listar()), descripcion);
    }

    private static boolean ofertasSemanticaIgual(java.util.ArrayList<Oferta> antes,
            java.util.ArrayList<Oferta> despues) {
        if (antes.size() != despues.size()) {
            return false;
        }
        for (int i = 0; i < antes.size(); i++) {
            Oferta ofertaAntes = antes.get(i);
            Oferta ofertaDespues = despues.get(i);
            if (!ofertaAntes.getFechaHora().equals(ofertaDespues.getFechaHora())
                    || !ofertaAntes.getPersonal().equals(ofertaDespues.getPersonal())
                    || ofertaAntes.isAceptada() != ofertaDespues.isAceptada()) {
                return false;
            }
        }
        return true;
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

    private static void probarEntrevistaYExpediente() throws Exception {
        Aula aula = new Aula("AUL-ENT", "Entrevistas", 4, 2);
        LocalDateTime base = LocalDateTime.of(2026, 9, 20, 10, 0);
        datos.ArregloEntrevistas entrevistas = new datos.ArregloEntrevistas();

        Solicitud favorable = solicitudConDocumentosValidados("ENT-FAV", base, aula);
        modelo.Entrevista entrevista = entrevistas.agendar(favorable, favorable.getExpediente(),
                base.plusDays(2), listaDe("Ana Ruiz", "Luis Ramos"), "Ana Ruiz");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> entrevistas.agendar(favorable, favorable.getExpediente(), base.plusDays(3),
                        listaDe("Ana Ruiz"), "Ana Ruiz"),
                "no se agenda dos veces la misma solicitud", "ya está agendada");
        entrevista.registrarAsistencia();
        entrevista.marcarRealizada();
        esperarFallo(DatoInvalidoException.class,
                () -> entrevista.registrarResultado(modelo.ResultadoEntrevista.FAVORABLE, null),
                "el resultado exige fecha de registro");
        comprobar(entrevista.getResultado() == null
                && favorable.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                "un resultado rechazado no deja la solicitud a medias");
        LocalDateTime registro = base.plusDays(2).plusHours(1);
        entrevista.registrarResultado(modelo.ResultadoEntrevista.FAVORABLE, registro);
        comprobar(favorable.estaEnColaFavorable()
                && favorable.getFechaIngresoCola().equals(registro),
                "FAVORABLE entra a la cola favorable con la fecha del registro");

        Solicitud rechazada = solicitudConDocumentosValidados("ENT-NOFAV", base, aula);
        modelo.Entrevista noFavorable = entrevistas.agendar(rechazada,
                rechazada.getExpediente(), base.plusDays(2), listaDe("Ana Ruiz"), "Ana Ruiz");
        noFavorable.registrarAsistencia();
        noFavorable.marcarRealizada();
        noFavorable.registrarResultado(modelo.ResultadoEntrevista.NO_FAVORABLE, registro);
        comprobar(rechazada.getEstado() == EstadoSolicitud.RECHAZADA,
                "NO_FAVORABLE deja la solicitud RECHAZADA");

        Solicitud ausente = solicitudConDocumentosValidados("ENT-AUS", base, aula);
        modelo.Entrevista inasistida = entrevistas.agendar(ausente, ausente.getExpediente(),
                base.plusDays(2), listaDe("Ana Ruiz"), "Ana Ruiz");
        inasistida.registrarInasistencia(base.plusDays(5));
        comprobar(inasistida.getInasistencias() == 1
                && inasistida.getFechaHora().equals(base.plusDays(5))
                && ausente.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                "la primera inasistencia reprograma");
        inasistida.registrarInasistencia(null);
        comprobar(inasistida.getInasistencias() == 2
                && ausente.getEstado() == EstadoSolicitud.CANCELADA,
                "la segunda inasistencia cancela");

        Solicitud bloqueada = solicitud("ENT-BLOQ", base.minusDays(2), aula);
        bloqueada.habilitarParaPago(base.minusHours(2));
        bloqueada.confirmarInscripcion(base);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> entrevistas.agendar(bloqueada, bloqueada.getExpediente(), base.plusDays(2),
                        listaDe("Ana Ruiz"), "Ana Ruiz"),
                "sin los 4 validados la entrevista está bloqueada", "Falta validar");

        modelo.Documento carne = bloqueada.getExpediente()
                .getDocumento(modelo.TipoDocumento.CARNE_VACUNAS);
        carne.registrarEntrega(base.plusHours(1));
        carne.observar("Falta la dosis de refuerzo", LocalDateTime.of(2026, 9, 21, 23, 30));
        comprobar(carne.getFechaHoraComunicacion().equals(LocalDateTime.of(2026, 9, 21, 23, 30))
                && carne.fechaLimiteCorreccion().equals(LocalDate.of(2026, 9, 28)),
                "la hora de comunicación no cambia el plazo en días calendario");
        esperarFallo(DatoInvalidoException.class,
                () -> carne.entregarCorreccion(LocalDateTime.of(2026, 9, 21, 22, 0)),
                "la corrección no puede ser anterior a la observación");
        modelo.Documento partida = bloqueada.getExpediente()
                .getDocumento(modelo.TipoDocumento.PARTIDA_NACIMIENTO);
        partida.registrarEntrega(base.plusHours(3));
        esperarFallo(DatoInvalidoException.class,
                () -> partida.observar("Ilegible", base.plusHours(2)),
                "la observación no puede comunicarse antes de la entrega");
        comprobar(partida.getEstado() == modelo.EstadoDocumento.EN_REVISION,
                "una observación rechazada no cambia el documento");

        Solicitud reprogramada = solicitudConDocumentosValidados("ENT-REPRO", base, aula);
        modelo.Entrevista cita = entrevistas.agendar(reprogramada,
                reprogramada.getExpediente(), base.plusDays(2), listaDe("Ana Ruiz"), "Ana Ruiz");
        esperarFallo(DatoInvalidoException.class,
                () -> cita.registrarInasistencia(base.plusDays(1)),
                "no se reprograma a una fecha anterior a la cita perdida");
        comprobar(cita.getInasistencias() == 0, "la reprogramación rechazada no cuenta");

        probarEntrevistaDeInscripcionAnulada(aula, base, entrevistas);
        probarIntegracionUiExpedienteVencido();
        probarIntegracionUiSolicitudesConReloj();
        probarIntegracionUiExpediente();
    }

    private static void probarEntrevistaDeInscripcionAnulada(Aula aula, LocalDateTime base,
            datos.ArregloEntrevistas entrevistas) throws Exception {
        Solicitud solicitud = solicitudConDocumentosValidados("ENT-ANUL", base, aula);
        modelo.Entrevista anterior = entrevistas.agendar(solicitud, solicitud.getExpediente(),
                base.plusDays(2), listaDe("Ana Ruiz"), "Ana Ruiz");
        anterior.registrarAsistencia();
        anterior.marcarRealizada();

        solicitud.revertirInscripcion(base.plusHours(3), base.plusHours(3));
        comprobar(solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO
                && !anterior.esDeLaInscripcionVigente(),
                "con la inscripción anulada la entrevista deja de estar vigente");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> anterior.registrarResultado(modelo.ResultadoEntrevista.FAVORABLE,
                        base.plusHours(4)),
                "sin inscripción vigente no se registra el resultado", "ya no está vigente");

        solicitud.confirmarInscripcion(base.plusHours(4));
        comprobar(entrevistas.buscarDe(solicitud) == null,
                "el expediente nuevo no hereda la entrevista anterior");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> anterior.registrarResultado(modelo.ResultadoEntrevista.FAVORABLE,
                        base.plusHours(5)),
                "la entrevista anterior no sirve para el expediente nuevo", "ya no está vigente");
        comprobar(solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                "la solicitud sigue en documentación, fuera de la cola favorable");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> entrevistas.agendar(solicitud, solicitud.getExpediente(),
                        base.plusDays(3), listaDe("Ana Ruiz"), "Ana Ruiz"),
                "la nueva entrevista espera los 4 documentos nuevos", "Falta validar");
    }

    private static void probarIntegracionUiExpediente() throws Exception {
        Reloj.porDefecto().fijar(LocalDateTime.of(2026, 10, 8, 10, 0));
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 10, 0);
                Aula aula = new Aula("NAMED-UI-EXP2", "UI expediente", 4, 1);
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                Solicitud solicitud = registrarSolicitud(solicitudes, aula, "NAMED-UI-EXP2-S",
                        ahora.minusDays(3));
                solicitud.habilitarParaPago(ahora.minusDays(2).minusHours(1));
                solicitud.confirmarInscripcion(ahora.minusDays(2));
                for (modelo.Documento documento : solicitud.getExpediente().getDocumentos()) {
                    documento.registrarEntrega(ahora.minusDays(1));
                    if (documento.getTipo() == modelo.TipoDocumento.CARNE_VACUNAS) {
                        documento.observar("Ilegible", ahora.minusHours(5));
                    } else {
                        documento.validar();
                    }
                }

                ui.PanelExpediente panel = new ui.PanelExpediente(solicitudes,
                        new datos.ArregloEntrevistas(), new ArregloMatriculas(), null);
                comprobar(textoEstadoEntrevista(panel).equals("BLOQUEADA · 1 observado"),
                        "con el carné observado la entrevista está BLOQUEADA");

                javax.swing.JTable tabla = (javax.swing.JTable) campoPrivado(panel,
                        "tablaDocumentos");
                int filaCarne = -1;
                for (int fila = 0; fila < tabla.getRowCount(); fila++) {
                    if (modelo.TipoDocumento.CARNE_VACUNAS.getDescripcion()
                            .equals(tabla.getValueAt(fila, 0))) {
                        filaCarne = fila;
                    }
                }
                tabla.setRowSelectionInterval(filaCarne, filaCarne);
                invocarPrivado(panel, "registrarEntrega");
                comprobar(tabla.getValueAt(filaCarne, 2) == modelo.EstadoDocumento.EN_REVISION,
                        "la tabla refleja la corrección en vivo");
                invocarPrivado(panel, "validarDocumento");
                comprobar(textoEstadoEntrevista(panel).equals("AGENDABLE"),
                        "validar el 4.º documento vuelve agendable la entrevista");

                ((javax.swing.JTextField) campoPrivado(panel, "campoFechaEntrevista"))
                        .setText(ahora.plusDays(1).format(ui.TarjetasPago.FORMATO_FECHA));
                ((javax.swing.JTextField) campoPrivado(panel, "campoPersonal"))
                        .setText("Ana Ruiz, Luis Ramos");
                ((javax.swing.JComboBox<?>) campoPrivado(panel, "comboResponsable"))
                        .setSelectedItem("Luis Ramos");
                invocarPrivado(panel, "agendar");
                comprobar(textoEstadoEntrevista(panel).equals("AGENDADA"),
                        "la entrevista queda agendada");
                invocarPrivado(panel, "registrarAsistencia");
                ((javax.swing.JComboBox<?>) campoPrivado(panel, "comboResultado"))
                        .setSelectedItem(modelo.ResultadoEntrevista.FAVORABLE);
                invocarPrivado(panel, "registrarResultado");
                comprobar(solicitud.estaEnColaFavorable(),
                        "FAVORABLE desde el panel pasa a la cola favorable");
                javax.swing.JPanel franja = (javax.swing.JPanel) campoPrivado(panel, "franja");
                comprobar(textosDe(franja).contains("ESTADO: EN_ESPERA_FAVORABLE"),
                        "la franja muestra el estado nuevo");

                javax.swing.JTextField campoFecha = (javax.swing.JTextField) campoPrivado(panel,
                        "campoFechaDocumento");
                String horaVieja = ahora.minusHours(7).format(ui.TarjetasPago.FORMATO_FECHA);
                campoFecha.setText(horaVieja);
                for (java.awt.event.ComponentListener oyente : panel.getComponentListeners()) {
                    oyente.componentShown(new java.awt.event.ComponentEvent(panel,
                            java.awt.event.ComponentEvent.COMPONENT_SHOWN));
                }
                comprobar(!campoFecha.getText().equals(horaVieja),
                        "al volver a la pantalla la hora propuesta es la actual");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static String textoEstadoEntrevista(ui.PanelExpediente panel) throws Exception {
        return ((javax.swing.JLabel) campoPrivado(panel, "estadoEntrevista")).getText();
    }

    private static String textosDe(java.awt.Container raiz) {
        StringBuilder textos = new StringBuilder();
        for (java.awt.Component hijo : raiz.getComponents()) {
            if (hijo instanceof javax.swing.JLabel) {
                textos.append(((javax.swing.JLabel) hijo).getText()).append(' ');
            } else if (hijo instanceof java.awt.Container) {
                textos.append(textosDe((java.awt.Container) hijo));
            }
        }
        return textos.toString();
    }

    private static void invocarPrivado(Object objeto, String metodo) throws Exception {
        java.lang.reflect.Method accion = objeto.getClass().getDeclaredMethod(metodo);
        accion.setAccessible(true);
        accion.invoke(objeto);
    }

    // Nueva solicitud con el reloj global fijado (§4.8) y plazos que vencen con la pantalla
    // abierta (§4.2): el guardado y las vacantes tienen que ver la misma hora.
    private static void probarIntegracionUiSolicitudesConReloj() throws Exception {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 10, 0);
        Reloj.porDefecto().fijar(ahora);
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                datos.ArregloAulas aulas = new datos.ArregloAulas();
                Aula girasoles = aulas.buscar("AUL-01");
                ArregloAlumnos alumnos = new ArregloAlumnos();
                ArregloApoderados apoderados = new ArregloApoderados();
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                ArregloMatriculas matriculas = new ArregloMatriculas();
                Alumno luz = alumnos.registrar("70123456", "Luz", "Prueba",
                        LocalDate.of(2022, 6, 18), null);
                luz.agregarApoderado(apoderados.registrar("80123456", "Ana", "Prueba",
                        "999111222"), true);
                Solicitud anterior = solicitudes.registrar(luz, girasoles);
                anterior.habilitarParaPago(ahora.minusHours(1));
                anterior.confirmarInscripcion(ahora);

                ui.PanelSolicitudes panel = new ui.PanelSolicitudes(aulas, solicitudes, alumnos,
                        apoderados, matriculas, null);
                ((javax.swing.JTextField) campoPrivado(panel, "campoDni")).setText("70123456");
                ((javax.swing.JComboBox<?>) campoPrivado(panel, "comboAula"))
                        .setSelectedItem(girasoles);
                javax.swing.JButton guardar = (javax.swing.JButton) campoPrivado(panel,
                        "botonGuardar");
                comprobar(!guardar.isEnabled(),
                        "la solicitud en documentación bloquea un duplicado");

                LocalDateTime despues = ahora.plusDays(8);
                Reloj.porDefecto().fijar(despues);
                invocarPrivado(panel, "guardar");
                Solicitud nueva = solicitudes.buscarActivaDe(luz);
                comprobar(anterior.getEstado() == EstadoSolicitud.CANCELADA,
                        "guardar cancela antes la documentación vencida");
                comprobar(nueva != null && nueva != anterior
                        && nueva.getFechaRegistro().equals(despues)
                        && nueva.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO
                        && nueva.getVencimientoHabilitacion().equals(despues.plusHours(48)),
                        "guardar registra y habilita con la hora del reloj global");

                Aula chica = new Aula("AUL-UI-SOL", "UI solicitudes", 4, 1);
                @SuppressWarnings("unchecked")
                java.util.ArrayList<Aula> almacen =
                        (java.util.ArrayList<Aula>) campoPrivado(aulas, "aulas");
                almacen.add(chica);
                ArregloSolicitudes conMatricula = new ArregloSolicitudes();
                ArregloMatriculas pendientes = new ArregloMatriculas();
                Solicitud ocupa = registrarSolicitud(conMatricula, chica, "NAMED-UI-SOL",
                        despues.minusDays(5));
                ocupa.habilitarParaPago(despues.minusDays(4).minusHours(1));
                ocupa.confirmarInscripcion(despues.minusDays(4));
                registrarYValidarDocumentos(ocupa, despues.minusDays(3), despues.minusDays(2));
                ocupa.ingresarAColaFavorable(despues.minusDays(2));
                Turnos.confirmarOferta(ocupa, true, "Personal de admisión",
                        despues.minusHours(1), conMatricula, pendientes);

                ui.PanelSolicitudes conCupo = new ui.PanelSolicitudes(aulas, conMatricula,
                        new ArregloAlumnos(), new ArregloApoderados(), pendientes, null);
                ((javax.swing.JComboBox<?>) campoPrivado(conCupo, "comboAula"))
                        .setSelectedItem(chica);
                javax.swing.JLabel vacantes = (javax.swing.JLabel) campoPrivado(conCupo,
                        "textoVacantes");
                comprobar(vacantes.getText().startsWith("Sin vacantes"),
                        "la matrícula pendiente ocupa la única vacante");
                Reloj.porDefecto().fijar(despues.plusHours(72));
                // El barrido muta estado solo en los bordes no-tecla (aquí, antes de guardar);
                // la tecla (recalcular) no barre. Guardar barre, graba y recalcula la vacante.
                invocarPrivado(conCupo, "guardar");
                comprobar(vacantes.getText().startsWith("1 disponible"),
                        "la matrícula vencida con la pantalla abierta libera la vacante");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static void probarIntegracionUiExpedienteVencido() throws Exception {
        Reloj.porDefecto().fijar(LocalDateTime.of(2026, 10, 8, 10, 0));
        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 10, 0);
                Aula aula = new Aula("NAMED-UI-EXP", "UI expediente", 4, 1);
                ArregloSolicitudes solicitudes = new ArregloSolicitudes();
                Solicitud vencida = registrarSolicitud(solicitudes, aula, "NAMED-UI-EXP-S",
                        ahora.minusDays(10));
                vencida.habilitarParaPago(ahora.minusDays(9).minusHours(1));
                vencida.confirmarInscripcion(ahora.minusDays(9));

                new ui.PanelExpediente(solicitudes, new datos.ArregloEntrevistas(),
                        new ArregloMatriculas(), null);

                comprobar(vencida.getEstado() == EstadoSolicitud.CANCELADA
                        && "Sistema".equals(vencida.getResponsableCancelacion()),
                        "abrir el expediente cancela el plazo vencido con responsable Sistema");

                ArregloSolicitudes conCita = new ArregloSolicitudes();
                Solicitud tardia = registrarSolicitud(conCita, aula, "NAMED-UI-EXP-T",
                        ahora.minusDays(10));
                tardia.habilitarParaPago(ahora.minusDays(9).minusHours(1));
                tardia.confirmarInscripcion(ahora.minusDays(9));
                registrarYValidarDocumentos(tardia, ahora.minusDays(1), ahora.minusHours(20));
                datos.ArregloEntrevistas entrevistas = new datos.ArregloEntrevistas();
                entrevistas.agendar(tardia, tardia.getExpediente(), ahora.plusDays(1),
                        listaDe("Ana Ruiz"), "Ana Ruiz");

                ui.PanelExpediente panel = new ui.PanelExpediente(conCita, entrevistas,
                        new ArregloMatriculas(), null);

                String motivoTarjeta = ((javax.swing.JLabel) campoPrivado(panel,
                        "motivoEntrevista")).getText();
                comprobar(tardia.getEstado() == EstadoSolicitud.CANCELADA
                        && textoEstadoEntrevista(panel).equals("CANCELADA")
                        && motivoTarjeta.equals(tardia.getMotivoCancelacion())
                        && !motivoTarjeta.contains("inasistencia"),
                        "la tarjeta de entrevista muestra el motivo real de la cancelación");
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static Solicitud solicitudConDocumentosValidados(String codigo, LocalDateTime base,
            Aula aula) throws Exception {
        Solicitud solicitud = solicitud(codigo, base.minusDays(2), aula);
        solicitud.habilitarParaPago(base.minusHours(2));
        solicitud.confirmarInscripcion(base);
        registrarYValidarDocumentos(solicitud, base.plusHours(1), base.plusHours(2));
        return solicitud;
    }

    private static java.util.ArrayList<String> listaDe(String... nombres) {
        return new java.util.ArrayList<String>(java.util.Arrays.asList(nombres));
    }

    private static void probarPlazosDocumentalesYCancelacion() throws Exception {
        Aula aula = new Aula("AUL-DOC", "Documentación", 4, 2);
        LocalDateTime confirmacion = LocalDateTime.of(2026, 9, 20, 10, 0);

        Solicitud inicial = solicitud("DOC-INICIAL", confirmacion.minusDays(2), aula);
        inicial.habilitarParaPago(confirmacion.minusHours(2));
        ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
        ArregloSolicitudes solicitudesInicial = new ArregloSolicitudes();
        ArregloMatriculas matriculasInicial = new ArregloMatriculas();
        ArregloPagos pagosCancelacion = new ArregloPagos();
        Pago pagoCancelado = Cobros.confirmarInscripcion(inicial, cuotas.getCuotaInscripcion(),
                MedioPago.EFECTIVO, "", confirmacion, "comprobante-documental", confirmacion,
                solicitudesInicial, matriculasInicial, pagosCancelacion, cuotas);
        Matricula matriculaCancelada = new Matricula(0, inicial, confirmacion.plusHours(1));
        comprobar(inicial.getExpediente().debeCancelarse(LocalDate.of(2026, 9, 28)),
                "el plazo inicial vencido permite cancelar");
        inicial.cancelarPorDocumentacion(LocalDateTime.of(2026, 9, 28, 9, 0),
                "Venció el plazo inicial", "Personal documental");
        comprobar(inicial.getEstado() == EstadoSolicitud.CANCELADA
                && inicial.getFechaCancelacion().equals(LocalDateTime.of(2026, 9, 28, 9, 0))
                && inicial.getMotivoCancelacion().equals("Venció el plazo inicial")
                && inicial.getResponsableCancelacion().equals("Personal documental"),
                "cancelación inicial conserva el evento de auditoría");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Cobros.confirmarInscripcion(inicial, cuotas.getCuotaInscripcion(),
                        MedioPago.EFECTIVO, "", confirmacion.plusDays(9), "otro",
                        confirmacion.plusDays(9), solicitudesInicial, matriculasInicial,
                        pagosCancelacion, cuotas),
                "una cancelada no recibe pagos", "ya tiene la inscripción pagada");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> new Pago(inicial, ConceptoPago.MATRICULA, matriculaCancelada,
                        cuotas.getCuotaMatricula(), MedioPago.EFECTIVO, "",
                        confirmacion.plusDays(9), confirmacion.plusDays(9),
                        "matricula-cancelada", cuotas),
                "una cancelada no recibe pago de matrícula", "no puede pagar la matrícula");
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Cobros.anularPago(pagoCancelado, "error", "Personal", confirmacion.plusDays(9),
                        solicitudesInicial, matriculasInicial, pagosCancelacion),
                "no se anula el pago de una cancelada", "no está en documentación");
        comprobar(pagoCancelado.estaConfirmado(), "el pago confirmado de una cancelada no se reembolsa");
        esperarFallo(TransicionInvalidaException.class,
                () -> inicial.cambiarEstado(EstadoSolicitud.EN_ESPERA_SIN_PAGO),
                "una solicitud cancelada es terminal");

        Solicitud correccion = solicitud("DOC-CORRECCION", confirmacion.minusDays(2), aula);
        correccion.habilitarParaPago(confirmacion.minusHours(2));
        correccion.confirmarInscripcion(confirmacion);
        modelo.Documento observado = correccion.getExpediente().getDocumentos().get(0);
        observado.registrarEntrega(confirmacion.plusHours(1));
        observado.observar("Foto ilegible", LocalDateTime.of(2026, 9, 25, 10, 0));
        comprobar(!observado.correccionVencida(LocalDate.of(2026, 10, 2))
                && observado.correccionVencida(LocalDate.of(2026, 10, 3)),
                "la corrección vence el día posterior al límite");
        correccion.cancelarPorDocumentacion(LocalDateTime.of(2026, 10, 3, 9, 0),
                "Venció el plazo de corrección", "Personal documental");
        comprobar(correccion.getEstado() == EstadoSolicitud.CANCELADA
                && correccion.getMotivoCancelacion().contains("corrección"),
                "cancelación por corrección conserva el evento");

        Solicitud entregaUltimoDia = solicitud("DOC-ULTIMO-DIA", confirmacion.minusDays(2), aula);
        entregaUltimoDia.habilitarParaPago(confirmacion.minusHours(2));
        entregaUltimoDia.confirmarInscripcion(confirmacion);
        for (modelo.Documento documento : entregaUltimoDia.getExpediente().getDocumentos()) {
            documento.registrarEntrega(LocalDateTime.of(2026, 9, 27, 20, 0));
        }
        comprobar(entregaUltimoDia.getExpediente().entregaOportuna()
                && !entregaUltimoDia.getExpediente().debeCancelarse(LocalDate.of(2026, 9, 28)),
                "los cuatro documentos entregados el último día son oportunos");

        Solicitud correccionUltimoDia = solicitud("DOC-CORR-ULTIMO", confirmacion.minusDays(2), aula);
        correccionUltimoDia.habilitarParaPago(confirmacion.minusHours(2));
        correccionUltimoDia.confirmarInscripcion(confirmacion);
        modelo.Documento documento = correccionUltimoDia.getExpediente().getDocumentos().get(0);
        documento.registrarEntrega(confirmacion);
        documento.observar("Falta dato", LocalDateTime.of(2026, 9, 25, 10, 0));
        documento.entregarCorreccion(LocalDateTime.of(2026, 10, 2, 23, 59));
        comprobar(!documento.correccionVencida(LocalDate.of(2026, 10, 3)),
                "la corrección entregada en el límite ya no vence");

        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloMatriculas matriculas = new ArregloMatriculas();
        Solicitud barrida = solicitud("DOC-BARRIDA", confirmacion.minusDays(2), aula);
        barrida.habilitarParaPago(confirmacion.minusHours(2));
        barrida.confirmarInscripcion(confirmacion);
        java.util.ArrayList<Solicitud> almacen =
                (java.util.ArrayList<Solicitud>) campoPrivado(solicitudes, "solicitudes");
        almacen.add(barrida);
        solicitudes.cancelarDocumentacionVencida(LocalDate.of(2026, 9, 28), "Personal de turnos");
        comprobar(barrida.getEstado() == EstadoSolicitud.CANCELADA,
                "el barrido cancela documentación vencida");
        Solicitud barridaPorOrden = solicitud("DOC-BARRIDA-ORDEN", confirmacion.minusDays(3), aula);
        barridaPorOrden.habilitarParaPago(confirmacion.minusHours(2));
        barridaPorOrden.confirmarInscripcion(confirmacion.minusDays(1));
        almacen.add(barridaPorOrden);
        comprobar(!Turnos.ordenPorPrioridad(aula, solicitudes, matriculas).contains(barrida)
                && barridaPorOrden.getEstado() == EstadoSolicitud.EN_DOCUMENTACION,
                "la solicitud cancelada no aparece en la cola y consultar no barre");
        negocio.Vencimientos.revisar(LocalDateTime.of(2026, 9, 28, 10, 0), solicitudes,
                matriculas);
        comprobar(barridaPorOrden.getEstado() == EstadoSolicitud.CANCELADA
                && barridaPorOrden.getFechaCancelacion().equals(LocalDate.of(2026, 9, 28)
                        .atStartOfDay())
                && negocio.Vencimientos.RESPONSABLE_DOCUMENTACION.equals(
                        barridaPorOrden.getResponsableCancelacion()),
                "Vencimientos.revisar cancela documentación vencida con auditoría");

        Snapshot antes = snapshot(null, correccionUltimoDia, null, new ArregloSolicitudes(),
                matriculas, new ArregloPagos());
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> correccionUltimoDia.cancelarPorDocumentacion(
                        LocalDateTime.of(2026, 9, 20, 12, 0), "prematura", "Personal"),
                "cancelación prematura rechazada", "todavía no venció");
        assertSnapshot(antes, null, correccionUltimoDia, null, new ArregloSolicitudes(),
                matriculas, new ArregloPagos(), "cancelación prematura no altera el estado");
    }

    private static void probarMotorVencimientosConReloj() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 10, 0);
        Reloj reloj = new Reloj();
        reloj.fijar(base.plusDays(4));
        Aula aula = new Aula("AUL-V", "Vencimientos", 4, 2);
        ArregloSolicitudes solicitudes = new ArregloSolicitudes();
        ArregloPagos pagos = new ArregloPagos();
        ArregloMatriculas matriculas = new ArregloMatriculas();
        Solicitud solicitud = registrarSolicitud(solicitudes, aula, "VENCIDA", base);
        solicitud.habilitarParaPago(base);
        solicitud.confirmarInscripcion(base.plusHours(1));
        Matricula matricula = matriculas.registrar(solicitud, base);
        Vencimientos.barrer(reloj, null, solicitudes, matriculas, pagos,
                new ConfiguracionCuotas());
        comprobar(matricula.getEstado() == EstadoMatricula.CANCELADA
                && solicitud.estaEnColaFavorable()
                && solicitud.getFechaIngresoCola().equals(reloj.now()),
                "barrido de matrícula vencida libera y devuelve al final favorable");

        Solicitud habilitada = registrarSolicitud(solicitudes, aula, "PROXIMA", base);
        habilitada.habilitarParaPago(reloj.now().minusHours(36));
        java.util.ArrayList<Vencimientos.Proximo> proximos = Vencimientos.proximos(reloj,
                solicitudes, matriculas);
        boolean hallada = false;
        for (Vencimientos.Proximo proximo : proximos) {
            if (proximo.getCodigoSolicitud().equals("PROXIMA")
                    && proximo.getTipo().equals("HABILITACION")) {
                hallada = true;
            }
        }
        comprobar(hallada, "consulta de próximos vencimientos incluye habilitación en 24 horas");

        LocalDateTime arranque = LocalDateTime.of(2026, 10, 9, 10, 0);
        reloj.fijar(arranque);
        Aula aulaArranque = new Aula("AUL-ARR", "Arranque", 4, 1);
        ArregloSolicitudes solicitudesArranque = new ArregloSolicitudes();
        ArregloPagos pagosArranque = new ArregloPagos();
        ArregloMatriculas matriculasArranque = new ArregloMatriculas();

        Solicitud habilitacionVencida = registrarSolicitud(solicitudesArranque, aulaArranque,
                "ARR-HAB", arranque.minusDays(3));
        habilitacionVencida.habilitarParaPago(arranque.minusHours(49));

        Solicitud documentosVencidos = registrarSolicitud(solicitudesArranque, aulaArranque,
                "ARR-DOC", arranque.minusDays(9));
        documentosVencidos.habilitarParaPago(arranque.minusDays(9));
        documentosVencidos.confirmarInscripcion(arranque.minusDays(8));

        Solicitud matriculaVencida = registrarSolicitud(solicitudesArranque, aulaArranque,
                "ARR-MAT", arranque.minusDays(5));
        matriculaVencida.habilitarParaPago(arranque.minusDays(5));
        Pago inscripcionArranque = new Pago(matriculaVencida, ConceptoPago.INSCRIPCION, null,
                new ConfiguracionCuotas().getCuotaInscripcion(), MedioPago.EFECTIVO, "",
                arranque.minusDays(4), arranque.minusDays(4), "arranque", new ConfiguracionCuotas());
        inscripcionArranque.confirmar();
        pagosArranque.agregar(inscripcionArranque);
        matriculaVencida.confirmarInscripcion(arranque.minusDays(4));
        matriculaVencida.ingresarAColaFavorable(arranque.minusDays(2));
        Matricula matriculaArranque = matriculasArranque.registrar(matriculaVencida,
                arranque.minusHours(72));
        Solicitud anterior = registrarSolicitud(solicitudesArranque, aulaArranque, "ARR-ANT",
                arranque.minusDays(10));
        anterior.habilitarParaPago(arranque.minusDays(10));
        anterior.confirmarInscripcion(arranque.minusDays(9));
        anterior.ingresarAColaFavorable(arranque.minusDays(1));

        comprobar(Vacantes.calcular(aulaArranque, matriculasArranque) == 0,
                "el barrido de arranque parte con la vacante reservada");
        Vencimientos.barrer(reloj, null, solicitudesArranque, matriculasArranque, pagosArranque,
                new ConfiguracionCuotas());
        comprobar(matriculaArranque.getEstado() == EstadoMatricula.CANCELADA
                && Vacantes.calcular(aulaArranque, matriculasArranque) == 1
                && solicitudesArranque.colaFavorable(aulaArranque).get(1) == matriculaVencida
                && pagosArranque.inscripcionConfirmada(matriculaVencida),
                "arranque cancela matrícula, libera vacante y conserva inscripción pagada");
        comprobar(habilitacionVencida.estaEnColaSinPago()
                && documentosVencidos.getEstado() == EstadoSolicitud.CANCELADA,
                "arranque vence habilitación y cancela documentación");

        Solicitud limiteHoy = registrarSolicitud(solicitudesArranque, aulaArranque, "PROX-DOC",
                arranque.minusDays(2));
        limiteHoy.habilitarParaPago(arranque.minusDays(7).minusHours(1));
        limiteHoy.confirmarInscripcion(arranque.minusDays(7));
        for (modelo.Documento documento : limiteHoy.getExpediente().getDocumentos()) {
            documento.registrarEntrega(arranque.minusMinutes(1));
        }
        Solicitud incompletaHoy = registrarSolicitud(solicitudesArranque, aulaArranque, "PROX-ENT",
                arranque.minusDays(2));
        incompletaHoy.habilitarParaPago(arranque.minusDays(7).minusHours(1));
        incompletaHoy.confirmarInscripcion(arranque.minusDays(7));
        Solicitud correccionManana = registrarSolicitud(solicitudesArranque, aulaArranque, "PROX-COR",
                arranque.minusDays(3));
        correccionManana.habilitarParaPago(arranque.minusDays(7).minusHours(1));
        correccionManana.confirmarInscripcion(arranque.minusDays(7));
        modelo.Documento observado = correccionManana.getExpediente().getDocumentos().get(0);
        observado.registrarEntrega(arranque.minusDays(6));
        observado.observar("Falta información", arranque.minusDays(6));
        Solicitud correccionDentro = registrarSolicitud(solicitudesArranque, aulaArranque,
                "PROX-COR-24", arranque.minusDays(8));
        correccionDentro.habilitarParaPago(arranque.minusDays(8));
        correccionDentro.confirmarInscripcion(arranque.minusDays(7));
        modelo.Documento correccionUrgente = correccionDentro.getExpediente().getDocumentos().get(0);
        correccionUrgente.registrarEntrega(arranque.minusDays(7));
        correccionUrgente.observar("Dato pendiente", arranque.minusDays(7));
        Solicitud matriculaProxima = registrarSolicitud(solicitudesArranque, aulaArranque, "PROX-MAT",
                arranque.minusDays(3));
        matriculaProxima.habilitarParaPago(arranque.minusDays(3));
        matriculaProxima.confirmarInscripcion(arranque.minusDays(2));
        matriculaProxima.ingresarAColaFavorable(arranque.minusDays(1));
        matriculasArranque.registrar(matriculaProxima, arranque.minusHours(60));
        Solicitud invitacionLejana = registrarSolicitud(solicitudesArranque, aulaArranque, "PROX-FAR",
                arranque);
        invitacionLejana.habilitarParaPago(arranque);
        java.util.ArrayList<Vencimientos.Proximo> proximosArranque = Vencimientos.proximos(reloj,
                solicitudesArranque, matriculasArranque);
        comprobar(!contieneProximo(proximosArranque, "PROX-DOC", "ENTREGA_DOCUMENTOS")
                && contieneProximo(proximosArranque, "PROX-ENT", "ENTREGA_DOCUMENTOS")
                && !contieneProximo(proximosArranque, "PROX-COR", "CORRECCION_DOCUMENTO")
                && contieneProximo(proximosArranque, "PROX-COR-24", "CORRECCION_DOCUMENTO")
                && contieneProximo(proximosArranque, "PROX-MAT", "MATRICULA")
                && !contieneProximo(proximosArranque, "PROX-FAR", "HABILITACION"),
                "próximos 24 horas respeta límites documentales y excluye lejanos");

        Solicitud sinVencer = registrarSolicitud(solicitudesArranque, aulaArranque, "SIN-VENCER",
                arranque);
        sinVencer.habilitarParaPago(arranque.plusHours(1));
        Matricula futura = matriculasArranque.registrar(sinVencer, arranque.plusHours(1));
        Snapshot intacto = snapshot(null, sinVencer, futura, solicitudesArranque,
                matriculasArranque, pagosArranque);
        Vencimientos.barrer(reloj, null, solicitudesArranque, matriculasArranque, pagosArranque,
                new ConfiguracionCuotas());
        assertSnapshot(intacto, null, sinVencer, futura, solicitudesArranque,
                matriculasArranque, pagosArranque, "barrido sin vencimientos conserva el estado");
        ArregloSolicitudes solicitudesRechazo = new ArregloSolicitudes();
        ArregloPagos pagosRechazo = new ArregloPagos();
        ArregloMatriculas matriculasRechazo = new ArregloMatriculas();
        Solicitud solicitudRechazo = registrarSolicitud(solicitudesRechazo, aulaArranque,
                "RECHAZO-VENC", arranque.minusDays(4));
        Matricula matriculaRechazo = matriculasRechazo.registrar(solicitudRechazo,
                arranque.minusHours(72));
        Snapshot rechazo = snapshot(null, solicitudRechazo, matriculaRechazo,
                solicitudesRechazo, matriculasRechazo, pagosRechazo);
        esperarFalloConMensaje(ReglaDominioException.class,
                () -> Vencimientos.barrer(reloj, null, solicitudesRechazo, matriculasRechazo,
                        pagosRechazo, new ConfiguracionCuotas()),
                "rechazo de vencimiento conserva estado", "no puede volver a la cola favorable");
        assertSnapshot(rechazo, null, solicitudRechazo, matriculaRechazo,
                solicitudesRechazo, matriculasRechazo, pagosRechazo,
                "rechazo del motor no altera estado");
        Aula aulaBorde = new Aula("AUL-BORDE", "Borde", 4, 1);
        ArregloSolicitudes solicitudesBorde = new ArregloSolicitudes();
        ArregloMatriculas matriculasBorde = new ArregloMatriculas();
        Solicitud solicitudBorde = registrarSolicitud(solicitudesBorde, aulaBorde, "BORDE-MAT",
                arranque.minusDays(5));
        solicitudBorde.habilitarParaPago(arranque.minusDays(5));
        solicitudBorde.confirmarInscripcion(arranque.minusDays(4));
        solicitudBorde.ingresarAColaFavorable(arranque.minusDays(3));
        Matricula matriculaBorde = matriculasBorde.registrar(solicitudBorde,
                arranque.minusHours(72));
        Vencimientos.barrer(reloj, null, solicitudesBorde, matriculasBorde, new ArregloPagos(),
                new ConfiguracionCuotas());
        comprobar(matriculaBorde.getEstado() == EstadoMatricula.CANCELADA
                && Vacantes.calcular(aulaBorde, matriculasBorde) == 1,
                "borde pre-vacantes barre matrícula antes de calcular la cola");
    }

    private static void probarGeneradorDemoProfesor() throws Exception {
        java.nio.file.Path raiz = Files.createTempDirectory("casaamarilla-demo-test");
        LocalDateTime base = LocalDateTime.of(2026, 10, 12, 0, 5);
        try {
            java.nio.file.Path normales = raiz.resolve("datos-normales.csv");
            Files.writeString(normales, "datos ajenos que deben conservarse");
            java.nio.file.Path padre = raiz.resolve("demo-profesor");
            java.nio.file.Path primera = GenerarDatosDemo.generar(padre, base);
            java.util.Map<java.nio.file.Path, byte[]> originales = new java.util.HashMap<>();
            try (var archivos = Files.walk(primera)) {
                for (var ruta : archivos.filter(Files::isRegularFile).toList()) {
                    originales.put(ruta, Files.readAllBytes(ruta));
                }
            }
            // Mismo instante: la unicidad no depende de la resolución del reloj.
            java.nio.file.Path segunda = GenerarDatosDemo.generar(padre, base);
            comprobar(!primera.equals(segunda) && primera.getParent().equals(padre)
                    && segunda.getParent().equals(padre), "demo: carpetas exclusivas bajo el padre");
            for (var archivo : originales.entrySet()) {
                comprobar(java.util.Arrays.equals(archivo.getValue(), Files.readAllBytes(archivo.getKey())),
                        "demo: segunda ejecución no modifica " + archivo.getKey().getFileName());
            }
            comprobar(Files.readString(normales).equals("datos ajenos que deben conservarse"),
                    "demo: datos normales intactos");
            comprobar(originales.size() == 17, "demo: once CSV y seis comprobantes persistentes");
            verificarDemoRecargada(primera, base);
            verificarDemoRecargada(segunda, base);
            java.nio.file.Path tercera = GenerarDatosDemo.generar(padre, base.plusDays(1).plusHours(12));
            verificarDemoRecargada(tercera, base.plusDays(1).plusHours(12));
            // Los comprobantes de la primera siguen disponibles tras generar y recargar las otras.
            verificarDemoRecargada(primera, base);
        } finally {
            Reloj.porDefecto().restablecer();
            try (var rutas = Files.walk(raiz)) {
                for (var ruta : rutas.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(ruta);
                }
            }
        }
    }

    private static void verificarDemoRecargada(java.nio.file.Path carpeta, LocalDateTime base)
            throws Exception {
        String[] nombres = {"apoderados", "alumnos", "vinculos", "aulas", "cuotas",
                "solicitudes", "documentos", "ofertas", "matriculas", "entrevistas", "pagos"};
        for (String nombre : nombres) {
            comprobar(Files.isRegularFile(carpeta.resolve(nombre + ".csv")),
                    "demo: CSV " + nombre + " generado");
        }
        Almacen almacen = new Almacen(carpeta.toFile());
        almacen.cargar();
        comprobar(almacen.getAlumnos().cantidad() == 5 && almacen.getApoderados().cantidad() == 5
                && almacen.getSolicitudes().listar().size() == 5
                && almacen.getMatriculas().listar().size() == 3
                && almacen.getEntrevistas().listar().size() == 3
                && almacen.getPagos().listar().size() == 6, "demo: recarga de todas las entidades");
        comprobar(almacen.getCuotas().getCuotaInscripcion() == 180
                && almacen.getCuotas().getCuotaMatricula() == 850, "demo: importes acordados");
        Aula tulipanes = almacen.getAulas().buscar("AUL-02");
        Aula girasoles = almacen.getAulas().buscar("AUL-01");
        comprobar(tulipanes.getEdadRequerida() == 3 && tulipanes.getCapacidad() == 18
                && girasoles.getEdadRequerida() == 4 && girasoles.getCapacidad() == 20,
                "demo: edades y capacidades sin cambios");
        Reloj reloj = new Reloj();
        reloj.fijar(base);
        Vencimientos.barrer(reloj, almacen.getAulas(), almacen.getSolicitudes(),
                almacen.getMatriculas(), almacen.getPagos(), almacen.getCuotas());
        java.util.Set<String> alumnos = new java.util.HashSet<>();
        java.util.Set<String> apoderados = new java.util.HashSet<>();
        for (Solicitud solicitud : almacen.getSolicitudes().listar()) {
            int caso = Integer.parseInt(solicitud.getAlumno().getDni().substring(7));
            comprobar(alumnos.add(solicitud.getAlumno().getDni())
                    && apoderados.add(solicitud.getAlumno().getPrincipal().getDni()),
                    "demo: identidades distintas");
            comprobar(solicitud.getAula() == tulipanes
                    && tulipanes.admiteEdad(util.FechasAdmision.edadAlCorte(
                            solicitud.getAlumno().getFechaNacimiento()))
                    && solicitud.getAlumno() == almacen.getAlumnos().buscarPorDni(
                            solicitud.getAlumno().getDni())
                    && solicitud.getAlumno().getApoderados().size() == 1
                    && solicitud.getAlumno().getPrincipal() == almacen.getApoderados().buscar(
                            solicitud.getAlumno().getPrincipal().getDni()),
                    "demo: relaciones y apoderado principal del caso " + caso);
            comprobar(!solicitud.getFechaRegistro().isAfter(base), "demo: registro no futuro");
            if (caso == 1) {
                comprobar(solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO
                        && solicitud.getFechaHabilitacion().plusHours(48).equals(
                                solicitud.getVencimientoHabilitacionOriginal())
                        && !solicitud.habilitacionVencida(base)
                        && !almacen.getPagos().inscripcionConfirmada(solicitud),
                        "demo: caso habilitado conserva 48 horas vigentes");
                int cantidadPagos = almacen.getPagos().listar().size();
                esperarFallo(ReglaDominioException.class, () -> Cobros.confirmarInscripcion(
                        solicitud, 1, MedioPago.EFECTIVO, "", base,
                        carpeta.resolve("comprobantes/caso-2-inscripcion.png").toString(), base,
                        almacen.getSolicitudes(), almacen.getMatriculas(), almacen.getPagos(),
                        almacen.getCuotas()), "demo: importe incorrecto rechazado");
                esperarFallo(modelo.SolicitudDuplicadaException.class,
                        () -> almacen.getSolicitudes().registrar(solicitud.getAlumno(), tulipanes),
                        "demo: solicitud activa duplicada rechazada");
                comprobar(almacen.getPagos().listar().size() == cantidadPagos
                        && almacen.getSolicitudes().listar().size() == 5
                        && solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO,
                        "demo: rechazos sin cambios parciales");
                continue;
            }
            comprobar(almacen.getPagos().inscripcionConfirmada(solicitud)
                    && solicitud.getExpediente().getDocumentos().size() == 4
                    && solicitud.getExpediente().fechaLimiteEntrega().equals(
                            solicitud.getFechaConfirmacionInscripcion().toLocalDate().plusDays(7)),
                    "demo: inscripción y plazo documental originales del caso " + caso);
            if (caso == 2) {
                var observado = solicitud.getExpediente().getDocumento(modelo.TipoDocumento.CARNE_VACUNAS);
                comprobar(solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                        && observado.getEstado() == modelo.EstadoDocumento.OBSERVADO
                        && observado.fechaLimiteCorreccion().equals(
                                observado.getFechaHoraComunicacion().toLocalDate().plusDays(7))
                        && !observado.correccionVencida(base.toLocalDate())
                        && solicitud.getExpediente().pendientesDeValidar().size() == 1,
                        "demo: observación vigente y único documento pendiente");
                esperarFallo(ReglaDominioException.class,
                        () -> almacen.getEntrevistas().agendar(solicitud, solicitud.getExpediente(),
                                base, new java.util.ArrayList<>(java.util.List.of("Personal DEMO")),
                                "Personal DEMO"), "demo: entrevista bloqueada por observación");
                comprobar(almacen.getEntrevistas().listar().size() == 3,
                        "demo: entrevista rechazada no registrada");
                continue;
            }
            Matricula matricula = almacen.getMatriculas().vigenteDe(solicitud);
            var entrevista = almacen.getEntrevistas().buscarDe(solicitud);
            comprobar(solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                    && solicitud.getExpediente().todosValidados() && solicitud.tieneOfertaAceptada()
                    && entrevista.getSolicitud() == solicitud && entrevista.isRealizada()
                    && entrevista.getResultado() == modelo.ResultadoEntrevista.FAVORABLE
                    && matricula.getSolicitud() == solicitud
                    && matricula.getFechaHoraCreacion().plusHours(72).equals(
                            matricula.getVencimientoPagoOriginal()), "demo: trayectoria completa " + caso);
            comprobar(matricula.getEstado() == (caso == 3 ? EstadoMatricula.PENDIENTE_PAGO
                    : EstadoMatricula.ACTIVA) && (caso != 3 || base.isBefore(
                            matricula.getVencimientoPagoOriginal()))
                    && almacen.getPagos().matriculaConfirmada(matricula) == (caso != 3),
                    "demo: reserva vigente y dos éxitos completos");
        }
        java.util.Set<String> operaciones = new java.util.HashSet<>();
        for (Pago pago : almacen.getPagos().listar()) {
            comprobar(pago.estaConfirmado() && operaciones.add(pago.getNumeroOperacion())
                    && pago.getSolicitud() == almacen.getSolicitudes().buscar(pago.getSolicitud().getCodigo())
                    && pago.getMontoPagado() == (pago.getConcepto() == ConceptoPago.INSCRIPCION ? 180 : 850),
                    "demo: pagos distintos, importes y relaciones");
            comprobar(!pago.getFechaHoraOperacion().isBefore(pago.getSolicitud().getFechaRegistro())
                    && !pago.getFechaHoraRegistro().isBefore(pago.getFechaHoraOperacion())
                    && !pago.getFechaHoraRegistro().isAfter(base), "demo: cronología de pagos válida");
            if (pago.getConcepto() == ConceptoPago.MATRICULA) {
                comprobar(pago.getMatricula() == almacen.getMatriculas().vigenteDe(pago.getSolicitud())
                        && !pago.getFechaHoraOperacion().isBefore(pago.getMatricula().getFechaHoraCreacion())
                        && pago.getFechaHoraRegistro().isBefore(pago.getMatricula().getVencimientoPagoOriginal()),
                        "demo: pago vinculado a su matrícula dentro de las 72 horas originales");
            } else {
                comprobar(pago.getMatricula() == null && pago.getFechaHoraRegistro().isBefore(
                        pago.getSolicitud().getVencimientoHabilitacionOriginal()),
                        "demo: inscripción sin matrícula dentro de las 48 horas originales");
            }
            java.nio.file.Path ruta = java.nio.file.Path.of(pago.getRutaComprobantePago());
            comprobar(ruta.startsWith(carpeta) && Files.isRegularFile(ruta)
                    && ImageIO.read(ruta.toFile()) != null, "demo: comprobante persistente legible");
        }
        comprobar(Vacantes.calcular(tulipanes, almacen.getMatriculas()) == 15
                && Vacantes.calcular(girasoles, almacen.getMatriculas()) == 20
                && Turnos.ordenPorPrioridad(girasoles, almacen.getSolicitudes(),
                        almacen.getMatriculas()).isEmpty(), "demo: tres reservas y Girasoles libre");
        java.util.Map<String, byte[]> antes = new java.util.HashMap<>();
        for (String nombre : nombres) {
            antes.put(nombre, Files.readAllBytes(carpeta.resolve(nombre + ".csv")));
        }
        almacen.grabar();
        for (String nombre : nombres) {
            comprobar(java.util.Arrays.equals(antes.get(nombre),
                    Files.readAllBytes(carpeta.resolve(nombre + ".csv"))),
                    "demo: recarga y motor sin pérdida de información en " + nombre);
        }
    }

    private static void probarPersistenciaIdaYVuelta() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-rt").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen origen = new Almacen();
        origen.getCuotas().setCuotas(200, 900);
        Aula aula = origen.getAulas().buscar("AUL-01");
        ArregloSolicitudes solicitudes = origen.getSolicitudes();
        ArregloMatriculas matriculas = origen.getMatriculas();
        ArregloPagos pagos = origen.getPagos();

        // (b) solicitud en cola conserva su fecha de ingreso original.
        Solicitud enCola = solicitud(origen, aula, "RT-QUEUE", "70000001", "80000001", "Mía",
                base.minusDays(4));
        enCola.ingresarAColaSinPago(base.minusDays(2));
        LocalDateTime fechaColaOriginal = enCola.getFechaIngresoCola();

        // EN_DOCUMENTACION con documentos y una observación con caracteres peligrosos.
        String observacion = "Ilegible; coma, salto\nde línea y barra \\ final";
        Solicitud enDoc = solicitud(origen, aula, "RT-DOC", "70000002", "80000002", "Sofía",
                base.minusDays(4));
        enDoc.habilitarParaPago(base.minusHours(6));
        pagos.agregar(pagoInscripcion(enDoc, base.minusHours(5), base.minusHours(4),
                origen.getCuotas()));
        enDoc.confirmarInscripcion(base.minusHours(4));
        for (modelo.Documento d : enDoc.getExpediente().getDocumentos()) {
            d.registrarEntrega(base.minusHours(3));
            if (d.getTipo() == modelo.TipoDocumento.CARNE_VACUNAS) {
                d.observar(observacion, base.minusHours(2));
            } else {
                d.validar();
            }
        }
        LocalDateTime confirmacionDoc = enDoc.getFechaConfirmacionInscripcion();

        // EN_ESPERA_FAVORABLE con ofertas, inicioCicloOfertas y matrícula PENDIENTE_PAGO.
        Solicitud favorable = solicitud(origen, aula, "RT-FAV", "70000003", "80000003", "Eva",
                base.minusDays(4));
        favorable.habilitarParaPago(base.minusHours(8));
        pagos.agregar(pagoInscripcion(favorable, base.minusHours(7), base.minusHours(6),
                origen.getCuotas()));
        favorable.confirmarInscripcion(base.minusHours(6));
        for (modelo.Documento d : favorable.getExpediente().getDocumentos()) {
            d.registrarEntrega(base.minusHours(5));
            d.validar();
        }
        favorable.ingresarAColaFavorable(base.minusHours(4));
        favorable.registrarOferta(new Oferta(base.minusHours(3), "Personal de admisión", false));
        Matricula matPendiente = matriculas.registrar(favorable, base.minusHours(2));
        favorable.ingresarAColaFavorable(base.minusHours(1));
        favorable.registrarOferta(new Oferta(base.minusMinutes(30), "Personal de admisión", true));
        int inicioCiclo = favorable.getInicioCicloOfertas();
        LocalDateTime fechaIngresoFavorable = favorable.getFechaIngresoCola();

        // Matrícula ACTIVA con pago de matrícula confirmado.
        Solicitud activaSolicitud = solicitud(origen, aula, "RT-ACT", "70000004", "80000004",
                "Ana", base.minusDays(4));
        activaSolicitud.habilitarParaPago(base.minusHours(10));
        pagos.agregar(pagoInscripcion(activaSolicitud, base.minusHours(9), base.minusHours(8),
                origen.getCuotas()));
        activaSolicitud.confirmarInscripcion(base.minusHours(8));
        for (modelo.Documento d : activaSolicitud.getExpediente().getDocumentos()) {
            d.registrarEntrega(base.minusHours(7));
            d.validar();
        }
        activaSolicitud.ingresarAColaFavorable(base.minusHours(6));
        Matricula matActiva = matriculas.registrar(activaSolicitud, base.minusHours(6));
        pagos.agregar(pagoMatriculaConfirmado(activaSolicitud, matActiva, base.minusHours(5),
                base.minusHours(4), origen.getCuotas()));
        matActiva.activar();

        // Solicitud CANCELADA con auditoría.
        Solicitud cancelada = solicitud(origen, aula, "RT-CANC", "70000005", "80000005", "Leo",
                base.minusDays(10));
        cancelada.habilitarParaPago(base.minusDays(9).minusHours(2));
        pagos.agregar(pagoInscripcion(cancelada, base.minusDays(9).minusHours(1),
                base.minusDays(9), origen.getCuotas()));
        cancelada.confirmarInscripcion(base.minusDays(9));
        cancelada.cancelarPorDocumentacion(base.minusDays(1).toLocalDate().atStartOfDay(),
                "Venció el plazo documental", "Sistema");

        // Matrícula CANCELADA con auditoría.
        Solicitud matCanceladaSol = solicitud(origen, aula, "RT-MATCANC", "70000006", "80000006",
                "Rosa", base.minusDays(4));
        matCanceladaSol.habilitarParaPago(base.minusHours(12));
        pagos.agregar(pagoInscripcion(matCanceladaSol, base.minusHours(11), base.minusHours(10),
                origen.getCuotas()));
        matCanceladaSol.confirmarInscripcion(base.minusHours(10));
        for (modelo.Documento d : matCanceladaSol.getExpediente().getDocumentos()) {
            d.registrarEntrega(base.minusHours(9));
            d.validar();
        }
        matCanceladaSol.ingresarAColaFavorable(base.minusHours(8));
        Matricula matCancelada = matriculas.registrar(matCanceladaSol, base.minusHours(8));
        matCancelada.cancelar(base.minusHours(7), "Cancelada por prueba", "Personal de caja");

        // Pago ANULADO con auditoría y comprobante.
        Solicitud pagoAnuladoSol = solicitud(origen, aula, "RT-ANUL", "70000007", "80000007",
                "Noa", base.minusDays(4));
        pagoAnuladoSol.habilitarParaPago(base.minusHours(14));
        Pago pagoAnulado = pagoInscripcion(pagoAnuladoSol, base.minusHours(13),
                base.minusHours(12), origen.getCuotas());
        pagoAnulado.registrarComprobante("123456");
        pagos.agregar(pagoAnulado);
        pagoAnulado.anular("Error de registro", "Personal de caja", base.minusHours(11));

        // Entrevista agendada con asistencia y realizada (sin resultado: queda EN_DOCUMENTACION).
        Solicitud entrevistaSol = solicitud(origen, aula, "RT-ENT", "70000008", "80000008",
                "Lucía", base.minusDays(4));
        entrevistaSol.habilitarParaPago(base.minusHours(16));
        pagos.agregar(pagoInscripcion(entrevistaSol, base.minusHours(15), base.minusHours(14),
                origen.getCuotas()));
        entrevistaSol.confirmarInscripcion(base.minusHours(14));
        for (modelo.Documento d : entrevistaSol.getExpediente().getDocumentos()) {
            d.registrarEntrega(base.minusHours(13));
            d.validar();
        }
        modelo.Entrevista entrevista = origen.getEntrevistas().agendar(entrevistaSol,
                entrevistaSol.getExpediente(), base.plusDays(1),
                listaDe("Ana Ruiz", "Luis Ramos"), "Ana Ruiz");
        entrevista.registrarAsistencia();
        entrevista.marcarRealizada();

        int cantidadPagos = pagos.listar().size();
        int cantidadSolicitudes = solicitudes.listar().size();
        int cantidadMatriculas = matriculas.listar().size();
        origen.grabar();

        // "Cerrar" = descartar las colecciones y recargar en objetos nuevos.
        Almacen recargado = new Almacen();
        recargado.cargar();
        comprobar(recargado.getApoderados().cantidad() == 8
                && recargado.getAlumnos().cantidad() == 8,
                "ida y vuelta conserva apoderados y alumnos con sus vínculos");
        comprobar(recargado.getAulas().listar().size() == origen.getAulas().listar().size(),
                "ida y vuelta conserva las aulas");
        comprobar(recargado.getCuotas().getCuotaInscripcion() == 200
                && recargado.getCuotas().getCuotaMatricula() == 900,
                "ida y vuelta conserva las cuotas");
        comprobar(recargado.getSolicitudes().listar().size() == cantidadSolicitudes
                && recargado.getMatriculas().listar().size() == cantidadMatriculas
                && recargado.getPagos().listar().size() == cantidadPagos,
                "ida y vuelta conserva la cantidad de solicitudes, matrículas y pagos");

        Solicitud colaRecargada = recargado.getSolicitudes().buscar("RT-QUEUE");
        comprobar(colaRecargada != null && colaRecargada.estaEnColaSinPago()
                && colaRecargada.getFechaIngresoCola().equals(fechaColaOriginal),
                "la solicitud en cola conserva su cola y su fecha de ingreso original");

        Solicitud docRecargada = recargado.getSolicitudes().buscar("RT-DOC");
        modelo.Documento carne = docRecargada.getExpediente()
                .getDocumento(modelo.TipoDocumento.CARNE_VACUNAS);
        comprobar(docRecargada.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                && docRecargada.getFechaConfirmacionInscripcion().equals(confirmacionDoc)
                && carne.getEstado() == modelo.EstadoDocumento.OBSERVADO
                && observacion.equals(carne.getObservacion()),
                "ida y vuelta conserva expediente, documentos y observación con caracteres especiales");

        Solicitud favRecargada = recargado.getSolicitudes().buscar("RT-FAV");
        comprobar(favRecargada.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && favRecargada.getOfertas().size() == 2
                && favRecargada.getInicioCicloOfertas() == inicioCiclo
                && favRecargada.getFechaIngresoCola().equals(fechaIngresoFavorable)
                && favRecargada.tieneOfertaAceptada(),
                "ida y vuelta conserva ofertas, inicio de ciclo y cola favorable");

        Matricula pendienteRecargada = recargado.getMatriculas().buscar(matPendiente.getNumMatricula());
        comprobar(pendienteRecargada != null
                && pendienteRecargada.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                && pendienteRecargada.getVencimientoPagoOriginal()
                        .equals(matPendiente.getVencimientoPagoOriginal()),
                "ida y vuelta conserva la matrícula pendiente con su vencimiento original");

        Matricula activaRecargada = recargado.getMatriculas().buscar(matActiva.getNumMatricula());
        comprobar(activaRecargada != null && activaRecargada.getEstado() == EstadoMatricula.ACTIVA
                && recargado.getPagos().matriculaConfirmada(activaRecargada),
                "ida y vuelta conserva la matrícula activa y su pago confirmado");

        Solicitud canceladaRecargada = recargado.getSolicitudes().buscar("RT-CANC");
        comprobar(canceladaRecargada.getEstado() == EstadoSolicitud.CANCELADA
                && canceladaRecargada.getMotivoCancelacion().equals("Venció el plazo documental")
                && "Sistema".equals(canceladaRecargada.getResponsableCancelacion())
                && canceladaRecargada.getFechaCancelacion().equals(base.minusDays(1).toLocalDate().atStartOfDay()),
                "ida y vuelta conserva la auditoría de cancelación de la solicitud");

        Matricula matCanceladaRecargada = recargado.getMatriculas().buscar(matCancelada.getNumMatricula());
        comprobar(matCanceladaRecargada.getEstado() == EstadoMatricula.CANCELADA
                && matCanceladaRecargada.getMotivoCancelacion().equals("Cancelada por prueba")
                && "Personal de caja".equals(matCanceladaRecargada.getResponsableCancelacion()),
                "ida y vuelta conserva la auditoría de cancelación de la matrícula");

        java.util.ArrayList<Pago> pagosRecargados = recargado.getPagos().listar();
        Pago anuladoRecargado = null;
        for (Pago pago : pagosRecargados) {
            if (pago.getEstado() == modelo.EstadoPago.ANULADO) {
                anuladoRecargado = pago;
            }
        }
        comprobar(anuladoRecargado != null
                && anuladoRecargado.getComprobante().equals("123456")
                && anuladoRecargado.getMotivoAnulacion().equals("Error de registro")
                && "Personal de caja".equals(anuladoRecargado.getResponsableAnulacion())
                && anuladoRecargado.getFechaAnulacion().equals(base.minusHours(11)),
                "ida y vuelta conserva la auditoría de anulación y el comprobante del pago");

        modelo.Entrevista entrevistaRecargada = recargado.getEntrevistas().buscarDe(
                recargado.getSolicitudes().buscar("RT-ENT"));
        comprobar(entrevistaRecargada != null && entrevistaRecargada.isRealizada()
                && Boolean.TRUE.equals(entrevistaRecargada.getAsistioApoderado())
                && entrevistaRecargada.getPersonal().size() == 2
                && "Ana Ruiz".equals(entrevistaRecargada.getResponsable())
                && entrevistaRecargada.getResultado() == null,
                "ida y vuelta conserva la entrevista con asistencia, personal y responsable");
    }

    private static void probarVencimientoAlReabrir() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-reabrir").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen origen = new Almacen();
        Aula aula = origen.getAulas().buscar("AUL-01");

        // Habilitación de 48 h que vence con la app cerrada.
        Solicitud habilitada = solicitud(origen, aula, "RE-HAB", "70000021", "80000021", "Iván",
                base.minusDays(3));
        habilitada.habilitarParaPago(base);
        LocalDateTime vencimientoHabilitacion = habilitada.getVencimientoHabilitacion();

        // Matrícula de 72 h que vence con la app cerrada.
        Solicitud matriculaSol = solicitud(origen, aula, "RE-MAT", "70000022", "80000022", "Pablo",
                base.minusDays(3));
        matriculaSol.habilitarParaPago(base.minusHours(2));
        origen.getPagos().agregar(pagoInscripcion(matriculaSol, base.minusHours(1), base,
                origen.getCuotas()));
        matriculaSol.confirmarInscripcion(base);
        for (modelo.Documento d : matriculaSol.getExpediente().getDocumentos()) {
            d.registrarEntrega(base);
            d.validar();
        }
        matriculaSol.ingresarAColaFavorable(base);
        Matricula matricula = origen.getMatriculas().registrar(matriculaSol, base);
        LocalDateTime vencimientoMatricula = matricula.getVencimientoPagoOriginal();
        origen.grabar();

        // La app estuvo cerrada: avanzar el reloj más allá de ambos plazos y reabrir.
        reloj.avanzar(java.time.Duration.ofHours(80));
        Almacen recargado = new Almacen();
        recargado.cargar();
        Vencimientos.barrer(reloj, recargado.getAulas(), recargado.getSolicitudes(),
                recargado.getMatriculas(), recargado.getPagos(), recargado.getCuotas());

        Solicitud habilitadaRecargada = recargado.getSolicitudes().buscar("RE-HAB");
        comprobar(habilitadaRecargada.getVencimientoHabilitacionOriginal()
                .equals(vencimientoHabilitacion)
                && habilitadaRecargada.estaEnColaSinPago()
                && habilitadaRecargada.getFechaIngresoCola().equals(vencimientoHabilitacion),
                "la habilitación vencida con la app cerrada vuelve a la cola sin pago al reabrir");

        Solicitud matriculaSolRecargada = recargado.getSolicitudes().buscar("RE-MAT");
        Matricula matriculaRecargada = recargado.getMatriculas().buscar(matricula.getNumMatricula());
        comprobar(matriculaRecargada.getVencimientoPagoOriginal().equals(vencimientoMatricula)
                && matriculaRecargada.getEstado() == EstadoMatricula.CANCELADA
                && Vencimientos.RESPONSABLE.equals(matriculaRecargada.getResponsableCancelacion())
                && matriculaSolRecargada.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && matriculaSolRecargada.getFechaIngresoCola().equals(reloj.now()),
                "la matrícula vencida con la app cerrada se cancela y devuelve la solicitud al reabrir");
    }

    private static void probarRechazoSinMutacionNoCambiaArchivos() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-nograba").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen almacen = new Almacen();
        Aula aula = almacen.getAulas().buscar("AUL-01");
        Solicitud objetivo = solicitud(almacen, aula, "NG-SOL", "70000031", "80000031", "Tito",
                base.minusDays(3));
        objetivo.habilitarParaPago(base.minusHours(2));
        almacen.grabar();

        java.nio.file.Path archivoSolicitudes = directorio.toPath().resolve("solicitudes.csv");
        byte[] antes = Files.readAllBytes(archivoSolicitudes);

        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Runnable guardar = () -> {
                    try {
                        almacen.grabar();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                ui.PanelPagos panel = new ui.PanelPagos(almacen.getSolicitudes(),
                        almacen.getPagos(), almacen.getMatriculas(), almacen.getCuotas(), guardar);
                elegirEnPagos(panel, objetivo);
                // Rechazo sin mutación: monto que no coincide con la cuota vigente.
                java.lang.reflect.Method confirmar = panel.getClass().getDeclaredMethod("confirmar",
                        double.class, MedioPago.class, String.class, LocalDateTime.class,
                        String.class);
                confirmar.setAccessible(true);
                confirmar.invoke(panel, 1.0, MedioPago.EFECTIVO, "",
                        base.minusHours(1), "voucher-ng");
            } catch (java.lang.reflect.InvocationTargetException e) {
                // La excepción de dominio del rechazo viaja envuelta: es el resultado esperado.
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        comprobar(objetivo.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO,
                "un rechazo sin vencimientos no muta la solicitud");
        byte[] despues = Files.readAllBytes(archivoSolicitudes);
        comprobar(java.util.Arrays.equals(antes, despues),
                "un rechazo sin mutaciones deja los archivos sin cambios");
    }

    private static void probarRechazoConBarridoPersiste() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-graba").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen almacen = new Almacen();
        Aula aula = almacen.getAulas().buscar("AUL-01");
        Solicitud vencida = solicitud(almacen, aula, "GB-MAT", "70000041", "80000041", "Iván",
                base.minusDays(6));
        vencida.habilitarParaPago(base.minusDays(6).minusHours(2));
        almacen.getPagos().agregar(pagoInscripcion(vencida, base.minusDays(5), base.minusDays(5),
                almacen.getCuotas()));
        vencida.confirmarInscripcion(base.minusDays(5));
        for (modelo.Documento d : vencida.getExpediente().getDocumentos()) {
            d.registrarEntrega(base.minusDays(4));
            d.validar();
        }
        vencida.ingresarAColaFavorable(base.minusDays(3));
        Matricula matricula = almacen.getMatriculas().registrar(vencida, base.minusHours(72));
        almacen.grabar();

        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Runnable guardar = () -> {
                    try {
                        almacen.grabar();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                ui.PanelPagos panel = new ui.PanelPagos(almacen.getSolicitudes(),
                        almacen.getPagos(), almacen.getMatriculas(), almacen.getCuotas(), guardar);
                java.lang.reflect.Field campoMatricula = panel.getClass()
                        .getDeclaredField("matriculaActual");
                campoMatricula.setAccessible(true);
                campoMatricula.set(panel, matricula);
                java.lang.reflect.Method confirmarMatricula = panel.getClass().getDeclaredMethod(
                        "confirmarMatricula", double.class, MedioPago.class, String.class,
                        LocalDateTime.class, String.class);
                confirmarMatricula.setAccessible(true);
                confirmarMatricula.invoke(panel, almacen.getCuotas().getCuotaMatricula(),
                        MedioPago.EFECTIVO, "", base.minusMinutes(1), "voucher-gb");
            } catch (java.lang.reflect.InvocationTargetException e) {
                // El rechazo por vencimiento es el resultado esperado.
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        comprobar(matricula.getEstado() == EstadoMatricula.CANCELADA,
                "el barrido cancela la matrícula vencida antes del rechazo de la operación");
        Almacen recargado = new Almacen();
        recargado.cargar();
        Matricula recargada = recargado.getMatriculas().buscar(matricula.getNumMatricula());
        comprobar(recargada.getEstado() == EstadoMatricula.CANCELADA
                && Vencimientos.MOTIVO_VENCIMIENTO.equals(recargada.getMotivoCancelacion())
                && Vencimientos.RESPONSABLE.equals(recargada.getResponsableCancelacion()),
                "el rechazo con barrido persiste la cancelación aplicada por el barrido");
    }

    private static void escribirLineas(File directorio, String nombre, String... lineas)
            throws IOException {
        Files.write(directorio.toPath().resolve(nombre), java.util.Arrays.asList(lineas),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void escribirBasePersistencia(File directorio) throws IOException {
        escribirLineas(directorio, "apoderados.csv",
                EscrituraCsv.unir("80000001", "Apo", "Uno", "999111222"));
        escribirLineas(directorio, "alumnos.csv",
                EscrituraCsv.unir("202010001", "70000001", "Alumno", "Uno", "2022-06-18", ""));
        escribirLineas(directorio, "vinculos.csv",
                EscrituraCsv.unir("202010001", "80000001", "true"));
    }

    private static void escribirSolicitudSimple(File directorio) throws IOException {
        escribirLineas(directorio, "solicitudes.csv",
                EscrituraCsv.unir("SOL-1001", "202010001", "AUL-01", "2026-10-01T10:00",
                        "EN_ESPERA_SIN_PAGO", "", "", "", "", "", "", "", "", "0"));
    }

    private static void escribirSolicitudConExpediente(File directorio) throws IOException {
        escribirLineas(directorio, "solicitudes.csv",
                EscrituraCsv.unir("SOL-1001", "202010001", "AUL-01", "2026-10-01T10:00",
                        "EN_DOCUMENTACION", "", "", "", "", "", "", "", "2026-10-01", "0"));
    }

    private static void esperarIoException(String fragmento, AccionFalla accion,
            String descripcion) throws Exception {
        try {
            accion.ejecutar();
        } catch (IOException e) {
            comprobar(e.getMessage() != null && e.getMessage().contains(fragmento),
                    descripcion + ": mensaje inesperado: " + e.getMessage());
            return;
        }
        throw new AssertionError(descripcion + ": se esperaba una IOException");
    }

    private static void probarCargaRechazaMatriculaColgante() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-mat-colg").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        escribirBasePersistencia(directorio);
        escribirSolicitudSimple(directorio);
        escribirLineas(directorio, "matriculas.csv",
                EscrituraCsv.unir("100001", "SOL-1001", "PENDIENTE_PAGO",
                        "2026-10-01T10:00", "2026-10-04T10:00", "", "", ""));
        escribirLineas(directorio, "pagos.csv",
                EscrituraCsv.unir("SOL-1001", "MATRICULA", "999999", "850.0", "EFECTIVO", "",
                        "2026-10-02T10:00", "2026-10-02T11:00", "comprobante.png", "",
                        "CONFIRMADO", "", "", ""));
        esperarIoException("pagos.csv", () -> new Almacen().cargar(),
                "numMatricula que no resuelve rechaza la carga");
    }

    private static void probarCargaRechazaMatriculaSinNumero() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-mat-sinnum").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        escribirBasePersistencia(directorio);
        escribirSolicitudSimple(directorio);
        escribirLineas(directorio, "pagos.csv",
                EscrituraCsv.unir("SOL-1001", "MATRICULA", "", "850.0", "EFECTIVO", "",
                        "2026-10-02T10:00", "2026-10-02T11:00", "comprobante.png", "",
                        "CONFIRMADO", "", "", ""));
        esperarIoException("pagos.csv", () -> new Almacen().cargar(),
                "MATRICULA sin numMatricula rechaza la carga");
    }

    private static void probarCargaRechazaOfertaHuerfana() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-of-huerf").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        escribirBasePersistencia(directorio);
        escribirSolicitudSimple(directorio);
        escribirLineas(directorio, "ofertas.csv",
                EscrituraCsv.unir("SOL-9999", "2026-10-02T10:00", "Personal", "true"));
        esperarIoException("ofertas.csv", () -> new Almacen().cargar(),
                "oferta con solicitud inexistente rechaza la carga");
    }

    private static void probarCargaRechazaEntrevistaDuplicada() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-ent-dup").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        escribirBasePersistencia(directorio);
        escribirSolicitudConExpediente(directorio);
        String fila = EscrituraCsv.unir("SOL-1001", "2026-10-02T10:00", "Ana", "Ana", "",
                "false", "0", "");
        escribirLineas(directorio, "entrevistas.csv", fila, fila);
        esperarIoException("entrevistas.csv", () -> new Almacen().cargar(),
                "entrevista duplicada por solicitud rechaza la carga");
    }

    private static void probarCargaRechazaDocumentoDuplicado() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-doc-dup").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        escribirBasePersistencia(directorio);
        escribirSolicitudConExpediente(directorio);
        String fila = EscrituraCsv.unir("SOL-1001", "DNI_ALUMNO", "", "", "", "", "");
        escribirLineas(directorio, "documentos.csv", fila, fila);
        esperarIoException("documentos.csv", () -> new Almacen().cargar(),
                "documento duplicado por (solicitud, tipo) rechaza la carga");
    }

    private static void probarArranqueGrabaBarrido() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-arranque").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen origen = new Almacen();
        Aula aula = origen.getAulas().buscar("AUL-01");

        // Habilitación de 48 h que vence con la app cerrada.
        Solicitud habilitada = solicitud(origen, aula, "ARR-HAB", "70000101", "80000101", "Noa",
                base.minusDays(3));
        habilitada.habilitarParaPago(base);

        // Matrícula de 72 h que vence con la app cerrada.
        Solicitud matriculaSol = solicitud(origen, aula, "ARR-MAT", "70000102", "80000102", "Iván",
                base.minusDays(3));
        matriculaSol.habilitarParaPago(base.minusHours(2));
        origen.getPagos().agregar(pagoInscripcion(matriculaSol, base.minusHours(1), base,
                origen.getCuotas()));
        matriculaSol.confirmarInscripcion(base);
        for (modelo.Documento d : matriculaSol.getExpediente().getDocumentos()) {
            d.registrarEntrega(base);
            d.validar();
        }
        matriculaSol.ingresarAColaFavorable(base);
        Matricula matricula = origen.getMatriculas().registrar(matriculaSol, base);
        LocalDateTime vencimientoMatricula = matricula.getVencimientoPagoOriginal();
        origen.grabar();

        // La app estuvo cerrada: avanzar el reloj más allá de ambos plazos.
        reloj.avanzar(java.time.Duration.ofHours(80));

        // El arranque carga y luego barre y graba (lo que hace PrincipalUI al abrir).
        Almacen recargado = new Almacen();
        recargado.cargar();
        PrincipalUI.barrerYGrabar(recargado);

        // Reabrir en objetos nuevos: el barrido quedó grabado en los archivos.
        Almacen reabierto = new Almacen();
        reabierto.cargar();
        Solicitud habilitadaRecargada = reabierto.getSolicitudes().buscar("ARR-HAB");
        comprobar(habilitadaRecargada != null && habilitadaRecargada.estaEnColaSinPago(),
                "el arranque graba el vencimiento de la habilitación");
        Solicitud solRecargada = reabierto.getSolicitudes().buscar("ARR-MAT");
        Matricula matriculaRecargada = reabierto.getMatriculas().buscar(matricula.getNumMatricula());
        comprobar(matriculaRecargada.getVencimientoPagoOriginal().equals(vencimientoMatricula)
                && matriculaRecargada.getEstado() == EstadoMatricula.CANCELADA
                && Vencimientos.RESPONSABLE.equals(matriculaRecargada.getResponsableCancelacion())
                && solRecargada.getEstado() == EstadoSolicitud.EN_ESPERA_FAVORABLE
                && solRecargada.getFechaIngresoCola().equals(reloj.now()),
                "el arranque graba la cancelación de la matrícula vencida y su reingreso");
    }

    private static void probarPanelSolicitudesGrabaBarrido() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-panel-solicitudes").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen origen = new Almacen();
        Aula aula = origen.getAulas().buscar("AUL-01");
        Solicitud matriculaSol = solicitud(origen, aula, "SOL-MAT", "70000201", "80000201", "Pablo",
                base.minusDays(3));
        matriculaSol.habilitarParaPago(base.minusHours(2));
        origen.getPagos().agregar(pagoInscripcion(matriculaSol, base.minusHours(1), base,
                origen.getCuotas()));
        matriculaSol.confirmarInscripcion(base);
        for (modelo.Documento d : matriculaSol.getExpediente().getDocumentos()) {
            d.registrarEntrega(base);
            d.validar();
        }
        matriculaSol.ingresarAColaFavorable(base);
        Matricula matricula = origen.getMatriculas().registrar(matriculaSol, base);
        origen.grabar();

        reloj.avanzar(java.time.Duration.ofHours(80));

        Almacen almacen = new Almacen();
        almacen.cargar();
        Matricula cargada = almacen.getMatriculas().buscar(matricula.getNumMatricula());

        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Runnable guardar = () -> {
                    try {
                        almacen.grabar();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                // El constructor (borde no-tecla) barre y graba el resultado.
                new ui.PanelSolicitudes(almacen.getAulas(), almacen.getSolicitudes(),
                        almacen.getAlumnos(), almacen.getApoderados(), almacen.getMatriculas(),
                        guardar);
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        comprobar(cargada.getEstado() == EstadoMatricula.CANCELADA,
                "el panel barre la matrícula vencida al construirse");
        Almacen reabierto = new Almacen();
        reabierto.cargar();
        Matricula persistida = reabierto.getMatriculas().buscar(matricula.getNumMatricula());
        comprobar(persistida.getEstado() == EstadoMatricula.CANCELADA
                && Vencimientos.RESPONSABLE.equals(persistida.getResponsableCancelacion()),
                "el panel graba la cancelación del barrido");
    }

    private static void probarPanelColasGrabaBarrido() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-panel-colas").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen origen = new Almacen();
        Aula aula = origen.getAulas().buscar("AUL-01");
        Solicitud matriculaSol = solicitud(origen, aula, "COL-MAT", "70000301", "80000301", "Rosa",
                base.minusDays(3));
        matriculaSol.habilitarParaPago(base.minusHours(2));
        origen.getPagos().agregar(pagoInscripcion(matriculaSol, base.minusHours(1), base,
                origen.getCuotas()));
        matriculaSol.confirmarInscripcion(base);
        for (modelo.Documento d : matriculaSol.getExpediente().getDocumentos()) {
            d.registrarEntrega(base);
            d.validar();
        }
        matriculaSol.ingresarAColaFavorable(base);
        Matricula matricula = origen.getMatriculas().registrar(matriculaSol, base);
        origen.grabar();

        reloj.avanzar(java.time.Duration.ofHours(80));

        Almacen almacen = new Almacen();
        almacen.cargar();
        Matricula cargada = almacen.getMatriculas().buscar(matricula.getNumMatricula());

        final RuntimeException[] failure = new RuntimeException[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Runnable guardar = () -> {
                    try {
                        almacen.grabar();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                ui.PanelColas panel = new ui.PanelColas(almacen.getAulas(),
                        almacen.getSolicitudes(), almacen.getEntrevistas(),
                        almacen.getMatriculas(), guardar);
                // componentShown ejecuta actualizar() → revisarTurnos(): barrido + grabado.
                java.lang.reflect.Method actualizar = panel.getClass().getDeclaredMethod("actualizar");
                actualizar.setAccessible(true);
                actualizar.invoke(panel);
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        comprobar(cargada.getEstado() == EstadoMatricula.CANCELADA,
                "el panel de colas barre la matrícula vencida al actualizarse");
        Almacen reabierto = new Almacen();
        reabierto.cargar();
        Matricula persistida = reabierto.getMatriculas().buscar(matricula.getNumMatricula());
        comprobar(persistida.getEstado() == EstadoMatricula.CANCELADA
                && Vencimientos.RESPONSABLE.equals(persistida.getResponsableCancelacion()),
                "el panel de colas graba la cancelación del barrido");
    }

    private static void probarRecalcularSinVencimientosNoEscribe() throws Exception {
        File directorio = Files.createTempDirectory("casaamarilla-norecalcular").toFile();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
        Reloj reloj = Reloj.porDefecto();
        LocalDateTime base = LocalDateTime.of(2026, 10, 8, 10, 0);
        reloj.fijar(base);

        Almacen origen = new Almacen();
        Aula aula = origen.getAulas().buscar("AUL-01");
        // Habilitación reciente que no vence: el barrido no muta nada.
        Solicitud fresca = solicitud(origen, aula, "SIN-VENC", "70000401", "80000401", "Lucía",
                base.minusDays(1));
        fresca.habilitarParaPago(base.minusHours(1));
        origen.grabar();

        Almacen almacen = new Almacen();
        almacen.cargar();
        java.nio.file.Path archivoSolicitudes = directorio.toPath().resolve("solicitudes.csv");

        final RuntimeException[] failure = new RuntimeException[1];
        final ui.PanelSolicitudes[] panelRef = new ui.PanelSolicitudes[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Runnable guardar = () -> {
                    try {
                        almacen.grabar();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                ui.PanelSolicitudes panel = new ui.PanelSolicitudes(almacen.getAulas(),
                        almacen.getSolicitudes(), almacen.getAlumnos(), almacen.getApoderados(),
                        almacen.getMatriculas(), guardar);
                panelRef[0] = panel;
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        // El constructor (borde no-tecla) ya grabó: esa es la línea base.
        byte[] antes = Files.readAllBytes(archivoSolicitudes);

        SwingUtilities.invokeAndWait(() -> {
            try {
                // Camino de tecla: recalcular no barre ni graba.
                java.lang.reflect.Method recalcular = panelRef[0].getClass().getDeclaredMethod(
                        "recalcular");
                recalcular.setAccessible(true);
                recalcular.invoke(panelRef[0]);
            } catch (Exception e) {
                failure[0] = new RuntimeException(e);
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        byte[] despues = Files.readAllBytes(archivoSolicitudes);
        comprobar(java.util.Arrays.equals(antes, despues),
                "una tecla sin vencimientos no reescribe los archivos");
    }

    private static Solicitud solicitud(Almacen almacen, Aula aula, String codigo,
            String dniAlumno, String dniApo, String nombres, LocalDateTime registro)
            throws Exception {
        Apoderado apo = almacen.getApoderados().registrar(dniApo, "Apoderado de", nombres,
                "999111222");
        Alumno alumno = almacen.getAlumnos().registrar(dniAlumno, nombres, "Apellido",
                LocalDate.of(2022, 6, 18), null);
        alumno.agregarApoderado(apo, true);
        Solicitud solicitud = new Solicitud(codigo, alumno, aula, registro);
        @SuppressWarnings("unchecked")
        java.util.ArrayList<Solicitud> lista =
                (java.util.ArrayList<Solicitud>) campoPrivado(almacen.getSolicitudes(),
                        "solicitudes");
        lista.add(solicitud);
        return solicitud;
    }

    private static Pago pagoInscripcion(Solicitud solicitud, LocalDateTime operacion,
            LocalDateTime registro, ConfiguracionCuotas cuotas) throws Exception {
        Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null,
                cuotas.getCuotaInscripcion(), MedioPago.EFECTIVO, "", operacion, registro,
                "comprobante-" + solicitud.getCodigo() + ".png", cuotas);
        pago.confirmar();
        return pago;
    }

    private static Pago pagoMatriculaConfirmado(Solicitud solicitud, Matricula matricula,
            LocalDateTime operacion, LocalDateTime registro, ConfiguracionCuotas cuotas)
            throws Exception {
        Pago pago = new Pago(solicitud, ConceptoPago.MATRICULA, matricula,
                cuotas.getCuotaMatricula(), MedioPago.YAPE, "YP-1234567", operacion, registro,
                "comprobante-matricula.png", cuotas);
        pago.confirmar();
        return pago;
    }

    private static boolean contieneProximo(java.util.ArrayList<Vencimientos.Proximo> proximos,
            String codigo, String tipo) {
        for (Vencimientos.Proximo proximo : proximos) {
            if (codigo.equals(proximo.getCodigoSolicitud()) && tipo.equals(proximo.getTipo())) {
                return true;
            }
        }
        return false;
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

    /** Devuelve el caso que abre el modo "expediente" (Noa, ejemplo del diseño 03). */
    private static Solicitud cargarCasos(PrincipalUI ventana) throws ReglaDominioException {
        ArregloAlumnos alumnos = ventana.getAlumnos();
        ArregloApoderados apoderados = ventana.getApoderados();
        Aula girasoles = ventana.getAulas().buscar("AUL-01");
        Aula tulipanes = ventana.getAulas().buscar("AUL-02");
        LocalDate cuatroAnios = LocalDate.of(2022, 6, 18);
        LocalDate tresAnios = LocalDate.of(2023, 5, 10);
        LocalDateTime ahora = Reloj.porDefecto().now();

        Solicitud vigente = registrar(ventana, alumnos, apoderados, "Mía", "León",
                cuatroAnios, girasoles);
        vigente.habilitarParaPago(ahora.minusHours(2));

        Solicitud otraVigente = registrar(ventana, alumnos, apoderados, "Sofía", "Paz",
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
                MedioPago.YAPE, "YP-8291457", Reloj.porDefecto().now(), comprobante,
                Reloj.porDefecto().now(), ventana.getSolicitudes(), ventana.getMatriculas(),
                ventana.getPagos(), ventana.getCuotas());

        Solicitud vencida = registrar(ventana, alumnos, apoderados, "Leo", "Silva",
                cuatroAnios, girasoles);
        vencida.habilitarParaPago(ahora.minusHours(50));

        Solicitud documentalVencida = registrar(ventana, alumnos, apoderados, "Ana", "Torres",
                cuatroAnios, girasoles);
        LocalDateTime confirmacionDocumental = ahora.minusDays(9);
        documentalVencida.habilitarParaPago(confirmacionDocumental.minusHours(2));
        documentalVencida.confirmarInscripcion(confirmacionDocumental);

        System.out.println("Casos de prueba de Pagos:");
        System.out.println("  " + vigente.getCodigo() + "  habilitada hace 2 h (pagar)");
        System.out.println("  " + otraVigente.getCodigo() + "  habilitada hace 47 h (vence en 1 h)");
        System.out.println("  " + vencida.getCodigo() + "  habilitación vencida");
        System.out.println("  " + documentalVencida.getCodigo() + "  documentación vencida (barrido)");
        System.out.println("  " + enCola.getCodigo() + "  en cola sin pago (no habilitada)");
        System.out.println("  " + pagada.getCodigo() + "  inscripción ya pagada (solo lectura)");
        System.out.println("  SOL-9999  no existe");
        System.out.println("Comprobante de ejemplo para adjuntar: " + comprobante);
        System.out.println("Las solicitudes se registran al abrir: la fecha de operación (con minutos)"
                + " recién es válida desde el minuto siguiente.");

        Solicitud casoExpediente = cargarCasosExpediente(ventana, alumnos, apoderados, tulipanes,
                girasoles);
        cargarCasosMatricula(ventana, alumnos, apoderados, girasoles);
        return casoExpediente;
    }

    private static void cargarCasosMatricula(PrincipalUI ventana, ArregloAlumnos alumnos,
            ArregloApoderados apoderados, Aula girasoles) throws ReglaDominioException {
        LocalDate cuatroAnios = LocalDate.of(2022, 6, 18);
        LocalDateTime ahora = LocalDateTime.now().withSecond(0).withNano(0);

        Solicitud pendiente = enColaFavorable(ventana, alumnos, apoderados, "Rosa", "León",
                cuatroAnios, girasoles, ahora.minusDays(3));
        Turnos.confirmarOferta(pendiente, true, "Personal de admisión", ahora.minusHours(20),
                ventana.getSolicitudes(), ventana.getMatriculas());

        Solicitud activa = enColaFavorable(ventana, alumnos, apoderados, "Pablo", "Quispe",
                cuatroAnios, girasoles, ahora.minusDays(3).plusMinutes(1));
        Turnos.confirmarOferta(activa, true, "Personal de admisión", ahora.minusHours(10),
                ventana.getSolicitudes(), ventana.getMatriculas());
        // La solicitud se registró recién: la operación no puede ser anterior a ese momento.
        LocalDateTime pago = LocalDateTime.now();
        Cobros.confirmarMatricula(ventana.getMatriculas().vigenteDe(activa),
                ventana.getCuotas().getCuotaMatricula(), MedioPago.YAPE, "YP-8291458", pago,
                crearComprobanteDeEjemplo(), pago, ventana.getSolicitudes(),
                ventana.getMatriculas(), ventana.getPagos(), ventana.getCuotas());

        Solicitud vencida = enColaFavorable(ventana, alumnos, apoderados, "Iván", "Salas",
                cuatroAnios, girasoles, ahora.minusDays(4));
        Turnos.confirmarOferta(vencida, true, "Personal de admisión", ahora.minusHours(80),
                ventana.getSolicitudes(), ventana.getMatriculas());
        // PrincipalUI revisó los vencimientos al abrir, antes de que existieran estos casos;
        // se repite la revisión para que la demo arranque como si ya estuvieran guardados.
        ventana.revisarVencimientos();

        System.out.println("Casos de matrícula (Pagos y matrículas):");
        System.out.println("  " + pendiente.getCodigo() + " / "
                + ventana.getMatriculas().ultimaDe(pendiente).getCodigo()
                + "  PENDIENTE_PAGO · vence en 52 h (diseño 05)");
        System.out.println("  " + activa.getCodigo() + " / "
                + ventana.getMatriculas().ultimaDe(activa).getCodigo()
                + "  ACTIVA · pago confirmado (solo lectura y anulación)");
        System.out.println("  " + vencida.getCodigo() + " / "
                + ventana.getMatriculas().ultimaDe(vencida).getCodigo()
                + "  CANCELADA por vencimiento de 72 h (barrido al abrir)");
    }

    private static Solicitud enColaFavorable(PrincipalUI ventana, ArregloAlumnos alumnos,
            ArregloApoderados apoderados, String nombres, String apellidos, LocalDate nacimiento,
            Aula aula, LocalDateTime ingreso) throws ReglaDominioException {
        Solicitud solicitud = registrar(ventana, alumnos, apoderados, nombres, apellidos,
                nacimiento, aula);
        LocalDateTime confirmacion = ingreso.minusDays(2);
        solicitud.habilitarParaPago(confirmacion.minusHours(2));
        solicitud.confirmarInscripcion(confirmacion);
        for (modelo.Documento documento : solicitud.getExpediente().getDocumentos()) {
            documento.registrarEntrega(confirmacion.plusDays(1));
            documento.validar();
        }
        solicitud.ingresarAColaFavorable(ingreso);
        return solicitud;
    }

    private static void elegirEnPagos(ui.PanelPagos panel, Solicitud solicitud)
            throws Exception {
        java.lang.reflect.Method recargar = panel.getClass().getDeclaredMethod("recargarLista");
        recargar.setAccessible(true);
        recargar.invoke(panel);
        ((javax.swing.JComboBox<?>) campoPrivado(campoPrivado(panel, "selector"), "combo"))
                .setSelectedItem(solicitud);
    }

    private static void seleccionarEnExpediente(PrincipalUI ventana, Solicitud solicitud) {
        ui.PanelExpediente panel = buscarComponente(ventana.getContentPane(),
                ui.PanelExpediente.class);
        try {
            ((javax.swing.JComboBox<?>) campoPrivado(campoPrivado(panel, "selector"), "combo"))
                    .setSelectedItem(solicitud);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo seleccionar " + solicitud.getCodigo()
                    + ".", e);
        }
    }

    private static <T> T buscarComponente(java.awt.Container raiz, Class<T> tipo) {
        for (java.awt.Component hijo : raiz.getComponents()) {
            if (tipo.isInstance(hijo)) {
                return tipo.cast(hijo);
            }
            if (hijo instanceof java.awt.Container) {
                T encontrado = buscarComponente((java.awt.Container) hijo, tipo);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private static Solicitud cargarCasosExpediente(PrincipalUI ventana, ArregloAlumnos alumnos,
            ArregloApoderados apoderados, Aula tulipanes, Aula girasoles)
            throws ReglaDominioException {
        LocalDate tresAnios = LocalDate.of(2023, 5, 10);
        LocalDate cuatroAnios = LocalDate.of(2022, 6, 18);
        LocalDateTime ahora = Reloj.porDefecto().now().withSecond(0).withNano(0);
        Alumno alumnaNoa = alumnos.registrar(String.valueOf(siguienteDni++), "Noa", "Ruiz",
                tresAnios, null);
        alumnaNoa.agregarApoderado(apoderados.registrar(String.valueOf(siguienteDni++), "Ana",
                "Ruiz", "999111222"), true);
        Solicitud noa = ventana.getSolicitudes().registrar(alumnaNoa, tulipanes);
        LocalDateTime confirmacion = ahora.minusDays(3);
        noa.habilitarParaPago(confirmacion.minusHours(2));
        noa.confirmarInscripcion(confirmacion);
        for (modelo.Documento documento : noa.getExpediente().getDocumentos()) {
            documento.registrarEntrega(confirmacion.plusDays(1));
            if (documento.getTipo() == modelo.TipoDocumento.CARNE_VACUNAS) {
                documento.observar("Falta página de refuerzo",
                        confirmacion.plusDays(2));
            } else {
                documento.validar();
            }
        }

        Solicitud mateo = registrar(ventana, alumnos, apoderados, "Mateo", "Rojas",
                cuatroAnios, girasoles);
        mateo.habilitarParaPago(confirmacion.minusHours(2));
        mateo.confirmarInscripcion(confirmacion);
        for (modelo.Documento documento : mateo.getExpediente().getDocumentos()) {
            documento.registrarEntrega(confirmacion.plusDays(1));
            documento.validar();
        }
        java.util.ArrayList<String> personal = new java.util.ArrayList<String>();
        personal.add("Ana Ruiz");
        personal.add("Luis Ramos");
        ventana.getEntrevistas().agendar(mateo, mateo.getExpediente(), ahora.plusDays(1),
                personal, "Ana Ruiz");

        System.out.println("Casos del expediente:");
        System.out.println("  " + noa.getCodigo() + "  3 de 4 validados · carné OBSERVADO ·"
                + " entrevista BLOQUEADA (ejemplo del diseño 03)");
        System.out.println("  " + mateo.getCodigo() + "  4 validados · entrevista agendada"
                + " (asistencia, inasistencias y resultado)");
        return noa;
    }

    // Apunta la persistencia a un directorio temporal para que la demo no toque datos/ real.
    private static void apuntarADirectorioTemporal() {
        File directorio = new File(System.getProperty("java.io.tmpdir"),
                "casaamarilla-demo-" + System.nanoTime());
        directorio.mkdirs();
        System.setProperty("casaamarilla.datos", directorio.getAbsolutePath());
    }

    private static String crearComprobanteDeEjemplo() {        try {
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
