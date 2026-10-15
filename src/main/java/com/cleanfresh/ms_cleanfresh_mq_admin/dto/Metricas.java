package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

import java.util.List;

/** Mensajes por cola y por DLQ; {@code alertas} trae una línea por cada DLQ que no está vacía. */
public record Metricas(List<ColaMetrica> colas, long mensajesEnDlq, boolean hayAlerta, List<String> alertas) {
}
