# One history for an original canonical pair

V132 adds immutable routing reservations for canonical participants who both retain
Matrix users from the same original company. One `(original company, unsigned
account pair)` selects either an original conversation or a new organization room
intent. Original company, organization space and conversation IDs remain distinct.
A group conversation alone still does not select a provider room or expose history.

Every original conversation insertion reserves the exact LEGACY route in the same
transaction. A SPACE intent using that same original canonical pair reserves the
exact SPACE route. Both insert paths contend on the same unique database key.
Checking absence alone is insufficient: a transaction using an older snapshot must
not create a second history after another transaction commits. Unique reservations
arbitrate that conflict, and serialization failure remains a safe failed result.
If the first transaction rolls back, the other can reserve and commit its own route.

Deferred foreign keys and a deferred constraint trigger require the selected row,
exact pair, original company/canonical users and original creation time at commit.
A reservation cannot be committed separately, forged for another pair, reassigned
or deleted. Both room creation and reservation roll back together on a failed write.
Existing original conversations and same-original-company SPACE intents are backfilled
exactly. Conflicting preexisting histories abort the migration transaction for explicit
recovery; the migration never silently chooses which history to retain.

An existing original binding continues to use its LEGACY route when adopted. An
unbound original conversation still requires explicit original room recovery; it
cannot become a SPACE replacement. Fresh organization users and canonical users
from different original companies cannot represent the same original-company Matrix
pair, so their group intents do not reserve an original-pair route. Other historical
company rooms remain explicit and keep their own participants and keys.

The table contains public routing metadata only. Row policies allow the owned pair
in root scope or its exact original company; unrelated actors and other selected
companies cannot read it. Insert policy distinguishes original company writes from
organization root writes. Invoker triggers use runtime permissions, with no new
security-definer role or provider/native secret access. Immutable routes survive
native parent cleanup and archive restore.

Application lock ordering remains important for liveness. Native group room setup
must acquire any original pair guard before account/native guards, in the same
order as the old opener. The unique route enforces correctness independently of
advisory lock timing. Current admission, actionable native routing behavior and
provider lifecycle integration remain separate work before product rollout.

[Evidence](../measurements/collaboration-original-pair-routing-2026-11-04.json)
records the final five focused cases PASS99.972s, then all98 database and25 existing
native API cases PASS967.86s with format/architecture on8,387 unchanged inputs.
Recovery19 passes with schema132 accepted and133 rejected. The first focused upgrade
failure (pending deferred FK events before ALTER TABLE) is resolved by moving the
backfill after DDL. Both concurrent insert orders, committed/rolled-back winner and
READ COMMITTED/REPEATABLE READ are covered. All test sessions are closed. No API/worker constructor,
product caller, live migration or phone APK is introduced by this storage change.
