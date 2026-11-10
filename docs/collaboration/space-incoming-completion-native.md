# Retained incoming-provider evidence on actual Matrix

Mobile commit07e22182 adds exact retained provider receipts, admitted phase advancement and clean
incoming-room completion. Four unchanged native scenario identities pass in228.267seconds with
the immutable Matrix SDK6 and a fresh2,560MiB/two-core emulator. The emulator was stopped afterward.

Both receiving scenarios retain32Matrix requests: four encrypted sends, three sync requests,
two secret-storage requests and two provider pages. Metadata-only completion uses9native requests
and4direct-transport admissions; resolving13pending source references uses10native requests and
5direct-transport admissions. Extra checkpoint readbacks are local. Exact offline reopening,
original/mixed sending and lost-callback recovery controls remain intact. No compilation occurred
while the emulator was running; source and APK hashes stayed unchanged.

This validates the new typed receipt inside the existing actual SDK/provider scenarios. It does
not enable an incoming worker, measure phone latency or deploy Group schema117–140. The live
backend remains90163/schema116.4. The next mobile increment owns durable scan/queue integration,
fair bounded worker turns and per-room retry eligibility.

[Native evidence](../measurements/collaboration-space-incoming-completion-native-2026-11-10.json).
