package notes

import java.time.Instant

class InMemoryNotesStore extends NotesStore:
  private val notes = scala.collection.mutable.Map.empty[String, Note]

  def put(id: String, text: String): Either[StoreError, Note] = synchronized {
    val note = Note(id, text, Instant.now().toString)
    notes.update(id, note)
    Right(note)
  }

  def get(id: String): Either[StoreError, Option[Note]] = synchronized {
    Right(notes.get(id))
  }
