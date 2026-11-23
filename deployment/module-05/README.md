# Module 5 deployment settings

These examples do not create or restart AWS resources. Keep the existing cost pause in place until deliberately resuming deployment.

1. Confirm the Standard `salah-messages` queue in `eu-west-2`: visibility timeout 30 seconds, retention four days, SSE-SQS encryption. Copy its actual URL and ARN.
2. Replace the account placeholder in `sqs-policy.json` with the actual queue ARN and attach the policy to the EC2 app role. It grants only send, receive, delete and queue-attribute access to that queue. Do not add access keys to source code or user data.
3. Run tests and build `app.jar` using the commands in the root README. Upload the verified JAR to the intended artifact location, for example `module-05/app.jar`, and ensure the EC2 role can read that specific S3 object. S3 is only distributing the JAR here; messages are stored in SQS.
4. In the launch-template user-data download command, change only the artifact source path; retain the destination `/opt/salah-app/app.jar` and region option.
5. In the systemd unit's `[Service]` section, keep HOST and DISPLAY_NAME and add:

```ini
Environment=APP_ENV=aws
Environment=AWS_REGION=eu-west-2
Environment=QUEUE_URL=YOUR_ACTUAL_QUEUE_URL
```

These lines belong inside the systemd unit, not inside the `aws s3 cp` command. `app.env.example` is a reference for required values; it is not automatically read by the app.

6. Review the complete launch-template version before selecting it for the Auto Scaling group. An instance refresh is a future deployment action and can start billable resources. Do not run it during the workshop break.
7. Verify live behaviour and CloudWatch logs, then restore any intentionally removed permissions. Keep Auto Scaling desired/min/max at zero when pausing; handle load-balancer costs separately.

The older `deployment/salah-app.service` is retained as the earlier EC2 example. No AWS settings were changed while preparing this branch.
