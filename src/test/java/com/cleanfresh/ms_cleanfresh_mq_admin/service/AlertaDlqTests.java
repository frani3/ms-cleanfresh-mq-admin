package com.cleanfresh.ms_cleanfresh_mq_admin.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Metricas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La revisión periódica avisa cuando cambia la situación de las DLQ, no en cada vuelta. */
class AlertaDlqTests {

    private final MqAdminService servicio = mock(MqAdminService.class);
    private final AlertaDlq alerta = new AlertaDlq(servicio);
    private final ListAppender<ILoggingEvent> registro = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertaDlq.class);

    @BeforeEach
    void escuchar() {
        registro.start();
        logger.addAppender(registro);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(registro);
    }

    private static Metricas con(String... alertas) {
        return new Metricas(List.of(), alertas.length, alertas.length > 0, List.of(alertas));
    }

    private long advertencias() {
        return registro.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    @Test
    void conLasDlqVaciasNoDiceNada() {
        when(servicio.medir()).thenReturn(con());

        alerta.revisar();
        alerta.revisar();

        assertEquals(0, registro.list.size());
    }

    @Test
    void avisaUnaVezCuandoApareceUnaAlertaYNoLaRepiteMientrasIgual() {
        when(servicio.medir()).thenReturn(con("La DLQ q.cmd.email.dlq tiene 1 mensaje(s) sin atender"));

        alerta.revisar();
        alerta.revisar();
        alerta.revisar();

        assertEquals(1, advertencias());
        assertEquals(true, registro.list.get(0).getFormattedMessage().contains("q.cmd.email.dlq"));
    }

    @Test
    void vuelveAAvisarSiCambiaElContenidoDeLaDlq() {
        when(servicio.medir()).thenReturn(con("La DLQ q.cmd.email.dlq tiene 1 mensaje(s) sin atender"),
                con("La DLQ q.cmd.email.dlq tiene 2 mensaje(s) sin atender"));

        alerta.revisar();
        alerta.revisar();

        assertEquals(2, advertencias());
    }

    @Test
    void avisaCuandoLaSituacionSeResuelve() {
        when(servicio.medir()).thenReturn(con("La DLQ q.cmd.email.dlq tiene 1 mensaje(s) sin atender"), con());

        alerta.revisar();
        alerta.revisar();

        assertEquals(1, advertencias());
        assertEquals(Level.INFO, registro.list.get(1).getLevel());
        assertEquals(true, registro.list.get(1).getFormattedMessage().contains("vacías"));
    }

    @Test
    void sinBrokerNoSeCaeYSeReintentaDespues() {
        when(servicio.medir()).thenThrow(new ResourceAccessException("Connection refused"))
                .thenReturn(con("La DLQ q.cmd.invoice.dlq tiene 1 mensaje(s) sin atender"));

        assertDoesNotThrow(alerta::revisar);
        alerta.revisar();

        // 1 aviso de "no se pudo revisar" y 1 alerta de DLQ
        assertEquals(2, advertencias());
    }
}
