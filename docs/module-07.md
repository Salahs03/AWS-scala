# Module 7: S3 notes

## Success criteria

- Save and retrieve named notes through PUT/GET endpoints.
- Validate IDs and text, returning clear 400, 404, 413 and 500 responses.
- Switch between local memory and private S3 through the NotesStore trait.
- Store each note as JSON and preserve it across AWS task replacement.
- Use scoped IAM permissions and diagnose missing-object versus access-denied failures.
- Pass automated tests, verify the live deployment, then record the completion checkpoint.

## Contract

| Request | Result |
|---|---|
| PUT /api/notes/{id}, body `{"text":"Hello"}` | 200 with id, text, savedAt; creates or replaces the same key |
| GET /api/notes/{id} | 200 with the saved note |
| Missing note | 404 NOTE_NOT_FOUND |
| ID outside `^[a-z0-9-]{1,64}$` | 400 INVALID_NOTE_ID |
| Invalid JSON | 400 INVALID_JSON |
| Missing, non-string or blank text | 400 VALIDATION_FAILED |
| Text longer than 10,000 characters | 413 NOTE_TOO_LARGE |
| Storage/decoding failure | 500 INTERNAL_ERROR with a request ID, without internal details |

Lengths use Scala String.length (UTF-16 code units). Errors use the existing JSON error contract. savedAt is a UTC timestamp string. Repeated PUTs replace the same note rather than creating extra objects; savedAt is refreshed each time. Concurrent writes use last-write-wins semantics, with no version history or conflict detection in this exercise.

## Main additions

- `notes/NotesStore.scala`: Note model and shared interface; Right(None) means absent, Left(error) means failed.
- `notes/InMemoryNotesStore.scala`: thread-safe local store; contents disappear when the app stops.
- `notes/S3NotesStore.scala`: reads/writes `notes/<id>.json` with text/savedAt, maps only NoSuchKey to absence and logs AWS failure identifiers.
- `http/NameServer.scala`: validates requests, selects status codes and logs note ID/size without note text.
- `Main.scala` and AppConfig: select the store and require NOTES_BUCKET in AWS mode; close SDK clients after shutdown draining.
- `resources/`: Notes form with Save/Get; renders values as text rather than HTML.
- NotesApi/S3NotesStore tests: exercise HTTP behavior and SDK calls without creating AWS resources.

## Local verification

```sh
bash scripts/check.sh
docker build --platform linux/amd64 -t salah-app:module-07 .
python3 scripts/container-smoke.py salah-app:module-07
```

For manual testing run `DISPLAY_NAME=Salah APP_ENV=local scala run Scala.intro --main-class Main --server=false` and open http://localhost:8080/. Use the Notes form to save and load a note. Local memory does not prove S3 persistence.

## Cloud status and Git checkpoint

Cloud deployment and live S3/IAM/failover exercises remain pending. Follow [deployment preparation](../deployment/module-07/README.md) after approval. Pushing this branch runs build checks, not an AWS deployment. Keep the code checkpoint named Module 7; add the `w07` completion tag after the live success criteria are verified.
