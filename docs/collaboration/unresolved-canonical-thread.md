# Mobile thread before room publication

Mobile now observes known canonical metadata and queued commands before a room
binding exists. One subscription switches to exact Space or Original history when
the authenticated binding appears, and retains commands if that binding disappears.
It neither invents empty history nor changes the original content's ownership.

The existing Group/session owner cancels obsolete reads and queued publication.
Known failures publish without waiting for an unread source; upstream invalid-access
failures never enter data reads. Bound history skips a second metadata lookup.

Focused100 and full14,764 pass, retaining all14,738 prior identities and adding26
JVM/browser cases. Actual encrypted tests cover publication, removal and renewed
login. [Evidence](../measurements/collaboration-unresolved-space-thread-mobile-2026-11-12.json)
includes the preserved failed candidate and exact source/report/proof digests.

No Group server source, deployment or phone behavior changes in this increment.
Continue local participant/composer context and organization UI. All54 PRD groups
remain open until their complete behavior is demonstrated.
