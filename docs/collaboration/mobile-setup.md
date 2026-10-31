# Mobile native setup ownership

Mobile [fb1b1c13](https://github.com/fajarxfce/hris-mobile/commit/fb1b1c13ef2ddce96d8fb3ab341a108d905d32f6)
implements the native setup HTTP adapters and durable registration, provisioning
retry and gateway-bootstrap commands. The app captures current HRIS company/native
family admission, saves immutable input before transport and retains uncertain
attempts. Retries preserve the operation and versions; bootstrap also pins its
client build. Current admission is reacquired after pending storage and HTTP.

Separate AES-GCM envelopes bind requests/outcomes to audience, account, company,
native family, operation, purpose and revision. The active local session is checked
transactionally without becoming a permanent ciphertext owner. Missing keys,
ordinary deadline expiry and account changes preserve stored evidence. Gateway
credentials remain encrypted until SDK installation can be confirmed. Only an
observed confirmed registration or new provisioning-job receipt can retire through
the setup reconciliation use case.

[Mobile evidence](https://github.com/fajarxfce/hris-mobile/blob/fb1b1c13ef2ddce96d8fb3ab341a108d905d32f6/docs/measurements/collaboration-setup-commands-2026-10-31.json)
retains 39 source hashes and final 62s root/assembly/browser checks: 2,040 desktop
cases, collaboration domain 23/data 15, browser domain 23/data 7; no failures or
skips. Its preceding CI exposed an obsolete SDK setup default and then missing
Wasm workspace lock entries. Both are corrected; inspect the new exact hosted
control run. Apple runtime acceptance remains pending.

The module is not yet installed into app navigation/DI. Next is the protected SDK
store/session owner and actual message queue restart, followed by bootstrap recovery
after a lost replay/build change, chat UI and all P0–P8. The
[SDK lifecycle findings](https://github.com/fajarxfce/hris-mobile/blob/fb1b1c13ef2ddce96d8fb3ab341a108d905d32f6/docs/collaboration-sdk-lifecycle.md)
cover the required exclusive store lock, one restore per client, queue pause before
restore/listener attachment, and void token-persistence callback. No reset/logout,
message durability, product-feature or capacity acceptance is implied. No Platform
production code, schema, live service, physical phone or Starter changed here.
