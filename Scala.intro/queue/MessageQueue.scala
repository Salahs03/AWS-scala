package queue

case class Message(
  messageId: String,
  text: String,
  sentAt: String
)

case class QueueError(message: String)

trait MessageQueue:
  def send(text: String): Either[QueueError, String]

  def receive(): Either[QueueError, Option[Message]]
