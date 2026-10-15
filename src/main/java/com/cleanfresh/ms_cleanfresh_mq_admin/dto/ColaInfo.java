package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

/** Una cola con su cola de mensajes muertos (si tiene) y el estado de su espejo en el otro nodo. */
public record ColaInfo(String nombre, boolean durable, boolean dlq, String exchangeMuertos, String claveMuertos,
                       long mensajes, int consumidores, String nodo, int espejos) {
}
