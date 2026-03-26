# Guía de Aprendizaje — Proyecto AI Backend Java

> Documento de referencia personal. Actualiza este archivo conforme vayas avanzando.
> Escrito asumiendo que vienes de proyectos monolíticos Spring Boot con Oracle/DB2 y CRUDs REST.

---

## ÍNDICE

1. [La pregunta clave: ¿dónde están mi Service, mi DAO y mi Controller?](#1-la-pregunta-clave)
2. [Qué es la Arquitectura Hexagonal](#2-arquitectura-hexagonal)
3. [Microservicios vs Arquitectura Hexagonal — no es lo mismo](#3-microservicios-vs-hexagonal)
4. [Tecnologías del proyecto explicadas desde cero](#4-tecnologias)
   - Kafka / Redpanda
   - Postgres + pgvector
   - RAG
   - AWS Bedrock
   - OpenTelemetry / Observabilidad
   - Guardrails
   - DLQ (Dead Letter Queue)
   - Docker Compose
5. [El mapa completo del proyecto — archivo por archivo](#5-mapa-del-proyecto)
6. [El flujo de negocio completo — qué pasa cuando haces POST /orders](#6-flujo-completo)
7. [Plan de aprendizaje por fases](#7-plan-de-aprendizaje)

---

## 1. LA PREGUNTA CLAVE

### ¿Dónde están mi Service, mi DAO y mi Controller?

En tu monolito normal tienes esto:

```
com.empresa.miapp/
  controller/
    PedidoController.java        ← recibe HTTP, llama al service
  service/
    PedidoService.java           ← lógica de negocio
  repository/
    PedidoRepository.java        ← extiende JpaRepository, habla con Oracle
  model/
    Pedido.java                  ← entidad JPA con @Entity
```

En este proyecto, las mismas tres piezas EXISTEN, pero tienen nombres distintos y están en carpetas distintas.
La lógica es la misma. Solo cambia la organización.

**Tabla de equivalencias:**

| Tu monolito                        | Este proyecto (hexagonal)                                      |
|------------------------------------|----------------------------------------------------------------|
| `controller/PedidoController.java` | `adapters/inbound/rest/OrderController.java`                   |
| `service/PedidoService.java`       | `application/service/OrderApplicationService.java`            |
| `repository/PedidoRepository.java` | `adapters/outbound/persistence/PostgresOrderRepositoryAdapter.java` |
| Extends JpaRepository              | `adapters/outbound/persistence/SpringDataOrderJpaRepository.java` |
| `model/Pedido.java` con @Entity    | DOS clases separadas: `domain/model/Order.java` (sin @Entity) y `adapters/outbound/persistence/OrderJpaEntity.java` (con @Entity) |

Esto es lo más importante de entender. Todo lo que hacías antes sigue existiendo. Solo está reorganizado.

---

## 2. ARQUITECTURA HEXAGONAL

### La idea en una frase

El "cerebro" (lógica de negocio) no sabe nada de la tecnología que lo rodea.

### Por qué existe

En tu monolito, el `PedidoService` importa directamente `PedidoRepository` que usa JPA con Oracle.
Si mañana quieres cambiar Oracle por Postgres, tienes que tocar el Service.
Si quieres hacer un test sin base de datos, no puedes sin mockear JPA.

La arquitectura hexagonal resuelve esto: el Service no habla con el Repository directamente.
Habla con una **interfaz** (llamada puerto). Quien implementa esa interfaz puede ser Postgres, Oracle, una lista en memoria, o un fake para tests. El Service no sabe ni le importa.

### Las tres zonas

```
┌─────────────────────────────────────────────────────┐
│  ZONA EXTERIOR (Adaptadores)                        │
│                                                     │
│  [HTTP]  [Kafka]          [Postgres]  [Bedrock]     │
│    ↓        ↓                 ↓          ↓          │
│  adapters/inbound/    adapters/outbound/             │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  ZONA MEDIA (Aplicación)                  │      │
│  │                                           │      │
│  │  application/service/                     │      │
│  │  application/usecase/                     │      │
│  │                                           │      │
│  │  ┌─────────────────────────────────────┐  │      │
│  │  │  NÚCLEO (Dominio)                   │  │      │
│  │  │                                     │  │      │
│  │  │  domain/model/    ← objetos puros   │  │      │
│  │  │  domain/port/out/ ← interfaces      │  │      │
│  │  └─────────────────────────────────────┘  │      │
│  └───────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────┘
```

### Qué hay en cada carpeta — con ejemplos reales del proyecto

**`domain/model/`** — Objetos Java puros. Sin Spring, sin JPA, sin nada.
Son los datos de negocio. Un `Order` es solo: id, product, quantity, price, createdAt.
No tiene `@Entity`, no tiene `@Column`, no sabe que existe una base de datos.

**`domain/port/out/`** — Interfaces que define el dominio. Son "promesas".
`OrderRepositoryPort` dice: "necesito guardar pedidos y listarlos".
No dice cómo. No sabe si es Postgres, Oracle, o una lista en memoria.

```java
// domain/port/out/OrderRepositoryPort.java
public interface OrderRepositoryPort {
    Order save(Order order);
    List<Order> findAll();
}
```

**`application/usecase/`** — Interfaces que definen qué "acciones" puede hacer el servicio.
Son el contrato externo de lo que puede hacer la aplicación.
`CreateOrderUseCase` dice: "se puede crear un pedido dando un comando".

**`application/service/`** — Aquí está tu `@Service` de toda la vida.
`OrderApplicationService` es lo que en tu monolito sería `PedidoService`.
Implementa los casos de uso. Usa los puertos (interfaces). Orquesta la lógica.

```java
// Lo que hace OrderApplicationService.createOrder():
// 1. Crea un objeto Order (dominio puro)
// 2. Lo guarda usando orderRepositoryPort.save() — no sabe si es Postgres u Oracle
// 3. Publica un evento usando orderEventPublisherPort.publish() — no sabe si es Kafka o RabbitMQ
// 4. Incrementa un contador de métricas
```

**`adapters/inbound/rest/`** — Tu Controller de siempre. Recibe HTTP y llama al caso de uso.
Solo traducción: HTTP Request → objeto Java → llamar al use case.

**`adapters/outbound/persistence/`** — Tu Repository/DAO de siempre. Habla con la base de datos.
`PostgresOrderRepositoryAdapter` implementa `OrderRepositoryPort`.
Usa `SpringDataOrderJpaRepository` (que sí extiende JpaRepository, como siempre).
Convierte entre `Order` (objeto de dominio) y `OrderJpaEntity` (objeto con @Entity para JPA).

**`adapters/outbound/messaging/`** — El publicador de Kafka.
`KafkaOrderEventPublisher` implementa `OrderEventPublisherPort`.
Usa `KafkaTemplate` para mandar mensajes. El Service no sabe nada de esto.

**`adapters/outbound/ai/`** — Los clientes de IA.
`BedrockLlmAdapter` implementa `LlmPort` — llama a AWS Bedrock de verdad.
`FakeLlmAdapter` implementa `LlmPort` — devuelve JSON hardcodeado, para tests y desarrollo local.

**`adapters/inbound/messaging/`** — El consumidor de Kafka.
`OrderEventsKafkaListener` escucha el topic Kafka y llama al caso de uso.
Es el equivalente al Controller pero para mensajes Kafka en vez de HTTP.

**`config/`** — Configuración de Spring (Beans, topics Kafka, clientes AWS...).

---

## 3. MICROSERVICIOS VS HEXAGONAL — NO ES LO MISMO

Esta es una duda muy común. Respuesta directa:

**La arquitectura hexagonal está DENTRO de cada microservicio.**
**Los microservicios son una decisión de cómo dividir el sistema en piezas independientes.**

Son dos conceptos separados que se usan juntos:

```
SISTEMA COMPLETO
├── order-service          ← microservicio 1 (aplicación Spring Boot independiente)
│   └── internamente usa arquitectura hexagonal
│       ├── domain/
│       ├── application/
│       └── adapters/
│
└── notification-service   ← microservicio 2 (otra aplicación Spring Boot independiente)
    └── internamente también usa arquitectura hexagonal
        ├── domain/
        ├── application/
        └── adapters/
```

`order-service` y `notification-service` son dos programas Java completamente independientes.
Cada uno tiene su propio `pom.xml`, su propia base de datos, su propio puerto HTTP.
Se comunican entre ellos por Kafka (mensajes), no por llamadas internas.

Si `order-service` se cae, `notification-service` sigue funcionando.
Si `notification-service` se cae, `order-service` sigue recibiendo pedidos (Kafka los guarda hasta que vuelva).

En tu monolito, todo estaba en un solo programa Java. Aquí son dos.

---

## 4. TECNOLOGÍAS

### Kafka / Redpanda

**Qué es (explicación tonta):**
Un tablón de anuncios digital. Un servicio pega un post, otro lo lee cuando quiere.
Nadie espera a nadie. Si el lector está caído, el post sigue ahí cuando vuelva.

**Qué es (técnico):**
Sistema de mensajería asíncrona basado en topics (colas persistentes).
El productor publica un mensaje en un topic. El consumidor lo lee cuando puede.
A diferencia de HTTP (síncrono: preguntas y esperas respuesta), Kafka es asíncrono: publicas y sigues.

**Redpanda** es un clon de Kafka más ligero para desarrollo. 100% compatible con clientes Kafka.

**Conceptos clave:**
- **Topic**: la "cola" con nombre. En este proyecto: `order-events` y `order-events-dlq`.
- **Producer**: quien publica mensajes. Aquí: `order-service`.
- **Consumer**: quien lee mensajes. Aquí: `notification-service`.
- **Group ID**: identidad del consumidor. Permite que varios consumidores repartan la carga.
- **Partition**: división interna del topic para paralelismo. Aquí: 3 particiones.

**Por qué se usa aquí:**
`order-service` no debería esperar a que `notification-service` procese la IA (puede tardar 2-3 segundos).
Con Kafka, `order-service` publica el evento y responde al usuario en milisegundos.
`notification-service` lo procesa después, a su ritmo.

**Dónde está en el código:**
- `order-service/adapters/outbound/messaging/KafkaOrderEventPublisher.java` — publica eventos
- `notification-service/adapters/inbound/messaging/OrderEventsKafkaListener.java` — los consume
- `notification-service/config/KafkaConfig.java` — configura topics, retry y DLQ

---

### Postgres + pgvector

**Qué es (tonto):**
Postgres es como Oracle, pero open source y gratuito.
pgvector es un add-on que le añade un tipo de columna especial para guardar listas de números,
y operadores para buscar "cuál de estas listas de números se parece más a esta otra lista".

**Qué es (técnico):**
`pgvector` añade el tipo de datos `vector(N)` a Postgres.
Puedes hacer queries como:
```sql
SELECT content FROM knowledge_chunks
ORDER BY embedding <=> '[0.1, 0.2, ...]'   -- <=> es distancia coseno
LIMIT 3;
```
Esto devuelve los 3 documentos cuyos vectores son matemáticamente más parecidos al vector dado.

**Por qué se necesita:**
Los LLMs no entienden texto directamente. Trabajan con vectores (listas de ~1500 números).
Para buscar "qué documentos de mi BD son relevantes para esta consulta",
conviertes la consulta a vector y buscas los vectores más cercanos.

**Dónde está en el código:**
- `notification-service/adapters/outbound/persistence/PostgresVectorStoreAdapter.java`
  — hace el INSERT y el SELECT con `<=>` directamente con JdbcTemplate
- `docker/postgres/init/01-init.sql` — activa la extensión `CREATE EXTENSION IF NOT EXISTS vector`

---

### RAG (Retrieval-Augmented Generation)

**Qué es (tonto):**
Darle chuleta al LLM antes de que conteste.
En vez de preguntarle algo directamente, primero buscas en tu BD qué información es relevante
y se la adjuntas en la pregunta.

**Qué es (técnico):**
Patrón para enriquecer los prompts con contexto propio antes de llamar al LLM.
Flujo:
1. Tienes una "knowledge base" (base de conocimiento) — documentos de tu empresa, reglas, plantillas.
2. Cuando llega una consulta, la conviertes a vector (embedding).
3. Buscas en pgvector qué documentos de la knowledge base son más similares (topK).
4. Concatenas esos documentos al prompt que mandas al LLM.
5. El LLM responde usando tu contexto, no solo lo que aprendió en su entrenamiento.

**El flujo concreto en este proyecto:**
```
Llega evento Kafka: "Pedido de Laptop x1 999€"
     ↓
embed("Laptop x1 999€") → vector [0.12, -0.45, 0.78, ...]
     ↓
pgvector: dame los 3 documentos cuyo vector se parece más a este
→ devuelve: "order-confirmation.md", "security.md"
     ↓
Construye prompt:
   SYSTEM: [reglas fijas de cómo generar notificaciones]
   USER: [datos del pedido]
   CONTEXT: [contenido de order-confirmation.md y security.md]
     ↓
LLM genera JSON de notificación
     ↓
Guardrails validan el JSON
     ↓
Se persiste en Postgres
```

**Dónde está en el código:**
- `notification-service/application/service/NotificationApplicationService.java` — orquesta todo el RAG
- `notification-service/application/service/NotificationPromptFactory.java` — construye los prompts
- `notification-service/application/service/KnowledgeBaseSeeder.java` — carga los docs al arrancar
- `notification-service/resources/seed-docs/` — los documentos .md de la knowledge base

---

### AWS Bedrock

**Qué es (tonto):**
Es como llamar a ChatGPT, pero desde AWS. Los datos no salen de tu cuenta de Amazon.
Puedes elegir qué modelo usar: Claude (Anthropic), Titan (Amazon), etc.

**Qué es (técnico):**
Servicio gestionado de AWS que expone LLMs y modelos de embeddings mediante una API REST.
En vez de `POST https://api.openai.com/v1/chat/completions`, haces una llamada al SDK de AWS.
Las credenciales son IAM (roles de AWS), no API keys.

**Dos usos en este proyecto:**
1. **Embeddings**: convierte texto a vector numérico. Modelo: Amazon Titan Embeddings.
   Input: "Laptop x1 999€" → Output: `[0.12, -0.45, 0.78, ...]` (1536 números)
2. **Generación de texto**: el LLM que decide qué notificación mandar. Modelo: Claude via Bedrock.
   Input: prompt con datos del pedido + contexto RAG → Output: JSON de notificación

**Por qué `@ConditionalOnProperty`:**
Las clases `BedrockLlmAdapter` y `BedrockEmbeddingAdapter` tienen:
```java
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "bedrock")
```
Esto significa: "solo crea este bean si en application.yml pone `app.ai.provider: bedrock`".
Si no, Spring usa `FakeLlmAdapter` y `FakeEmbeddingAdapter` (que son instantáneos, sin coste, sin AWS).
Por defecto, el proyecto arranca en modo fake. Esto permite desarrollar y hacer tests sin AWS.

**Dónde está en el código:**
- `notification-service/adapters/outbound/ai/BedrockLlmAdapter.java` — llama al LLM de Claude
- `notification-service/adapters/outbound/ai/BedrockEmbeddingAdapter.java` — obtiene embeddings
- `notification-service/adapters/outbound/ai/FakeLlmAdapter.java` — LLM falso para tests/local
- `notification-service/adapters/outbound/ai/FakeEmbeddingAdapter.java` — embeddings falsos para tests/local
- `notification-service/adapters/outbound/ai/BedrockClientConfig.java` — configura el cliente AWS SDK

---

### OpenTelemetry / Observabilidad (Micrometer + Prometheus + Grafana + Jaeger)

**Qué es (tonto):**
Los instrumentos del salpicadero del coche. Sin ellos conduces a ciegas.
Velocímetro = cuántas peticiones por segundo.
Temperatura = cuánto tarda el LLM.
Combustible = cuántos errores ha habido.

**Qué es (técnico):**
Conjunto de librerías y herramientas para medir lo que hace tu aplicación en tiempo real.
Tres tipos de señales:

**Métricas** (números agregados):
- `orders.created.total` — cuántos pedidos se han creado (total)
- `notifications.llm.latency` — cuánto tarda el LLM de media, máximo, percentil 99
- `notifications.events.dlq.total` — cuántos mensajes han ido a la cola de errores
Prometheus recoge estos números. Grafana los muestra en gráficas.

**Trazas** (camino de una petición concreta):
Si un pedido tardó 3 segundos, una traza te dice exactamente dónde:
- 5ms en la BD al guardar el pedido
- 2ms en publicar a Kafka
- 2800ms en llamar a Bedrock (aquí está el cuello de botella)
Jaeger visualiza estas trazas.

**Logs estructurados**:
Logs con `traceId` y `spanId` para poder correlacionar: "este log pertenece a esta petición concreta".

**Dónde está en el código:**
- `notification-service/application/service/NotificationApplicationService.java`
  — `consumedCounter`, `errorsCounter`, `llmTimer`, `embeddingTimer`
- `order-service/adapters/outbound/messaging/KafkaOrderEventPublisher.java`
  — `publishedCounter`
- `docker/otel-collector-config.yml` — el colector recibe datos de los servicios y los reenvía
- `docker/prometheus.yml` — Prometheus scrapeа métricas
- `docker/grafana/` — Grafana lee de Prometheus y muestra dashboards

---

### Guardrails

**Qué es (tonto):**
El portero del LLM. Revisa lo que dice antes de que salga al mundo.
Si el LLM devuelve una tontería o un formato incorrecto, lo bloquea y usa una respuesta segura.

**Qué es (técnico):**
Validaciones deterministas en código Java sobre la salida del LLM.
El LLM puede:
- Devolver JSON malformado
- Usar un canal no permitido (ej: "whatsapp" cuando solo se permite "email")
- Incluir datos sensibles (emails, teléfonos) en el cuerpo
- Dar una confianza fuera de rango (ej: 1.5 cuando debe ser 0-1)

Los guardrails comprueban todo esto. Si algo falla → fallback.
El fallback es una notificación generada sin IA, determinista, que siempre funciona.

**Dónde está en el código:**
- `notification-service/application/service/NotificationGuardrails.java`
  — valida JSON, canal, confianza, redacta PII, trunca longitud, genera fallback

---

### DLQ (Dead Letter Queue)

**Qué es (tonto):**
La papelera del cartero. Si no puede entregar un paquete después de N intentos, lo deja aquí.
Así el mensaje no se pierde y alguien puede revisarlo y reintentarlo manualmente.

**Qué es (técnico):**
Topic Kafka especial al que van los mensajes que fallaron después de todos los reintentos.
Configurado en `KafkaConfig.java`:
- Si `notification-service` falla al procesar un mensaje de `order-events`
- Reintenta 3 veces con espera entre intentos
- Si sigue fallando, el mensaje va a `order-events-dlq`
- Un operador puede inspeccionar ese topic y decidir qué hacer

**Dónde está en el código:**
- `notification-service/config/KafkaConfig.java` — `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`
- La consola de Redpanda (`http://localhost:8080`) permite ver los mensajes en `order-events-dlq`

---

### Docker Compose

**Qué es (tonto):**
Un archivo que le dice a Docker: "arranca estos 7 programas juntos".
En vez de instalar Kafka, Postgres, Grafana, etc. en tu máquina, Docker los corre en contenedores.
Si no te gustan, haces `docker compose down` y desaparecen sin rastro.

**Qué levanta `docker-compose.yml` de este proyecto:**

| Servicio         | Qué es                            | URL local                |
|------------------|-----------------------------------|--------------------------|
| redpanda         | Kafka (el broker de mensajes)     | localhost:19092          |
| redpanda-console | UI web para ver topics y mensajes | http://localhost:8080    |
| postgres         | Base de datos (con pgvector)      | localhost:5432           |
| otel-collector   | Recoge trazas y métricas          | localhost:4317 (gRPC)    |
| prometheus       | Almacena métricas                 | http://localhost:9090    |
| grafana          | Dashboards de métricas            | http://localhost:3000    |
| jaeger           | Visualiza trazas                  | http://localhost:16686   |

**Los servicios Java (order-service y notification-service) NO están en docker-compose.**
Los arrancas tú desde IntelliJ, apuntando a los contenedores de arriba.

---

## 5. MAPA DEL PROYECTO — ARCHIVO POR ARCHIVO

### order-service

```
order-service/src/main/java/es/tirea/orderservice/

adapters/
  inbound/
    rest/
      OrderController.java              ← Tu @RestController de siempre. POST /orders, GET /orders.
      dto/
        OrderRequest.java               ← El body del POST. @NotBlank, @Positive...

  outbound/
    messaging/
      KafkaOrderEventPublisher.java     ← Implementa OrderEventPublisherPort. Usa KafkaTemplate para
                                           publicar OrderCreatedEvent en el topic "order-events".
    persistence/
      OrderJpaEntity.java               ← El @Entity con @Table, @Column. Solo para JPA/Postgres.
      SpringDataOrderJpaRepository.java ← Extends JpaRepository<OrderJpaEntity, String>. Igual que siempre.
      PostgresOrderRepositoryAdapter.java ← Implementa OrderRepositoryPort. Convierte Order ↔ OrderJpaEntity.
                                            Es tu DAO de siempre, con otro nombre.

application/
  command/
    CreateOrderCommand.java             ← Objeto inmutable con los datos para crear un pedido.
                                           Es lo que recibe el use case desde el controller.
  usecase/
    CreateOrderUseCase.java             ← Interface: "se puede crear un pedido"
    ListOrdersUseCase.java              ← Interface: "se pueden listar pedidos"
  service/
    OrderApplicationService.java       ← Tu @Service de siempre. Implementa los dos use cases.
                                           Guarda en BD y publica en Kafka.

config/
  KafkaTopicConfig.java                ← Crea el topic "order-events" en Kafka al arrancar.

domain/
  model/
    Order.java                         ← Java record puro. Sin @Entity, sin Spring. Solo datos.
    OrderCreatedEvent.java             ← El evento que se publica en Kafka. También record puro.
  port/out/
    OrderRepositoryPort.java           ← Interface: save(Order), findAll()
    OrderEventPublisherPort.java       ← Interface: publish(OrderCreatedEvent)
```

### notification-service

```
notification-service/src/main/java/es/tirea/notificationservice/

adapters/
  inbound/
    messaging/
      OrderEventsKafkaListener.java    ← @KafkaListener. Escucha "order-events" y llama al use case.
                                          Es como un @RestController pero para mensajes Kafka.
    rest/
      NotificationController.java      ← GET /notifications, POST /notifications (manual/debug)
      dto/
        NotificationRequest.java       ← Body del POST manual.

  outbound/
    ai/
      BedrockClientConfig.java         ← Crea el BedrockRuntimeClient de AWS SDK. Credenciales IAM.
      BedrockLlmAdapter.java           ← Implementa LlmPort. Llama a Claude en AWS Bedrock.
                                          Solo activo si app.ai.provider=bedrock en application.yml.
      BedrockEmbeddingAdapter.java     ← Implementa EmbeddingPort. Llama a Titan Embeddings en Bedrock.
                                          Solo activo si app.ai.provider=bedrock.
      FakeLlmAdapter.java              ← Implementa LlmPort. Devuelve JSON hardcodeado. Para tests/local.
                                          Activo por defecto si NO se pone app.ai.provider=bedrock.
      FakeEmbeddingAdapter.java        ← Implementa EmbeddingPort. Devuelve vector de ceros. Para tests/local.
    persistence/
      NotificationJpaEntity.java       ← @Entity para la tabla de notificaciones.
      SpringDataNotificationJpaRepository.java ← Extends JpaRepository.
      PostgresNotificationRepositoryAdapter.java ← Implementa NotificationRepositoryPort.
      PostgresVectorStoreAdapter.java  ← Implementa VectorStorePort. Usa JdbcTemplate con SQL nativo
                                          y el operador <=> de pgvector para similitud.

application/
  usecase/
    ProcessOrderCreatedUseCase.java    ← Interface: "procesar un evento de pedido creado"
    ListNotificationsUseCase.java      ← Interface: "listar notificaciones"
    CreateManualNotificationUseCase.java ← Interface: "crear notificación manual"
  service/
    NotificationApplicationService.java ← El @Service principal. Orquesta todo el flujo RAG:
                                           evento → embedding → pgvector → LLM → guardrails → persistir.
    NotificationGuardrails.java         ← Valida y sanea la salida del LLM.
    NotificationPromptFactory.java      ← Construye los prompts (system, user, contexto RAG).
    KnowledgeBaseSeeder.java            ← Al arrancar, lee los .md de seed-docs/, les hace embedding
                                           y los guarda en pgvector. Es la "carga de documentos" del RAG.

config/
  KafkaConfig.java                    ← Topics, retry (3 intentos) y DLQ con DefaultErrorHandler.

domain/
  model/
    OrderCreatedEvent.java            ← El evento que viene de Kafka (igual que en order-service).
    NotificationRecord.java           ← La notificación ya procesada y guardada en BD.
    GeneratedNotification.java        ← Lo que devuelve el LLM después de pasar guardrails.
    KnowledgeChunk.java               ← Un fragmento de documento de la knowledge base.
  port/out/
    NotificationRepositoryPort.java   ← Interface: save, findAll, existsBySourceOrderIdAndChannel
    VectorStorePort.java              ← Interface: upsert (guardar doc+embedding), searchSimilar (RAG)
    LlmPort.java                      ← Interface: generateNotificationJson, modelName
    EmbeddingPort.java                ← Interface: embed(texto) → double[], modelName

resources/
  application.yml                    ← Configuración. Perfiles default (docker-compose) y test.
  schema.sql                         ← Crea las tablas en Postgres al arrancar.
  seed-docs/
    order-confirmation.md            ← Documento de knowledge base: cómo confirmar pedidos.
    security.md                      ← Documento de knowledge base: reglas de seguridad.
```

---

## 6. FLUJO COMPLETO

Qué pasa exactamente cuando haces `POST /orders`:

```
1. HTTP POST /orders llega a OrderController.java
   Body: { "product": "Laptop", "quantity": 1, "price": 999.99 }

2. OrderController valida el body (@Valid) y llama a:
   createOrderUseCase.createOrder(new CreateOrderCommand("Laptop", 1, 999.99))

3. OrderApplicationService.createOrder() ejecuta:
   a. Crea Order(id=UUID, product="Laptop", quantity=1, price=999.99, createdAt=ahora)
   b. orderRepositoryPort.save(order)
      → PostgresOrderRepositoryAdapter convierte Order → OrderJpaEntity
      → JPA hace INSERT en tabla orders de Postgres
   c. orderEventPublisherPort.publish(new OrderCreatedEvent(...))
      → KafkaOrderEventPublisher serializa el evento a JSON
      → KafkaTemplate lo publica en el topic "order-events" de Redpanda
   d. Incrementa el contador orders.created.total

4. OrderController devuelve HTTP 201 con el Order creado.
   order-service ya terminó. No sabe nada de lo que pasa después.

--- (asíncrono, puede ser milisegundos o segundos después) ---

5. notification-service tiene un hilo escuchando "order-events".
   OrderEventsKafkaListener.onOrderCreated(event) recibe el mensaje.

6. Llama a processOrderCreatedUseCase.process(event)
   → NotificationApplicationService.process(event)

7. Comprueba idempotencia:
   ¿Ya existe notificación para este orderId + canal "email"?
   Si sí → se ignora (evita duplicados si Kafka reenvía el mensaje)
   Si no → continúa

8. RAG - Paso 1: Embedding de la consulta
   embeddingPort.embed("Laptop 1 999.99")
   → FakeEmbeddingAdapter devuelve vector de ceros (en local)
   → BedrockEmbeddingAdapter llamaría a AWS Titan (si provider=bedrock)
   Resultado: double[] queryVector = [0.0, 0.0, ..., 0.0]

9. RAG - Paso 2: Búsqueda de similitud en pgvector
   vectorStorePort.searchSimilar(queryVector, 3)
   → PostgresVectorStoreAdapter ejecuta:
     SELECT external_id, title, content FROM knowledge_chunks
     ORDER BY embedding <=> '[0.0, 0.0, ...]' LIMIT 3
   Resultado: lista de KnowledgeChunk (fragmentos de seed-docs/)

10. Construye prompts con NotificationPromptFactory:
    - systemPrompt: instrucciones fijas ("eres un sistema de notificaciones, responde JSON...")
    - userPrompt: datos del pedido
    - retrievedContext: el texto de los documentos encontrados en paso 9

11. Llama al LLM:
    llmPort.generateNotificationJson(systemPrompt, userPrompt, retrievedContext)
    → FakeLlmAdapter devuelve JSON hardcodeado (en local)
    → BedrockLlmAdapter llamaría a Claude (si provider=bedrock)
    Resultado: String rawJson = '{"channel":"email","title":"...","body":"...","confidence":0.93}'

12. Guardrails: NotificationGuardrails.validateAndSanitize(rawJson)
    - ¿Es JSON válido? ✓
    - ¿Tiene channel, title, body, confidence? ✓
    - ¿El channel está en la lista blanca? ✓ ("email" está permitida)
    - ¿confidence entre 0 y 1? ✓
    - ¿Hay emails o teléfonos en el body? → reemplaza por [REDACTED_EMAIL]
    - ¿body es demasiado largo? → trunca
    Si cualquier validación falla → fallbackNotification() (respuesta segura sin IA)

13. Persiste en Postgres:
    notificationRepositoryPort.save(new NotificationRecord(
        id=UUID,
        sourceOrderId=event.orderId(),
        source="kafka",
        channel="email",
        title="Order confirmed",
        body="Your order has been received...",
        confidence=0.93,
        modelUsed="fake-llm-v1",
        latencyMs=45,
        traceId="abc123",
        fallback=false,
        createdAt=ahora
    ))

14. Incrementa contador notifications.events.consumed.total.

--- fin ---

GET /notifications en notification-service devuelve el registro guardado.
```

---

## 7. PLAN DE APRENDIZAJE POR FASES

### Estado actual del proyecto
- El código está escrito pero NUNCA ha compilado ni ejecutado.
- Hay un bug en el pom.xml de notification-service (falta el BOM del AWS SDK).
- Los tests no pasan todavía.

---

### FASE 0 — Preparación (hacer que compile)

**Objetivo:** que `./mvnw test` no explote.

**Paso 1:** Arreglar el pom.xml de notification-service.
Falta la gestión de versiones del AWS SDK. Sin esto, Maven no sabe qué versión de
`bedrockruntime` usar y falla la compilación.
→ Pedirle a Claude que lo arregle (es una línea de `<dependencyManagement>`).

**Paso 2:** Compilar cada servicio por separado:
```bash
cd notification-service
./mvnw compile

cd ../order-service
./mvnw compile
```
Si hay errores → copiarlos y pedir ayuda para interpretarlos.

**Paso 3:** Correr los tests:
```bash
cd notification-service
./mvnw test

cd ../order-service
./mvnw test
```
Los tests usan modo fake (sin Docker, sin Bedrock). Deberían pasar en local.

**Lo que aprendes en esta fase:**
- Cómo funciona Maven (pom.xml, dependencias, BOM)
- Que `./mvnw test` corre todos los tests de una vez
- A leer errores de compilación de Java

---

### FASE 1 — Levantar infraestructura con Docker Compose

**Objetivo:** tener Kafka, Postgres, Grafana y Jaeger corriendo.

**Paso 1:** Desde la raíz del proyecto:
```bash
docker compose up -d
```
`-d` = background (no ocupa la terminal)

**Paso 2:** Verificar que todo está corriendo:
```bash
docker compose ps
```
Deben aparecer: redpanda, redpanda-console, postgres, otel-collector, prometheus, grafana, jaeger.

**Paso 3:** Abrir en el navegador (no tiene que hacer nada concreto, solo ver que existen):
- http://localhost:8080 → Redpanda Console (UI de Kafka). Ve a Topics. Verás order-events.
- http://localhost:3000 → Grafana (admin/admin). Explora, no hay datos todavía.
- http://localhost:16686 → Jaeger. Explora, no hay trazas todavía.
- http://localhost:9090 → Prometheus. Busca "orders_created_total" en el buscador.

**Lo que aprendes en esta fase:**
- Qué es Docker Compose y para qué sirve
- Que Kafka tiene una UI donde puedes ver los mensajes en tiempo real
- Que Grafana y Jaeger existen como herramientas de observabilidad

---

### FASE 2 — Primer flujo end-to-end

**Objetivo:** ver el sistema funcionando de punta a punta.

**Requisito previo:** Docker Compose corriendo (Fase 1).

**Paso 1:** Abrir IntelliJ y arrancar order-service (puerto 8081).
Configuración de arranque normal, como cualquier Spring Boot.

**Paso 2:** Arrancar notification-service (puerto 8082).

**Paso 3:** Hacer el pedido:
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"product":"Laptop","quantity":1,"price":999.99}'
```
O desde Postman/IntelliJ HTTP Client.

**Paso 4:** Ver la notificación:
```bash
curl http://localhost:8082/notifications
```
Deberías ver un JSON con la notificación creada, incluyendo `source: "kafka"`, `fallback: false`, etc.

**Paso 5:** Ver el mensaje en Kafka.
Abre http://localhost:8080 → Topics → order-events → Messages.
Verás el OrderCreatedEvent que publicó order-service.

**Lo que aprendes en esta fase:**
- Ver con tus propios ojos la comunicación asíncrona Kafka
- Que order-service responde inmediatamente aunque notification-service tarde
- El formato del NotificationRecord que se guarda en BD

---

### FASE 3 — Explorar con el debugger

**Objetivo:** entender qué hace cada línea de código importante.

**Pon breakpoints en estos sitios y sigue la ejecución:**

1. `OrderController.java` línea del `createOrder` → ¿qué llega en el request?
2. `OrderApplicationService.java` línea del `orderRepositoryPort.save()` → ¿qué objeto es?
3. `KafkaOrderEventPublisher.java` dentro del `publish()` → ¿qué se manda a Kafka?
4. `OrderEventsKafkaListener.java` dentro del `onOrderCreated()` → ¿cuándo se activa?
5. `NotificationApplicationService.java` dentro del `process()` → sigue todo el flujo RAG
6. `NotificationGuardrails.java` dentro del `validateAndSanitize()` → ¿qué valida?

**Lo que aprendes en esta fase:**
- El flujo real de datos a través de las capas
- Por qué existen los adaptadores (conversión entre objetos de dominio y entidades JPA)
- Cómo el @ConditionalOnProperty elige entre Fake y Bedrock

---

### FASE 4 — Observabilidad

**Objetivo:** ver métricas y trazas reales en Grafana y Jaeger.

**Paso 1:** Haz 5-10 pedidos (repite el curl de antes).

**Paso 2:** Ve a Prometheus (http://localhost:9090) y busca:
- `orders_created_total`
- `notifications_events_consumed_total`
- `notifications_llm_latency_seconds`

**Paso 3:** Ve a Jaeger (http://localhost:16686):
- Service: order-service
- Busca trazas
- Abre una traza y verás el span de HTTP + el span de Kafka

**Paso 4:** En los logs de notification-service, busca líneas con `traceId`.
Copia ese traceId y pégalo en Jaeger. Verás la traza completa.

**Lo que aprendes en esta fase:**
- Qué son métricas y trazas en la práctica
- Por qué son importantes en producción
- Cómo correlacionar logs con trazas

---

### FASE 5 — Romper cosas adrede

**Objetivo:** entender por qué existen los mecanismos de resiliencia.

**Experimento 1 — Idempotencia:**
Haz el mismo pedido dos veces con el mismo curl.
¿Se crea una segunda notificación? No debe. ¿Por qué? Busca `existsBySourceOrderIdAndChannel` en el código.

**Experimento 2 — DLQ:**
Para notification-service mientras haces un pedido.
¿Qué pasa? order-service responde igual. El mensaje se queda en Kafka.
Cuando reinicies notification-service, ¿procesa el mensaje pendiente?
Ve a Redpanda Console → Topics → order-events y observa el offset.

**Experimento 3 — Guardrails:**
En `FakeLlmAdapter.java`, cambia el JSON que devuelve para que `channel` sea `"whatsapp"`.
¿Qué pasa? El guardrail rechaza esa respuesta y activa el fallback.
Verás `fallback: true` en la notificación.

**Experimento 4 — Fallback:**
En `FakeLlmAdapter.java`, haz que lance una excepción.
¿Se cae notification-service? No. ¿Genera una notificación de todas formas? Sí, el fallback.

**Lo que aprendes en esta fase:**
- Por qué existen la idempotencia, la DLQ y el fallback
- Que los sistemas distribuidos fallan, y lo importante es cómo se recuperan

---

### FASE 6 — Añadir algo tú solo (sin IA)

**Objetivo:** confirmar que has entendido de verdad.

**Ejercicio A — fácil:**
Añade el endpoint `GET /orders/{id}` en order-service que devuelve un pedido por su ID.
Necesitarás añadir `findById(String id)` en `OrderRepositoryPort`, su implementación en el adaptador,
un nuevo use case, y el endpoint en el controller.

**Ejercicio B — intermedio:**
Añade una nueva métrica en notification-service que cuente cuántas notificaciones
salieron con `fallback=true`. Debe aparecer en Prometheus con nombre `notifications.fallback.total`.

**Ejercicio C — avanzado:**
Añade un segundo canal: `sms`. Modifica el whitelist de canales en `application.yml`.
El FakeLlmAdapter debe devolver `sms` el 50% de las veces.

---

### FASE 7 — Bedrock real (cuando tengas AWS)

**Objetivo:** ver la diferencia entre el fake y un LLM real.

**Pasos:**
1. Crear cuenta AWS (free tier)
2. Ir a AWS Console → Bedrock → Model access → habilitar Claude 3 Haiku (us-east-1)
3. En tu terminal: `aws configure` (introduce tus credenciales)
4. En `notification-service/src/main/resources/application.yml`:
   Cambia `app.ai.provider: fake` → `app.ai.provider: bedrock`
5. Arranca notification-service y haz un pedido
6. Observa la latencia en Grafana (será segundos, no milisegundos como el fake)
7. Mira la notificación generada: ahora el texto lo ha escrito Claude de verdad

---

### RESUMEN DE FASES

| Fase | Qué haces                         | Qué aprendes                               |
|------|-----------------------------------|--------------------------------------------|
| 0    | Arreglar pom.xml y compilar       | Maven, gestión de dependencias             |
| 1    | Docker Compose up                 | Infraestructura local, qué es cada servicio|
| 2    | Primer flujo end-to-end           | Comunicación asíncrona Kafka, el flujo RAG |
| 3    | Debugger en clases clave          | Arquitectura hexagonal en la práctica      |
| 4    | Grafana y Jaeger                  | Observabilidad, métricas, trazas           |
| 5    | Romper cosas adrede               | Resiliencia, DLQ, idempotencia, fallback   |
| 6    | Añadir algo tú solo               | Confirmar comprensión real                 |
| 7    | Bedrock real                      | Cómo funciona un LLM en producción        |

---

*Documento actualizado: 2026-03-17*
*Proyecto: /mnt/c/Users/MiguelÁngelRamírezCi/IdeaProjects/test*
