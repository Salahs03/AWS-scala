# Module 5 — SQS, messages and queues

## Purpose

Accept a message now and let a consumer retrieve it later. The same routes work with a local in-memory queue or Amazon SQS.

## API contract

| Request | Result |
|---|---|
| `POST /api/messages` with `{"text":"hello"}` | 202 with `messageId` and `status: queued` |
| Invalid JSON | 400 `INVALID_JSON` |
| Missing, blank, non-string or over-500-character text | 400 `VALIDATION_FAILED` |
| `POST /api/messages/receive` with an available message | 200 with `messageId`, `text`, `sentAt` |
| No message returned by SQS or the local queue | 204 without a body |
| Queue operation failure | 500 `INTERNAL_ERROR` with a request ID |

A 204 means that this receive returned nothing, not proof that SQS contains no messages: other messages may be invisible to this consumer.

## Scala reminders

- `trait`: the shared operations both queue implementations must provide.
- `Either`: `Left` represents a failure; `Right` contains a successful result.
- `Option`: `None` means no message; `Some(message)` means one was received.
- Environment variables: settings supplied when starting the app without editing source code.

## SQS behaviour

Messages contain a JSON envelope with text and an ISO-8601 UTC timestamp. The app receives at most one message with a one-second wait, decodes it, then deletes it using its receipt handle before returning the HTTP response. This follows the workshop's receive-and-remove demonstration. If the HTTP response is lost after deletion, the browser cannot retrieve that message again. A production worker needs an appropriate process/acknowledgement design.

Standard queues can deliver duplicates and do not guarantee ordering. Consumers with side effects must be idempotent: repeated processing must not repeat the effect. Receiving temporarily hides a message; if it is not deleted before the visibility timeout, it becomes eligible for delivery again. The in-memory queue is simpler and does not simulate that timeout or duplicate delivery.

## Logs and checks

`message_sent` records the message ID and text length; `message_received` records the message ID and age. Request IDs link HTTP responses to logs. Message bodies are not logged. SQS errors record AWS error codes and AWS request IDs while the API returns a generic error.

Local checks cover name/health/identity/tracing, queue send/receive/absence, validation limits, simulated failures, required settings, SQS envelopes and receipt-handle deletion. SDK tests use doubles and need no AWS credentials.

## Still to verify on AWS

- Correct EC2 role permissions and queue settings.
- Send and receive through the deployed app, including across instances if applicable.
- Match sent/received message IDs in CloudWatch.
- Observe visibility timeout and recover from a deliberate permission failure.
- Restore permissions and pause compute after testing.

Commit/push the implementation independently of deployment. Reserve `w05` as a completion marker until live checks are done.
