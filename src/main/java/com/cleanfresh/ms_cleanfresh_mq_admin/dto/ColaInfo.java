package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

/**
 * Una cola con su DLX efectivo (viene de su política, como en la guía del profesor; si la cola lo trae como argumento
 * también se muestra), la política que la gobierna y el estado de su espejo en el otro nodo.
 */
public record ColaInfo(String nombre, boolean durable, boolean dlq, String exchangeMuertos, String claveMuertos,
                       long mensajes, int consumidores, String nodo, int espejos, String politica) {
}
