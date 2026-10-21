package com.cleanfresh.ms_cleanfresh_mq_admin.controller;

import com.cleanfresh.ms_cleanfresh_mq_admin.dto.BindingRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ColaRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ExchangeRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Metricas;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Resultado;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Topologia;
import com.cleanfresh.ms_cleanfresh_mq_admin.service.MqAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mq")
@Tag(name = "Administrador de RabbitMQ", description = "Colas, exchanges, bindings y métricas del clúster. Solo se llega por el BFF, con rol Admin.")
public class MqAdminController {

    private final MqAdminService servicio;

    public MqAdminController(MqAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/topologia")
    @Operation(summary = "Lista los nodos, exchanges, colas y bindings del clúster")
    public Topologia topologia() {
        return servicio.topologia();
    }

    @PostMapping("/queues/{nombre}")
    @Operation(summary = "Crea una cola durable sin argumentos, por defecto con su DLQ (nombre.dlq) enlazada a cmd.dead.dlx y su política de DLX (409 si ya existe)")
    public Resultado crearCola(@PathVariable String nombre, @RequestBody(required = false) ColaRequest solicitud) {
        return servicio.crearCola(nombre, solicitud);
    }

    @DeleteMapping("/queues/{nombre}")
    @Operation(summary = "Elimina una cola (y su DLQ si conDlq=true)")
    public Resultado eliminarCola(@PathVariable String nombre, @RequestParam(defaultValue = "false") boolean conDlq) {
        return servicio.eliminarCola(nombre, conDlq);
    }

    @PostMapping("/exchanges/{nombre}")
    @Operation(summary = "Crea un exchange durable de tipo direct o topic")
    public Resultado crearExchange(@PathVariable String nombre, @RequestBody ExchangeRequest solicitud) {
        return servicio.crearExchange(nombre, solicitud == null ? null : solicitud.tipo());
    }

    @DeleteMapping("/exchanges/{nombre}")
    @Operation(summary = "Elimina un exchange")
    public Resultado eliminarExchange(@PathVariable String nombre) {
        return servicio.eliminarExchange(nombre);
    }

    @PostMapping("/bindings")
    @Operation(summary = "Crea un binding exchange -> cola con una clave de ruteo")
    public Resultado crearBinding(@RequestBody BindingRequest solicitud) {
        return servicio.crearBinding(solicitud);
    }

    @DeleteMapping("/bindings")
    @Operation(summary = "Elimina un binding exchange -> cola con esa clave")
    public Resultado eliminarBinding(@RequestBody BindingRequest solicitud) {
        return servicio.eliminarBinding(solicitud);
    }

    @PostMapping("/topologia/base")
    @Operation(summary = "Declara de nuevo la topología de la guía (3 exchanges, 6 colas, 9 bindings y las políticas de DLX y de alta disponibilidad). Idempotente")
    public Resultado declararBase() {
        return servicio.declararBase();
    }

    @GetMapping("/metricas")
    @Operation(summary = "Mensajes por cola y por DLQ, con alerta si alguna DLQ no está vacía")
    public Metricas metricas() {
        return servicio.metricas();
    }
}
