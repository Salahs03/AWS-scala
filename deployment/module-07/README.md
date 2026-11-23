# Module 7 deployment preparation

These files do not deploy anything by being committed or pushed. Keep the workshop cost pause in place until deployment is reviewed.

## Deployment order after approval

1. Finish the Module 6 foundation/service prerequisites. Reuse its ECR repository, task role, execution role, queue and logs. Do not create a duplicate foundation or load balancer.
2. Create a separate CloudFormation stack, for example `salah-module-07-notes`, using `notes.json`. Supply the existing ECS **task role name** as TaskRoleName (the final name in its ARN, not the ARN itself). Review the IAM changes and acknowledge CAPABILITY_IAM.
3. Record NotesBucketName. The bucket uses SSE-S3 encryption, blocks public access, disables ACLs and requires HTTPS. Object access is limited to GetObject/PutObject under `notes/*`. ListBucket applies to this bucket only so missing objects can be identified as 404; it also allows listing its keys.
4. Build/test and publish the Module 7 image using the Module 6 process. Record its immutable tag/digest. Uploading source or a JAR does not automatically update running tasks.
5. **Update the existing Module 6 service stack** using this folder's `service.json`, preserving its existing parameter values. Change ImageUri and supply NotesBucket from step 3. This template retains the same resource logical IDs and adds NOTES_BUCKET to the task. Review a change set before executing it. Do not create it as a second service stack.
6. When ready for live verification, deliberately set DesiredCount=2. Watch ECS deployment and target health, then use the service AppUrl for the checks below.

AWS mode now requires NOTES_BUCKET as well as AWS_REGION, QUEUE_URL and DISPLAY_NAME. Local mode uses memory and needs no AWS credentials. The app obtains AWS credentials from its task role; never place access keys in the environment example or Git.

## Live checks still required

- Save and retrieve a note, overwrite the same ID, and confirm one object exists at `notes/<id>.json` with JSON text/savedAt.
- Confirm `/api/whoami` identifies different tasks; check notes across task requests and after a rolling task replacement.
- Verify 400 for invalid IDs/blank text, 413 for oversized text, and 404 for a missing note.
- With no other policy granting ListBucket, temporarily update AllowMissingNoteLookup=false: a missing object should produce a generic 500 and an AccessDenied log. Restore true and verify 404. Existing objects should still read successfully.
- Check CloudWatch note_saved/note_read and request IDs. Note text must not appear in logs. Confirm an unauthenticated direct S3 object URL is denied.
- Optional controlled wrong-prefix permission test: observe a 500, diagnose the S3 error in logs, then restore the reviewed policy.

## Pause and cleanup

Scale the service to zero when finished. Its ALB still incurs charges until the service stack is deleted; preserve settings first if doing so. Deleting the notes stack removes its IAM policy but **retains the bucket and stored notes**, which can incur storage costs. Empty/delete that bucket separately only if the notes are no longer wanted. Existing queue, logs and ECR images have separate retention/costs. No resource was created or resumed while preparing these files.
