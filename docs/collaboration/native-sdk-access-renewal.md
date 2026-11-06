# Original Android SDK access renewal

The actual pinned Matrix SDK renews both original gateway sessions after their access
expires. The fixture advances the isolated HRIS server clock beyond both access
expirations after process death and restoration of the original protected SDK stores.
Two successful Matrix `/refresh` requests and exactly one metadata version advance
per original gateway must finish before delivery resumes. Device identity, keys,
namespace and queued transaction remain the same; no replacement bootstrap or login
is issued by the mobile fixture.

The existing encrypted exchange, retained sync cursor after a 429, canonical history,
read receipts, uncertain acknowledgement and subsequent process-death checks remain
mandatory. Exactly three encrypted sends reach the gateway. Held-room delivery stays
paused. After the native driver finishes, the server replays each original successful
refresh to obtain a reader token, verifies unchanged gateway versions and checks the
same ciphertext/sender/plaintext-absence assertions. Tokens never enter evidence.

This fixture freezes the server clock after advancing it; Android wall time does not
change. Consequently the existing 30-second replay window remains open for the final
independent ciphertext verifier. This is not a proof of late replay or client clock
skew. It verifies normal SDK renewal and persistence, not the cause of the actual Bima
failure or recovery after a rejected/lost refresh response.

Native case 51.293 s; complete native/format/architecture gate 123.990 s, one case
passed with no skip. All 12,248 backend/mobile inputs and APK bytes remain unchanged.
The dedicated proof APK build passed 18 host cases in 168.282 s on 4,040 unchanged
Mobile inputs. Product source is Mobile9d5ca633; backend runtime matches live8b40,
with only the two test files changed. Fixture cleanup and owned-emulator shutdown
succeed. [Evidence](../measurements/collaboration-sdk-access-renewal-2026-11-05.json).

Release31 is installed on the authorized phone; no phone UI is driven by this test.
Continue operation-only diagnosis of the real Bima error. A typed SDK auth-error
callback and exact original-gateway recovery may be needed, but this normal-renewal
result does not establish that they caused or solve the user's failure. Keep owned
local history independent of transport and preserve private SDK keys. All54PRD groups
remain open; no new production deployment, FCM/SystemUI, Apple or performance claim.
