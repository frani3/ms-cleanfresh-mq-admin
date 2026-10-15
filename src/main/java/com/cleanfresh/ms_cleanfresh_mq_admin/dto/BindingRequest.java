package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record BindingRequest(
        @Schema(example = "cmd.topic") String exchange,
        @Schema(example = "q.cmd.email") String cola,
        @Schema(example = "email.send.*") String clave) {
}
