# LLM Monkey

A Java clone of [LiteLLM](https://github.com/BerriAI/litellm) -- a unified LLM proxy/gateway that provides an OpenAI-compatible API across multiple LLM providers, with enterprise features including virtual keys, RBAC, budgets, rate limiting, caching, guardrails, and audit logging.

Built with **Java 21** and **Spring Boot 3.4** using reactive WebFlux.

## Features

### Multi-Provider Support

Route requests through a single OpenAI-compatible API to any of these providers:

| Provider | Chat | Completions | Embeddings | Streaming |
|----------|------|-------------|------------|-----------|
| OpenAI | Yes | Yes | Yes | Yes |
| Anthropic | Yes | Yes | -- | Yes |
| Azure OpenAI | Yes | Yes | Yes | Yes |
| AWS Bedrock | Yes | -- | Yes | Yes |
| Google Vertex AI | Yes | -- | Yes | Yes |
| Cohere | Yes | -- | Yes | Yes |
| Ollama | Yes | Yes | Yes | Yes |

### Enterprise Features

- **Virtual Keys** -- Generate API keys (`sk-lm-*`) with per-key model access, budgets, rate limits, and expiration. Keys are SHA-256 hashed in the database.
- **RBAC** -- Five roles: `PROXY_ADMIN`, `ADMIN_VIEWER`, `ORG_ADMIN`, `TEAM_ADMIN`, `TEAM_MEMBER` with granular permissions.
- **SSO / OIDC** -- JWT-based authentication alongside virtual keys. Auto-provisions users on first login.
- **Budget Enforcement** -- Spend tracking and budget limits at the key, user, team, and organization level with configurable reset durations.
- **Rate Limiting** -- TPM/RPM limits backed by Redis (sliding window) with in-memory fallback.
- **Response Caching** -- Two-level cache: L1 (Caffeine, in-process) + L2 (Redis, shared). Caches deterministic requests (temperature=0, non-streaming).
- **Guardrails** -- Pre-request and post-response content filtering: regex patterns, keyword blocking, and PII masking.
- **Audit Logging** -- All admin operations logged with before/after values.
- **Observability** -- Prometheus metrics for requests, tokens, spend, latency, and deployment health.

### Routing Strategies

- **Round Robin** -- Cycle through deployments evenly
- **Least Busy** -- Route to the deployment with the fewest in-flight requests
- **Latency-Based** -- Route to the deployment with the lowest average latency (EMA)
- **Cost-Based** -- Route to the cheapest deployment
- **Usage-Based** -- Balance by total token usage

All strategies include automatic **failover**, **retry with exponential backoff**, and **deployment cooldowns**.

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Run with Docker Compose

```bash
# Set your provider API keys
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...

# Start the app + PostgreSQL + Redis
docker-compose up
```

The proxy starts on **port 4000**.

### Run with Gradle

```bash
# Requires external PostgreSQL and Redis
./gradlew bootRun
```

### Test It

```bash
# Chat completion
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-master-1234" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'

# Streaming
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-master-1234" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'

# List models
curl http://localhost:4000/v1/models \
  -H "Authorization: Bearer sk-master-1234"
```

## API Endpoints

### OpenAI-Compatible

| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/chat/completions` | Chat completion (streaming & non-streaming) |
| POST | `/v1/completions` | Text completion |
| POST | `/v1/embeddings` | Generate embeddings |
| GET | `/v1/models` | List available models |

### Key Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/key/generate` | Create a virtual key |
| GET | `/key/info` | Get key details |
| POST | `/key/update` | Update key settings |
| POST | `/key/delete` | Revoke a key |

### Team / User / Organization Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/team/new` | Create a team |
| POST | `/team/update` | Update a team |
| POST | `/team/delete` | Delete a team |
| POST | `/user/new` | Create a user |
| POST | `/user/update` | Update a user |
| POST | `/user/delete` | Delete a user |
| POST | `/organization/new` | Create an organization |
| POST | `/organization/update` | Update an organization |
| POST | `/organization/delete` | Delete an organization |

### Health & Metrics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| GET | `/health/readiness` | Readiness probe |
| GET | `/actuator/prometheus` | Prometheus metrics |

## Configuration

Configure models and settings in `application.yml`:

```yaml
llm-monkey:
  master-key: "${MASTER_KEY:sk-master-1234}"

  model-list:
    - model-name: "gpt-4"
      provider: "openai"
      params:
        model: "gpt-4"
        api-key: "${OPENAI_API_KEY}"
        api-base: "https://api.openai.com/v1"

    - model-name: "claude-sonnet"
      provider: "anthropic"
      params:
        model: "claude-sonnet-4-20250514"
        api-key: "${ANTHROPIC_API_KEY}"
        api-base: "https://api.anthropic.com"

  router-settings:
    routing-strategy: "round-robin"  # round-robin | least-busy | latency-based | cost-based | usage-based
    num-retries: 2
    timeout-seconds: 300
    allowed-fails: 3
    cooldown-seconds: 60

  general-settings:
    database-enabled: true
    cache-enabled: true
    rate-limit-enabled: true
```

## Architecture

```
Request -> AuthFilter -> RateLimitFilter -> CacheCheck
  -> Router (strategy + failover + retry)
  -> Provider (request translation + HTTP call)
  -> Response translation -> CacheStore -> SpendTracker -> Response
```

### Tech Stack

- **Java 21** -- Records, sealed interfaces, pattern matching, virtual threads
- **Spring Boot 3.4 + WebFlux** -- Reactive HTTP server and client
- **PostgreSQL** -- Persistence (Flyway migrations)
- **Redis** -- Distributed rate limiting and L2 cache
- **Caffeine** -- L1 in-memory cache
- **Micrometer + Prometheus** -- Metrics
- **Gradle Kotlin DSL** -- Build system

### Project Structure

```
src/main/java/ai/llmmonkey/
  admin/           # Admin management endpoints (keys, teams, users, orgs)
  api/controller/  # OpenAI-compatible REST endpoints
  api/dto/         # Request/response records
  api/exception/   # Exception hierarchy + global handler
  audit/           # Audit logging
  auth/            # Virtual keys, RBAC, SSO/OIDC, JWT
  budget/          # Spend tracking, budget enforcement
  cache/           # L1/L2 response caching
  config/          # Spring config, properties, YAML parsing
  guardrail/       # Content filtering, PII masking
  model/           # JPA entities
  observability/   # Prometheus metrics, callbacks
  provider/        # Provider implementations (7 providers)
  ratelimit/       # TPM/RPM rate limiting
  repository/      # Spring Data repositories
  router/          # Routing strategies, failover, retry
```

### Database Schema

7 Flyway migrations create:
- **budgets** -- Budget limits, durations, and reset schedules
- **organizations** -- Multi-tenant org hierarchy
- **teams** -- Team grouping within orgs
- **llm_users** -- Users with roles and SSO mapping
- **verification_tokens** -- Virtual API keys with scoped access
- **spend_logs** -- Per-request cost and usage tracking
- **audit_logs** -- Admin operation audit trail

## License

MIT
