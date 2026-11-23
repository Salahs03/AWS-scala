# Salah's Scala + AWS workshop

This branch adds **Module 5: SQS, messages and queues** to the existing Give Name application.

The app can send and receive messages through an in-memory queue locally, or Amazon SQS on AWS. Its web page includes Send message and Read message buttons. Existing name, health, identity and request-tracing routes remain available.

## Run and test locally

From the repository root:

```sh
DISPLAY_NAME=Salah APP_ENV=local scala run Scala.intro --main-class Main --server=false
```

Open <http://localhost:8080/>. Local messages disappear when the app stops. Stop any older server using port 8080 before starting this one.

```sh
scala test Scala.intro --server=false
scala --power package Scala.intro --assembly --main-class Main --server=false -o app.jar --force
```

Run the package command only after tests pass. The JAR and build caches are excluded from Git.

## Main files

| File | Purpose |
|---|---|
| `Scala.intro/Main.scala` | Starts the app and selects the local queue or SQS |
| `Scala.intro/config/AppConfig.scala` | Validates environment settings |
| `Scala.intro/http/NameServer.scala` | Handles API requests and logs results |
| `Scala.intro/http/Responses.scala` | Defines JSON response structures |
| `Scala.intro/queue/MessageQueue.scala` | Shared interface, message and error types |
| `Scala.intro/queue/InMemoryMessageQueue.scala` | Temporary local queue |
| `Scala.intro/queue/SqsMessageQueue.scala` | Sends, receives and deletes SQS messages |
| `Scala.intro/resources/` | Web page, browser JavaScript and logging configuration |
| `Scala.intro/*.test.scala` | HTTP, configuration and queue checks |
| `deployment/module-05/` | Example settings, scoped IAM policy and deployment instructions |

See [Module 5 notes](docs/module-05.md) and [deployment steps](deployment/module-05/README.md).

## Status

The Module 5 implementation is prepared for review. Automated checks use local queues and simulated AWS SDK clients. Live SQS permissions, visibility/redelivery and cloud deployment checks remain outstanding. Pushing this branch saves source code to GitHub; it does not deploy or restart AWS resources. Add a completion tag only after the remaining workshop checks pass.
