package com.cleanfresh.ms_cleanfresh_mq_admin;

import com.cleanfresh.ms_cleanfresh_mq_admin.controller.ErroresHandler;
import com.cleanfresh.ms_cleanfresh_mq_admin.controller.MqAdminController;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.BindingRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ColaRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Metricas;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Resultado;
import com.cleanfresh.ms_cleanfresh_mq_admin.service.MqAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MqAdminControllerTests {

    private final MqAdminService servicio = mock(MqAdminService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new MqAdminController(servicio)).setControllerAdvice(new ErroresHandler()).build();
    }

    @Test
    void crearColaSinCuerpoUsaLosValoresPorDefecto() throws Exception {
        when(servicio.crearCola("q.cmd.pagos", null)).thenReturn(new Resultado("cola creada", List.of("cola q.cmd.pagos")));

        mvc.perform(post("/api/mq/queues/q.cmd.pagos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("cola creada"));
    }

    @Test
    void crearColaConCuerpo() throws Exception {
        when(servicio.crearCola(eq("q.simple"), any(ColaRequest.class))).thenReturn(new Resultado("cola creada", List.of()));

        mvc.perform(post("/api/mq/queues/q.simple").contentType(MediaType.APPLICATION_JSON).content("{\"conDlq\":false}"))
                .andExpect(status().isOk());
        verify(servicio).crearCola(eq("q.simple"), any(ColaRequest.class));
    }

    @Test
    void eliminarColaLeeElParametroConDlq() throws Exception {
        when(servicio.eliminarCola("q.cmd.pagos", true)).thenReturn(new Resultado("cola eliminada", List.of()));

        mvc.perform(delete("/api/mq/queues/q.cmd.pagos").param("conDlq", "true")).andExpect(status().isOk());
        verify(servicio).eliminarCola("q.cmd.pagos", true);
    }

    @Test
    void crearBindingRecibeElCuerpo() throws Exception {
        when(servicio.crearBinding(any(BindingRequest.class))).thenReturn(new Resultado("binding creado", List.of()));

        mvc.perform(post("/api/mq/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"cmd.topic\",\"cola\":\"q.cmd.email\",\"clave\":\"email.*\"}"))
                .andExpect(status().isOk());
        verify(servicio).crearBinding(new BindingRequest("cmd.topic", "q.cmd.email", "email.*"));
    }

    @Test
    void unaValidacionFallidaSaleComo400() throws Exception {
        when(servicio.crearCola(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre inválido"));

        mvc.perform(post("/api/mq/queues/mala")).andExpect(status().isBadRequest());
    }

    @Test
    void siElBrokerDiceQueNoExisteSale404() throws Exception {
        when(servicio.eliminarCola("fantasma", false))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, new byte[0], null));

        mvc.perform(delete("/api/mq/queues/fantasma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe en RabbitMQ"));
    }

    @Test
    void siElBrokerRechazaLaSolicitudSaleConSuCodigoYSuMotivo() throws Exception {
        when(servicio.crearCola(any(), any())).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                "inequivalent arg 'x-dead-letter-exchange'".getBytes(), null));

        mvc.perform(post("/api/mq/queues/q.cmd.email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("inequivalent")));
    }

    @Test
    void unErrorDelBrokerSale502() throws Exception {
        when(servicio.metricas())
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Error", null, new byte[0], null));

        mvc.perform(get("/api/mq/metricas")).andExpect(status().isBadGateway());
    }

    @Test
    void sinBrokerSale503() throws Exception {
        when(servicio.metricas()).thenThrow(new ResourceAccessException("Connection refused"));

        mvc.perform(get("/api/mq/metricas")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void metricasDevuelveLaAlerta() throws Exception {
        when(servicio.metricas()).thenReturn(new Metricas(List.of(), 2, true, List.of("La DLQ q.cmd.email.dlq tiene 2 mensaje(s) sin atender")));

        mvc.perform(get("/api/mq/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayAlerta").value(true))
                .andExpect(jsonPath("$.alertas[0]").value(containsString("q.cmd.email.dlq")));
    }
}
