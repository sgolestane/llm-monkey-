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
- **Request Tags** -- Pass tags on any request for cost attribution, usage reporting, and per-tag budget enforcement. Query spend grouped by tag via the admin API.
- **Budget Enforcement** -- Spend tracking and budget limits at the key, user, team, organization, and tag level with configurable reset durations.
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

### Tag Budget & Reporting

| Method | Path | Description |
|--------|------|-------------|
| POST | `/tag/budget/new` | Create a per-tag budget |
| POST | `/tag/budget/update` | Update a tag budget |
| POST | `/tag/budget/delete` | Delete a tag budget |
| GET | `/tag/budget/info?tag=` | Get tag budget details |
| GET | `/tag/budget/list` | List all tag budgets |
| GET | `/tag/spend?tag=&period=` | Get spend report for a tag |

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

## Tags

Tags let you categorize and track LLM usage across projects, features, teams, or any custom dimension. Pass tags on any request, then use them for cost attribution, usage reporting, and budget enforcement.

### Passing Tags on Requests

Include a `tags` array in any request body:

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-master-1234" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Summarize this report"}],
    "tags": ["project-alpha", "summarization", "team-data-science"]
  }'
```

Tags work on all endpoints: `/v1/chat/completions`, `/v1/completions`, and `/v1/embeddings`.

### Spend Reporting by Tag

Query spend for any tag, optionally filtered by time period (`1h`, `24h`, `7d`, `30d`):

```bash
# Total spend for a tag
curl "http://localhost:4000/tag/spend?tag=project-alpha" \
  -H "Authorization: Bearer sk-master-1234"

# Spend in the last 7 days
curl "http://localhost:4000/tag/spend?tag=project-alpha&period=7d" \
  -H "Authorization: Bearer sk-master-1234"
```

Response includes total spend, request count, total tokens, and spend broken down by model:

```json
{
  "tag": "project-alpha",
  "totalSpend": 12.50,
  "requestCount": 245,
  "totalTokens": 1250000,
  "spendByModel": {
    "gpt-4": 10.00,
    "gpt-4o": 2.50
  },
  "period": "7d"
}
```

### Tag-Based Budget Enforcement

Set a maximum budget for a tag. Once the tag's cumulative spend reaches the limit, further requests with that tag are rejected with a 400 error.

```bash
# Create a tag budget ($100 max, resets monthly)
curl -X POST http://localhost:4000/tag/budget/new \
  -H "Authorization: Bearer sk-master-1234" \
  -H "Content-Type: application/json" \
  -d '{
    "tag": "project-alpha",
    "maxBudget": 100.00,
    "budgetDuration": "30d"
  }'

# Check tag budget status
curl "http://localhost:4000/tag/budget/info?tag=project-alpha" \
  -H "Authorization: Bearer sk-master-1234"

# Update a tag budget
curl -X POST http://localhost:4000/tag/budget/update \
  -H "Authorization: Bearer sk-master-1234" \
  -H "Content-Type: application/json" \
  -d '{"tag": "project-alpha", "maxBudget": 200.00}'

# Delete a tag budget
curl -X POST http://localhost:4000/tag/budget/delete \
  -H "Authorization: Bearer sk-master-1234" \
  -H "Content-Type: application/json" \
  -d '{"tag": "project-alpha"}'
```

### Tag Metrics (Prometheus)

Tags are automatically tracked in Prometheus metrics:

- `llm_monkey_tag_requests_total{tag, model}` -- request count per tag
- `llm_monkey_tag_spend_total{tag}` -- cumulative spend per tag
- `llm_monkey_tag_tokens_total{tag, direction}` -- token count per tag (input/output)

Query in Grafana to build dashboards showing spend by project, team, or feature.

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

8 Flyway migrations create:
- **budgets** -- Budget limits, durations, and reset schedules
- **organizations** -- Multi-tenant org hierarchy
- **teams** -- Team grouping within orgs
- **llm_users** -- Users with roles and SSO mapping
- **verification_tokens** -- Virtual API keys with scoped access
- **spend_logs** -- Per-request cost, usage tracking, and tags (GIN-indexed)
- **audit_logs** -- Admin operation audit trail
- **tag_budgets** -- Per-tag budget enforcement with spend tracking

## License

MIT
