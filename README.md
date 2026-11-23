# Salah's Scala + AWS workshop

This branch adds **Module 6: Containers with ECR and ECS Fargate** to the Module 5 messaging app. It packages the app in Docker, reports ECS task identity, drains requests on shutdown and provides automated build checks and deployment templates.

## Run and verify locally

```sh
DISPLAY_NAME=Salah APP_ENV=local scala run Scala.intro --main-class Main --server=false
```

Open http://localhost:8080/. Stop an older local server first if it uses port 8080.

```sh
bash scripts/check.sh
docker build --platform linux/amd64 -t salah-app:module-06 .
python3 scripts/container-smoke.py salah-app:module-06
```

The check script tests before assembling app.jar. The JAR/build caches stay outside Git. The container check uses in-memory messaging and removes its own container.

## Main files

| File | Purpose |
|---|---|
| `Scala.intro/Main.scala` | Loads settings, selects messaging and starts the server |
| `Scala.intro/config/AppConfig.scala` | Validates environment settings |
| `Scala.intro/http/` | Routes, JSON contracts, tracing and request logs |
| `Scala.intro/queue/` | In-memory and SQS messaging implementations |
| `Scala.intro/metadata/ServerMetadata.scala` | Resolves ECS, EC2 or local server identity |
| `Scala.intro/runtime/ServerRuntime.scala` | Starts HTTP and drains requests during shutdown |
| `Dockerfile` | Java 21 image running as a non-root user |
| `scripts/` | Test/package and local container verification |
| `.github/workflows/ci.yml` | Automated checks and optional ECR publication |
| `deployment/module-06/` | Reviewed-before-use ECR/IAM/Fargate infrastructure definitions |

See [Module 5](docs/module-05.md), [Module 6](docs/module-06.md) and the [Module 6 deployment guide](deployment/module-06/README.md).

## Status

This is a source checkpoint, not a completed cloud deployment. Live ECR publication, ECS deployment, rolling-update/failure exercises and remaining Module 5 AWS verification are outstanding. AWS resources remain paused. ECR publication requires explicit repository-variable opt-in on main; the workflow never deploys ECS. Add completion tags after the relevant live checks pass.
