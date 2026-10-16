# SubNest Android Migration

## Baseline

- Upstream: https://github.com/5151561/nodyssey
- Source revision: 7eccd05bc45093dbf2fe31671ebd32979a0c6aee
- License: GPL-3.0; preserve notices and publish corresponding source with APK releases.
- Status: source imported; no SubNest business integration or APK verification yet.

## Boundaries

The Android client is a separate repository. The existing SubNest web workspace,
backend deployment, database and release pipeline must remain untouched.
Use the backend's actual API contracts rather than scraping the SubNest website.
Do not copy credentials, authenticated fixtures or user data into this repository.

Reuse the upstream Compose design system, theme, adaptive layout and platform
infrastructure where useful. Replace forum models, routes, authentication,
repositories and screens with SubNest workflows. A native client still requires
a reachable SubNest server; this is not an offline replacement for the backend.

## Implementation Order

1. Audit the existing SubNest API and authentication contract. Define server URL
   validation, secure session storage, expiration and sign-out cleanup.
2. Establish an independent application ID, branding and version scheme. Remove
   forum shortcuts, upstream updater destinations, scheduled forum workers and
   notification permissions before distributing any derivative APK.
3. Add native server setup and sign-in, then the subscription list with numeric
   search, platform selection and due/overdue filters. Preserve vertical scrolling.
4. Add shared accounts, available/full seat filters and member management.
   Renewal is an explicit user action after payment, not an automatic debit.
5. Add a compact dashboard. Confirm financial semantics against backend behavior;
   do not conflate monthly allocation with actual cash receipts.
6. Verify on phone and tablet layouts, test authentication and renewal failure
   paths, and build a separately signed self-use APK with matching source tag.

## Release Safety

GitHub Actions are disabled for this initial import. Review inherited workflows
before re-enabling CI. Replace upstream repository references, release targets,
application update URLs and signing secret names before enabling releases.
Do not publish a forum APK under the SubNest name. No message push is planned.

Each release must state tested workflows, known limitations, backend compatibility,
source revision and installation instructions. Keep signing keys out of Git and
reuse the same private signing key for future installable updates.
