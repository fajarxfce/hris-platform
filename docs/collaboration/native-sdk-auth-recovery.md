# Actual SDK rejected-auth recovery

Mobile [8fdbe89b](https://github.com/fajarxfce/hris-mobile/commit/8fdbe89bc9c97efc152c96367e24c0a0116f8fe8) is committed; matching root/native evidence and reviewed screenshots are included.

A real second bootstrap rotates credentials on the original gateway while its HRIS native parent and device remain active. The pinned Android SDK receives a real HTTP401 on refresh and a typed ClientDelegate authentication callback. Live entry/sync stops, while existing local-read policy stays allowed. The product repository consumes the confirmed newer bootstrap only after draining the exact old native owner, preserving original private keys and encrypted store. A real SDK sync succeeds using the replacement credentials. No fixture pre-close or reenrollment substitutes for product replacement.

The dedicated driver accepts only the owned HRIS emulator/debug proof package and loopback fictional API server. Private bootstrap credentials are removed, diagnostics contain closed categories/frames only, and native handles/store/client lifetimes are bounded. The test clock advances 11 seconds past the actual ten-second rotation guard; the guard is not disabled. The native-local-read assertion checks policy, not a new actual-cache read.

The final gate also runs the normal access-renewal suite on the exact same APK, retaining its encrypted bidirectional exchange, canonical history, queued transaction, receipts, process-death and cursor assertions. Exactly three encrypted sends remain required. Both cases pass in 145.221s, with no skip, on 12,260 unchanged inputs; APK `ab255d5b991727ab1ef29fae990cc9f82e218e1e67ce847639977c26008a6ed6`. Format/architecture pass, driver cleanup and owned emulator shutdown confirmed. [Evidence](../measurements/collaboration-sdk-auth-recovery-2026-11-05.json).

Backend runtime still equals live8b40/schema116; only test/driver/docs are added. No deployment is required for this Mobile adapter fix. It does not attribute the user's actual Bima error, accept group runtime/adoption, prove late bootstrap replay/hard-parent recovery, or close any of the 54 PRD groups. Release32 is a separate validated delivery; phone31 remains until recorded.

The user confirmed that release31 displays Bima messages immediately when reopening the room. That observed improvement is separate from attributing the earlier generic connection error.
