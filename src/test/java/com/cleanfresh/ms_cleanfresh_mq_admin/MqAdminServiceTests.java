package com.cleanfresh.ms_cleanfresh_mq_admin;

import com.cleanfresh.ms_cleanfresh_mq_admin.client.RabbitManagementClient;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.BindingRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ColaRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Metricas;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Resultado;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Topologia;
import com.cleanfresh.ms_cleanfresh_mq_admin.service.MqAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 033, fase 3: las reglas del administrador, con el cliente de RabbitMQ simulado. */
class MqAdminServiceTests {

    private final RabbitManagementClient rabbit = mock(RabbitManagementClient.class);
    private final MqAdminService servicio = new MqAdminService(rabbit);

    private static HttpStatus estado(ResponseStatusException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // ---------- colas ----------

    @Test
    void crearColaCreaPrimeroLaDlqYSuBindingLuegoLaColaSinArgumentosYAlFinalSuPolitica() {
        Resultado r = servicio.crearCola("q.cmd.pagos", null);

        InOrder orden = inOrder(rabbit);
        orden.verify(rabbit).declararExchange("cmd.dead.dlx", "direct");
        orden.verify(rabbit).declararCola("q.cmd.pagos.dlq", Map.of());
        orden.verify(rabbit).crearBinding("cmd.dead.dlx", "q.cmd.pagos.dlq", "q.cmd.pagos");
        // sin argumentos x-dead-letter-*: el DLX lo da la política, como en la guía del profesor
        orden.verify(rabbit).declararCola("q.cmd.pagos", Map.of());
        // y la política va después de crear la cola, para que RabbitMQ cree también sus espejos
        orden.verify(rabbit).ponerPolitica(eq("policy.pagos.dlx"), any());
        assertEquals(2, r.detalle().size());
        assertTrue(r.detalle().get(1).contains("policy.pagos.dlx"));
    }

    @Test
    void unaColaQueYaExisteNoSeVuelveACrearNiSePisaSuPolitica() {
        when(rabbit.colas()).thenReturn(List.of(Map.of("name", "q.cmd.email", "durable", true)));

        for (ColaRequest solicitud : new ColaRequest[]{null, new ColaRequest(false, null), new ColaRequest(true, "otra.clave")}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.crearCola("q.cmd.email", solicitud));
            assertEquals(HttpStatus.CONFLICT, estado(e));
        }

        // no se tocó nada: ni la cola, ni su DLQ, ni su política (policy.email.dlx sigue con su clave original)
        verify(rabbit, never()).declararCola(anyString(), any());
        verify(rabbit, never()).crearBinding(anyString(), anyString(), anyString());
        verify(rabbit, never()).ponerPolitica(anyString(), any());
    }

    @Test
    void crearUnaColaConOtroNombreSigueSiendoPosibleAunqueHayaOtras() {
        when(rabbit.colas()).thenReturn(List.of(Map.of("name", "q.cmd.email", "durable", true)));

        servicio.crearCola("q.cmd.pagos", null);

        verify(rabbit).declararCola("q.cmd.pagos", Map.of());
        verify(rabbit).ponerPolitica(eq("policy.pagos.dlx"), any());
    }

    @Test
    void laPoliticaDeDeadLetteringLlevaPatronExactoDlxClaveYReplicacion() {
        servicio.crearCola("q.demo.pagos", null);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> cuerpo = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(rabbit).ponerPolitica(eq("policy.q.demo.pagos.dlx"), cuerpo.capture());
        Map<String, Object> politica = cuerpo.getValue();
        // el punto del nombre se escapa y el patrón es exacto: no alcanza a q.demo.pagos.dlq ni a otras colas
        assertEquals("^q\\.demo\\.pagos$", politica.get("pattern"));
        assertEquals("queues", politica.get("apply-to"));
        // prioridad 1: gana a ha-all (0) y por eso lleva también la replicación (solo rige una política por cola)
        assertEquals(1, politica.get("priority"));
        assertEquals(Map.of(
                "dead-letter-exchange", "cmd.dead.dlx",
                "dead-letter-routing-key", "q.demo.pagos",
                "ha-mode", "all",
                "ha-sync-mode", "automatic"), politica.get("definition"));
        assertFalse(java.util.regex.Pattern.matches((String) politica.get("pattern"), "q.demo.pagos.dlq"));
        assertTrue(java.util.regex.Pattern.matches((String) politica.get("pattern"), "q.demo.pagos"));
    }

    @Test
    void elNombreDeLaPoliticaSigueLaGuia() {
        assertEquals("policy.email.dlx", com.cleanfresh.ms_cleanfresh_mq_admin.service.TopologiaBase.nombrePoliticaDlx("q.cmd.email"));
        assertEquals("policy.kitchen.dlx", com.cleanfresh.ms_cleanfresh_mq_admin.service.TopologiaBase.nombrePoliticaDlx("q.cmd.kitchen"));
        assertEquals("policy.invoice.dlx", com.cleanfresh.ms_cleanfresh_mq_admin.service.TopologiaBase.nombrePoliticaDlx("q.cmd.invoice"));
        assertEquals("policy.q.demo.pagos.dlx", com.cleanfresh.ms_cleanfresh_mq_admin.service.TopologiaBase.nombrePoliticaDlx("q.demo.pagos"));
    }

    @Test
    void crearColaConClaveDeDlqPropia() {
        servicio.crearCola("q.cmd.pagos", new ColaRequest(true, "pagos.cobrar"));

        verify(rabbit).crearBinding("cmd.dead.dlx", "q.cmd.pagos.dlq", "pagos.cobrar");
        verify(rabbit).declararCola("q.cmd.pagos", Map.of());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> cuerpo = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(rabbit).ponerPolitica(eq("policy.pagos.dlx"), cuerpo.capture());
        assertEquals("pagos.cobrar", ((Map<?, ?>) cuerpo.getValue().get("definition")).get("dead-letter-routing-key"));
    }

    @Test
    void crearColaSinDlqNoToqueNadaMas() {
        servicio.crearCola("q.simple", new ColaRequest(false, null));

        verify(rabbit).declararCola("q.simple", Map.of());
        verify(rabbit, never()).ponerPolitica(anyString(), any());
        verify(rabbit, never()).crearBinding(anyString(), anyString(), anyString());
        verify(rabbit, never()).declararExchange(anyString(), anyString());
    }

    @Test
    void unaColaQueYaEsDlqNoLlevaOtraDlq() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.crearCola("q.cmd.pagos.dlq", null));
        assertEquals(HttpStatus.BAD_REQUEST, estado(e));
        verify(rabbit, never()).declararCola(anyString(), any());
    }

    @Test
    void losNombresInvalidosSeRechazanSinLlamarAlBroker() {
        for (String malo : new String[]{"con espacio", "a/b", "", "x".repeat(101), "ñandú", "../etc"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.crearCola(malo, null), malo);
            assertEquals(HttpStatus.BAD_REQUEST, estado(e));
        }
        verify(rabbit, never()).declararCola(anyString(), any());
    }

    @Test
    void eliminarColaConDlqBorraAmbas() {
        Resultado r = servicio.eliminarCola("q.cmd.pagos", true);

        verify(rabbit).eliminarCola("q.cmd.pagos");
        verify(rabbit).eliminarCola("q.cmd.pagos.dlq");
        // y la política de dead-lettering de la cola, que ya no tiene a quién aplicarse
        verify(rabbit).eliminarPolitica("policy.pagos.dlx");
        assertEquals(3, r.detalle().size());
    }

    @Test
    void siLaColaNoTeniaPoliticaEliminarlaNoFalla() {
        org.mockito.Mockito.doThrow(org.springframework.web.client.HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, new byte[0], null))
                .when(rabbit).eliminarPolitica("policy.q.simple.dlx");

        Resultado r = servicio.eliminarCola("q.simple", false);

        verify(rabbit).eliminarCola("q.simple");
        assertEquals(List.of("cola q.simple eliminada"), r.detalle());
    }

    @Test
    void eliminarColaSinDlqBorraSoloEsa() {
        servicio.eliminarCola("q.cmd.pagos", false);

        verify(rabbit).eliminarCola("q.cmd.pagos");
        verify(rabbit, times(1)).eliminarCola(anyString());
    }

    // ---------- exchanges ----------

    @Test
    void crearExchangeAceptaDirectYTopic() {
        servicio.crearExchange("cmd.nuevo", "topic");
        servicio.crearExchange("cmd.otro", "DIRECT");

        verify(rabbit).declararExchange("cmd.nuevo", "topic");
        verify(rabbit).declararExchange("cmd.otro", "direct");
    }

    @Test
    void crearExchangeRechazaOtrosTiposYNombresDelSistema() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class, () -> servicio.crearExchange("x", "fanout"))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class, () -> servicio.crearExchange("x", null))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class, () -> servicio.crearExchange("amq.direct", "direct"))));
        verify(rabbit, never()).declararExchange(anyString(), anyString());
    }

    @Test
    void noSeEliminanLosExchangesDelSistema() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class, () -> servicio.eliminarExchange("amq.topic"))));
        verify(rabbit, never()).eliminarExchange(anyString());
    }

    @Test
    void eliminarExchangeDeUsuario() {
        servicio.eliminarExchange("cmd.nuevo");
        verify(rabbit).eliminarExchange("cmd.nuevo");
    }

    // ---------- bindings ----------

    @Test
    void crearBindingValidaYEnlaza() {
        servicio.crearBinding(new BindingRequest("cmd.topic", "q.cmd.email", "email.send.*"));
        verify(rabbit).crearBinding("cmd.topic", "q.cmd.email", "email.send.*");
    }

    @Test
    void crearBindingRechazaDatosIncompletosOInvalidos() {
        for (BindingRequest malo : new BindingRequest[]{
                null,
                new BindingRequest(null, "q", "k"),
                new BindingRequest("cmd.topic", "", "k"),
                new BindingRequest("cmd.topic", "q", "con espacio"),
                new BindingRequest("amq.direct", "q", "k")}) {
            assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class, () -> servicio.crearBinding(malo))));
        }
        verify(rabbit, never()).crearBinding(anyString(), anyString(), anyString());
    }

    @Test
    void eliminarBindingInexistenteResponde404() {
        when(rabbit.eliminarBinding("cmd.topic", "q.cmd.email", "no.existe")).thenReturn(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.eliminarBinding(new BindingRequest("cmd.topic", "q.cmd.email", "no.existe")));
        assertEquals(HttpStatus.NOT_FOUND, estado(e));
    }

    @Test
    void eliminarBindingExistente() {
        when(rabbit.eliminarBinding("cmd.topic", "q.cmd.email", "email.*")).thenReturn(true);

        Resultado r = servicio.eliminarBinding(new BindingRequest("cmd.topic", "q.cmd.email", "email.*"));
        assertEquals("binding eliminado", r.accion());
    }

    // ---------- topología base ----------

    @Test
    void laTopologiaBaseDeclaraTodoYAplicaLaPoliticaAlFinal() {
        servicio.declararBase();

        verify(rabbit, times(3)).declararExchange(anyString(), anyString());
        verify(rabbit, times(6)).declararCola(anyString(), any());
        // los 9 bindings de la guía
        verify(rabbit, times(9)).crearBinding(anyString(), anyString(), anyString());
        verify(rabbit).crearBinding("cmd.topic", "q.cmd.email", "email.#");
        verify(rabbit).crearBinding("cmd.topic", "q.cmd.kitchen", "kitchen.#");
        verify(rabbit).crearBinding("cmd.topic", "q.cmd.invoice", "invoice.#");
        verify(rabbit).crearBinding("cmd.dead.dlx", "q.cmd.invoice.dlq", "invoice.gen");
        verify(rabbit, never()).crearBinding(anyString(), anyString(), eq("email.*"));
        verify(rabbit, never()).crearBinding(anyString(), anyString(), eq("invoice.*"));
        // las colas, todas sin argumentos
        verify(rabbit).declararCola("q.cmd.email", Map.of());
        verify(rabbit, never()).declararCola(anyString(), org.mockito.ArgumentMatchers.argThat(m -> m != null && !m.isEmpty()));
        // 3 políticas de dead-lettering (guía) + ha-all, y todas después de crear las colas
        verify(rabbit, times(4)).ponerPolitica(anyString(), any());
        InOrder orden = inOrder(rabbit);
        orden.verify(rabbit, times(6)).declararCola(anyString(), any());
        orden.verify(rabbit).ponerPolitica(eq("policy.email.dlx"), any());
        orden.verify(rabbit).ponerPolitica(eq("policy.kitchen.dlx"), any());
        orden.verify(rabbit).ponerPolitica(eq("policy.invoice.dlx"), any());
        orden.verify(rabbit).ponerPolitica(eq("ha-all"), any());
    }

    @Test
    void lasTresPoliticasDeLaBaseCoincidenConLasDeLaGuia() {
        servicio.declararBase();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> cuerpo = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(rabbit).ponerPolitica(eq("policy.kitchen.dlx"), cuerpo.capture());
        assertEquals("^q\\.cmd\\.kitchen$", cuerpo.getValue().get("pattern"));
        Map<?, ?> definicion = (Map<?, ?>) cuerpo.getValue().get("definition");
        assertEquals("cmd.dead.dlx", definicion.get("dead-letter-exchange"));
        assertEquals("kitchen.ticket", definicion.get("dead-letter-routing-key"));
        assertEquals("all", definicion.get("ha-mode"));
    }

    @Test
    void declararLaBaseDosVecesPideLoMismoAlBroker() {
        servicio.declararBase();
        servicio.declararBase();

        // idempotente: PUT/POST sobre lo que ya existe, sin que el servicio guarde estado
        verify(rabbit, times(6)).declararExchange(anyString(), anyString());
        verify(rabbit, times(18)).crearBinding(anyString(), anyString(), anyString());
        verify(rabbit, times(8)).ponerPolitica(anyString(), any());
    }

    // ---------- métricas ----------

    private static Map<String, Object> cola(String nombre, long mensajes, long listos, long sinConfirmar, int consumidores) {
        return Map.of("name", nombre, "messages", mensajes, "messages_ready", listos,
                "messages_unacknowledged", sinConfirmar, "consumers", consumidores);
    }

    @Test
    void sinMensajesEnLasDlqNoHayAlerta() {
        when(rabbit.colas()).thenReturn(List.of(
                cola("q.cmd.email", 2, 2, 0, 1), cola("q.cmd.email.dlq", 0, 0, 0, 0)));

        Metricas m = servicio.metricas();

        assertFalse(m.hayAlerta());
        assertTrue(m.alertas().isEmpty());
        assertEquals(0, m.mensajesEnDlq());
        assertEquals(2, m.colas().size());
    }

    @Test
    void unaDlqConMensajesGeneraUnaAlertaPorCadaUna() {
        when(rabbit.colas()).thenReturn(List.of(
                cola("q.cmd.email", 0, 0, 0, 1),
                cola("q.cmd.email.dlq", 3, 3, 0, 0),
                cola("q.cmd.kitchen.dlq", 1, 1, 0, 0),
                cola("q.cmd.invoice.dlq", 0, 0, 0, 0)));

        Metricas m = servicio.metricas();

        assertTrue(m.hayAlerta());
        assertEquals(2, m.alertas().size());
        assertTrue(m.alertas().stream().anyMatch(a -> a.contains("q.cmd.email.dlq") && a.contains("3")));
        assertTrue(m.alertas().stream().anyMatch(a -> a.contains("q.cmd.kitchen.dlq")));
        assertEquals(4, m.mensajesEnDlq());
        // las colas normales van primero y las DLQ después
        assertEquals("q.cmd.email", m.colas().get(0).nombre());
        assertFalse(m.colas().get(0).dlq());
        assertTrue(m.colas().get(3).dlq());
    }

    @Test
    void unaColaConMensajesPeroQueNoEsDlqNoGeneraAlerta() {
        when(rabbit.colas()).thenReturn(List.of(cola("q.cmd.email", 500, 500, 0, 0)));
        assertFalse(servicio.metricas().hayAlerta());
    }

    // ---------- topología ----------

    @Test
    void laTopologiaOcultaLosExchangesDelSistemaYLosBindingsImplicitos() {
        when(rabbit.nodosDelCluster()).thenReturn(List.of(
                Map.of("name", "rabbit@rabbit2", "running", true), Map.of("name", "rabbit@rabbit1", "running", true)));
        when(rabbit.exchanges()).thenReturn(List.of(
                Map.of("name", "", "type", "direct", "durable", true),
                Map.of("name", "amq.topic", "type", "topic", "durable", true),
                Map.of("name", "cmd.topic", "type", "topic", "durable", true)));
        when(rabbit.colas()).thenReturn(List.of(Map.of("name", "q.cmd.email", "durable", true, "messages", 0, "consumers", 1,
                "node", "rabbit@rabbit1", "slave_nodes", List.of("rabbit@rabbit2"),
                "policy", "policy.email.dlx",
                "effective_policy_definition", Map.of("dead-letter-exchange", "cmd.dead.dlx", "dead-letter-routing-key", "email.send",
                        "ha-mode", "all"),
                "arguments", Map.of())));
        when(rabbit.bindings()).thenReturn(List.of(
                Map.of("source", "", "destination", "q.cmd.email", "destination_type", "queue", "routing_key", "q.cmd.email"),
                Map.of("source", "cmd.topic", "destination", "q.cmd.email", "destination_type", "queue", "routing_key", "email.*")));

        Topologia t = servicio.topologia();

        assertEquals(List.of("rabbit@rabbit1 (activo)", "rabbit@rabbit2 (activo)"), t.nodos());
        assertEquals(1, t.exchanges().size());
        assertEquals("cmd.topic", t.exchanges().get(0).nombre());
        assertEquals(1, t.bindings().size());
        assertEquals("email.*", t.bindings().get(0).clave());
        assertEquals("cmd.dead.dlx", t.colas().get(0).exchangeMuertos());
        assertEquals("email.send", t.colas().get(0).claveMuertos());
        assertEquals(1, t.colas().get(0).espejos());
        assertEquals("policy.email.dlx", t.colas().get(0).politica());
    }

    @Test
    void unaColaAnteriorConElDlxComoArgumentoSeMuestraIgual() {
        when(rabbit.nodosDelCluster()).thenReturn(List.of());
        when(rabbit.exchanges()).thenReturn(List.of());
        when(rabbit.bindings()).thenReturn(List.of());
        when(rabbit.colas()).thenReturn(List.of(Map.of("name", "q.vieja", "durable", true, "messages", 0, "consumers", 0,
                "arguments", Map.of("x-dead-letter-exchange", "cmd.dead.dlx", "x-dead-letter-routing-key", "vieja"))));

        var cola = servicio.topologia().colas().get(0);

        assertEquals("cmd.dead.dlx", cola.exchangeMuertos());
        assertEquals("vieja", cola.claveMuertos());
        org.junit.jupiter.api.Assertions.assertNull(cola.politica());
    }
}
