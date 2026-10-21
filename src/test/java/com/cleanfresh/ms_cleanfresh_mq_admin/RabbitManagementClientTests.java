package com.cleanfresh.ms_cleanfresh_mq_admin;

import com.cleanfresh.ms_cleanfresh_mq_admin.client.RabbitManagementClient;
import com.cleanfresh.ms_cleanfresh_mq_admin.config.MqProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El cliente contra un servidor HTTP real que imita la API de RabbitMQ (qué pide y cómo se recupera de un nodo caído). */
class RabbitManagementClientTests {

    private HttpServer servidor;
    private final List<String> pedidos = new ArrayList<>();
    private final List<String> cuerpos = new ArrayList<>();
    private final List<String> autorizaciones = new ArrayList<>();

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api", ex -> {
            // getRawPath: así se ve lo que de verdad se mandó (con %2F y %2A sin decodificar)
            String ruta = ex.getRequestURI().getRawPath();
            String metodo = ex.getRequestMethod();
            pedidos.add(metodo + " " + ruta);
            cuerpos.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizaciones.add(ex.getRequestHeaders().getFirst("Authorization"));
            String respuesta = "[]";
            int codigo = 200;
            if (ruta.equals("/api/bindings/%2F/e/cmd.topic/q/q.cmd.email")) {
                respuesta = "[{\"routing_key\":\"email.*\",\"properties_key\":\"email.%2A\"},"
                        + "{\"routing_key\":\"email.send.*\",\"properties_key\":\"email.send.%2A\"}]";
            } else if (metodo.equals("DELETE") && ruta.endsWith("/fantasma")) {
                codigo = 404;
                respuesta = "{\"error\":\"Object Not Found\"}";
            } else if (!metodo.equals("GET")) {
                codigo = 201;
            }
            ex.getResponseHeaders().add("Content-Type", "application/json");
            if (codigo == 201) {
                ex.sendResponseHeaders(codigo, -1);
            } else {
                byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(codigo, bytes.length);
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/api";
    }

    private static String urlSinNadie() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return "http://127.0.0.1:" + s.getLocalPort() + "/api";
        }
    }

    private RabbitManagementClient cliente(String... urls) {
        return new RabbitManagementClient(new MqProperties(List.of(urls), "admin", "secreta", "/"));
    }

    @Test
    void usaAutenticacionBasicaYElVhostCodificado() {
        cliente(url()).colas();

        assertEquals("GET /api/queues/%2F", pedidos.get(0));
        assertEquals("Basic " + Base64.getEncoder().encodeToString("admin:secreta".getBytes(StandardCharsets.UTF_8)),
                autorizaciones.get(0));
    }

    @Test
    void declararColaMandaDurableYArgumentos() {
        cliente(url()).declararCola("q.cmd.pagos", Map.of("x-dead-letter-exchange", "cmd.dead.dlx"));

        assertEquals("PUT /api/queues/%2F/q.cmd.pagos", pedidos.get(0));
        assertTrue(cuerpos.get(0).contains("\"durable\":true"));
        assertTrue(cuerpos.get(0).contains("\"x-dead-letter-exchange\":\"cmd.dead.dlx\""));
    }

    @Test
    void crearBindingMandaLaClave() {
        cliente(url()).crearBinding("cmd.topic", "q.cmd.email", "email.send.*");

        assertEquals("POST /api/bindings/%2F/e/cmd.topic/q/q.cmd.email", pedidos.get(0));
        assertTrue(cuerpos.get(0).contains("\"routing_key\":\"email.send.*\""));
    }

    @Test
    void eliminarBindingUsaLaPropertiesKeyCodificadaDeLaClave() {
        boolean eliminado = cliente(url()).eliminarBinding("cmd.topic", "q.cmd.email", "email.*");

        assertTrue(eliminado);
        assertEquals("DELETE /api/bindings/%2F/e/cmd.topic/q/q.cmd.email/email.%2A", pedidos.get(1));
    }

    @Test
    void eliminarUnBindingQueNoExisteDevuelveFalseSinBorrarNada() {
        boolean eliminado = cliente(url()).eliminarBinding("cmd.topic", "q.cmd.email", "otra.clave");

        assertFalse(eliminado);
        assertEquals(1, pedidos.size());
    }

    @Test
    void siElPrimerNodoNoRespondePruebaElSegundo() throws IOException {
        RabbitManagementClient c = cliente(urlSinNadie(), url());

        c.colas();

        assertEquals(1, pedidos.size());
        assertEquals("GET /api/queues/%2F", pedidos.get(0));
    }

    @Test
    void siNingunNodoRespondeLanzaElErrorDeConexion() throws IOException {
        RabbitManagementClient c = cliente(urlSinNadie());

        assertThrows(ResourceAccessException.class, c::colas);
    }

    @Test
    void unaRespuestaDeErrorDelBrokerNoHaceSaltarAlSiguienteNodo() {
        RabbitManagementClient c = cliente(url(), url());

        assertThrows(HttpClientErrorException.NotFound.class, () -> c.eliminarCola("fantasma"));
        assertEquals(1, pedidos.size());
    }

    @Test
    void ponerYEliminarUnaPoliticaUsanSuNombreEnLaRuta() {
        RabbitManagementClient c = cliente(url());

        c.ponerPolitica("policy.pagos.dlx", Map.of("pattern", "^q\\.pagos$", "apply-to", "queues", "priority", 1,
                "definition", Map.of("dead-letter-exchange", "cmd.dead.dlx")));
        c.eliminarPolitica("policy.pagos.dlx");

        assertEquals("PUT /api/policies/%2F/policy.pagos.dlx", pedidos.get(0));
        assertTrue(cuerpos.get(0).contains("\"dead-letter-exchange\":\"cmd.dead.dlx\""));
        assertTrue(cuerpos.get(0).contains("\"priority\":1"));
        assertEquals("DELETE /api/policies/%2F/policy.pagos.dlx", pedidos.get(1));
    }
}
