package com.cleanfresh.ms_cleanfresh_mq_admin.client;

import com.cleanfresh.ms_cleanfresh_mq_admin.config.MqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Cliente de la API HTTP de administración de RabbitMQ (la misma del panel). Recibe las URL de los dos
 * nodos: si el primero no responde, prueba el siguiente, así el administrador sigue funcionando con un
 * nodo caído.
 */
@Component
public class RabbitManagementClient {

    private static final Logger log = LoggerFactory.getLogger(RabbitManagementClient.class);

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {
    };

    private record Nodo(String url, RestClient http) {
    }

    private final List<Nodo> nodos = new ArrayList<>();
    private final String vhost;

    public RabbitManagementClient(MqProperties props) {
        this.vhost = props.vhost();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        for (String url : props.apiUrls()) {
            String limpia = url.trim().replaceAll("/+$", "");
            nodos.add(new Nodo(limpia, RestClient.builder()
                    .baseUrl(limpia)
                    .requestFactory(fabrica)
                    .defaultHeaders(h -> h.setBasicAuth(props.user(), props.password()))
                    .build()));
        }
    }

    // ---------- lecturas ----------

    public List<Map<String, Object>> exchanges() {
        return usar(n -> n.http().get().uri("/exchanges/{v}", vhost).retrieve().body(LISTA));
    }

    public List<Map<String, Object>> colas() {
        return usar(n -> n.http().get().uri("/queues/{v}", vhost).retrieve().body(LISTA));
    }

    public List<Map<String, Object>> bindings() {
        return usar(n -> n.http().get().uri("/bindings/{v}", vhost).retrieve().body(LISTA));
    }

    public List<Map<String, Object>> nodosDelCluster() {
        return usar(n -> n.http().get().uri("/nodes").retrieve().body(LISTA));
    }

    // ---------- escrituras ----------

    public void declararExchange(String nombre, String tipo) {
        Map<String, Object> cuerpo = Map.of("type", tipo, "durable", true, "auto_delete", false, "internal", false,
                "arguments", Map.of());
        usar(n -> n.http().put().uri("/exchanges/{v}/{n}", vhost, nombre).contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo).retrieve().toBodilessEntity());
    }

    public void eliminarExchange(String nombre) {
        usar(n -> n.http().delete().uri("/exchanges/{v}/{n}", vhost, nombre).retrieve().toBodilessEntity());
    }

    public void declararCola(String nombre, Map<String, Object> argumentos) {
        Map<String, Object> cuerpo = Map.of("durable", true, "auto_delete", false, "arguments", argumentos);
        usar(n -> n.http().put().uri("/queues/{v}/{n}", vhost, nombre).contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo).retrieve().toBodilessEntity());
    }

    public void eliminarCola(String nombre) {
        usar(n -> n.http().delete().uri("/queues/{v}/{n}", vhost, nombre).retrieve().toBodilessEntity());
    }

    public void crearBinding(String exchange, String cola, String clave) {
        Map<String, Object> cuerpo = Map.of("routing_key", clave, "arguments", Map.of());
        usar(n -> n.http().post().uri("/bindings/{v}/e/{e}/q/{q}", vhost, exchange, cola)
                .contentType(MediaType.APPLICATION_JSON).body(cuerpo).retrieve().toBodilessEntity());
    }

    /**
     * Elimina el binding con esa clave. La API pide la "properties_key" (la clave con sus caracteres
     * especiales codificados, p. ej. {@code email.%2A}), así que se busca en los bindings existentes.
     *
     * @return false si no existe ese binding
     */
    public boolean eliminarBinding(String exchange, String cola, String clave) {
        return usar(n -> {
            List<Map<String, Object>> existentes = n.http().get()
                    .uri("/bindings/{v}/e/{e}/q/{q}", vhost, exchange, cola).retrieve().body(LISTA);
            if (existentes == null) {
                return false;
            }
            for (Map<String, Object> b : existentes) {
                if (clave.equals(b.get("routing_key"))) {
                    String ruta = n.url() + "/bindings/" + codificar(vhost) + "/e/" + codificar(exchange) + "/q/"
                            + codificar(cola) + "/" + b.get("properties_key");
                    n.http().delete().uri(URI.create(ruta)).retrieve().toBodilessEntity();
                    return true;
                }
            }
            return false;
        });
    }

    public void ponerPolitica(String nombre, Map<String, Object> cuerpo) {
        usar(n -> n.http().put().uri("/policies/{v}/{n}", vhost, nombre).contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo).retrieve().toBodilessEntity());
    }

    public void eliminarPolitica(String nombre) {
        usar(n -> n.http().delete().uri("/policies/{v}/{n}", vhost, nombre).retrieve().toBodilessEntity());
    }

    // ---------- interno ----------

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // Prueba cada nodo en orden; solo salta al siguiente si no se puede conectar (una respuesta de error
    // del broker, como un 404, es una respuesta y se devuelve tal cual).
    private <T> T usar(Function<Nodo, T> operacion) {
        ResourceAccessException ultimo = null;
        for (Nodo nodo : nodos) {
            try {
                return operacion.apply(nodo);
            } catch (ResourceAccessException e) {
                log.warn("La API de RabbitMQ en {} no responde: {}", nodo.url(), e.getMessage());
                ultimo = e;
            }
        }
        throw ultimo;
    }
}
