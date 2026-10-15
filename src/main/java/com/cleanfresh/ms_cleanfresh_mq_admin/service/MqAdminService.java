package com.cleanfresh.ms_cleanfresh_mq_admin.service;

import com.cleanfresh.ms_cleanfresh_mq_admin.client.RabbitManagementClient;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.BindingInfo;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.BindingRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ColaInfo;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ColaMetrica;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ColaRequest;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.ExchangeInfo;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Metricas;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Resultado;
import com.cleanfresh.ms_cleanfresh_mq_admin.dto.Topologia;
import com.cleanfresh.ms_cleanfresh_mq_admin.service.TopologiaBase.Binding;
import com.cleanfresh.ms_cleanfresh_mq_admin.service.TopologiaBase.Cola;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Crea, lista y elimina colas, exchanges y bindings de RabbitMQ, y mide las colas. Las reglas de negocio
 * (nombres válidos, no tocar los exchanges del sistema, DLQ asociada) viven acá; hablar con el broker es
 * trabajo de {@link RabbitManagementClient}.
 */
@Service
public class MqAdminService {

    private static final Logger log = LoggerFactory.getLogger(MqAdminService.class);

    private static final Pattern NOMBRE = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9._*#-]{1,255}");
    private static final List<String> TIPOS = List.of("direct", "topic");

    private final RabbitManagementClient rabbit;

    public MqAdminService(RabbitManagementClient rabbit) {
        this.rabbit = rabbit;
    }

    // ---------- topología ----------

    public Topologia topologia() {
        List<String> nodos = rabbit.nodosDelCluster().stream()
                .map(n -> n.get("name") + (Boolean.TRUE.equals(n.get("running")) ? " (activo)" : " (caído)"))
                .sorted().toList();
        List<ExchangeInfo> exchanges = rabbit.exchanges().stream()
                .filter(e -> esUsuario(texto(e, "name")))
                .map(e -> new ExchangeInfo(texto(e, "name"), texto(e, "type"), Boolean.TRUE.equals(e.get("durable"))))
                .sorted(Comparator.comparing(ExchangeInfo::nombre)).toList();
        List<ColaInfo> colas = rabbit.colas().stream().map(MqAdminService::aCola)
                .sorted(Comparator.comparing(ColaInfo::nombre)).toList();
        // los bindings implícitos de cada cola al exchange por defecto ("") no se muestran
        List<BindingInfo> bindings = rabbit.bindings().stream()
                .filter(b -> !texto(b, "source").isEmpty() && "queue".equals(b.get("destination_type")))
                .map(b -> new BindingInfo(texto(b, "source"), texto(b, "destination"), texto(b, "routing_key")))
                .sorted(Comparator.comparing(BindingInfo::exchange).thenComparing(BindingInfo::cola).thenComparing(BindingInfo::clave))
                .toList();
        return new Topologia(nodos, exchanges, colas, bindings);
    }

    // ---------- colas ----------

    /** Crea una cola durable; por defecto con su DLQ ({@code nombre.dlq}) enlazada a {@code cmd.dead.dlx}. */
    public Resultado crearCola(String nombre, ColaRequest solicitud) {
        validarNombre(nombre, "cola");
        boolean conDlq = solicitud == null || solicitud.conDlq() == null || solicitud.conDlq();
        List<String> hecho = new ArrayList<>();
        if (!conDlq) {
            rabbit.declararCola(nombre, Map.of());
            hecho.add("cola " + nombre);
            return new Resultado("cola creada", hecho);
        }
        if (nombre.endsWith(".dlq")) {
            throw error(HttpStatus.BAD_REQUEST, "Una cola que ya es DLQ (" + nombre + ") no lleva otra DLQ");
        }
        String clave = solicitud != null && solicitud.claveDlq() != null && !solicitud.claveDlq().isBlank()
                ? solicitud.claveDlq().trim() : nombre;
        validarClave(clave);
        String dlq = nombre + ".dlq";
        rabbit.declararExchange(TopologiaBase.EXCHANGE_MUERTOS, "direct");
        rabbit.declararCola(dlq, Map.of());
        rabbit.crearBinding(TopologiaBase.EXCHANGE_MUERTOS, dlq, clave);
        rabbit.declararCola(nombre, argumentosDlq(clave));
        hecho.add("cola " + dlq + " enlazada a " + TopologiaBase.EXCHANGE_MUERTOS + " con la clave " + clave);
        hecho.add("cola " + nombre + " (los mensajes rechazados van a " + dlq + ")");
        log.info("Cola creada: {} (con DLQ {})", nombre, dlq);
        return new Resultado("cola creada", hecho);
    }

    public Resultado eliminarCola(String nombre, boolean conDlq) {
        validarNombre(nombre, "cola");
        List<String> hecho = new ArrayList<>();
        rabbit.eliminarCola(nombre);
        hecho.add("cola " + nombre + " eliminada");
        if (conDlq) {
            String dlq = nombre + ".dlq";
            try {
                rabbit.eliminarCola(dlq);
                hecho.add("cola " + dlq + " eliminada");
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                hecho.add("la cola " + dlq + " no existía");
            }
        }
        log.info("Cola eliminada: {}", hecho);
        return new Resultado("cola eliminada", hecho);
    }

    // ---------- exchanges ----------

    public Resultado crearExchange(String nombre, String tipo) {
        validarNombre(nombre, "exchange");
        protegerSistema(nombre);
        if (tipo == null || !TIPOS.contains(tipo.toLowerCase())) {
            throw error(HttpStatus.BAD_REQUEST, "El tipo de exchange debe ser direct o topic");
        }
        rabbit.declararExchange(nombre, tipo.toLowerCase());
        log.info("Exchange creado: {} ({})", nombre, tipo);
        return new Resultado("exchange creado", List.of("exchange " + nombre + " (" + tipo.toLowerCase() + ")"));
    }

    public Resultado eliminarExchange(String nombre) {
        validarNombre(nombre, "exchange");
        protegerSistema(nombre);
        rabbit.eliminarExchange(nombre);
        log.info("Exchange eliminado: {}", nombre);
        return new Resultado("exchange eliminado", List.of("exchange " + nombre));
    }

    // ---------- bindings ----------

    public Resultado crearBinding(BindingRequest b) {
        validarBinding(b);
        rabbit.crearBinding(b.exchange(), b.cola(), b.clave());
        log.info("Binding creado: {} -> {} ({})", b.exchange(), b.cola(), b.clave());
        return new Resultado("binding creado", List.of(b.exchange() + " -> " + b.cola() + " con la clave " + b.clave()));
    }

    public Resultado eliminarBinding(BindingRequest b) {
        validarBinding(b);
        if (!rabbit.eliminarBinding(b.exchange(), b.cola(), b.clave())) {
            throw error(HttpStatus.NOT_FOUND, "No existe el binding " + b.exchange() + " -> " + b.cola() + " con la clave " + b.clave());
        }
        log.info("Binding eliminado: {} -> {} ({})", b.exchange(), b.cola(), b.clave());
        return new Resultado("binding eliminado", List.of(b.exchange() + " -> " + b.cola() + " con la clave " + b.clave()));
    }

    // ---------- topología del enunciado ----------

    /** Declara de nuevo la topología base. Es idempotente: lo que ya existe no cambia ni se duplica. */
    public Resultado declararBase() {
        List<String> hecho = new ArrayList<>();
        for (Map.Entry<String, String> e : TopologiaBase.EXCHANGES) {
            rabbit.declararExchange(e.getKey(), e.getValue());
        }
        hecho.add(TopologiaBase.EXCHANGES.size() + " exchanges");
        for (Cola c : TopologiaBase.COLAS) {
            rabbit.declararCola(c.dlq(), Map.of());
            rabbit.declararCola(c.nombre(), argumentosDlq(c.claveMuertos()));
        }
        hecho.add(TopologiaBase.COLAS.size() * 2 + " colas (3 de comandos y sus 3 DLQ)");
        for (Binding b : TopologiaBase.BINDINGS) {
            rabbit.crearBinding(b.exchange(), b.cola(), b.clave());
        }
        hecho.add(TopologiaBase.BINDINGS.size() + " bindings");
        // al final, con las colas ya creadas, para que sí se creen los espejos
        rabbit.ponerPolitica(TopologiaBase.POLITICA_HA, TopologiaBase.POLITICA_HA_CUERPO);
        hecho.add("política " + TopologiaBase.POLITICA_HA + " (colas q.cmd.* replicadas en los dos nodos)");
        log.info("Topología base declarada: {}", hecho);
        return new Resultado("topología base declarada", hecho);
    }

    // ---------- métricas ----------

    /** Mensajes por cola y por DLQ. Cada DLQ que no está vacía genera una alerta y una advertencia en el log. */
    public Metricas metricas() {
        Metricas m = medir();
        m.alertas().forEach(a -> log.warn("ALERTA DLQ: {}", a));
        return m;
    }

    /** Lo mismo que {@link #metricas()} pero sin escribir en el log (lo usa la revisión periódica). */
    public Metricas medir() {
        List<ColaMetrica> colas = rabbit.colas().stream().map(q -> {
            String nombre = texto(q, "name");
            return new ColaMetrica(nombre, nombre.endsWith(".dlq"), numero(q, "messages"),
                    numero(q, "messages_ready"), numero(q, "messages_unacknowledged"), (int) numero(q, "consumers"));
        }).sorted(Comparator.comparing(ColaMetrica::dlq).thenComparing(ColaMetrica::nombre)).toList();
        List<String> alertas = colas.stream().filter(c -> c.dlq() && c.mensajes() > 0)
                .map(c -> "La DLQ " + c.nombre() + " tiene " + c.mensajes() + " mensaje(s) sin atender").toList();
        long enDlq = colas.stream().filter(ColaMetrica::dlq).mapToLong(ColaMetrica::mensajes).sum();
        return new Metricas(colas, enDlq, !alertas.isEmpty(), alertas);
    }

    // ---------- interno ----------

    // Argumentos con los que una cola manda sus mensajes rechazados a cmd.dead.dlx.
    private static Map<String, Object> argumentosDlq(String clave) {
        return Map.of("x-dead-letter-exchange", TopologiaBase.EXCHANGE_MUERTOS, "x-dead-letter-routing-key", clave);
    }

    private static ColaInfo aCola(Map<String, Object> q) {
        String nombre = texto(q, "name");
        Map<?, ?> args = q.get("arguments") instanceof Map<?, ?> m ? m : Map.of();
        Object espejos = q.get("slave_nodes");
        return new ColaInfo(nombre, Boolean.TRUE.equals(q.get("durable")), nombre.endsWith(".dlq"),
                args.get("x-dead-letter-exchange") == null ? null : args.get("x-dead-letter-exchange").toString(),
                args.get("x-dead-letter-routing-key") == null ? null : args.get("x-dead-letter-routing-key").toString(),
                numero(q, "messages"), (int) numero(q, "consumers"), q.get("node") == null ? null : q.get("node").toString(),
                espejos instanceof List<?> l ? l.size() : 0);
    }

    private static String texto(Map<String, Object> m, String campo) {
        Object v = m.get(campo);
        return v == null ? "" : v.toString();
    }

    private static long numero(Map<String, Object> m, String campo) {
        return m.get(campo) instanceof Number n ? n.longValue() : 0;
    }

    private static boolean esUsuario(String exchange) {
        return !exchange.isEmpty() && !exchange.startsWith("amq.");
    }

    private void validarNombre(String nombre, String que) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            throw error(HttpStatus.BAD_REQUEST, "Nombre de " + que + " inválido: usa letras, números, punto, guion y guion bajo (hasta 100)");
        }
    }

    private void validarClave(String clave) {
        if (clave == null || !CLAVE.matcher(clave).matches()) {
            throw error(HttpStatus.BAD_REQUEST, "Clave de ruteo inválida: usa letras, números, punto, guion, * y #");
        }
    }

    private void validarBinding(BindingRequest b) {
        if (b == null) {
            throw error(HttpStatus.BAD_REQUEST, "Falta el cuerpo con exchange, cola y clave");
        }
        validarNombre(b.exchange(), "exchange");
        validarNombre(b.cola(), "cola");
        validarClave(b.clave());
        if (!esUsuario(b.exchange())) {
            throw error(HttpStatus.BAD_REQUEST, "No se enlazan colas a exchanges del sistema (" + b.exchange() + ")");
        }
    }

    private void protegerSistema(String exchange) {
        if (!esUsuario(exchange)) {
            throw error(HttpStatus.BAD_REQUEST, "Los exchanges del sistema (amq.*) no se pueden crear ni eliminar");
        }
    }

    private static ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
