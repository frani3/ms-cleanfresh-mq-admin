# ms-cleanfresh-mq-admin — Contexto para Claude Code

Parte de **Clean&Fresh Manager**, sistema de varios repositorios (DSY1107 Cloud Native 1, DuocUC; Evaluación Parcial N°2).
Este repositorio es el servicio de **administrador de RabbitMQ** (Spring Boot 4.1.1, Java 21, Maven), puerto **8086**.

Habla con la API de administración de RabbitMQ para crear y eliminar colas, exchanges y bindings, listar la topología y medir las colas, con alerta de DLQ. Sin base de datos. Se llega solo por el BFF (`/api/mq/**`, rol Admin).

## Contexto del sistema

El contexto completo (stack, AWS, Cognito, roles, decisiones técnicas, pendientes y metodología) vive en **un solo lugar**, el
`CLAUDE.md` del repo del frontend, para no mantener copias en cada servicio:

- En línea: https://github.com/frani3/cleanfresh-frontend/blob/main/CLAUDE.md
- En local: `..\cleanfresh-frontend\CLAUDE.md`

Conviene leerlo antes de un cambio que cruce servicios. La documentación (arquitectura, despliegue, demostraciones y specs) está
indexada en `EP2/README.md` de ese mismo repo.

## Este servicio

- `README.md`: endpoints, variables de entorno, cómo levantarlo y probarlo, y Docker.
- Los cambios funcionales se documentan como spec o fix en `EP2/specs/` del repo del frontend (con Acceptance Criteria), antes de
  programarlos y se verifican AC por AC.
- No se escriben contraseñas, tokens ni claves en ningún archivo del repositorio.

## Comandos (Windows, PowerShell)

```powershell
# Java 21, necesario en TODAS las terminales
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\mvnw.cmd clean package -DskipTests     # compilar
.\mvnw.cmd test                          # pruebas
java -jar target/ms-cleanfresh-mq-admin-0.0.1-SNAPSHOT.jar   # levantar (variables de entorno en el README)
```
