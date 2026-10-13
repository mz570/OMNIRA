# OMNIRA Changelog

Production-facing changes are recorded here, newest first. Entries under
`Unreleased` are implemented but have not necessarily shipped. Move them into a
dated version section only after that build is published to production.

## Unreleased

### Added

- Show an app-wide offline banner when the device has no validated internet connection.
- Add Profile account deletion with typed `DELETE` confirmation and password
  reauthentication. The flow removes the profile and supported account-linked
  Firestore records, including authored posts, comments, materials, assignments,
  class-chat messages, attendance records/sheets, reports, targeted notifications,
  memberships, and the user's direct messages.
- Transfer owned classes to an existing admin or member and permit the departing
  participant to be removed from a direct-message thread.

### Notes

- Files uploaded to Cloudinary are not removed by account deletion and may remain
  accessible.
- Account deletion is blocked if the user owns a class with no other admin or member
  to receive ownership.
- The Firestore cleanup depends on ownership fields present in records and does not
  remove unidentifiable class records such as routines. The multi-step client cleanup
  can partially complete if a Firestore operation fails.
- Publish the matching `firestore.rules` changes to the Firebase project before
  releasing account deletion.

## Production release template

Copy this section for each published production build. Use the version name and
version code from `app/build.gradle.kts`.

```markdown
## [<versionName>] - YYYY-MM-DD

### Added
- <User-visible feature>

### Changed
- <Behavior or workflow change>

### Fixed
- <Bug fix>

### Notes
- <Required backend/rules migration, limitation, or action>
```
