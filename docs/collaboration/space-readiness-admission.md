# Current target admission for an opaque readiness signal

Pure domain policy now validates the complete target metadata acquired under its
account/native/device/gateway guards. The snapshot contains no bearer or push token.
Current organization directory eligibility is required independently of any selected
HR company; short native/gateway access expiry and old MFA assurance do not retire
an otherwise valid recipient. Native hard expiry,revocation,account security changes,
client minimums and the claimed publication lifetime still apply.

The policy preserves actual selected Matrix identity. A retained HRIS_ device needs
its exact completed original owner,fingerprint,retired source and provisioning
provenance. A fresh HRISG_ device may retain that original Matrix user without gaining
an original-device history reference. It requires its own confirmed key publication.
Receiver keys use capturedAt as their cutoff; the issuer's earlier observation clock
is not a receiver commit boundary. The queue projection already fences commit order.

Admission retains exact device/native/security/gateway/key/provider observations for
comparison after external I/O. A provider response cannot acknowledge a changed
identity or credential generation. Missing,expired or mismatched bearer credentials
are not refreshed here; the owning worker must defer them within its finite queue
budget. FCM registration is not required for Matrix readiness. Admission grants only
one bounded opaque wake,never content,interactive access,key generation or a message ACK.

[Audited validation](../measurements/collaboration-space-readiness-admission-2026-11-09.json)
passes203 domain tests in30.847 seconds,including seven new policy tests,zero failures/
errors/skips,on8,720 unchanged inputs. Tests cover short-access versus hard expiry,
account/scope/native substitutions,Android/iOS minimums,receiver publication timing,
post-I/O changes,bearer bounds,and both retained original and fresh-on-original-user
identity. Format and architecture pass. These are pure JVM domain tests,not a native
phone,provider-delivery or end-to-end fanout claim.

No DI,worker loop,migration,provider I/O,Mobile or SDK change. Next connect the policy
to bounded Matrix/PUSH fanout with target-scoped acknowledgement and publisher-only
settlement,then durable Mobile foreground/background ownership. Live schema116.4,
group routing OFF and phone87 remain;all54 PRD acceptance groups are still open.
