# Clean&Fresh Manager — Contexto del Proyecto

## Descripción General

Sistema de gestión para una cadena de lavanderías llamada **Clean&Fresh**, desarrollado como caso adaptado del caso semestral Pedidos360 para la asignatura **DSY1107 Cloud Native 1** de DuocUC. Trabajo individual, **Evaluación Parcial N°1 (EP1)**.

---

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Frontend | React (Create React App, **NO Vite**), JavaScript |
| Autenticación Frontend | `oidc-client-ts` + `react-oidc-context` |
| BFF | Spring Boot 4.1.1, Java 21, Maven |
| Microservicios | Spring Boot 4.1.1, Java 21, Maven |
| IDaaS | AWS Cognito (User Pool) |
| JDK local | Java 21 en `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |

> **Historial:** el proyecto arrancó con MSAL + Azure Entra External ID
> (CIAM); se migró a AWS Cognito porque cambió el requisito de la
> pauta. La lógica de roles/guards/interceptor es equivalente, cambian
> el proveedor y algunos nombres de claim (ver más abajo).

---

## AWS Cognito — User Pool

| Dato | Valor |
|---|---|
| Authority (issuer) | `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Xj0EYCnUK` |
| Client ID | `37oq5a3q9ur02q13k6c8rg6mct` |
| Dominio (Hosted UI) | `https://us-east-1xj0eycnuk.auth.us-east-1.amazoncognito.com` |
| Redirect URI (callback) | `http://localhost:3000/` (con la barra final; el código usa `${origin}/`) |
| Logout URI | `http://localhost:3000` |
| Scope | `openid email profile https://api.cleanfresh.com/access_as_user` |

### Usuarios de prueba

Uno por rol (Admin, Operador, Cliente), como grupos del User Pool
(`cognito:groups`): `admin@cleanfresh.com`, `operador@cleanfresh.com` y un
Cliente. Las contraseñas no se documentan acá. El User Pool actual
(`cleanfresh-users`, App Client `cleanfresh-spa`) se creó de nuevo en EP2: el
laboratorio de EP1 ya no existe. El access token solo trae `username` (un UUID),
no el correo.

---

## Repositorios GitHub (usuario: frani3)

| Proyecto | URL |
|---|---|
| Frontend | https://github.com/frani3/cleanfresh-frontend |
| BFF | https://github.com/frani3/ms-cleanfresh-bff |
| MS Orders | https://github.com/frani3/ms-cleanfresh-orders |
| MS Catalog | https://github.com/frani3/ms-cleanfresh-catalog |
| MS Notificaciones (EP2) | https://github.com/frani3/ms-cleanfresh-notificaciones |
| MS Reportes (EP2) | https://github.com/frani3/ms-cleanfresh-reportes |
| MS Auditoría (EP2) | https://github.com/frani3/ms-cleanfresh-auditoria |

---

## Puertos Locales

| Servicio | Puerto |
|---|---|
| Frontend | http://localhost:3000 |
| BFF | http://localhost:8080 |
| ms-cleanfresh-orders | http://localhost:8081 |
| ms-cleanfresh-catalog | http://localhost:8082 |
| ms-cleanfresh-notificaciones (EP2) | http://localhost:8083 |
| ms-cleanfresh-reportes (EP2) | http://localhost:8084 |
| ms-cleanfresh-auditoria (EP2) | http://localhost:8085 |

---

## Despliegue en AWS (EP2, Spec 029)

El entorno de EP1 (3 EC2 + API Gateway) dejó de existir al renovarse el
laboratorio, y en EP2 se recreó con otra arquitectura. El frontend sigue en
`localhost:3000` apuntando por `.env` a API Gateway. Detalle y verificación en
[`EP2/ARQUITECTURA.md`](EP2/ARQUITECTURA.md) y `EP2/EVIDENCIA-EP2.md`.

| Recurso | Valor |
|---|---|
| EC2 #1 `cleanfresh-bff` (`t3.small`) | **Elastic IP `3.213.118.143`**:8080 (IP fija), BFF en Docker; IP privada `172.31.33.129` |
| EC2 #2 `cleanfresh-ms` (`t3.medium`) | IP privada `172.31.39.91`; IP pública variable (cambia al apagar el laboratorio; última conocida `52.90.146.243`, solo se usa para SSH). 5 microservicios con `docker compose` en 8081–8085 |
| RDS PostgreSQL 16 `cleanfresh-db` | `cleanfresh-db.cjeictyledp6.us-east-1.rds.amazonaws.com:5432`, bases `orders_db`, `catalog_db` y `notificaciones_db`, un usuario por servicio, sin acceso público |
| SQS | cola Standard `cleanfresh-ordenes` (`us-east-1`); las EC2 usan el `LabInstanceProfile` |
| API Gateway (`cleanfresh-api`, HTTP API) | `https://0ksy5y3586.execute-api.us-east-1.amazonaws.com` |
| Ruta `ANY /{proxy+}` | → BFF, con JWT Authorizer de Cognito (audience = client id) |
| Ruta `OPTIONS /{proxy+}` | → BFF, sin authorizer (el preflight no manda token) |

**IPs al apagar el laboratorio:** las IPs públicas normales cambian cuando el
laboratorio detiene las instancias. El BFF tiene una **Elastic IP** (`3.213.118.143`)
justamente para que la API Gateway no apunte a una IP vieja: las integraciones `ANY` y
`OPTIONS` de la API Gateway usan `http://3.213.118.143:8080/{proxy}`. La EC2 #2 no la
necesita (el BFF la alcanza por su IP privada `172.31.39.91`). Una Elastic IP sin asociar
cobra: liberarla al terminar la evaluación. Al reiniciar, los contenedores arrancan solos
(`restart: unless-stopped`).

Grupos de seguridad: `cleanfresh-sg-bff` (8080 público, 22 desde la IP del
autor), `cleanfresh-sg-ms` (8081–8085 solo desde `cleanfresh-sg-bff`) y
`cleanfresh-sg-rds` (5432 solo desde `cleanfresh-sg-ms`).

**CORS**: lo resuelve el propio BFF; no se configura CORS en API Gateway (si se
activa, pisa los headers del BFF y rompe el preflight).

**Operación:** el swap de la EC2 #2 es persistente (`/etc/fstab`). Con
`t3.small` los 5 servicios Java dejaron la máquina sin responder tras un
reinicio; por eso es `t3.medium`.

**Costo real:** son recursos reales de AWS con costo — apagar o eliminar las
2 EC2, la RDS y la API Gateway después de la evaluación si no se van a usar.

---

## Variables de Entorno

### Frontend (`.env` en raíz del proyecto)
```
REACT_APP_COGNITO_AUTHORITY=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Xj0EYCnUK
REACT_APP_COGNITO_CLIENT_ID=37oq5a3q9ur02q13k6c8rg6mct
REACT_APP_COGNITO_DOMAIN=https://us-east-1xj0eycnuk.auth.us-east-1.amazoncognito.com
REACT_APP_API_SCOPE=https://api.cleanfresh.com/access_as_user
REACT_APP_BFF_URL=https://0ksy5y3586.execute-api.us-east-1.amazonaws.com/api
```

### BFF (variables de entorno del proceso, no `.env`)
```
COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Xj0EYCnUK
COGNITO_CLIENT_ID=37oq5a3q9ur02q13k6c8rg6mct
COGNITO_DOMAIN=https://us-east-1xj0eycnuk.auth.us-east-1.amazoncognito.com   # opcional: nombre legible del cliente (Spec 032)
```

---

## Configuración Frontend (`src/authConfig.js`)

```javascript
import { UserManager } from "oidc-client-ts";

export const cognitoAuthConfig = {
  authority: process.env.REACT_APP_COGNITO_AUTHORITY,
  client_id: process.env.REACT_APP_COGNITO_CLIENT_ID,
  redirect_uri: `${window.location.origin}/`,
  response_type: "code",
  scope: `openid email profile ${process.env.REACT_APP_API_SCOPE}`,
  post_logout_redirect_uri: window.location.origin,
  loadUserInfo: true,
};

export const cognitoDomain = process.env.REACT_APP_COGNITO_DOMAIN;

// Compartida entre el AuthProvider (index.js) y apiService.js (que no
// es un componente y no puede usar el hook useAuth()).
export const userManager = new UserManager(cognitoAuthConfig);
```

> **IMPORTANTE:** el BFF recibe el **access_token** (no el idToken) —
> al revés que con Azure CIAM. Acá sí hay un scope de API custom
> (`https://api.cleanfresh.com/access_as_user`), así que el access
> token es el que corresponde para autorizar llamadas al BFF.
> `apiService.js` usa `user.access_token`.
>
> **Ojo con los claims:** el access token de Cognito **no** trae
> `name`/`email`/`preferred_username` (esos son del idToken) — el
> único identificador de la persona ahí es `username`. El BFF lo usa en
> vez de `preferred_username` (ver `OrderService.java`).

> **Callback:** la app usa `${window.location.origin}/` como `redirect_uri`, así
> que el App Client de Cognito debe tener registrado `http://localhost:3000/`
> (con barra final y `http`). `public/redirect.html` es un resto de EP1: ya no
> se usa y no hace falta registrarlo. Si falta la URL exacta, el Hosted UI
> responde `redirect_mismatch` al pulsar "Iniciar sesión".

---

## Configuración BFF (`src/main/resources/application.yaml`)

```yaml
server:
  port: 8080

spring:
  application:
    name: ms-cleanfresh-bff
  security:
    oauth2:
      resourceserver:
        jwt:
          # Los access tokens de Cognito no traen claim "aud" (a diferencia
          # de Azure) — no se usa "audiences" acá, esa validación la hace
          # CognitoTokenValidator contra "client_id" (ver SecurityConfig).
          issuer-uri: ${COGNITO_ISSUER_URI}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never

cognito:
  client-id: ${COGNITO_CLIENT_ID}

microservices:
  orders:
    base-url: http://localhost:8081
  catalog:
    base-url: http://localhost:8082
```

---

## Estructura del BFF (`ms-cleanfresh-bff`)

```
src/main/java/com/cleanfresh/ms_cleanfresh_bff/
├── config/
│   └── SecurityConfig.java        # JWT validation, CORS, stateless, @EnableMethodSecurity
├── controller/
│   ├── HealthController.java      # GET /api/health — requiere token (solo /actuator/health es público)
│   ├── OrderController.java       # GET /api/orders, /api/orders/{id}, /api/orders/estado/{estado}; PUT /api/orders/{numeroOrden}/estado (Admin/Operador, Spec 030)
│   ├── NotificacionController.java # GET /api/notificaciones, POST /api/notificaciones/leidas — filtrados por rol (Spec 030)
│   ├── CatalogController.java     # GET /api/catalog, /api/catalog/{id}, /api/catalog/disponibles
│   ├── ReportesController.java    # GET /api/reportes — solo Admin (EP2, respuesta fija)
│   └── AuditoriaController.java   # GET /api/auditoria — solo Admin (EP2, respuesta fija)
├── service/
│   ├── OrderService.java
│   ├── CatalogService.java
│   ├── NotificacionService.java   # qué avisos ve cada rol (Spec 030)
│   └── AccesoPorRol.java          # rol, username y sucursal en turno (compartido)
├── repository/
│   ├── OrderRepository.java       # RestClient → ms-cleanfresh-orders:8081
│   ├── CatalogRepository.java     # RestClient → ms-cleanfresh-catalog:8082
│   └── NotificacionRepository.java # RestClient → ms-cleanfresh-notificaciones:8083
└── dto/
    ├── OrderResponse.java
    └── ServiceResponse.java
```

### Roles y autorización en el BFF

- Los roles vienen en el claim `cognito:groups` del JWT (grupos del User Pool), con prefijo `ROLE_`
- `SecurityConfig` extrae roles con `JwtGrantedAuthoritiesConverter` usando `setAuthoritiesClaimName("cognito:groups")`
- Los controllers usan `@PreAuthorize("hasAnyRole('Admin','Operador','Cliente')")` según corresponda
- `CognitoTokenValidator` (en `config/`) reemplaza la validación de `audiences` de Azure: chequea `token_use == "access"` y `client_id` contra el de esta app, sobre el `JwtDecoder` custom que registra `SecurityConfig`

---

## Estructura del Frontend (`cleanfresh-frontend`)

```
src/
├── authConfig.js                  # Configuración Cognito + UserManager compartido
├── index.js                       # AuthProvider (react-oidc-context) wrapping App
├── App.jsx                        # AuthGuard -> Navbar + BentoDashboard
├── App.css                        # Estilos globales + bento grid
├── components/
│   ├── AuthGuard.jsx               # Pantalla de login / protección de la app
│   ├── Navbar.jsx                  # Logo, nombre usuario, rol, botón logout
│   └── NotificationBell.jsx        # Campanita de avisos con contador (Spec 030)
├── pages/
│   └── BentoDashboard.jsx         # Dashboard bento único con tarjetas por rol
└── services/
    └── apiService.js              # Llamadas al BFF con access_token como Bearer
```

### Diseño actual

El frontend usa un **diseño bento grid** en una sola página. No hay React Router ni sidebar. Según el rol del usuario se muestran diferentes tarjetas:

| Rol | Tarjetas visibles |
|---|---|
| Admin | Estado BFF, KPIs, Órdenes recientes, Catálogo, Reportería por sucursal, Auditoría |
| Operador | KPIs operacionales, Órdenes, Catálogo |
| Cliente | Mis órdenes, Catálogo con botón solicitar, Puntos de fidelidad |

### Lectura de roles

Con `react-oidc-context`, `auth.user` ya refleja el resultado completo
del login/silent renew procesado por `oidc-client-ts` — no hace falta
el workaround que sí necesitaba MSAL (forzar `acquireTokenSilent` tras
cada F5 porque la cuenta cacheada podía traer claims incompletos). Se
lee directo:

```javascript
const auth = useAuth();
const roles = auth.user?.profile?.["cognito:groups"] || [];
const isAdmin = roles.includes("Admin");
```

---

### Avisos por rol (Spec 030)

`orders` publica `ORDEN_CREADA` (al crear una orden) y `ORDEN_LISTA` (al pasar a
`DESPACHADO`) en la cola SQS; `notificaciones` los guarda como avisos dirigidos en
su base: el de pedido nuevo va a la **sucursal** de la orden (le llega al Operador
que la tenga en turno) y el de pedido listo al **cliente** dueño (su `username`).
El BFF decide qué ve cada rol (el Cliente solo lo suyo por el `username` del token;
el Operador los de su sucursal en turno; el Admin todos, en solo lectura) y el
frontend los muestra en la campanita. El cambio de estado de una orden ahora se
guarda en el backend (`PUT /api/orders/{numeroOrden}/estado`).

**Nombre legible (Spec 032):** una orden guarda `cliente` (el `username` de Cognito, un UUID, que
dice de quién es) y, aparte, `clienteNombre` (el `name` o el `email` del usuario). El BFF lo pide a
Cognito con el access token del usuario (`/oauth2/userInfo`, usa `COGNITO_DOMAIN`); nunca lo manda el
navegador. Las pantallas y los avisos muestran `clienteNombre` y, si no existe, `cliente`. El
Cliente ahora elige la sucursal en el catálogo y ve qué servicios hay disponibles allí.

---

## Módulos del Sistema

### Servicios de lavandería (catálogo)
- Lavado y secado — $18.000
- Lavado en seco — $25.000
- Planchado — $9.500
- Lavado de edredones — $32.000
- Servicio exprés — $12.000

### Estados de órdenes
`CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO / CANCELADO`

---

## Comandos para levantar el entorno local

```powershell
# Configurar Java 21 (requerido en TODAS las terminales Java)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# (EP2) orders, catalog y notificaciones necesitan una base PostgreSQL: sin
# DB_URL/DB_USER/DB_PASSWORD no arrancan. Ver el README de cada repo para levantar una
# con Docker (orders_db / catalog_db, un usuario por servicio).
# (EP2) SQS: orders y notificaciones lo traen apagado (SQS_ENABLED=false); para
# activarlo y probarlo en local con ElasticMQ, ver el README de cada uno.

# Terminal 1 — ms-cleanfresh-orders (puerto 8081)
cd C:\Users\franc\ms-cleanfresh-orders
$env:DB_URL="jdbc:postgresql://localhost:5432/orders_db"
$env:DB_USER="orders_user"; $env:DB_PASSWORD="<clave>"
java -jar target/ms-cleanfresh-orders-0.0.1-SNAPSHOT.jar

# Terminal 2 — ms-cleanfresh-catalog (puerto 8082)
cd C:\Users\franc\ms-cleanfresh-catalog
$env:DB_URL="jdbc:postgresql://localhost:5432/catalog_db"
$env:DB_USER="catalog_user"; $env:DB_PASSWORD="<clave>"
java -jar target/ms-cleanfresh-catalog-0.0.1-SNAPSHOT.jar

# Terminal 3 — BFF (puerto 8080)
cd C:\Users\franc\ms-cleanfresh-bff
$env:COGNITO_ISSUER_URI="https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Xj0EYCnUK"
$env:COGNITO_CLIENT_ID="37oq5a3q9ur02q13k6c8rg6mct"
java -jar target/ms-cleanfresh-bff-0.0.1-SNAPSHOT.jar

# Terminal 4 — Frontend (puerto 3000)
cd C:\Users\franc\cleanfresh-frontend
npm start

# Si necesitas recompilar algún proyecto Java (Windows):
.\mvnw.cmd clean package -DskipTests
```

---

## Requisitos EP1 — Pauta de Evaluación

> Los títulos de los indicadores ("MSAL"/"BFF") quedaron como los dio
> el profesor originalmente; el mecanismo de auth detrás cambió de
> MSAL/Azure a `react-oidc-context`/Cognito, pero lo que evalúa cada
> punto (login real con IDaaS, roles desde el token, JWT validado en
> el BFF) es lo mismo.

### Indicador 1 — MSAL (60%)

Para nota máxima se requiere:
- ✅ Login y logout funcionando correctamente
- ✅ Token adjuntado en todas las llamadas al backend
- ✅ Roles y scopes leídos correctamente desde los claims del token
- ✅ Guards/protección de vistas según rol
- ✅ Tokens obtenidos para consumir el API Gateway

### Indicador 2 — BFF (40%)

Para nota máxima se requiere:
- ✅ Validar issuer del token correctamente
- ✅ Validar audience del token
- ✅ Verificar firma y vigencia del token
- ✅ Aplicar autorización por rol en los endpoints (`@PreAuthorize`)
- ✅ Responder con códigos de error adecuados (401, 403)

### Requisitos adicionales del profesor (instrucciones específicas EP1)

- ✅ El código del frontend debe estar completo, modular, sin errores de compilación y con vistas funcionales
- ✅ El código de todos los componentes del backend debe compilar, seguir buenas prácticas y responder a pruebas básicas
- ✅ El backend debe corresponder a **varios microservicios** construidos en Java con Spring Boot
- ✅ El frontend debe implementar el flujo de login con IDaaS y utilizar el JWT en las llamadas al backend
- ✅ El backend debe incluir filtros que validen el JWT recibido desde el IDaaS
- ⚠️ La integración del backend con **base de datos cloud** debe estar configurada correctamente mediante entidades, repositorios y propiedades de conexión (**PENDIENTE**)
- ✅ Los archivos `.gitignore` deben estar correctamente configurados para no subir `.env` ni `node_modules`
- ✅ Entrega mediante enlaces de repositorios GitHub a AVA y correo del docente

---

## Arquitectura del Sistema (EP1)

```
Frontend React (localhost:3000)
        |
        | access_token (Bearer)
        v
BFF Spring Boot (localhost:8080)
        |
        |-- valida JWT contra el User Pool de Cognito
        |-- extrae rol del claim "cognito:groups"
        |-- valida token_use=access y client_id (CognitoTokenValidator)
        |-- aplica @PreAuthorize por rol
        |
        |-- RestClient --> ms-cleanfresh-orders (localhost:8081)
        |-- RestClient --> ms-cleanfresh-catalog (localhost:8082)
        |
AWS Cognito (User Pool, Hosted UI)
        |
        |-- emite access_token con claims "cognito:groups", "client_id", "token_use", "username"
        |-- issuer: https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Xj0EYCnUK
```

---

## Decisiones técnicas importantes

1. **access_token como Bearer:** a diferencia de Azure CIAM (que no soportaba scopes de API custom, por eso se usaba el idToken), acá sí hay un scope propio (`https://api.cleanfresh.com/access_as_user`), así que el BFF valida el **access_token**. `apiService.js` usa `user.access_token` en vez de `result.idToken`.

2. **Sin claim `aud` en el access token:** Cognito no lo incluye (a diferencia de Azure). El BFF reemplaza esa validación con `CognitoTokenValidator`, que chequea `token_use == "access"` y `client_id` contra el de esta app.

3. **Roles en el token:** los roles llegan como grupos del User Pool, en el claim `cognito:groups`. El BFF los extrae con `JwtGrantedAuthoritiesConverter` y los prefija con `ROLE_` (ej: `ROLE_Admin`).

4. **Identidad limitada en el access token:** el access token no trae `name`/`email`/`preferred_username` (son del idToken) — solo `username`. El BFF usa ese claim donde antes usaba `preferred_username` (mapeo operador→sucursal, autor de un pedido nuevo).

5. **`onSigninCallback` en vez de manejo manual de cuenta activa:** `oidc-client-ts` no tiene el problema de caché que tenía MSAL (cuenta reconstruida con claims incompletos tras F5) — no hace falta el workaround de forzar `acquireTokenSilent` que sí era necesario antes.

6. **Diseño bento grid:** Se eliminó el sidebar y React Router. Toda la interfaz está en un solo `BentoDashboard.jsx` con tarjetas bento según rol.

---

## Documentación por entrega (`EP1/` y `EP2/`)

La documentación se separa por entrega, en la raíz del repo del frontend
(`CLAUDE.md` y `README.md` se quedan en la raíz: Claude Code carga el primero
automáticamente y el segundo es la portada del repo):

- **`EP1/`** — entrega 1 (cerrada): Cognito + BFF + API Gateway + EC2.
  Incluye `EP1/specs/` (specs/fixes 001–028), `EP1/EVIDENCIA-EP1.md` y las
  explicaciones `EP1/EXPLICACION-*.md`.
- **`EP2/`** — entrega 2 (en curso): toda documentación nueva va acá.
  Arquitectura objetivo en [`EP2/ARQUITECTURA.md`](EP2/ARQUITECTURA.md) e
  índice de specs (029 en adelante) en [`EP2/specs/README.md`](EP2/specs/README.md).

Los cambios funcionales se documentan con metodología Spec-Driven
Development manual (índice en [`EP1/specs/README.md`](EP1/specs/README.md)):
cada ítem tiene su spec o fix con Acceptance Criteria, numerados de forma
única sin importar el tipo. La numeración **continúa** en EP2 (029, 030, ...)
para que "Spec NNN" siga siendo inequívoco entre entregas. Ver el índice antes
de tocar algo ya resuelto.

---

## Pendientes

- [x] Conectar microservicios a **base de datos cloud** con entidades JPA y repositorios Spring Data — hecho en EP2 (Spec 029): `orders` y `catalog` sobre PostgreSQL en RDS, verificado en vivo
- [x] Migración a Cognito probada en vivo (Fix 027) — login, interceptor y creación de pedidos por Cliente confirmados funcionando; se corrigieron 3 problemas reales en el proceso (typo de `.env`, jar del BFF desactualizado, mismatch `username`/email en "Tus pedidos") y se agregó validación de `scope` en `CognitoTokenValidator`
- [ ] **Al terminar la evaluación:** liberar la Elastic IP `3.213.118.143` y apagar o eliminar las 2 EC2, la RDS y la API Gateway (cobran mientras existan)
- [ ] El mapa `SUCURSAL_POR_OPERADOR` en `OrderService.java` (BFF) sigue con el valor viejo de Azure (`operador@cleanfreshchain.onmicrosoft.com`), así que no coincide con el `username` (UUID) del Operador de Cognito. No bloquea nada: el Operador filtra por su selector "Sucursal en turno" (`?sucursal=`), y el mapa solo es un respaldo
- [x] Grupos de Cognito `Admin`/`Operador`/`Cliente` confirmados en vivo en EP2: el Admin ve Reportería/Auditoría y recibe 200 en `/api/reportes` y `/api/auditoria`; el Operador recibe 403 en ambas
- [ ] **Seguridad:** `GET /api/orders` devuelve todas las órdenes a un Cliente; hoy es la pantalla la que filtra las suyas en el navegador. Debería filtrarse en el BFF por el `username` del token (como ya se hace con los avisos)
- [ ] Texto "JWT Azure CIAM" en la tarjeta de estado del panel Admin: resto cosmético de la migración a Cognito
- [ ] Verificar el 403 de `/api/reportes` y `/api/auditoria` con el rol Cliente (solo se probó Operador)
- [x] Corregir que al hacer F5 con rol Admin/Operador no muestre vista de Cliente — ya no aplica el workaround original de MSAL; `react-oidc-context` no tiene ese problema de caché
- [x] Mejorar diseño bento: ajustar overflow de tabla de órdenes, max-height del JSON del BFF — resuelto (`overflow-x: auto` + columnas compactas en la tabla, `max-height: 300px` en el bloque de estado del BFF)
- [ ] Ver `EP1/specs/README.md` → "Pendiente de verificación visual" para los ítems de responsive que faltan probar en navegador
