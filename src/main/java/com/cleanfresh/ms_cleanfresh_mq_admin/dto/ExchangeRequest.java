package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExchangeRequest(@Schema(description = "direct o topic", example = "topic") String tipo) {
}
