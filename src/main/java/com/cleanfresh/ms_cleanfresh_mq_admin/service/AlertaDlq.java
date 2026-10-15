package com.cleanfresh.ms_cleanfresh_mq_admin.service;

import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Metricas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vigila las DLQ por su cuenta (Spec 033): cada cierto tiempo mide las colas y deja una advertencia en
 * el log cuando una DLQ pasa a tener mensajes. Avisa cuando <b>cambia</b> la situación (aparece una
 * alerta, cambia su contenido o se resuelve) para no repetir la misma línea cada intervalo.
 */
@Component
@ConditionalOnProperty(name = "mq.alertas-activas", havingValue = "true", matchIfMissing = true)
public class AlertaDlq {

    private static final Logger log = LoggerFactory.getLogger(AlertaDlq.class);

    private final MqAdminService servicio;
    private List<String> ultimas = List.of();

    public AlertaDlq(MqAdminService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(initialDelayString = "${mq.alertas-intervalo-ms:30000}", fixedDelayString = "${mq.alertas-intervalo-ms:30000}")
    public void revisar() {
        try {
            Metricas metricas = servicio.medir();
            evaluar(metricas.alertas());
        } catch (RuntimeException e) {
            // sin broker no hay nada que medir; se reintenta en el siguiente intervalo
            log.warn("No se pudieron revisar las DLQ: {}", e.getMessage());
        }
    }

    // Package-private para probarlo sin broker.
    void evaluar(List<String> alertas) {
        if (alertas.equals(ultimas)) {
            return;
        }
        if (alertas.isEmpty()) {
            log.info("Las DLQ volvieron a estar vacías");
        } else {
            alertas.forEach(a -> log.warn("ALERTA DLQ (revisión periódica): {}", a));
        }
        ultimas = alertas;
    }
}
