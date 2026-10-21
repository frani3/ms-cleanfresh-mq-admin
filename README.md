# ms-cleanfresh-mq-admin

Administrador de RabbitMQ de Clean&Fresh Manager. Spring Boot 4.1.1 / Java 21, puerto **8086**, **sin base
de datos**. Habla con la API de administración de RabbitMQ (la misma del panel, puertos 15672/15673) para
crear y eliminar colas, exchanges y bindings, listar la topología del clúster y medir las colas, con alerta
cuando una cola de mensajes muertos (DLQ) no está vacía.

Como los demás microservicios, no valida JWT: no es público y se llega a él solo por el BFF
(`/api/mq/**`), que exige el rol **Admin**. Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC),
Spec 033 del repo del frontend.

## Endpoints

Documentación interactiva: `http://localhost:8086/swagger-ui.html` (OpenAPI en `/v3/api-docs`).

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/mq/topologia` | Nodos del clúster, exchanges, colas (con su DLX efectivo, su política y sus espejos) y bindings |
| POST | `/api/mq/queues/{nombre}` | Crea una cola durable sin argumentos. Por defecto crea también `{nombre}.dlq`, enlazada a `cmd.dead.dlx`, y la política `policy.{nombre}.dlx` que le asigna ese DLX (como en la guía). Cuerpo opcional: `{ "conDlq": false }` o `{ "claveDlq": "mi.clave" }`. Si la cola ya existe responde `409` y no la toca |
| DELETE | `/api/mq/queues/{nombre}?conDlq=true` | Elimina la cola, su política de DLX y, con `conDlq=true`, su DLQ |
| POST | `/api/mq/exchanges/{nombre}` | Crea un exchange durable. Cuerpo: `{ "tipo": "direct" }` o `"topic"` |
| DELETE | `/api/mq/exchanges/{nombre}` | Elimina un exchange |
| POST | `/api/mq/bindings` | Crea un binding. Cuerpo: `{ "exchange": "...", "cola": "...", "clave": "..." }` |
| DELETE | `/api/mq/bindings` | Elimina el binding con ese cuerpo |
| POST | `/api/mq/topologia/base` | Vuelve a declarar la topología de la guía del profesor (3 exchanges, 6 colas sin argumentos, 9 bindings, las políticas `policy.email.dlx`, `policy.kitchen.dlx`, `policy.invoice.dlx` y `ha-all`). Idempotente: se puede repetir y repara lo que falte |
| GET | `/api/mq/metricas` | Mensajes por cola y por DLQ; `hayAlerta` y `alertas` si alguna DLQ tiene mensajes |

Reglas: los nombres usan letras, números, punto, guion y guion bajo (hasta 100); los exchanges `amq.*` del
sistema no se crean, eliminan ni enlazan; solo existen los tipos `direct` y `topic`. Lo que el broker rechaza
(una cola que ya existe con otros argumentos, algo que no existe...) sale con su código: 400, 404. Si no
responde ningún nodo, 503.

Cada DLQ con mensajes se anota como `WARN` en el log (`ALERTA DLQ: ...`) cada vez que se piden las
métricas. Además, el administrador **revisa las DLQ por su cuenta** cada 30 s: deja una advertencia cuando
una DLQ pasa a tener mensajes (o cambia su cantidad) y un aviso cuando vuelven a estar vacías, sin repetir
la misma línea en cada vuelta. Las cifras vienen de las estadísticas de la API de RabbitMQ, que se refrescan cada pocos segundos:
un mensaje recién publicado o consumido puede tardar unos segundos en verse.

La política `ha-all` cubre las colas `q.cmd.*`; la política de DLX de cada cola lleva además la replicación (prioridad 1),
así que toda cola creada con DLQ queda replicada en los dos nodos. Una cola creada sin DLQ y con otro nombre no.

## Configuración (variables de entorno)

| Variable | Descripción |
|---|---|
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Credenciales con permiso de administración (sin valor por defecto: sin ellas no arranca) |
| `RABBITMQ_MGMT_URLS` | URL de la API de cada nodo, separadas por coma; si el primero no responde se prueba el siguiente. Por defecto `http://localhost:15672/api,http://localhost:15673/api` |
| `MQ_ALERTAS_ACTIVAS` | `false` apaga la revisión periódica de las DLQ (por defecto `true`) |
| `MQ_ALERTAS_INTERVALO_MS` | Cada cuánto se revisan (por defecto `30000`) |

## Levantar en local

Con el clúster de `EP2/despliegue/mq/` (repo del frontend) arriba:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:RABBITMQ_USER="<usuario>"; $env:RABBITMQ_PASSWORD="<clave>"
.\mvnw.cmd spring-boot:run
```

Los tests (`.\mvnw.cmd test`) no necesitan RabbitMQ: usan un cliente simulado y un servidor HTTP local que
imita la API de administración.

Verificación contra el clúster real: `EP2/despliegue/mq/verificar-admin.sh` (repo del frontend).

## Docker

`Dockerfile` multi-etapa (Maven + JDK 21 para compilar, JRE 21 sin root para correr). Se configura solo por variables de entorno.

```bash
docker build -t cleanfresh/mq-admin .
docker run -p 8086:8086 -e RABBITMQ_USER=... -e RABBITMQ_PASSWORD=... -e RABBITMQ_MGMT_URLS=http://<mq>:15672/api,http://<mq>:15673/api cleanfresh/mq-admin
```

## Arquitectura y decisiones técnicas

Ver [`CLAUDE.md`](CLAUDE.md) y, en el repo del frontend, `EP2/specs/033-spec-rabbitmq.md`.
