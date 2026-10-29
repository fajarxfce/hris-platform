# HTTPS links to HRIS Mobile

The dashboard serves `/app/{environment}/{route}` as a public handoff page.
An explicit **Open HRIS Mobile** action uses the environment's native scheme:
`hrismobile-dev`, `hrismobile-staging`, or `hrismobile` for production. The page
keeps the existing language/theme controls and does not activate the dashboard
session lifecycle or perform business requests. It does not automatically redirect,
show account/company identifiers, or read/acknowledge an Inbox item.

Routes are `overview`, `account`, `inbox`, `data-sync`, `pending-actions`, or
`accounts/{account}/companies/{company}/inbox/{item}` with canonical lowercase UUIDs.
Unknown environments/routes, queries, fragments, escaped segments, empty segments
and inputs over 4096 characters produce a public unavailable page with no native
action. The native application independently admits the route, session and company;
a URL never grants access or changes the selected identity.

Mobile `d36a1bc9` adds optional per-environment domain registration, bounded HTTPS
admission, Android App Links and Apple Universal Links. Its generated public
`.well-known/assetlinks.json` and `apple-app-site-association` must be served on the
registered HTTPS host; the dashboard handoff supplies the browser fallback on that
same host. See the [Mobile setup contract](https://github.com/fajarxfce/hris-mobile/blob/feat/native-push/docs/app-links.md)
for signing identifiers, build inputs and generated files. Association files must
be served directly, with the correct JSON content type and no login or redirect;
the application fallback must not replace `.well-known` responses.

No domain has been selected or published by this change. The implementation stays
disabled in unconfigured native builds. Build checks with a fictional domain prove
generated artifacts and routing, not DNS, TLS, OS verification or a live deployment.
The user waived physical iPhone tests for this completion pass on 29 October 2026.

Validation on 29 October: dashboard check passed 4002 unit cases in 353 files,
TypeScript, Biome and architecture; 14 related Chromium cases passed, including
three new handoff cases and existing identity/password recovery. A first browser
run exposed the recovery test filling the old login Email field during navigation;
it now waits for the recovery heading before filling. The corrected run passed
without retry or a larger timeout. The 320×720 Indonesian dark-mode screenshot
was visually reviewed, with no horizontal overflow and a keyboard-focusable action.
[Measurements](measurements/app-links-2026-10-29.json) retain the result and boundary.
