# Mobile published room binding observation

Mobile commit `1f47eeddb833054ae2b3d52c20439e6d11518a02` exposes exact reactive local conversation/room binding reads through the
existing encrypted repository. A changed checkpoint cancels older acquisition; identical
bindings do not publish again. The reader creates no network work, keys, bindings or retry.
Session and Group admission stay in the owning domain use case.

Focused 35 and full 14,738 pass with all 14,724 prior identities and 14 new JVM/browser cases.
Actual shared storage tests cover encrypted reopen, both origins, corrupted pairs, account
replacement and cancellation. The existing inactive-scope contract returns absence; all
old-account non-null results are explicitly rejected by the corrected new assertions.

[Evidence](../measurements/collaboration-space-room-binding-observation-mobile-2026-11-12.json)
records the preserved failure and exact hashes. No new native execution, phone build or
Group deployment is claimed. Continue unresolved-thread/composer integration in the existing
admission owner before organization UI; the current thread still uses one-shot binding reads.
