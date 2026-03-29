# LLM Monkey -- Service Owner Usage Guide

This guide is for platform teams and service owners who want to deploy LLM Monkey and onboard internal services. It covers deployment, configuration, key management, API usage, cost tracking, and monitoring.

---

## Table of Contents

1. [Deployment](#deployment)
2. [Configuration](#configuration)
3. [Authentication](#authentication)
4. [Organizational Hierarchy](#organizational-hierarchy)
5. [Key Management](#key-management)
6. [Using the API](#using-the-api)
7. [Request Tags & Cost Attribution](#request-tags--cost-attribution)
8. [Budgets & Rate Limits](#budgets--rate-limits)
9. [Kubernetes Service-to-Service Auth](#kubernetes-service-to-service-auth)
10. [Monitoring & Observability](#monitoring--observability)
11. [Caching](#caching)
12. [Guardrails](#guardrails)

---

## Deployment

### Docker Compose (recommended for getting started)

```bash
# Set provider API keys
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export LLM_MONKEY_MASTER_KEY=your-secret-master-key

# Start LLM Monkey + PostgreSQL + Redis
docker-compose up
```

This starts three containers:
- **LLM Monkey** on port `4000`
- **PostgreSQL 16** on port `5432` (data persisted in a Docker volume)
- **Redis 7** on port `6379`

### Gradle (local development)

Requires a running PostgreSQL and Redis instance.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/llmmonkey
export SPRING_DATA_REDIS_HOST=localhost
export LLM_MONKEY_MASTER_KEY=your-secret-master-key
./gradlew bootRun
```

### Production (Docker / Kubernetes)

Build the image:

```bash
docker build -t llm-monkey .
```

Required environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://db:5432/llmmonkey` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `llmmonkey` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | (secret) |
| `SPRING_DATA_REDIS_HOST` | Redis hostname | `redis` |
| `SPRING_DATA_REDIS_PORT` | Redis port (default 6379) | `6379` |
| `LLM_MONKEY_MASTER_KEY` | Admin master key | (secret) |
| `OPENAI_API_KEY` | OpenAI API key | `sk-...` |
| `ANTHROPIC_API_KEY` | Anthropic API key | `sk-ant-...` |

Database migrations run automatically on startup via Flyway.

---

## Configuration

All configuration lives in `application.yml` or can be overridden with environment variables.

### Adding LLM Providers

Each entry in `model-list` maps a public model name to a provider backend:

```yaml
llm-monkey:
  model-list:
    # OpenAI
    - model-name: "gpt-4"
      provider: "openai"
      params:
        model: "gpt-4"
        api-key: "${OPENAI_API_KEY}"
        api-base: "https://api.openai.com/v1"

    # Anthropic
    - model-name: "claude-sonnet"
      provider: "anthropic"
      params:
        model: "claude-sonnet-4-20250514"
        api-key: "${ANTHROPIC_API_KEY}"
        api-base: "https://api.anthropic.com"

    # Azure OpenAI
    - model-name: "gpt-4-azure"
      provider: "azure"
      params:
        model: "gpt-4"
        api-key: "${AZURE_OPENAI_API_KEY}"
        api-base: "https://your-resource.openai.azure.com/openai/deployments/gpt-4"
        api-version: "2024-02-01"

    # AWS Bedrock
    - model-name: "claude-bedrock"
      provider: "bedrock"
      params:
        model: "anthropic.claude-v2"
        aws-region: "us-east-1"
        aws-access-key-id: "${AWS_ACCESS_KEY_ID}"
        aws-secret-access-key: "${AWS_SECRET_ACCESS_KEY}"

    # Ollama (self-hosted)
    - model-name: "llama3"
      provider: "ollama"
      params:
        model: "llama3"
        api-base: "http://ollama:11434"
```

Supported providers: `openai`, `anthropic`, `azure`, `bedrock`, `vertex`, `cohere`, `ollama`.

### Routing Strategy

Control how requests are distributed across deployments of the same model:

```yaml
llm-monkey:
  router-settings:
    routing-strategy: "round-robin"  # Options: round-robin, least-busy, latency-based, cost-based, usage-based
    num-retries: 2                   # Retries on provider failure
    timeout-seconds: 300             # Request timeout
    allowed-fails: 3                 # Failures before a deployment is cooled down
    cooldown-seconds: 60             # How long a failed deployment stays out of rotation
```

| Strategy | Behavior |
|----------|----------|
| `round-robin` | Cycle through deployments evenly |
| `least-busy` | Route to the deployment with the fewest in-flight requests |
| `latency-based` | Route to the deployment with the lowest average latency (EMA) |
| `cost-based` | Route to the cheapest deployment |
| `usage-based` | Balance by total token usage |

All strategies include automatic failover, retry with exponential backoff, and deployment cooldowns.

### Feature Toggles

```yaml
llm-monkey:
  general-settings:
    database-enabled: true       # Persistence (keys, spend, budgets)
    cache-enabled: true          # Response caching (L1 + L2)
    rate-limit-enabled: true     # TPM/RPM rate limiting
```

---

## Authentication

LLM Monkey supports three authentication methods, evaluated in this order:

1. **Master key** -- Full admin access. Set via `LLM_MONKEY_MASTER_KEY`. Use only for admin operations and initial setup.
2. **Virtual keys** (`sk-lm-*`) -- Scoped API keys with per-key budgets, rate limits, model access, and expiration. This is the primary auth method for services.
3. **Kubernetes ServiceAccount tokens** -- For in-cluster service-to-service auth without static keys. See [K8s Auth](#kubernetes-service-to-service-auth).
4. **SSO/OIDC JWTs** -- JWT-based auth for user-facing applications. Users are auto-provisioned on first login.

All requests require a `Bearer` token in the `Authorization` header:

```
Authorization: Bearer <token>
```

Public endpoints that skip auth: `/health`, `/health/readiness`, `/actuator/prometheus`.

---

## Organizational Hierarchy

LLM Monkey supports a multi-tenant hierarchy: **Organizations > Teams > Users > Keys**. Each level can have its own budgets and rate limits.

### Create an Organization

```bash
curl -X POST http://localhost:4000/organization/new \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "alias": "Acme Corp",
    "models": ["gpt-4", "claude-sonnet"],
    "maxBudget": 10000.00
  }'
```

### Create a Team

```bash
curl -X POST http://localhost:4000/team/new \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "teamAlias": "Data Science",
    "organizationId": "<org-uuid>",
    "models": ["gpt-4", "claude-sonnet"],
    "maxBudget": 2000.00,
    "tpmLimit": 100000,
    "rpmLimit": 500
  }'
```

### Create a User

```bash
curl -X POST http://localhost:4000/user/new \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "alice",
    "userEmail": "alice@acme.com",
    "userRole": "TEAM_MEMBER",
    "organizationId": "<org-uuid>",
    "maxBudget": 500.00
  }'
```

Available roles: `PROXY_ADMIN`, `ADMIN_VIEWER`, `ORG_ADMIN`, `TEAM_ADMIN`, `TEAM_MEMBER`.

### List & Inspect

```bash
# List organizations
curl http://localhost:4000/organization/list -H "Authorization: Bearer $MASTER_KEY"

# Get org details
curl http://localhost:4000/organization/info/<org-uuid> -H "Authorization: Bearer $MASTER_KEY"

# List teams
curl http://localhost:4000/team/list -H "Authorization: Bearer $MASTER_KEY"

# List users
curl http://localhost:4000/user/list -H "Authorization: Bearer $MASTER_KEY"
```

---

## Key Management

Virtual keys are the primary way to grant services access to LLM Monkey. Each key is hashed (SHA-256) before storage -- the plaintext is only returned once at creation time.

### Generate a Key

```bash
curl -X POST http://localhost:4000/key/generate \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "keyName": "billing-service-prod",
    "userId": "alice",
    "teamId": "<team-uuid>",
    "models": ["gpt-4", "gpt-4o"],
    "maxBudget": 100.00,
    "tpmLimit": 50000,
    "rpmLimit": 200,
    "budgetDuration": "30d",
    "expires": "2025-12-31T23:59:59Z"
  }'
```

Response (save the `key` field -- it won't be shown again):

```json
{
  "key": "sk-lm-abc123...",
  "keyName": "billing-service-prod",
  "token": "sha256-hash...",
  "expires": "2025-12-31T23:59:59Z",
  "maxBudget": 100.00
}
```

### Key Options

| Field | Description |
|-------|-------------|
| `keyName` | Human-readable name for the key |
| `userId` | Owner of the key |
| `teamId` | Team the key belongs to |
| `organizationId` | Organization scope |
| `models` | Allowed models (empty = all models) |
| `maxBudget` | Maximum spend in USD |
| `tpmLimit` | Tokens per minute limit |
| `rpmLimit` | Requests per minute limit |
| `budgetDuration` | Auto-reset period (`1h`, `24h`, `7d`, `30d`) |
| `expires` | Key expiration timestamp (ISO 8601) |
| `metadata` | Arbitrary key-value pairs for your tracking |

### Update a Key

```bash
curl -X POST http://localhost:4000/key/update/<key-hash> \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "maxBudget": 200.00,
    "models": ["gpt-4", "gpt-4o", "claude-sonnet"]
  }'
```

### Revoke a Key

```bash
curl -X POST http://localhost:4000/key/delete/<key-hash> \
  -H "Authorization: Bearer $MASTER_KEY"
```

### List Keys

```bash
# All keys
curl http://localhost:4000/key/list -H "Authorization: Bearer $MASTER_KEY"

# Keys for a specific user
curl "http://localhost:4000/key/list?userId=alice" -H "Authorization: Bearer $MASTER_KEY"
```

### Best Practices

- **One key per service per environment** -- e.g., `billing-service-prod`, `billing-service-staging`
- **Set model restrictions** -- Only allow models the service actually needs
- **Set budgets** -- Prevent runaway costs with per-key spend limits
- **Set expiration** -- Rotate keys regularly, especially for production
- **Use teams** -- Group related services and apply team-level budgets
- **Store keys in a secret manager** -- Never commit keys to source control

---

## Using the API

LLM Monkey exposes an **OpenAI-compatible API**. Any SDK or library that works with OpenAI will work with LLM Monkey -- just change the base URL and API key.

### Chat Completion

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-lm-your-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Summarize this quarterly report."}
    ],
    "temperature": 0.7,
    "max_tokens": 1000
  }'
```

### Streaming

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-lm-your-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Write a poem"}],
    "stream": true
  }'
```

Streaming responses use Server-Sent Events (SSE). Each chunk is a `data:` line with a JSON object, ending with `data: [DONE]`.

### Text Completion

```bash
curl http://localhost:4000/v1/completions \
  -H "Authorization: Bearer sk-lm-your-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "prompt": "The quick brown fox"
  }'
```

### Embeddings

```bash
curl http://localhost:4000/v1/embeddings \
  -H "Authorization: Bearer sk-lm-your-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "input": "The food was delicious and the waiter was friendly."
  }'
```

### List Available Models

```bash
curl http://localhost:4000/v1/models \
  -H "Authorization: Bearer sk-lm-your-key"
```

Returns only models the key is authorized to use.

### Using with OpenAI SDKs

**Python (openai library):**

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-lm-your-key",
    base_url="http://localhost:4000/v1"
)

response = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

**JavaScript/TypeScript (openai library):**

```typescript
import OpenAI from 'openai';

const client = new OpenAI({
  apiKey: 'sk-lm-your-key',
  baseURL: 'http://localhost:4000/v1',
});

const response = await client.chat.completions.create({
  model: 'gpt-4',
  messages: [{ role: 'user', content: 'Hello!' }],
});
console.log(response.choices[0].message.content);
```

**Java (OpenAI SDK or any HTTP client):**

```java
// Using Spring WebClient
var response = webClient.post()
    .uri("http://localhost:4000/v1/chat/completions")
    .header("Authorization", "Bearer sk-lm-your-key")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(Map.of(
        "model", "gpt-4",
        "messages", List.of(Map.of("role", "user", "content", "Hello!"))
    ))
    .retrieve()
    .bodyToMono(Map.class)
    .block();
```

---

## Request Tags & Cost Attribution

Tags let you track LLM usage across projects, features, teams, or any custom dimension. Pass tags on any request, then use them for cost reporting and budget enforcement.

### Passing Tags

Include a `tags` array in any request body:

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer sk-lm-your-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Summarize this report"}],
    "tags": ["project-alpha", "summarization", "team-data-science"]
  }'
```

Tags work on all endpoints: `/v1/chat/completions`, `/v1/completions`, and `/v1/embeddings`.

**Important: Tags are global.** If multiple services use the same tag (e.g., `project-alpha`), their spend is aggregated together. Tag budgets, spend reports, and Prometheus metrics are all shared across every service using that tag. This is useful for cross-cutting cost attribution (e.g., total spend for a project regardless of which service made the call), but means a tag budget is consumed by all services that send that tag.

If you need per-service visibility within a shared tag, use multiple tags per request:

```json
"tags": ["project-alpha", "billing-service", "billing-service:project-alpha"]
```

This gives you three reporting dimensions: by project, by service, and by the intersection.

**Tagging best practices:**
- Use consistent naming conventions (e.g., `project-<name>`, `team-<name>`, `service-<name>`)
- Tag by both project and service for multi-dimensional reporting
- Use composite tags (e.g., `billing-service:project-alpha`) when you need per-service breakdowns within a shared project tag
- Keep tag cardinality reasonable (dozens to hundreds, not thousands)

### Spend Reporting by Tag

Query spend for any tag, optionally filtered by time period:

```bash
# Total spend for a tag (all time)
curl "http://localhost:4000/tag/spend?tag=project-alpha" \
  -H "Authorization: Bearer $MASTER_KEY"

# Spend in the last 7 days
curl "http://localhost:4000/tag/spend?tag=project-alpha&period=7d" \
  -H "Authorization: Bearer $MASTER_KEY"
```

Available periods: `1h`, `24h` (or `1d`), `7d`, `30d`. Omit for all-time.

Response:

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

### Tag Budgets

Set a maximum budget for a tag. Once spend reaches the limit, requests with that tag are rejected with HTTP 400.

```bash
# Create a tag budget ($100 max, resets monthly)
curl -X POST http://localhost:4000/tag/budget/new \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "tag": "project-alpha",
    "maxBudget": 100.00,
    "budgetDuration": "30d"
  }'

# Check budget status
curl "http://localhost:4000/tag/budget/info?tag=project-alpha" \
  -H "Authorization: Bearer $MASTER_KEY"

# Update budget
curl -X POST http://localhost:4000/tag/budget/update \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"tag": "project-alpha", "maxBudget": 200.00}'

# Delete budget
curl -X POST http://localhost:4000/tag/budget/delete \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"tag": "project-alpha"}'

# List all tag budgets
curl http://localhost:4000/tag/budget/list \
  -H "Authorization: Bearer $MASTER_KEY"
```

---

## Budgets & Rate Limits

Budgets and rate limits can be set at every level of the hierarchy.

### Budget Enforcement Levels

| Level | Set via | Scope |
|-------|---------|-------|
| **Key** | `POST /key/generate` or `/key/update` | Single API key |
| **User** | `POST /user/new` or `/user/update` | All keys owned by a user |
| **Team** | `POST /team/new` or `/team/update` | All keys in a team |
| **Organization** | `POST /organization/new` or `/organization/update` | All teams in an org |
| **Tag** | `POST /tag/budget/new` | All requests with a given tag |

When a request arrives, budgets are checked at all applicable levels. If any level is exceeded, the request is rejected.

### Rate Limits

Rate limits are specified as:
- **TPM** (tokens per minute) -- limits total token throughput
- **RPM** (requests per minute) -- limits request count

Rate limiting uses Redis (sliding window algorithm) with an in-memory fallback when Redis is unavailable.

```bash
# Key with rate limits
curl -X POST http://localhost:4000/key/generate \
  -H "Authorization: Bearer $MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "keyName": "rate-limited-key",
    "tpmLimit": 50000,
    "rpmLimit": 100
  }'
```

### Budget Reset

Budgets can auto-reset on a schedule using `budgetDuration`:

| Duration | Meaning |
|----------|---------|
| `1h` | Reset every hour |
| `24h` | Reset every 24 hours |
| `7d` | Reset every 7 days |
| `30d` | Reset every 30 days |

When the duration elapses, the spend counter resets to zero and the budget is available again.

---

## Kubernetes Service-to-Service Auth

For services running in Kubernetes, LLM Monkey supports authentication via projected ServiceAccount tokens. This eliminates the need for static API keys in your cluster.

### How It Works

1. Your service mounts a projected ServiceAccount token with the correct audience
2. Your service sends requests with `Authorization: Bearer <sa-token>`
3. LLM Monkey validates the token via the Kubernetes TokenReview API
4. Authorization is checked against a configured allowlist of `(namespace, serviceAccount, path, method)` tuples

### Enable K8s Auth

Add to your `application.yml` or environment:

```yaml
llm-monkey:
  k8s-auth:
    enabled: true
    audience: "llm-monkey"
    allowlist:
      - namespace: "billing"
        service-account: "billing-api"
        permissions:
          - path-pattern: "/v1/chat/completions"
            methods: ["POST"]
          - path-pattern: "/v1/models"
            methods: ["GET"]
      - namespace: "search"
        service-account: "search-service"
        permissions:
          - path-pattern: "/v1/embeddings"
            methods: ["POST"]
          - path-pattern: "/v1/**"
            methods: ["GET"]
```

Path patterns support Ant-style globs (`/v1/**` matches any path under `/v1/`).

### Caller Setup

In the calling service's pod spec, add a projected volume with the matching audience:

```yaml
spec:
  serviceAccountName: billing-api
  containers:
    - name: app
      volumeMounts:
        - name: sa-token
          mountPath: /var/run/secrets/tokens
          readOnly: true
      env:
        - name: LLM_MONKEY_TOKEN_PATH
          value: /var/run/secrets/tokens/llm-monkey-token
  volumes:
    - name: sa-token
      projected:
        sources:
          - serviceAccountToken:
              audience: "llm-monkey"
              expirationSeconds: 3600
              path: llm-monkey-token
```

Then use the token in requests:

```bash
TOKEN=$(cat /var/run/secrets/tokens/llm-monkey-token)
curl http://llm-monkey.platform:4000/v1/chat/completions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"model": "gpt-4", "messages": [{"role": "user", "content": "Hello"}]}'
```

### Token Caching

LLM Monkey caches TokenReview results (default 30s TTL) to avoid calling the K8s API on every request. Projected tokens typically have a 1-hour lifetime, so the cache is safe.

Configure with: `llm-monkey.k8s-auth.token-cache-ttl-seconds: 30`

### Error Behavior

| Scenario | HTTP Status | Behavior |
|----------|-------------|----------|
| Valid token, authorized | 200 | Request proceeds |
| Valid token, not in allowlist | 403 | Rejected |
| Invalid/expired token | 401 | Falls through to SSO JWT check, then 401 |
| K8s API unreachable | 503 | Rejected (no silent fallthrough) |

---

## Monitoring & Observability

### Health Checks

```bash
# Liveness
curl http://localhost:4000/health

# Readiness (checks DB + Redis connectivity)
curl http://localhost:4000/health/readiness
```

### Prometheus Metrics

Scrape `http://localhost:4000/actuator/prometheus` for metrics including:

| Metric | Description |
|--------|-------------|
| `llm_monkey_requests_total{model, provider, status}` | Request count by model and status |
| `llm_monkey_tokens_total{model, direction}` | Token count (input/output) |
| `llm_monkey_spend_total{model}` | Cumulative spend by model |
| `llm_monkey_latency_seconds{model, provider}` | Request latency histogram |
| `llm_monkey_tag_requests_total{tag, model}` | Request count per tag |
| `llm_monkey_tag_spend_total{tag}` | Cumulative spend per tag |
| `llm_monkey_tag_tokens_total{tag, direction}` | Token count per tag (input/output) |

### Example Prometheus Scrape Config

```yaml
scrape_configs:
  - job_name: 'llm-monkey'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['llm-monkey:4000']
```

### Grafana Dashboard Ideas

- **Cost dashboard**: `sum(llm_monkey_spend_total) by (model)` -- spend by model over time
- **Tag dashboard**: `sum(llm_monkey_tag_spend_total) by (tag)` -- spend by project/team
- **Latency dashboard**: `histogram_quantile(0.95, llm_monkey_latency_seconds)` -- P95 latency per provider
- **Rate dashboard**: `rate(llm_monkey_requests_total[5m])` -- request rate per model

### Audit Logging

All admin operations (key creation/deletion, team changes, budget updates) are logged to the `audit_logs` table with before/after values. Query this table for compliance and forensic analysis.

---

## Caching

LLM Monkey uses a two-level cache to reduce costs and latency for repeated queries:

- **L1 (Caffeine)**: In-process, per-instance. Fast but not shared across replicas.
- **L2 (Redis)**: Shared across all instances. Slightly slower but consistent.

Caching is automatic for **deterministic** requests: `temperature=0` and `stream=false`. Requests with `temperature > 0` or streaming enabled are never cached.

To disable caching:

```yaml
llm-monkey:
  general-settings:
    cache-enabled: false
```

---

## Guardrails

LLM Monkey includes content filtering that runs before and after LLM calls:

- **Regex patterns** -- Block requests matching custom regex
- **Keyword blocking** -- Block requests containing specific words
- **PII masking** -- Detect and mask personally identifiable information

Configure guardrails in your deployment configuration. Guardrails apply globally to all requests passing through the proxy.

---

## Quick Reference: All API Endpoints

### OpenAI-Compatible

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/chat/completions` | Chat completion (streaming & non-streaming) |
| `POST` | `/v1/completions` | Text completion |
| `POST` | `/v1/embeddings` | Generate embeddings |
| `GET` | `/v1/models` | List available models |

### Key Management

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/key/generate` | Create a virtual key |
| `GET` | `/key/info/{keyHash}` | Get key details |
| `POST` | `/key/update/{keyHash}` | Update key settings |
| `POST` | `/key/delete/{keyHash}` | Revoke a key |
| `GET` | `/key/list` | List all keys |

### Tags & Budgets

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/tag/budget/new` | Create a per-tag budget |
| `POST` | `/tag/budget/update` | Update a tag budget |
| `POST` | `/tag/budget/delete` | Delete a tag budget |
| `GET` | `/tag/budget/info?tag=` | Get tag budget details |
| `GET` | `/tag/budget/list` | List all tag budgets |
| `GET` | `/tag/spend?tag=&period=` | Get spend report for a tag |

### Organization Management

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/organization/new` | Create an organization |
| `GET` | `/organization/list` | List organizations |
| `GET` | `/organization/info/{orgId}` | Get organization details |
| `POST` | `/organization/update/{orgId}` | Update an organization |

### Team Management

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/team/new` | Create a team |
| `GET` | `/team/list` | List teams |
| `GET` | `/team/info/{teamId}` | Get team details |
| `POST` | `/team/update/{teamId}` | Update a team |
| `POST` | `/team/delete/{teamId}` | Delete a team |

### User Management

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/user/new` | Create a user |
| `GET` | `/user/list` | List users |
| `GET` | `/user/info/{userId}` | Get user details |
| `POST` | `/user/update/{userId}` | Update a user |

### Health & Metrics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `GET` | `/health/readiness` | Readiness probe |
| `GET` | `/actuator/prometheus` | Prometheus metrics |
