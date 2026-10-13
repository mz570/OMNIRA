package com.omnira.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source

fun deleteAccount(auth: FirebaseAuth, db: FirebaseFirestore, password: String): Task<Void> {
    val user = auth.currentUser
        ?: return Tasks.forException(IllegalStateException("You must be signed in to delete your account."))
    val email = user.email
        ?: return Tasks.forException(IllegalStateException("This account has no email address to reauthenticate."))
    if (user.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }) {
        return Tasks.forException(IllegalStateException("Password reauthentication is not available for this account."))
    }

    return user.reauthenticate(EmailAuthProvider.getCredential(email, password))
        .onSuccessTask {
            deleteAccountData(db, user.uid)
        }
        .onSuccessTask {
            user.delete()
        }
}

private fun deleteAccountData(db: FirebaseFirestore, uid: String): Task<Void> {
    val classes = db.collection("classes")
    val ownedClasses = classes.whereEqualTo("creatorId", uid).get(Source.SERVER)
    val memberClasses = classes.whereArrayContains("members", uid).get(Source.SERVER)
    val adminClasses = classes.whereArrayContains("admins", uid).get(Source.SERVER)

    return Tasks.whenAll(ownedClasses, memberClasses, adminClasses)
        .onSuccessTask {
            val allClasses = (ownedClasses.result.documents +
                memberClasses.result.documents +
                adminClasses.result.documents).distinctBy { it.id }
            val ownedIds = ownedClasses.result.documents.mapTo(mutableSetOf()) { it.id }
            val newOwners = ownedClasses.result.documents.associate { classDocument ->
                val members = (classDocument.get("members") as? List<*>)
                    .orEmpty().filterIsInstance<String>()
                val admins = (classDocument.get("admins") as? List<*>)
                    .orEmpty().filterIsInstance<String>()
                classDocument.id to (admins.firstOrNull { it != uid }
                    ?: members.firstOrNull { it != uid }
                )
            }

            if (newOwners.values.any { it == null }) {
                return@onSuccessTask Tasks.forException<Void>(
                    IllegalStateException(
                        "You own a class with no other members. Add an admin or member before deleting your account."
                    )
                )
            }

            val classUpdates = allClasses.map { classDocument ->
                val changes = mutableMapOf<String, Any>(
                    "members" to FieldValue.arrayRemove(uid),
                    "admins" to FieldValue.arrayRemove(uid)
                )
                if (classDocument.id in ownedIds) {
                    changes["creatorId"] = checkNotNull(newOwners.getValue(classDocument.id))
                }
                classDocument.reference.update(changes)
            }

            Tasks.whenAll(classUpdates).onSuccessTask {
                deleteLinkedDocuments(db, uid, allClasses)
            }
        }
        .onSuccessTask {
            db.collection("users").document(uid).delete()
        }
}

private fun deleteLinkedDocuments(
    db: FirebaseFirestore,
    uid: String,
    classes: List<DocumentSnapshot>
): Task<Void> {
    val authoredPosts = db.collectionGroup("posts").whereEqualTo("authorId", uid).get(Source.SERVER)
    val authoredComments = db.collectionGroup("comments").whereEqualTo("authorId", uid).get(Source.SERVER)
    val authoredMaterials = db.collectionGroup("materials").whereEqualTo("authorId", uid).get(Source.SERVER)
    val authoredAssignments = db.collectionGroup("assignments").whereEqualTo("authorId", uid).get(Source.SERVER)
    val authoredChats = db.collectionGroup("chats").whereEqualTo("senderId", uid).get(Source.SERVER)
    val authoredAttendance = db.collectionGroup("attendance").whereEqualTo("teacherId", uid).get(Source.SERVER)
    val attendanceSheets = db.collectionGroup("attendance_sheets").whereEqualTo("userId", uid).get(Source.SERVER)
    val reportedByUser = db.collectionGroup("chatReports").whereEqualTo("reporterId", uid).get(Source.SERVER)
    val reportsAboutUser = db.collectionGroup("chatReports").whereEqualTo("messageSenderId", uid).get(Source.SERVER)
    val recipientNotifications = db.collection("notifications")
        .whereEqualTo("recipientId", uid).get(Source.SERVER)
    val requestNotifications = db.collection("notifications")
        .whereEqualTo("requestId", uid).get(Source.SERVER)
    val directThreads = db.collection("directThreads")
        .whereArrayContains("participants", uid).get(Source.SERVER)
    val queries: List<Task<QuerySnapshot>> = listOf(
        authoredPosts,
        authoredComments,
        authoredMaterials,
        authoredAssignments,
        authoredChats,
        authoredAttendance,
        attendanceSheets,
        reportedByUser,
        reportsAboutUser,
        recipientNotifications,
        requestNotifications,
        directThreads
    )

    return Tasks.whenAll(queries).onSuccessTask {
        val postDocuments = authoredPosts.result.documents
        val commentsOnAuthoredPosts = postDocuments.map {
            it.reference.collection("comments").get(Source.SERVER)
        }
        Tasks.whenAll(commentsOnAuthoredPosts).onSuccessTask {
            val commentReferences = authoredComments.result.documents.map { it.reference } +
                commentsOnAuthoredPosts.flatMap { it.result.documents.map(DocumentSnapshot::getReference) }
            val ordinaryDeletes = listOf(
                deleteDocuments(db, commentReferences.distinct()),
                deleteDocuments(db, postDocuments.map { it.reference }),
                deleteDocuments(db, authoredMaterials.result.documents.map { it.reference }),
                deleteDocuments(db, authoredAssignments.result.documents.map { it.reference }),
                deleteDocuments(db, authoredChats.result.documents.map { it.reference }),
                deleteDocuments(db, authoredAttendance.result.documents.map { it.reference }),
                deleteDocuments(db, attendanceSheets.result.documents.map { it.reference }),
                deleteDocuments(
                    db,
                    (reportedByUser.result.documents + reportsAboutUser.result.documents)
                        .distinctBy { it.reference.path }
                        .map { it.reference }
                ),
                deleteDocuments(
                    db,
                    (recipientNotifications.result.documents + requestNotifications.result.documents)
                        .distinctBy { it.reference.path }
                        .map { it.reference }
                ),
                deleteClassJoinRequests(classes, uid),
                scrubClassAttendance(classes, uid),
                detachDirectThreads(db, directThreads.result.documents, uid)
            )
            Tasks.whenAll(ordinaryDeletes)
        }
    }
}

private fun deleteClassJoinRequests(
    classes: List<DocumentSnapshot>,
    uid: String
): Task<Void> = Tasks.whenAll(classes.map { classDocument ->
    classDocument.reference.collection("joinRequests").document(uid).delete()
})

private fun scrubClassAttendance(classes: List<DocumentSnapshot>, uid: String): Task<Void> {
    val reads = classes.map { it.reference.collection("attendance").get(Source.SERVER) }
    return Tasks.whenAll(reads).onSuccessTask {
        val writes = reads.flatMap { read ->
            read.result.documents.mapNotNull { record ->
                val present = record.get("presentStudents") as? List<*>
                val absent = record.get("absentStudents") as? List<*>
                if (uid in present.orEmpty() || uid in absent.orEmpty()) {
                    record.reference.update(
                        mapOf(
                            "presentStudents" to FieldValue.arrayRemove(uid),
                            "absentStudents" to FieldValue.arrayRemove(uid)
                        )
                    )
                } else {
                    null
                }
            }
        }
        Tasks.whenAll(writes)
    }
}

private fun detachDirectThreads(
    db: FirebaseFirestore,
    threads: List<DocumentSnapshot>,
    uid: String
): Task<Void> = Tasks.whenAll(threads.map { thread ->
    val threadReference = thread.reference
    threadReference.collection("messages")
        .whereEqualTo("senderId", uid)
        .get(Source.SERVER)
        .onSuccessTask { messages ->
            deleteDocuments(db, messages.documents.map { it.reference })
                .onSuccessTask {
                    val changes = mutableMapOf<String, Any>(
                        "participants" to FieldValue.arrayRemove(uid),
                        "hiddenFor" to FieldValue.arrayRemove(uid),
                        "lastReadBy" to FieldValue.arrayRemove(uid)
                    )
                    if (thread.getString("lastSenderId") == uid) {
                        changes["lastMessage"] = ""
                        changes["lastSenderId"] = ""
                        changes["lastTimestamp"] = 0L
                    }
                    threadReference.update(changes)
                }
        }
})

private fun deleteDocuments(
    db: FirebaseFirestore,
    references: List<DocumentReference>
): Task<Void> {
    if (references.isEmpty()) return Tasks.forResult(null)
    val batch = db.batch()
    val page = references.take(400)
    page.forEach(batch::delete)
    return batch.commit().onSuccessTask {
        deleteDocuments(db, references.drop(page.size))
    }
}
