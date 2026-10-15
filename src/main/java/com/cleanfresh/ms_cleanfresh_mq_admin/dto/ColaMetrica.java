package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

public record ColaMetrica(String nombre, boolean dlq, long mensajes, long listos, long sinConfirmar, int consumidores) {
}
