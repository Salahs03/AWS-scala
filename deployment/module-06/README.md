# Module 6 deployment preparation

These CloudFormation files are reviewable infrastructure definitions. Saving or pushing them does not create AWS resources. No AWS deployment is performed by the default GitHub workflow.

## Prerequisites

- London region `eu-west-2`, the existing VPC, and two public subnets in different Availability Zones with internet-gateway routes.
- Existing Standard SQS queue: its URL and ARN must refer to the same queue.
- Existing CloudWatch log group `/salah/app` with an intentional retention period.
- Review the current AWS inventory before deploying: fixed ECR repository name `salah-app` must not already belong to another stack.

## 1. Foundation

`foundation.json` creates a private ECR repository with immutable tags, basic scanning and a policy retaining the ten newest images. Preserve any deployed/rollback image before cleanup; a ten-image policy does not know which images ECS still uses.

It also creates two ECS roles:

- **Execution role:** ECS pulls the app image and writes streams to the specified existing log group.
- **Task role:** the app sends, receives, deletes and checks attributes on the single queue supplied by `QueueArn`.

Suggested stack name: `salah-module-06-foundation`. Review the IAM resources before granting CloudFormation `CAPABILITY_IAM`. Parameters are QueueArn, LogGroupName (default `/salah/app`), and optional GitHubOidcProviderArn/GitHubOidcSubject.

The publishing role is omitted unless both OIDC fields are supplied. Create or reuse the GitHub identity provider, audience `sts.amazonaws.com`, then verify this repository's exact main-branch subject. Depending on repository settings it may be `repo:Salahs03/AWS-scala:ref:refs/heads/main` or include immutable owner/repository IDs. Do not substitute wildcard subjects. The policy permits ECR publication only.

## 2. Image publication

Keep `ENABLE_ECR_PUBLISH` unset while preparing source. After approval and foundation creation, set repository variables `AWS_DEPLOY_ROLE_ARN` to the PublisherRoleArn output and `ENABLE_ECR_PUBLISH=true`. Run the workflow on main. It first tests/builds, then publishes `salah-app:<commit-sha>` to ECR. It does not update ECS.

Review the image scan, record the exact tag/digest and use it as ImageUri. Source pushes on feature branches only run local build/test jobs. A GitHub token used to push this workflow file needs repository Contents read/write and Workflows write permission.

## 3. Service

`service.json` creates a Fargate cluster/service, task definition, IP target group, security groups and an internet-facing ALB. The earlier workshop ALB was deleted during the cost pause, so this template creates a replacement rather than attempting to reuse it. If an ALB is present when resuming, review this choice first.

Parameters:

| Parameter | Value |
|---|---|
| FoundationStack | `salah-module-06-foundation` or the chosen foundation name |
| VpcId | Existing workshop VPC |
| PublicSubnetA / PublicSubnetB | Public subnets in different AZs |
| AllowedClientCidr | Your public IPv4 address with `/32` |
| ImageUri | ECR URI with immutable SHA tag or digest |
| QueueUrl | Existing queue URL matching QueueArn |
| DisplayName | `Salah` or the intended display name |
| DesiredCount | Defaults to 0; set to 2 only when ready for the live exercise |

The ALB accepts HTTP 80 from AllowedClientCidr only; tasks accept 8080 from the ALB security group only. Tasks have public IPs for outbound access to ECR, SQS and CloudWatch in this no-NAT workshop. `/health` is the health-check path. ECS stop timeout is 30 seconds, allowing the app's 20-second drain. Rolling replacement uses min 100%, max 200% and a circuit breaker with rollback. Initial deployment needs a completed healthy deployment before rollback has a known good revision.

**Deploying the service template incurs ALB charges even with DesiredCount=0.** Running tasks, public addresses, logs and stored images can also incur charges. Do not deploy as part of merely pushing the module to GitHub.

## 4. Verify, update and pause

Use the AppUrl output for name, health, identity and messaging checks. Confirm tasks span the AZs and JSON logs arrive in `/salah/app`. Update ImageUri through the stack for each new version, monitor deployment events and verify graceful shutdown. Follow the controlled failures in [Module 6 notes](../../docs/module-06.md).

Keep the stopped EC2 setup until ECS verification succeeds. Afterwards review and remove obsolete EC2/ASG resources deliberately. For a pause, set DesiredCount=0; ALB charges continue until the service stack is deleted. Foundation deletion retains the ECR repository/images, which can still have storage costs. Existing queue and log group are not deleted by these templates.
