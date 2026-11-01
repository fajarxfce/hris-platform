# Retrying while device keys are being published

A valid own-device key reservation is temporary unavailability while provider upload/readback
is in progress. Returning a malformed-request error can stop the SDK's encrypted send before
a concurrent key upload finishes. Both to-device envelopes and encrypted room sends now use
the same exact-owner publication check.

| Own publication | Gateway response | Send behavior |
| --- | --- | --- |
| Absent | 400 / `collaboration_device_keys_required` | No send; enrollment remains required. |
| Version 0, unpublished | 503 / `collaboration_device_keys_pending` | No send; the SDK can retry. |
| Version 1, published | Existing admission and provider-key validation | Send only after all checks pass. |
| Wrong owner or malformed state | Existing invalid-provider failure | No send. |

Account/company/device admission and permission denial still precede this check. The
reservation cannot publish itself, bypass fingerprint readback, or establish verification
trust. Signed-key-only upload and recipient publication requirements are unchanged. There
is no new database migration or DI boundary.

## Evidence

The first native five-case attempt passed SDK, WorkManager, queued and cancelled-draft
cases, but frozen-only delivery exhausted its bounded retries. The sanitized trace records
key upload/query 503 responses, then a to-device 400 `collaboration_device_keys_required`,
later successful key uploads and no encrypted room send. The final diagnostic was
`HrisProof_Timeout_DeliveryTimeout`; cleanup succeeded. This failure is retained.

Three new real-SQL tests exercise in-flight publication, a lost upload response followed by
exact reservation recovery, and revoked permission. Before correction, two failed with
VALIDATION where UNAVAILABLE was expected; denial precedence already passed. The correction
passes 40 domain, 210 data and 26 HTTP cases, plus format/architecture in 423s / 424.148s.
Missing-registration tests keep their original validation behavior. Concurrent upload must
finish before either send reaches the provider; original transaction/message input remains.

The same Android proof APK `3c15eb832190528e92cd85c8385e3367815cd6cc6ae809f7dd26525af4c17275` then passes all five native cases in 356s Gradle / 357.121s wall.
[Source, failed attempt, correction and final evidence](../measurements/chat-cancellation-native-2026-11-01.json)
retain both Platform versions and unchanged Mobile artifact inputs. The final QUEUED native case actually receives a pending-key 503 and completes in two worker
passes with exactly 33 encrypted sends. Frozen and cancelled cases take one pass. This verifies
the SDK retry path on an observed pending response. Test deadlines and retry limits were not increased. The hosted workflow needs a longer overall build/test job budget
for the additional case; that is separate from these operation limits.

No live server/provider rollout or complete communications feature group is accepted by
these fixtures. Continue push/pusher admission, opaque notification recovery and inline reply.
