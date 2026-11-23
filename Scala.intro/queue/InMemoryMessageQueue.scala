package queue

import java.time.Instant
import java.util.UUID

class InMemoryMessageQueue extends MessageQueue:
  private val messages =
    scala.collection.mutable.Queue.empty[Message]

  override def send(text: String): Either[QueueError, String] =
    synchronized {
      val message = Message(
        messageId = UUID.randomUUID().toString,
        text = text,
        sentAt = Instant.now().toString
      )

      messages.enqueue(message)
      Right(message.messageId)
    }

  override def receive(): Either[QueueError, Option[Message]] =
    synchronized {
      if messages.isEmpty then
        Right(None)
      else
        Right(Some(messages.dequeue()))
    }
