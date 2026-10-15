package com.cleanfresh.ms_cleanfresh_mq_admin.dto;

import java.util.List;

public record Topologia(List<String> nodos, List<ExchangeInfo> exchanges, List<ColaInfo> colas, List<BindingInfo> bindings) {
}
