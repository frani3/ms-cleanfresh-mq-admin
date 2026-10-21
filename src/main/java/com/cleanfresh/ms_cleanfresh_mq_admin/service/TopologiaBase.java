package com.cleanfresh.ms_cleanfresh_mq_admin.service;

import java.util.List;
import java.util.Map;

/**
 * La topología de la guía del profesor (la misma de {@code EP2/despliegue/mq/definitions.json} y de la carpeta
 * {@code politicas/}): 3 exchanges, 3 colas de comandos sin argumentos, cada una con su DLQ, 9 bindings y las
 * políticas de dead-lettering (Spec 037).
 */
public final class TopologiaBase {

    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC = "cmd.topic";
    public static final String EXCHANGE_MUERTOS = "cmd.dead.dlx";

    public record Cola(String nombre, String claveMuertos) {
        public String dlq() {
            return nombre + ".dlq";
        }

        /** policy.email.dlx para q.cmd.email, como en la guía. */
        public String politica() {
            return nombrePoliticaDlx(nombre);
        }
    }

    public record Binding(String exchange, String cola, String clave) {
    }

    public static final List<Map.Entry<String, String>> EXCHANGES = List.of(
            Map.entry(EXCHANGE_DIRECT, "direct"),
            Map.entry(EXCHANGE_TOPIC, "topic"),
            Map.entry(EXCHANGE_MUERTOS, "direct"));

    public static final List<Cola> COLAS = List.of(
            new Cola("q.cmd.email", "email.send"),
            new Cola("q.cmd.kitchen", "kitchen.ticket"),
            new Cola("q.cmd.invoice", "invoice.gen"));

    // Los 9 bindings de la guía. En cmd.topic se usa '#' (cero o más palabras) para que lleguen las variantes
    // email.send.high, kitchen.ticket.thermal e invoice.gen.pdf: la guía lo hace con email.# y avisa que
    // invoice.* no atrapa invoice.gen.pdf "sin adaptar ese patrón", así que invoice también va con '#'.
    public static final List<Binding> BINDINGS = List.of(
            new Binding(EXCHANGE_DIRECT, "q.cmd.email", "email.send"),
            new Binding(EXCHANGE_DIRECT, "q.cmd.kitchen", "kitchen.ticket"),
            new Binding(EXCHANGE_DIRECT, "q.cmd.invoice", "invoice.gen"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.email", "email.#"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.kitchen", "kitchen.#"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.invoice", "invoice.#"),
            new Binding(EXCHANGE_MUERTOS, "q.cmd.email.dlq", "email.send"),
            new Binding(EXCHANGE_MUERTOS, "q.cmd.kitchen.dlq", "kitchen.ticket"),
            new Binding(EXCHANGE_MUERTOS, "q.cmd.invoice.dlq", "invoice.gen"));

    // Replica las colas q.cmd.* en los dos nodos. Se aplica DESPUÉS de crear las colas: si se carga junto
    // con ellas no se crean los espejos (se comprobó en la fase 1).
    public static final String POLITICA_HA = "ha-all";
    public static final Map<String, Object> POLITICA_HA_CUERPO = Map.of(
            "pattern", "^q\\.cmd\\.",
            "apply-to", "queues",
            "priority", 0,
            "definition", Map.of("ha-mode", "all", "ha-sync-mode", "automatic"));

    /**
     * Nombre de la política de dead-lettering de una cola: {@code policy.email.dlx} para {@code q.cmd.email} (como la
     * guía) y {@code policy.<cola>.dlx} para cualquier otra.
     */
    public static String nombrePoliticaDlx(String cola) {
        String corto = cola.startsWith("q.cmd.") ? cola.substring("q.cmd.".length()) : cola;
        return "policy." + corto + ".dlx";
    }

    /**
     * Cuerpo de la política de dead-lettering de una cola: patrón exacto de la cola, DLX {@code cmd.dead.dlx} y la
     * routing key de su DLQ. En RabbitMQ solo rige <b>una</b> política por cola (la de mayor prioridad): esta lleva
     * prioridad 1 y además la replicación, para que la cola no pierda la de {@code ha-all} (prioridad 0).
     */
    public static Map<String, Object> politicaDlx(String cola, String claveMuertos) {
        return Map.of(
                "pattern", "^" + cola.replace(".", "\\.") + "$",
                "apply-to", "queues",
                "priority", 1,
                "definition", Map.of(
                        "dead-letter-exchange", EXCHANGE_MUERTOS,
                        "dead-letter-routing-key", claveMuertos,
                        "ha-mode", "all",
                        "ha-sync-mode", "automatic"));
    }

    private TopologiaBase() {
    }
}
