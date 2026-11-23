package notes

import upickle.default.ReadWriter

case class Note(id: String, text: String, savedAt: String) derives ReadWriter

// Absence is Right(None); a storage failure is Left(error).
sealed trait StoreError
case class StorageFailure(operation: String) extends StoreError
case object InvalidStoredNote extends StoreError

trait NotesStore:
  def put(id: String, text: String): Either[StoreError, Note]
  def get(id: String): Either[StoreError, Option[Note]]
