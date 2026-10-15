package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo opcional al crear una cola. */
public record ColaRequest(
        @Schema(description = "Crear también la cola nombre.dlq, enlazada a cmd.dead.dlx (por defecto true)")
        Boolean conDlq,
        @Schema(description = "Clave con la que la cola manda a su DLQ (por defecto, el nombre de la cola)")
        String claveDlq) {
}
