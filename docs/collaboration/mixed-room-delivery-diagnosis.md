# Mixed-room delivery observation — 7 November 2026

`NativeMixedRoomDeliveryTest` exercises actual HRIS API/PostgreSQL/Synapse and the
Android SDK4/application worker using separate fictional accounts and an owned fresh
emulator. It creates two READY rooms, confirms recipient key publication in only one,
and waits for a real missing-key rejection in the first room before requesting a new
durable message in the second. The ready recipient's SDK is closed before sending;
its native parent/device remain active. The driver never accepts a user-phone serial.

The first room returned `403 collaboration_recipient_unavailable` in 34 ms. The ready
room returned one200 in110ms; the accepted event was independently verified as
ciphertext. The ready command reached local Sent1,316ms after queue-call return,
journal observation1,321ms and immutable completion1,815ms. These device observations
are sampled independently; they are not exact storage commit timestamps, and host/
device clocks are never subtracted. The unavailable original/ID remained retained
without any receipt or fabricated success. No production guard or policy was changed.

This warm-runtime fixture did **not** reproduce the physical phone's reported20-second
to2-minute delay. It shows that the ready recipient need not keep its SDK running.
Physical phone53 latency remains open, as does its existing optional feedback request.
No production timeout, retry or writer change follows from this negative reproduction.

Mobile proof/root/Android/quality gate passed134.102s/20fresh cases/4,325 unchanged inputs,
zero failures/errors/skips/lint errors;49 existing proof lint warnings. Backend test
compilation, format and architecture passed31.020s after a retained test-only missing
URI encoder. Four driver validator cases passed. Actual native run48997 passed149.551s,
one JUnit case/no skips, including fresh emulator boot. Source and APK hashes stayed
unchanged; SDK4 was verified before startup, and the emulator/process/transport stopped.
[Complete measurement](../measurements/collaboration-mixed-room-delivery-2026-11-07.json).

Mobile owns `HrisMixedRoomProbe` and its bounded local observations. Platform owns
`NativeMixedRoomTrace` (two anonymous room labels; finite actual status/timing/error-code
observations) and `tool/collaboration_mixed_room_native.py` (readiness handshakes, private
original failure evidence and cleanup). Preparation writes only controlled fixture
metadata; actual sending/key guards execute normally. The accepted 175-message history
scenario was not repeated. This is an HRIS-specific fixture, with no Starter port.

To run it, build/freeze the proof APK and compile the backend test before starting the
dedicated emulator. Set `HRIS_COLLAB_NATIVE_APK`, `HRIS_COLLAB_NATIVE_SERIAL=emulator-5560`
and `ANDROID_HOME`, then run only `NativeMixedRoomDeliveryTest` with Docker access.
The accepted run's exact manifests and result hashes are in the measurement; never
substitute the user's Bima store or reinterpret quiet phone logs as acceptance.

Continue original organization queue delivery/publication, native-parent recovery,
product routing and all54 PRD groups. Group V117–137 remain disabled/unshipped; metadata
history and a local queue match grant no send authority. Current recipient admission
and the published group room binding remain required, preserving V132 canonical pairs.
