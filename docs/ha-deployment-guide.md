# LLM Monkey - HA Deployment Guide

This guide covers three deployment scenarios for running LLM Monkey with high availability and zero-downtime upgrades.

## Prerequisites

- Docker and Docker Compose (for all scenarios)
- Java 21 (for local development)
- PostgreSQL 16 and Redis 7

## Application HA Features

The following features are built into the application to support HA:

| Feature | Description |
|---------|-------------|
| Graceful shutdown | 30-second drain period for in-flight requests (`server.shutdown=graceful`) |
| Liveness probe | `GET /health/liveness` — basic alive check |
| Readiness probe | `GET /health/readiness` — checks available LLM models |
| Leader election | ShedLock ensures `BudgetResetJob` runs on only one instance |
| Stateless design | All state in PostgreSQL/Redis; instances are interchangeable |
| Container-aware JVM | `MaxRAMPercentage=75%`, G1GC, `ExitOnOutOfMemoryError` |

---

## Scenario 1: Docker Compose HA

Best for: small teams, self-hosted, development/staging.

### Quick Start

```bash
# Set secrets in environment
export DB_PASSWORD=your-secure-password
export MASTER_KEY=sk-your-master-key

# Start the HA stack
docker compose -f docker-compose.ha.yml up -d

# Verify both instances are healthy
docker compose -f docker-compose.ha.yml ps
curl http://localhost/health/readiness
```

### Architecture

- **Nginx** reverse proxy with round-robin load balancing
- **2 app instances** behind Nginx
- **PostgreSQL** with volume persistence
- **Redis** with AOF persistence

### Zero-Downtime Rolling Upgrade

```bash
# Rebuild and restart one instance at a time
docker compose -f docker-compose.ha.yml up -d --no-deps --build app1
# Wait for health check to pass (~30s)
sleep 30
docker compose -f docker-compose.ha.yml up -d --no-deps --build app2
```

---

## Scenario 2: Kubernetes

Best for: production workloads, auto-scaling, multi-cloud.

### Quick Start

```bash
# Create namespace
kubectl create namespace llm-monkey

# Update secrets.yaml with real values, then apply
kubectl apply -f k8s/ -n llm-monkey

# Verify rollout
kubectl rollout status deployment/llm-monkey -n llm-monkey
```

### Architecture

- **Deployment**: 3 replicas with pod anti-affinity across zones
- **Service**: ClusterIP on port 80
- **Ingress**: Nginx ingress with TLS
- **HPA**: Auto-scales 2-10 replicas based on CPU/memory
- **PDB**: Guarantees minimum 2 available pods during disruptions

### Zero-Downtime Rolling Update

The Deployment is configured with `maxUnavailable: 0` and `maxSurge: 1`:

```bash
# Update image
kubectl set image deployment/llm-monkey llm-monkey=your-registry/llm-monkey:v2 -n llm-monkey

# Watch the rollout
kubectl rollout status deployment/llm-monkey -n llm-monkey
```

How it works:
1. New pod starts with the updated image
2. Startup probe passes (up to 60s)
3. Readiness probe passes — pod joins Service endpoints
4. Old pod receives `preStop` hook (5s sleep for LB deregistration)
5. Old pod receives SIGTERM — Spring Boot drains in-flight requests (30s)
6. Old pod terminates after `terminationGracePeriodSeconds` (35s)

### Database and Redis

**Recommended**: Use managed services (RDS, CloudSQL, ElastiCache, Memorystore).

Alternatively, use operators:
- PostgreSQL: [CrunchyData PGO](https://github.com/CrunchyData/postgres-operator)
- Redis: [Bitnami Redis Sentinel Helm chart](https://github.com/bitnami/charts/tree/main/bitnami/redis)

---

## Scenario 3: AWS ECS Fargate

Best for: AWS-native teams, managed infrastructure, production.

### Quick Start

```bash
# Deploy the CloudFormation stack
aws cloudformation deploy \
  --template-file aws/cloudformation.yml \
  --stack-name llm-monkey-production \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    VpcId=vpc-xxx \
    SubnetIds=subnet-aaa,subnet-bbb \
    PrivateSubnetIds=subnet-ccc,subnet-ddd \
    CertificateArn=arn:aws:acm:... \
    ImageUri=123456789.dkr.ecr.us-east-1.amazonaws.com/llm-monkey:latest \
    DBPassword=your-secure-password \
    MasterKey=sk-your-master-key
```

### Architecture

| Component | AWS Service | Configuration |
|-----------|------------|---------------|
| Compute | ECS Fargate | 3 tasks, multi-AZ |
| Load Balancer | ALB | TLS termination, health checks |
| Database | RDS PostgreSQL 16 | Multi-AZ, encrypted, 7-day backups |
| Cache | ElastiCache Redis 7 | Cluster mode, 1 primary + 2 replicas |
| Secrets | Secrets Manager | DB password, master key |
| Logs | CloudWatch | JSON format, 30-day retention |
| Auto-scaling | Application Auto Scaling | CPU target 60%, min 2, max 10 |

### Zero-Downtime Deployment

ECS rolling update with `minimumHealthyPercent: 100`:

```bash
# Build and push new image
docker build -t llm-monkey .
docker tag llm-monkey:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/llm-monkey:v2
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/llm-monkey:v2

# Update the task definition with new image, then update service
aws ecs update-service --cluster llm-monkey-production --service llm-monkey --force-new-deployment
```

---

## Disaster Recovery

| Strategy | RTO | RPO | Cost |
|----------|-----|-----|------|
| Backup-Restore | ~30 min | ~1 hour | 1.1x |
| Active-Passive | ~5 min | ~1 min | 1.5x |
| Active-Active | ~0 | ~0 | 2x+ |

**Recommended: Active-Passive**
- Primary region: full stack
- Secondary region: RDS cross-region read replica, warm Redis standby
- Route53 health-checked failover routing
- On failover: promote RDS replica, spin up ECS tasks / scale K8s replicas

---

## Cost Optimization

| Optimization | Savings | Applicable To |
|-------------|---------|---------------|
| Fargate Spot (dev/staging) | ~70% | AWS |
| RDS Reserved Instances (1-year) | ~40% | AWS |
| Spot/preemptible nodes | ~60% | K8s |
| Right-size via metrics | 20-40% | All |
| Scale to zero in dev | ~80% off-hours | All |

---

## Monitoring

### Prometheus Metrics

All scenarios expose metrics at `/actuator/prometheus`:

- `llm_monkey_requests_total` — request count by model/provider/status
- `llm_monkey_request_duration_seconds` — latency histogram
- `llm_monkey_tokens_total` — token usage
- `llm_monkey_spend_total` — cumulative spend
- `llm_monkey_active_requests` — in-flight request gauge

### Alerting Recommendations

| Alert | Condition | Severity |
|-------|-----------|----------|
| High error rate | 5xx > 5% for 5 min | Critical |
| High latency | p99 > 30s for 5 min | Warning |
| Pod/task unhealthy | readiness fails 3x | Critical |
| Budget near limit | spend > 90% of budget | Warning |
| Redis unavailable | connection errors > 0 for 2 min | Warning |
| DB connection pool exhausted | active = max for 1 min | Critical |
