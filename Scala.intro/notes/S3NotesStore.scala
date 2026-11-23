package notes

import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, PutObjectRequest, NoSuchKeyException, S3Exception}

class S3NotesStore(client: S3Client, bucket: String) extends NotesStore:
  private val logger = org.slf4j.LoggerFactory.getLogger("S3NotesStore")

  def put(id: String, text: String): Either[StoreError, Note] =
    attempt("put") {
      val note = Note(id, text, Instant.now().toString)
      val body = ujson.Obj("text" -> text, "savedAt" -> note.savedAt).render()
      client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(s"notes/$id.json")
          .contentType("application/json").build(),
        RequestBody.fromString(body)
      )
      note
    }

  def get(id: String): Either[StoreError, Option[Note]] =
    attempt("get") {
      try
        val response = client.getObjectAsBytes(
          GetObjectRequest.builder().bucket(bucket).key(s"notes/$id.json").build()
        )
        Some(response.asUtf8String())
      catch
        // Permission failures and missing buckets are NOT missing notes.
        case _: NoSuchKeyException => None
    }.flatMap {
      case None => Right(None)
      case Some(body) =>
        Try {
          val json = ujson.read(body)
          val savedAt = json("savedAt").str
          Instant.parse(savedAt)
          Some(Note(id, json("text").str, savedAt))
        }.toEither.left.map { _ =>
          logger.atError().addKeyValue("event", "invalid_stored_note")
            .addKeyValue("noteId", id).log("Stored note could not be decoded")
          InvalidStoredNote
        }
    }

  private def attempt[A](operation: String)(action: => A): Either[StoreError, A] =
    try Right(action)
    catch
      case error: S3Exception =>
        logger.atError().addKeyValue("event", "s3_failure")
          .addKeyValue("operation", operation)
          .addKeyValue("awsErrorCode", Option(error.awsErrorDetails()).map(_.errorCode()).getOrElse("UNKNOWN"))
          .addKeyValue("awsRequestId", error.requestId())
          .addKeyValue("awsExtendedRequestId", error.extendedRequestId())
          .log("S3 operation failed")
        Left(StorageFailure(operation))
      case NonFatal(error) =>
        logger.atError().addKeyValue("event", "storage_failure")
          .addKeyValue("operation", operation)
          .addKeyValue("errorType", error.getClass.getSimpleName)
          .log("Storage operation failed")
        Left(StorageFailure(operation))
