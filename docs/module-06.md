# Module 6: Containers, ECR and ECS Fargate

## Purpose and success criteria

- Package the existing messaging app with Java 21 in a non-root Docker image.
- Keep the same APIs; report ECS task identity through `/api/whoami`.
- Drain active requests on shutdown, then close clients and log completion.
- Test and build in GitHub Actions; optionally publish a commit-tagged image to ECR using OIDC.
- Run two Fargate tasks across two Availability Zones, verify logs and rolling deployments, then retire EC2 after verification.

## What changes in the code

`Main.scala` selects the queue and starts `ServerRuntime`. The runtime owns Undertow's lifecycle: on SIGTERM it logs `app_stopping`, drains requests for up to 20 seconds, stops the HTTP server, closes resources and logs `app_stopped`.

`ServerMetadata.scala` uses a for-comprehension over `Either` to read ECS task metadata. Failure falls back to EC2 IMDS, then local identity. Local mode does not contact cloud metadata. The `/api/whoami` response fields stay `serverId` and `availabilityZone`.

`Dockerfile` combines Java and the assembled JAR. It runs as user 10001, exposes port 8080 and uses exec-form ENTRYPOINT so Java receives SIGTERM. The JVM heap limit is 75% of container memory, leaving room for native memory and threads.

Containers write JSON logs to stdout/stderr. ECS uses its awslogs driver to send them to the existing `/salah/app` group; systemd, logrotate and a CloudWatch agent are not needed inside the container.

## Local verification

```sh
bash scripts/check.sh
docker build --platform linux/amd64 -t salah-app:module-06 .
python3 scripts/container-smoke.py salah-app:module-06
```

The test suite covers the existing APIs and queue adapters, ECS metadata precedence/fallback and draining an active request before closing resources. The container check uses a random loopback port, checks the non-root user, health/name/identity/messaging routes and SIGTERM logs, then removes its own container.

For an interactive preview:

```sh
docker run --rm --name salah-module-06 --memory 1g -e DISPLAY_NAME=Salah -p 127.0.0.1:8080:8080 salah-app:module-06
```

Open http://localhost:8080/. Local messages are temporary. Stop with `docker stop --time 30 salah-module-06` in another terminal. Port 8080 must be available.

## GitHub workflow

Pushes and pull requests run tests, assemble the JAR, build the Linux image and exercise the container. Test failure stops packaging/publishing. A seven-day JAR artifact makes debugging convenient without committing binaries. A Release JAR is optional; the ECR image is the deployment artifact, and keeping both indefinitely duplicates storage and release bookkeeping.

Publishing is disabled unless `ENABLE_ECR_PUBLISH=true` is set as a repository variable. When enabled, only main builds may assume `AWS_DEPLOY_ROLE_ARN` through GitHub OIDC and publish to `salah-app` in eu-west-2 using the commit SHA. Existing SHA tags are preserved. The role can publish/check this ECR repository; it cannot deploy ECS, modify IAM, or access the queue. No long-lived AWS keys are stored in GitHub.

The OIDC trust must match this repository's exact `sub` claim on main. GitHub supports name-based and immutable-ID subject formats; verify which this repository uses before configuring the role. The foundation template accepts that exact value.

## Remaining live workshop checks

The code checkpoint is not proof of an AWS deployment. After separately reviewing and authorizing deployment:

1. Verify the ECR image and scan results, and two healthy ECS targets across the two AZs.
2. Confirm whoami returns task IDs/AZs, real SQS messaging works, and CloudWatch has `ecs/...` streams.
3. Deploy a DISPLAY_NAME change while sending requests; check successful responses and shutdown logs.
4. Test missing DISPLAY_NAME and a missing image tag; inspect stopped reasons and rollback to a known working deployment.
5. Stop a task and verify replacement; test public-IP removal only as a controlled exercise and restore it.
6. Retire EC2 resources only after the ECS app is verified; tag `w06` when the workshop checks are complete.

See [deployment guide](../deployment/module-06/README.md). The existing cost pause remains in place.

References: [ECS task metadata](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint-v4-response.html), [GitHub OIDC with AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).
