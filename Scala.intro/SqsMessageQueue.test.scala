import java.lang.reflect.{InvocationHandler, Method, Proxy}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{Message as SqsMessage, *}
import queue.SqsMessageQueue

// SDK client doubles: validate adapter calls without credentials or paid AWS resources.
class SqsMessageQueueTests extends munit.FunSuite:
  private def stub[A](clazz: Class[A])(call: (String, Array[AnyRef]) => AnyRef): A =
    Proxy.newProxyInstance(clazz.getClassLoader, Array(clazz), new InvocationHandler:
      def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        call(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    ).asInstanceOf[A]

  test("SQS sends the envelope and deletes received messages using the receipt handle") {
    var body = ""
    var deleted = false
    val client = stub(classOf[SqsClient]) { (method, args) =>
      method match
        case "sendMessage" =>
          val request = args(0).asInstanceOf[SendMessageRequest]
          assertEquals(request.queueUrl(), "queue-url")
          body = request.messageBody()
          assertEquals(ujson.read(body)("text").str, "hello")
          SendMessageResponse.builder().messageId("message-1").build()
        case "receiveMessage" =>
          val request = args(0).asInstanceOf[ReceiveMessageRequest]
          assertEquals(request.waitTimeSeconds().intValue(), 1)
          assertEquals(request.maxNumberOfMessages().intValue(), 1)
          ReceiveMessageResponse.builder().messages(
            SqsMessage.builder().messageId("message-1").body(body).receiptHandle("receipt-1").build()
          ).build()
        case "deleteMessage" =>
          assertEquals(args(0).asInstanceOf[DeleteMessageRequest].receiptHandle(), "receipt-1")
          deleted = true
          DeleteMessageResponse.builder().build()
        case other => fail(s"Unexpected call $other")
    }
    val queue = new SqsMessageQueue(client, "queue-url")
    assertEquals(queue.send("hello"), Right("message-1"))
    assertEquals(queue.receive().toOption.flatten.get.text, "hello")
    assert(deleted)
  }

  test("SQS empty receive, malformed envelope and denied access are distinct") {
    val empty = stub(classOf[SqsClient]) { (method, _) =>
      assertEquals(method, "receiveMessage")
      ReceiveMessageResponse.builder().build()
    }
    assertEquals(new SqsMessageQueue(empty, "queue").receive(), Right(None))
    var deleted = false
    val corrupt = stub(classOf[SqsClient]) { (method, _) =>
      if method == "deleteMessage" then deleted = true
      ReceiveMessageResponse.builder().messages(SqsMessage.builder().body("invalid").build()).build()
    }
    assert(new SqsMessageQueue(corrupt, "queue").receive().isLeft)
    assert(!deleted)
    val denied = stub(classOf[SqsClient]) { (_, _) => throw SqsException.builder().statusCode(403).build() }
    assert(new SqsMessageQueue(denied, "queue").receive().isLeft)
    assert(new SqsMessageQueue(denied, "queue").send("hello").isLeft)
  }
