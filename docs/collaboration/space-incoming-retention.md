# Incoming source retention

Mobile implementation [5f678ed3](https://github.com/fajarxfce/hris-mobile/commit/5f678ed3feab2d24e3b97e5fc9b58159f15f785e)
retains source cursors and physical/logical work in one existing encrypted snapshot/wake transaction.
Its [contract](https://github.com/fajarxfce/hris-mobile/blob/5f678ed3feab2d24e3b97e5fc9b58159f15f785e/docs/collaboration-space-incoming-retention.md)
defines bounded authenticated parts, legacy reads, immutable captures and wake completion.
The source identity is the original protected SDK store's full SHA-256 plus Matrix user/device;
it remains separate from current native admission. No new source alias or SDK client is created.

[Shared evidence](../measurements/collaboration-space-incoming-retention-2026-11-11.json)
records 552 focused SQLite/browser cases and 11,061 full cases, including 2,038 exact unchanged
controls. All prior case IDs remain; format/architecture/detekt, Android host/release compile/lint,
Wasm/browser and iOS metadata gates pass. Group product and native fixtures are unchanged.
This increment includes no Group deployment, new phone installation or phone latency claim.

The application producer, physical/inventory worker and stored-key consumer remain to integrate.
The [physical-room API](space-physical-room.md) covers published Space and retained LEGACY bindings.
An SDK locator cannot create a logical binding or grant content access. Unpublished Company
originals keep separate routing. The producer must preserve existing work on capacity loss,
record its source inventory requirement with the cursor, and avoid transport refresh per hint.
All 54 communications acceptance groups remain open.
