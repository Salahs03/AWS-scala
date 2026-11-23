package queue

import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{
  SendMessageRequest,
  ReceiveMessageRequest,
  DeleteMessageRequest,
  SqsException
}

class SqsMessageQueue(
    client: SqsClient,
    queueUrl: String
) extends MessageQueue:

  private val logger =
    org.slf4j.LoggerFactory.getLogger("SqsMessageQueue")

  override def send(text: String): Either[QueueError, String] =
    attempt("send") {
      val body = ujson.Obj(
        "text" -> text,
        "sentAt" -> Instant.now().toString
      ).render()

      val request = SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(body)
        .build()

      val response = client.sendMessage(request)

      response.messageId()
    }

  override def receive(): Either[QueueError, Option[Message]] =
    attempt("receive") {
      val request = ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .maxNumberOfMessages(1)
        .waitTimeSeconds(1)
        .build()

      val response = client.receiveMessage(request)

      response.messages().asScala.headOption.map { received =>
        val body = ujson.read(received.body())

        val text = body("text").str
        val sentAt = body("sentAt").str

        Instant.parse(sentAt)

        val message = Message(
          messageId = received.messageId(),
          text = text,
          sentAt = sentAt
        )

        val deleteRequest = DeleteMessageRequest.builder()
          .queueUrl(queueUrl)
          .receiptHandle(received.receiptHandle())
          .build()

        client.deleteMessage(deleteRequest)

        message
      }
    }

  private def attempt[A](
      operation: String
  )(action: => A): Either[QueueError, A] =
    try
      Right(action)
    catch
      case error: SqsException =>
        val errorCode =
          Option(error.awsErrorDetails())
            .flatMap(details => Option(details.errorCode()))
            .getOrElse("UNKNOWN")

        val awsRequestId =
          Option(error.requestId()).getOrElse("unavailable")

        logger.atError()
          .addKeyValue("event", "sqs_failure")
          .addKeyValue("operation", operation)
          .addKeyValue("awsErrorCode", errorCode)
          .addKeyValue("awsRequestId", awsRequestId)
          .log("SQS operation failed")

        Left(QueueError(s"SQS $operation failed"))

      case NonFatal(error) =>
        logger.atError()
          .addKeyValue("event", "queue_operation_failed")
          .addKeyValue("operation", operation)
          .addKeyValue("errorType", error.getClass.getSimpleName)
          .log("Queue operation failed")

        Left(QueueError(s"Queue $operation failed"))
