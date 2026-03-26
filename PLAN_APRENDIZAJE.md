# Plan de aprendizaje del proyecto — v2

Este documento es tu hoja de ruta permanente.
No tiene fechas. Tiene iteraciones.
Cada iteración tiene un objetivo, un conjunto de cosas que leer/probar/romper, y un entregable claro.
No pases a la siguiente iteración hasta que puedas cumplir el entregable de la actual.

---

## Iteración 1 — Mapa del sistema

### Objetivo
Saber qué piezas existen, para qué sirven, y cómo se relacionan.
Sin entrar todavía en código. Solo inventario y modelo mental.

### Qué estudiar
- Lee el `GUIA_APRENDIZAJE.md` de este repositorio
- Lee los dos `application.yml` (tienen comentarios en todas las líneas)
- Lee el `docker-compose.yml` entero
- Abre Redpanda Console (http://localhost:8080), Grafana (http://localhost:3000) y navega sin objetivo

### Entregable 1A — Dibujo del sistema
Haz un dibujo a mano (papel o cualquier herramienta) con flechas que muestre:
```
Postman → order-service → Postgres (schema: orders)
                       → Kafka (topic: order-events)
notification-service ← Kafka
notification-service → pgvector / Postgres (schema: notifications)
notification-service → FakeLlmAdapter / BedrockLlmAdapter

Prometheus ← scraping ← /actuator/prometheus (ambos servicios)
Grafana    ← Prometheus
Jaeger     ← OTel Collector ← ambos servicios (cuando sampling > 0)
```
No hace falta que sea bonito. Hace falta que esté en tu cabeza.

### Entregable 1B — Tabla de componentes (sin mirar nada)

| Componente | Para qué sirve | ¿Síncrono o asíncrono? | ¿Local o externo? |
|---|---|---|---|
| order-service | | | |
| notification-service | | | |
| Redpanda | | | |
| Postgres | | | |
| pgvector | | | |
| FakeLlmAdapter | | | |
| BedrockLlmAdapter | | | |
| KnowledgeBaseSeeder | | | |
| NotificationGuardrails | | | |
| DLQ (order-events-dlq) | | | |
| Grafana | | | |
| Jaeger | | | |
| Prometheus | | | |
| Redpanda Console | | | |

Cuando puedas rellenar la tabla y explicar el dibujo de memoria, pasas a la Iteración 2A.

---

## Iteración 2A — El flujo desde HTTP hasta Kafka

### Objetivo
Entender exactamente qué pasa en order-service desde que entra el JSON hasta que el mensaje sale por Kafka.

### Qué leer (en este orden)
1. `OrderRequest` — ¿Qué campos tiene? ¿Qué validaciones? ¿Por qué @NotBlank y no @NotNull?
2. `OrderController` — ¿Qué anotaciones tiene? ¿Qué recibe? ¿Qué devuelve? ¿A qué llama?
3. `CreateOrderUseCase` — ¿Es clase o interfaz? ¿Por qué?
4. `CreateOrderCommand` — ¿Qué diferencia hay con `OrderRequest`? ¿Por qué existe?
5. `OrderApplicationService` — ¿Qué hace paso a paso? ¿Qué puertos usa?
6. `Order` (domain/model) — ¿Tiene anotaciones de Spring o JPA? ¿Por qué no?
7. `OrderRepositoryPort` — ¿Interfaz o clase? ¿Quién la implementa?
8. `OrderJpaEntity` — ¿Qué diferencia hay con `Order`? ¿Por qué dos objetos?
9. `PostgresOrderRepositoryAdapter` — ¿Cómo convierte Order a OrderJpaEntity?
10. `OrderEventPublisherPort` — ¿Interfaz o clase? ¿Quién la implementa?
11. `OrderCreatedEvent` — ¿Qué campos viajan por Kafka? ¿Por qué estos y no el objeto `Order` entero?
12. `KafkaOrderEventPublisher` — ¿Cómo publica? ¿Qué es KafkaTemplate?

### Qué probar
- Haz POST /orders y mira los logs de order-service línea a línea
- Mira el mensaje en Redpanda Console → order-events → Messages
- Compara los campos del mensaje Kafka con los campos de `OrderCreatedEvent`

### Preguntas que debes responder
- ¿Cuántos objetos Java distintos representan un "pedido" en order-service? Nómbralos y di para qué sirve cada uno
- ¿En qué capa está la lógica de negocio? ¿Podría estar en el controller? ¿Por qué no?
- ¿Por qué `OrderApplicationService` no usa `KafkaTemplate` directamente?
- ¿Qué pasaría si la BD falla al guardar pero Kafka sí publica? ¿Es un problema?

### Entregable
Contar de memoria: "Entra el JSON por el controller, que llama a... que hace... que llama a... que publica..." sin mirar código.

---

## Iteración 2B — El flujo desde Kafka hasta la notificación

### Objetivo
Entender exactamente qué pasa en notification-service desde que llega el mensaje de Kafka hasta que se guarda la notificación.

### Qué leer (en este orden)
1. `OrderEventsKafkaListener` — ¿Qué anotaciones tiene? ¿Qué recibe? ¿A qué llama?
2. `OrderCreatedEvent` (en notification-service) — ¿Es el mismo objeto que en order-service?
3. `NotificationApplicationService` — Leer ENTERO. Es el corazón del sistema. Línea por línea:
   - ¿Dónde está el check de idempotencia? ¿Qué consulta hace?
   - ¿Qué hace `embeddingPort.embed()`?
   - ¿Qué hace `vectorStorePort.searchSimilar()`? ¿Qué devuelve?
   - ¿Qué es el "prompt" que se construye? ¿Qué lleva dentro?
   - ¿Qué hace `llmPort.generate()`?
   - ¿Qué hace `guardrails.validate()`?
   - ¿Qué se guarda finalmente?
4. `FakeEmbeddingAdapter` — ¿Qué devuelve `embed()`? ¿Por qué sirve para tests?
5. `FakeLlmAdapter` — ¿Qué devuelve `generate()`? ¿Qué formato tiene?
6. `NotificationGuardrails` — Leer ENTERO:
   - ¿Qué valida primero? ¿Segundo? ¿Tercero?
   - ¿Qué hace si el canal no está permitido?
   - ¿Cómo detecta PII (emails, teléfonos)?
   - ¿Qué devuelve si todo falla? ¿Qué es el "fallback"?
7. `PostgresVectorStoreAdapter` — ¿Qué SQL usa para buscar? ¿Qué es el operador `<=>`?
8. `KnowledgeBaseSeeder` — ¿Cuándo se ejecuta? ¿Qué lee? ¿Qué guarda en pgvector?

### El contrato Kafka y la entrega garantizada (importante)
Lee en `application.yml` del notification-service:
```yaml
ack-mode: record
retry.max-attempts: 3
retry.backoff-ms: 1000
```
Pregunta clave: si notification-service procesa el mensaje correctamente pero luego crashea ANTES de confirmar el offset a Kafka, ¿qué pasa? ¿Procesará el mensaje dos veces cuando vuelva a levantarse? ¿Cómo lo evita el sistema?
(Pista: busca `existsBySourceOrderIdAndChannel`)

### Preguntas que debes responder
- ¿Qué es RAG? Explícalo con este proyecto como ejemplo concreto
- ¿Por qué el embedding del texto del pedido se usa para buscar en knowledge_chunks?
- ¿Qué contiene knowledge_chunks? ¿Quién lo llena y cuándo?
- ¿Por qué la idempotencia está en notification-service y no en order-service?
- ¿Qué pasaría si guardrails rechaza el JSON del LLM? ¿Se pierde la notificación o hay fallback?
- ¿Cuántas veces puede recibir notification-service el mismo mensaje? ¿Por qué?

### Entregable
Contar el flujo completo de memoria (2A + 2B juntos), nombrando cada clase, sin mirar código.

---

## Iteración 3 — Arquitectura hexagonal

### Objetivo
Entender por qué el proyecto está organizado así y no de otra manera.
No solo saber qué hace cada clase, sino por qué está donde está.

### La estructura
```
adapters/
  inbound/rest/         ← entra del mundo exterior al dominio (HTTP)
  outbound/persistence/ ← sale del dominio hacia BD
  outbound/messaging/   ← sale del dominio hacia Kafka
  outbound/ai/          ← sale del dominio hacia LLM
application/
  service/              ← lógica de negocio (orquesta, no implementa)
  usecase/              ← contratos de entrada (interfaces)
domain/
  model/                ← objetos puros de negocio (sin Spring, sin JPA)
  port/out/             ← contratos de salida (interfaces)
```

### Qué buscar en el código
- En `domain/model/`: ¿hay alguna anotación de Spring (`@Component`, `@Service`)? ¿De JPA (`@Entity`)? ¿Por qué no?
- En `domain/port/out/`: ¿son clases o interfaces?
- En `adapters/outbound/ai/`: busca `@ConditionalOnProperty`. ¿Qué hace exactamente? ¿Cómo decide Spring qué adaptador cargar?
- En `application/service/`: ¿`OrderApplicationService` importa `KafkaTemplate`? ¿Importa `JpaRepository`? ¿Por qué no?

### Preguntas que debes responder
- ¿Qué es un "puerto" en hexagonal? ¿Interface o clase?
- ¿Qué es un "adaptador"? ¿Qué diferencia hay entre inbound y outbound?
- ¿Por qué hay una interfaz `OrderRepositoryPort` si solo tiene una implementación?
- Si mañana cambias Kafka por RabbitMQ, ¿qué clases tocas? ¿Cuáles NO tocas?
- Si mañana cambias Postgres por MongoDB, ¿qué clases tocas? ¿Cuáles NO tocas?
- ¿Qué ventaja da tener `FakeLlmAdapter` y `BedrockLlmAdapter` implementando la misma interfaz?
- En tu proyecto monolítico anterior el Service llamaba directamente al Repository. ¿Qué problema tiene eso según hexagonal?
- ¿Qué es `@ConditionalOnProperty` y por qué es clave en los adaptadores de IA?

### Entregable
Responder estas dos preguntas sin dudar:
1. "¿Por qué hay interfaces si solo tienen una implementación?"
2. "Si hay que cambiar Kafka por RabbitMQ, ¿qué cambias y qué no tocas?"

---

## Iteración 4 — La base de datos por dentro

### Objetivo
Ver exactamente qué se guarda en Postgres y entender la inicialización de schemas y pgvector.

### El schema.sql y por qué existe
Lee `notification-service/src/main/resources/schema.sql`.
Luego lee en `application.yml`:
```yaml
sql:
  init:
    mode: always
    schema-locations: classpath:schema.sql
```
Pregunta clave: Hibernate crea tablas automáticamente con `ddl-auto: update`, ¿por qué entonces existe schema.sql?
(Pista: Hibernate no sabe qué es una columna `VECTOR`. No puede crearla. Necesita que alguien lo haga antes.)

### Conectar a Postgres y ver los datos reales
Conecta con IntelliJ Database Tool o con DBeaver a:
- Host: localhost
- Puerto: 5432
- Base de datos: app
- Usuario: app
- Contraseña: app

Una vez dentro, explora:
- Schema `orders` → tabla `orders` → ¿qué filas hay? ¿qué columnas?
- Schema `notifications` → tabla `notification_records` → ¿qué se guardó?
- Schema `notifications` → tabla `knowledge_chunks` → ¿qué filas hay? ¿qué aspecto tiene la columna `embedding`?

### Preguntas que debes responder
- ¿Por qué hay dos schemas (`orders` y `notifications`) en la misma base de datos? ¿No sería mejor una base de datos por servicio?
- ¿Qué columnas tiene `knowledge_chunks`? ¿Qué tipo tiene la columna `embedding`?
- ¿Cuántos knowledge_chunks hay? ¿De dónde vienen?
- ¿Qué es `CREATE EXTENSION IF NOT EXISTS vector`? ¿Sin esto funciona pgvector?
- ¿Por qué notification-service necesita `schema.sql` y order-service no?

### Entregable
Ver con tus propios ojos las 3 tablas en la BD y poder explicar qué hay en cada una.

---

## Iteración 5 — Romper el sistema

### Objetivo
Aprender el comportamiento real del sistema bajo condiciones adversas.
Anotar todo en una tabla al final.

### Experimento 1: Idempotencia
- Haz varios POST /orders con datos distintos
- Luego busca en el código la línea exacta de idempotencia en `NotificationApplicationService`
- Comprueba en GET /notifications que cada orderId tiene exactamente UNA notificación
- Reflexión: si ese check no existiera y Kafka entregara el mensaje dos veces, ¿qué pasaría?

### Experimento 2: Validaciones del endpoint
- POST sin el campo `price` → ¿qué error devuelve? ¿qué clase lo lanza?
- POST con `quantity: 0` → ¿qué error? ¿qué anotación lo valida?
- POST con `price: -5` → ¿qué error?
- POST con body vacío `{}` → ¿qué pasa?
- ¿En qué capa están estas validaciones? ¿Podrían estar en otra capa?

### Experimento 3: Guardrails de canal
- Edita `notification-service/src/main/resources/application.yml`
- Cambia `allowed-channels: email,sms,push` por `allowed-channels: sms,push` (quita email)
- Ctrl+F9 y reinicia notification-service
- Haz POST /orders
- ¿Qué canal tiene la notificación? ¿Es fallback? ¿Qué pasó exactamente en guardrails?
- Vuelve a poner `email,sms,push` cuando termines

### Experimento 4: Caída y recuperación del notification-service
- Para notification-service desde IntelliJ
- Haz 3 POST /orders (los mensajes se quedarán en Kafka sin consumir)
- Mira en Redpanda Console cuántos mensajes hay en order-events sin consumir
- Levanta notification-service
- ¿Procesa los mensajes atrasados? ¿En qué orden?
- ¿Por qué puede hacer esto Kafka y no podría si la comunicación fuera HTTP directo?

### Experimento 5: PII en la notificación
- Edita `FakeLlmAdapter` para que devuelva un body con un email: `"Contact us at test@example.com for support."`
- Ctrl+F9 y reinicia notification-service
- Haz POST /orders
- Mira el body en GET /notifications: ¿fue redactado el email?
- ¿Qué regex usa `NotificationGuardrails` para detectar emails?

### Experimento 6: Ver la DLQ
- Abre Redpanda Console → Topics → `order-events-dlq`
- ¿Hay mensajes? ¿Por qué (no) los hay?
- Entiende cuándo iría un mensaje ahí: falla 3 veces → DLQ
- Reflexión: si no existiera la DLQ y un mensaje fallara siempre, ¿qué pasaría con todos los mensajes posteriores de ese topic?

### Entregable — Tabla de incidencias

| Experimento | Qué esperaba | Qué pasó | Qué clase/mecanismo lo gestionó |
|---|---|---|---|
| Idempotencia | | | |
| Validación sin price | | | |
| Guardrails sin email | | | |
| Caída del servicio | | | |
| PII redactado | | | |
| DLQ | | | |

---

## Iteración 6 — Tests

### Objetivo
Entender los tests existentes y escribir uno nuevo.
Los tests son documentación ejecutable. Si no puedes escribir un test de algo, es que no lo entiendes del todo.

### Qué leer
- `notification-service/src/test/java/.../RagGoldenTest.java` — lee entero
  - ¿Qué prueba exactamente?
  - ¿Qué es `@Testcontainers`? ¿Qué levanta?
  - ¿Por qué usa `pgvector/pgvector:pg16` como imagen?
  - ¿Qué es `@DynamicPropertySource`?
  - ¿Qué es `withInitScript`? ¿Qué SQL ejecuta?
- Busca si hay más tests en el proyecto. ¿Hay tests de `NotificationGuardrails`?

### Qué escribir
Escribe un test nuevo para `NotificationGuardrails`. Sin Testcontainers, sin Spring — un test unitario puro.

Casos mínimos a cubrir:
1. Canal no permitido → debe devolver fallback
2. Body con email → el email debe estar redactado en el resultado
3. Body demasiado largo → debe truncarse a `max-body-length`
4. JSON inválido del LLM → debe devolver fallback

Pista: `NotificationGuardrails` es una clase Java normal. Puedes instanciarla directamente en el test sin Spring.

### Preguntas que debes responder
- ¿Qué diferencia hay entre un test unitario y un test de integración?
- ¿Por qué `RagGoldenTest` necesita una base de datos real (Testcontainers) y no un mock?
- ¿Qué ventaja tiene `FakeEmbeddingAdapter` sobre un `@MockBean` en los tests?
- ¿Qué es un "golden test"? ¿Por qué se llama así?

### Entregable
Al menos 2 tests unitarios de `NotificationGuardrails` que pasen (verde en IntelliJ).

---

## Iteración 7 — Hacer cambios tú solo

### Objetivo
Dejar de ser espectador. Modificar el sistema sin romper la arquitectura hexagonal.

### Cambio 0 (calentamiento): Cambiar el mensaje de log
En `NotificationApplicationService`, cuando se detecta un duplicado y se hace return, añade un log:
```java
log.info("Notificación duplicada ignorada para orderId={}", event.orderId());
```
Verifica que aparece en los logs cuando mandas el mismo pedido dos veces.
Objetivo real: perder el miedo a tocar código.

### Cambio 1 (fácil): Nuevo endpoint de detalle
Añade `GET /notifications/{id}` en notification-service.
- ¿En qué clase añades el endpoint? (`adapters/inbound/rest`)
- ¿En qué interfaz añades el método? (`domain/port/out` o `application/usecase`)
- ¿En qué clase implementas la consulta? (adapter de persistencia)
- Prueba con el id de una notificación real de GET /notifications

### Cambio 2 (fácil): Nuevo campo en el pedido
Añade el campo `customerEmail` a `OrderRequest` (opcional, sin @NotNull).
- ¿Aparece en la respuesta de POST /orders?
- ¿Viaja por Kafka? (Tendrías que añadirlo a `OrderCreatedEvent` también)
- ¿Llega a notification-service?

### Cambio 3 (medio): Nueva métrica
Añade un Counter en `NotificationApplicationService` que cuente fallbacks:
```java
Counter.builder("notifications.fallback.total").register(meterRegistry).increment();
```
- Verifica en http://localhost:8082/actuator/prometheus que aparece la métrica
- Provoca un fallback (experimento de guardrails) y comprueba que el contador sube

### Cambio 4 (medio): Nuevo documento en el knowledge base
Crea `notification-service/src/main/resources/seed-docs/refund-policy.md` con contenido inventado sobre política de devoluciones.
- Reinicia notification-service y mira los logs del KnowledgeBaseSeeder
- ¿Lo cargó? ¿Cuántos chunks hay ahora en knowledge_chunks?

### Cambio 5 (difícil): Nuevo guardrail
Añade en `NotificationGuardrails` una regla: si el título supera 60 caracteres, truncarlo.
- Escribe un test unitario para este caso antes de implementarlo.

### Entregable
Cambios 0, 1 y 3 funcionando. Poder explicar qué tocaste y por qué en cada capa.

---

## Iteración 8 — Criterio de diseño

### Objetivo
Saber defender por qué el sistema está diseñado así.
No memorizar respuestas, sino entender los trade-offs.

### Preguntas de diseño (para pensar, no hay una única respuesta correcta)

1. **Kafka vs HTTP**: notification-service podría recibir el pedido por HTTP directo. ¿Por qué no se hizo así? ¿En qué situaciones sería mejor HTTP?

2. **Hexagonal vs capas clásicas**: Controller → Service → Repository añade complejidad. ¿Cuándo merece la pena hexagonal? ¿Cuándo no?

3. **Microservicio vs monolito**: ¿Por qué dos servicios separados y no uno? ¿Qué coste tiene tener dos? ¿Qué beneficio?

4. **Fake adapter vs @MockBean**: ¿Cuándo usarías un Fake adapter? ¿Cuándo un mock de test? ¿Qué diferencia práctica hay?

5. **pgvector vs base de datos vectorial dedicada**: ¿Por qué Postgres y no Pinecone, Weaviate o Elasticsearch? ¿Cuándo cambiarías?

6. **RAG vs fine-tuning**: Este proyecto usa RAG. ¿Cuándo tiene sentido RAG? ¿Cuándo haría falta fine-tuning?

7. **Guardrails en Java vs en el modelo**: Los guardrails están en código Java, no en el prompt. ¿Por qué? ¿Qué ventaja da?

8. **Idempotencia en consumer vs en producer**: El check está en notification-service. ¿Por qué ahí y no en order-service?

9. **Complejidad esencial vs accidental** (pregunta difícil): ¿Qué piezas de este proyecto son realmente necesarias para el problema de negocio? ¿Cuáles están aquí para aprender pero serían discutibles en un CRUD simple? ¿Cuáles estarían en cualquier sistema de producción serio?

### Entregable
Responder cada pregunta con una postura tuya, con argumentos. No hace falta que sea perfecta — hace falta que sea razonada.

---

## Iteración 9 — Observabilidad real

### Objetivo
Entender qué mide el sistema y cómo correlacionar logs, métricas y trazas.

### Fase 9A: Métricas (sin tocar sampling)
- Abre http://localhost:8082/actuator/prometheus
- Busca métricas que empiecen por `notifications.` — ¿cuáles ves? ¿qué mide cada una?
- Abre Prometheus (http://localhost:9090) y escribe una query: `notifications_consumed_total`
- Abre Grafana (http://localhost:3000, admin/admin) y explora si hay dashboards

### Fase 9B: Logs correlacionados
- Haz POST /orders y copia el `traceId` que aparece en los logs de order-service (formato: `traceId=abc123...`)
- Busca ese mismo traceId en los logs de notification-service
- ¿Aparece? ¿Por qué el mismo id viaja de un servicio a otro?
- ¿Qué es un `spanId`? ¿Qué diferencia hay con `traceId`?

### Fase 9C: Jaeger (requiere reactivar sampling)
- Cambia `probability: 0.0` a `probability: 1.0` en ambos `application.yml`
- Ctrl+F9 y reinicia ambos servicios
- Haz POST /orders
- Abre Jaeger (http://localhost:16686) y busca por servicio `order-service`
- Encuentra la traza de tu pedido. ¿Ves el span que cruza a notification-service?
- Cuando termines, vuelve a `probability: 0.0` si los errores de OTel te molestan

### Preguntas que debes responder
- ¿Qué diferencia hay entre una métrica, un log y una traza?
- ¿Para qué sirve el traceId en los logs si ya tienes los logs de cada servicio?
- ¿Qué es un span? ¿Qué relación tiene con un traceId?
- ¿Por qué notification-service tiene el mismo traceId que order-service para el mismo pedido?
- ¿Qué es Prometheus y qué es Grafana? ¿Son lo mismo? ¿Qué hace cada uno?

### Entregable
Ver en Jaeger la traza de un pedido cruzando los dos servicios. Poder explicar qué representa cada span.

---

## Iteración 10 — AWS Bedrock real

### Prerrequisitos
- Haber completado las iteraciones 1-8
- Crear cuenta nueva de AWS (para aprovechar créditos)
- Tener AWS CLI instalado y configurado
- Solicitar acceso a los modelos en la consola de Bedrock (Claude 3.5 Sonnet + Titan Embeddings v2)

### Qué hacer
1. Configura credenciales AWS: `aws configure`
2. En IntelliJ, añade variable de entorno: `AI_PROVIDER=bedrock`
3. Arranca notification-service
4. Haz POST /orders y compara con el fake:
   - ¿Qué devuelve Bedrock vs el fake?
   - ¿Cuánto tarda (`latencyMs`)?
   - ¿Qué `modelUsed` aparece en GET /notifications?
5. Vigila el billing en la consola de AWS desde el primer momento

### Qué aprender
- Cómo funciona `@ConditionalOnProperty` para cambiar de adaptador sin tocar código
- Qué es el AWS SDK y cómo autentica con credenciales locales
- Por qué el mismo código funciona con fake o con real sin cambiar nada excepto la config
- Diferencia entre usar el LLM real vs el fake: latencia, calidad, coste

---

## Referencia rápida

### URLs del proyecto

| URL | Servicio |
|---|---|
| http://localhost:8081/orders | POST crear pedido / GET listar pedidos |
| http://localhost:8082/notifications | GET listar notificaciones |
| http://localhost:8081/actuator/health | health order-service |
| http://localhost:8082/actuator/health | health notification-service |
| http://localhost:8081/actuator/prometheus | métricas order-service |
| http://localhost:8082/actuator/prometheus | métricas notification-service |
| http://localhost:8080 | Redpanda Console |
| http://localhost:3000 | Grafana (admin/admin) |
| http://localhost:16686 | Jaeger UI |
| http://localhost:9090 | Prometheus |

### Postman — crear pedido
```json
POST http://localhost:8081/orders
Content-Type: application/json

{
  "product": "Laptop Gaming",
  "quantity": 2,
  "price": 999.99
}
```

### PowerShell — crear pedido
```powershell
curl -X POST http://localhost:8081/orders `
  -H "Content-Type: application/json" `
  -d '{"product":"Laptop Gaming","quantity":2,"price":999.99}'
```

### Infraestructura Docker
```powershell
# Arrancar todo
cd C:\Users\MiguelÁngelRamírezCi\IdeaProjects\test
docker compose up -d

# Parar todo
docker compose down

# Ver logs de un contenedor
docker compose logs redpanda
docker compose logs postgres
```

### Rebuild en IntelliJ
Después de cambiar cualquier archivo: **Ctrl+F9** (Build → Build Project), luego reinicia el servicio.
