# Native proof storage failure evidence

The first x86_64 transport CI failed during priority history with only
`HrisProof_Storage_Unclassified`. The equivalent ARM64 run passed. That does not
identify or dismiss the original failure.

Instrumentation can now attach up to eight recent storage failures, each containing
at most four exception class labels and six application/SDK code frames. The driver
validates these bounds and copies only those fields. Messages, file names, values,
IDs, request bodies and tokens are excluded. Earlier proof APKs remain compatible.
Recent context is evidence for investigation; it does not prove which exception
caused the final result and does not change retry behavior or acceptance assertions.

Thirteen native-device tests, thirty-four HRIS driver tests and architecture checks
pass. Bounds, invalid frames/categories, privacy and detached output are tested.
No live deployment or native send was performed for this parser change.
[Evidence](../measurements/collaboration-native-storage-diagnostic-parser-2026-11-07.json).

The previously failed frozen-draft/priority worker case passed locally in350.485s
with the new diagnostic APK, one test and original assertions intact. It recovered
original transactions, priority history and all incoming history on fresh owned
x86_64 emulator5560, then removed the fixture and stopped its exact emulator.
Main/Mobile source inputs and APK remained unchanged. The original CI failure was
not reproduced and its cause remains unresolved; this result does not repair the
phone's pending messages. [Investigation](../measurements/collaboration-native-storage-investigation-2026-11-07.json).
