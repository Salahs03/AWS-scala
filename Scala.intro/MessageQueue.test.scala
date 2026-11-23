import queue.InMemoryMessageQueue

class MessageQueueTests extends munit.FunSuite:

  test("An empty queue returns no message") {
    val queue = new InMemoryMessageQueue

    assertEquals(queue.receive(), Right(None))
  }

  test("Sending then receiving returns the same message and ID") {
    val queue = new InMemoryMessageQueue

    val sentId = queue.send("Hello Salah") match
      case Right(id) => id
      case Left(error) => fail(error.message)

    assert(sentId.nonEmpty)

    queue.receive() match
      case Right(Some(message)) =>
        assertEquals(message.messageId, sentId)
        assertEquals(message.text, "Hello Salah")
        assert(message.sentAt.nonEmpty)

      case Right(None) =>
        fail("Expected a message, but the queue was empty")

      case Left(error) =>
        fail(error.message)
  }

  test("Receiving removes the message from the local queue") {
    val queue = new InMemoryMessageQueue

    assert(queue.send("Read me once").isRight)

    val firstRead = queue.receive()
    assert(firstRead.exists(_.nonEmpty))

    assertEquals(queue.receive(), Right(None))
  }