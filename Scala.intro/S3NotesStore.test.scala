import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.nio.charset.StandardCharsets.UTF_8
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import notes.*

// SDK client doubles: validate adapter calls without credentials or paid AWS resources.
class S3NotesStoreTests extends munit.FunSuite:
  private def stub[A](clazz: Class[A])(call: (String, Array[AnyRef]) => AnyRef): A =
    Proxy.newProxyInstance(clazz.getClassLoader, Array(clazz), new InvocationHandler:
      def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef =
        call(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    ).asInstanceOf[A]

  test("S3 stores JSON at notes/id.json and reads it back") {
    var stored = ""
    val client = stub(classOf[S3Client]) { (method, args) =>
      method match
        case "putObject" =>
          val request = args(0).asInstanceOf[PutObjectRequest]
          assertEquals(request.bucket(), "example-notes")
          assertEquals(request.key(), "notes/hello.json")
          assertEquals(request.contentType(), "application/json")
          val stream = args(1).asInstanceOf[RequestBody].contentStreamProvider().newStream()
          try stored = new String(stream.readAllBytes(), UTF_8)
          finally stream.close()
          PutObjectResponse.builder().build()
        case "getObjectAsBytes" =>
          val request = args(0).asInstanceOf[GetObjectRequest]
          assertEquals(request.key(), "notes/hello.json")
          ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), stored.getBytes(UTF_8))
        case other => fail(s"Unexpected call $other")
    }
    val store = new S3NotesStore(client, "example-notes")
    val saved = store.put("hello", "Private text").toOption.get
    assertEquals(ujson.read(stored).obj.keySet.toSet, Set("text", "savedAt"))
    assertEquals(store.get("hello"), Right(Some(saved)))
  }

  test("Only NoSuchKey is absence; denied access and missing buckets remain errors") {
    val missing = stub(classOf[S3Client]) { (_, _) => throw NoSuchKeyException.builder().message("missing").build() }
    assertEquals(new S3NotesStore(missing, "bucket").get("note"), Right(None))
    for error <- Seq(S3Exception.builder().statusCode(403).message("Denied").build(),
                      S3Exception.builder().statusCode(404).message("Missing bucket").build()) do
      val client = stub(classOf[S3Client]) { (_, _) => throw error }
      assert(new S3NotesStore(client, "bucket").get("note").isLeft)
      assert(new S3NotesStore(client, "bucket").put("note", "text").isLeft)
  }

  test("Corrupt S3 data is a decoding failure, not absence") {
    val client = stub(classOf[S3Client]) { (_, _) =>
      ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "{}".getBytes(UTF_8))
    }
    assertEquals(new S3NotesStore(client, "bucket").get("note"), Left(InvalidStoredNote))
  }

