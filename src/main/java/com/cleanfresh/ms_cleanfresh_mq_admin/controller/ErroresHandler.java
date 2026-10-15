package com.cleanfresh.ms_cleanfresh_mq_admin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Traduce los fallos de RabbitMQ a respuestas claras: lo que el broker rechaza por culpa de la solicitud
 * (cola inexistente, argumentos distintos a los de una cola ya creada...) sale con su mismo código;
 * si el broker no responde, 503.
 */
@RestControllerAdvice
public class ErroresHandler {

    private static final Logger log = LoggerFactory.getLogger(ErroresHandler.class);

    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail respuestaDelBroker(RestClientResponseException e) {
        HttpStatusCode codigo = e.getStatusCode();
        if (codigo.is5xxServerError()) {
            log.error("RabbitMQ respondió {}: {}", codigo.value(), e.getResponseBodyAsString());
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "RabbitMQ respondió con un error");
        }
        // 404: no existe; 400/406: el broker rechazó la solicitud (p. ej. argumentos distintos)
        String detalle = e.getStatusCode().value() == 404 ? "No existe en RabbitMQ"
                : "RabbitMQ rechazó la solicitud: " + e.getResponseBodyAsString();
        return ProblemDetail.forStatusAndDetail(codigo, detalle);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail brokerCaido(ResourceAccessException e) {
        log.error("No se pudo hablar con RabbitMQ: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo conectar con ningún nodo de RabbitMQ");
    }
}
