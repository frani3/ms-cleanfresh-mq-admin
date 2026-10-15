package com.cleanfresh.ms_cleanfresh_mq_admin.service;

import java.util.List;
import java.util.Map;

/**
 * La topología del enunciado (la misma de {@code EP2/despliegue/mq/definitions.json}): 3 exchanges,
 * 3 colas de comandos, cada una con su DLQ, y 11 bindings.
 */
public final class TopologiaBase {

    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC = "cmd.topic";
    public static final String EXCHANGE_MUERTOS = "cmd.dead.dlx";

    public record Cola(String nombre, String claveMuertos) {
        public String dlq() {
            return nombre + ".dlq";
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

    // Los .* y .# extra de cmd.topic hacen que email.send.high, kitchen.ticket.thermal e invoice.gen.pdf
    // lleguen a su cola ('*' es exactamente una palabra).
    public static final List<Binding> BINDINGS = List.of(
            new Binding(EXCHANGE_DIRECT, "q.cmd.email", "email.send"),
            new Binding(EXCHANGE_DIRECT, "q.cmd.kitchen", "kitchen.ticket"),
            new Binding(EXCHANGE_DIRECT, "q.cmd.invoice", "invoice.gen"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.email", "email.*"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.email", "email.send.*"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.kitchen", "kitchen.#"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.invoice", "invoice.*"),
            new Binding(EXCHANGE_TOPIC, "q.cmd.invoice", "invoice.gen.*"),
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

    private TopologiaBase() {
    }
}
