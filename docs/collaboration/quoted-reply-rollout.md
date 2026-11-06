# Quoted-reply pilot rollout

The private API and worker now run CI source11a98115, including the bounded encrypted
reply envelope. All14 full Verify jobs passed. Downloaded archive digests match
GitHub; image JAR bytes match those artifacts and runtime user remains10001:10001.
Only the two application image references changed. Prior images remain tagged.

Activation passed19.094s: API and provider healthy, worker started without restarts.
Schema116, database/storage/provider containers, Matrix identity keys, private TLS,
environment and dashboard are preserved. No migration or provider restart occurred.
Trusted empty callback input returns400; direct/forged and untrusted proxy requests
remain403. FCM stays enabled and SMTP disabled. Rollback changes images/config only,
never restores stale data over messages. [Evidence](../measurements/chat-quoted-rollout-2026-11-05.json).

Actual quoted encryption/process/offline recovery and current product Firebase/SystemUI
reply pass separately. Mobile release33 is installed in place with verified pullback; pending Bima
messages remain unaccepted as fixed. Continue diagnosis using the new closed markers.
All54 communications groups remain open.
