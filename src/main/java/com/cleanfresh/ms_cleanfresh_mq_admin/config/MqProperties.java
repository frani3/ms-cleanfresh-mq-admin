package com.cleanfresh.ms_cleanfresh_mq_admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Conexión a la API de administración de RabbitMQ (la del panel, puertos 15672/15673). */
@ConfigurationProperties(prefix = "mq")
public record MqProperties(List<String> apiUrls, String user, String password, String vhost) {

    public MqProperties {
        if (apiUrls == null || apiUrls.isEmpty()) {
            throw new IllegalArgumentException("mq.api-urls es obligatorio");
        }
        vhost = vhost == null || vhost.isBlank() ? "/" : vhost;
    }
}
