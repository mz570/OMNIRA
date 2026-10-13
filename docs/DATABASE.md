# Firestore data model

This document describes the collection paths used by the Android client. It is a guide to current code, not a substitute for Firestore Security Rules. Field names and default values may evolve as features change.

## Top-level collections

### `users/{userId}`

User profiles are read by screens that display names and photos. Common fields include:

| Field | Type | Purpose |
| --- | --- | --- |
| `uid` | string | Firebase user ID (some queries use this field) |
| `firstName`, `lastName` | string | Display name parts |
| `profileImageUrl` | string or null | Cloudinary profile image URL |
| Other profile fields | varies | Account/profile screen data |

### `classes/{classCode}`

The class code is also used as the Firestore document ID.

| Field | Type | Purpose |
| --- | --- | --- |
| `code` | string | Shareable class code |
| `name` | string | Class display name |
| `section`, `batch`, `teacher` | string | Class metadata |
| `creatorId` | string | Original creator; treated as an administrator |
| `admins` | array of strings | Additional administrator user IDs |
| `members` | array of strings | Class member user IDs |
| `imageUrl` | string or null | Class image |
| `chatRetentionMonths` | integer | Chat retention setting; client constrains it to 1–12 |

### `notifications/{notificationId}`

Shared notification documents power the dashboard, class alerts, and user-targeted updates.

| Field | Type | Purpose |
| --- | --- | --- |
| `title`, `content` | string | Notification text |
| `timestamp` | integer | Unix epoch milliseconds |
| `authorName` | string | Display name of the actor |
| `className`, `classId` | string | Optional class context |
| `type` | string | Event discriminator, such as `MATERIAL`, `ASSIGNMENT`, `JOIN_REQUEST`, `POST_APPROVAL`, `COMMENT_APPROVAL`, or `CHAT_REPORT` |
| `recipientId` | string | Target user; empty for general/class-wide items |
| `requestId`, `postId`, `commentId`, `reportId` | string | References used by approval/report flows |
| `readBy` | array of strings | User IDs who have marked the item read |

The class bell filters notifications by class and recipient. The general notification screen also reads recipient-targeted items so alerts remain accessible after class membership changes.

### `directThreads/{threadId}`

`threadId` is the two participant user IDs, sorted and joined with `_`.

| Field | Type | Purpose |
| --- | --- | --- |
| `participants` | array of user IDs | Normally two user IDs; account deletion removes the departing user, so a thread can temporarily remain with one participant |
| `lastMessage` | string | Inbox preview (text or attachment label) |
| `lastTimestamp` | integer | Timestamp of the latest message |
| `lastSenderId` | string | Sender of the latest message |
| `lastReadBy` | array of strings | Users who have read the latest thread activity |
| `hiddenFor` | array of strings | Users who removed the thread from their inbox |

Messages are stored in `directThreads/{threadId}/messages/{messageId}`.

Account deletion removes the departing user's direct messages and removes their ID
from the thread's `participants`, `hiddenFor`, and `lastReadBy` arrays. If they sent
the last message, the preview fields are cleared. A remaining thread document can
have one participant after cleanup.

## Class subcollections

| Path | Purpose / model |
| --- | --- |
| `classes/{classCode}/posts/{postId}` | Stream post; `Post` |
| `classes/{classCode}/posts/{postId}/comments/{commentId}` | Stream comment; `Comment` |
| `classes/{classCode}/materials/{materialId}` | Material post/file; `Post` |
| `classes/{classCode}/assignments/{assignmentId}` | Assignment post/file; `Post` |
| `classes/{classCode}/routines/{routineId}` | Scheduled class; `RoutineData` |
| `classes/{classCode}/attendance/{recordId}` | Attendance session; `AttendanceRecord` |
| `classes/{classCode}/attendance_sheets/{sheetId}` | Uploaded attendance sheet; `AttendanceSheetRecord` |
| `classes/{classCode}/chats/{messageId}` | Group chat; `ChatMessage` |
| `classes/{classCode}/joinRequests/{userId}` | Pending membership request |
| `classes/{classCode}/chatReports/{reportId}` | Submitted chat report and reason |

### Chat message fields

`ChatMessage` includes `senderId`, `senderName`, `senderProfileImageUrl`, `message`, `timestamp`, `attachmentUrl`, `attachmentName`, `readBy`, and `deletedFor`. `deletedFor` hides a message from an individual user; deleting the Firestore document removes it for everyone.

### Direct message fields

`DirectMessage` includes sender identity and profile photo, text, optional attachment URL/name, timestamp, and `readBy`. Direct messages are separate from class chat.

## Model mapping

The canonical Kotlin data classes live in `app/src/main/java/com/omnira/data/Models.kt`. Fields with Firestore-specific names (for example, `approved`) use `PropertyName` annotations where needed. Keep Kotlin defaults aligned with existing stored documents so older data can still deserialize.

## Query and security considerations

- Firestore `whereIn` requests are chunked in code to stay within query limits.
- Some list fields (`members`, `admins`, `readBy`, `deletedFor`) are updated using `FieldValue.arrayUnion` and `arrayRemove`.
- UI filtering does not provide authorization. `firestore.rules` includes participant-scoped direct-message access, but its legacy class/profile/notification permissions remain broad and must be tightened before production.
- Define a retention policy and server-side cleanup for production. Client-side pruning only runs when an admin opens class chat and is not a guaranteed scheduled deletion mechanism.
- Consider moving large membership arrays into membership documents as classes grow; arrays are subject to Firestore document limits and are less suitable for high fan-out.
- The Profile account-deletion flow locates supported authored content through `authorId`, `senderId`, `teacherId`, `userId`, and chat-report identity fields. Class routines do not currently carry an author ID, so the client cannot identify and remove a user's routines. Cloudinary file objects are also outside Firestore cleanup.
- Account deletion is a sequence of client-side Firestore operations followed by Firebase Authentication deletion, not an atomic cascade; partial cleanup is possible if an operation fails. The thread participant-removal update requires the corresponding rule in `firestore.rules` to be deployed.
