package com.omnira.data

import com.google.firebase.firestore.PropertyName

data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val classId: String = "",
    val className: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    val fileName: String? = null,
    val fileUrl: String? = null,
    
    @get:PropertyName("approved")
    @set:PropertyName("approved")
    var isApproved: Boolean = true
)

data class Comment(
    val id: String = "",
    val authorId: String? = null,
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    val fileUrl: String? = null,
    val fileName: String? = null,

    @get:PropertyName("approved")
    @set:PropertyName("approved")
    var isApproved: Boolean = true
)

data class RoutineData(
    val id: String = "",
    val subject: String = "",
    val time: String = "",
    val endTime: String = "",
    val day: String = "",
    val date: String = "",
    val dayIndex: Int = 0,
    val classCode: String = "",
    val className: String = "",
    val roomNumber: String = "",
    
    @get:PropertyName("isCancelled")
    @set:PropertyName("isCancelled")
    var isCancelled: Boolean = false,

    val rescheduledDate: String = "",
    val rescheduledTime: String = "",
    val rescheduledEndTime: String = ""
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val deletedFor: List<String> = emptyList(),
    val senderProfileImageUrl: String? = null,
    val readBy: List<String> = emptyList(),
    val attachmentUrl: String? = null,
    val attachmentName: String? = null
)

data class DirectMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderProfileImageUrl: String? = null,
    val message: String = "",
    val attachmentUrl: String? = null,
    val attachmentName: String? = null,
    val timestamp: Long = 0,
    val readBy: List<String> = emptyList()
)

data class Notification(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    val authorName: String = "",
    val className: String = "",
    val classId: String = "",
    val type: String = "",
    val recipientId: String = "",
    val requestId: String = "",
    val postId: String = "",
    val commentId: String = "",
    val reportId: String = "",
    val readBy: List<String> = emptyList()
)

data class AttendanceRecord(
    val id: String = "",
    val title: String = "",
    val teacherId: String = "",
    val classId: String = "",
    val timestamp: Long = 0,
    val presentStudents: List<String> = emptyList(),
    val absentStudents: List<String> = emptyList(),
    val pdfUrl: String = ""
)

data class AttendanceUser(
    val id: String = "", 
    val name: String = "", 
    @get:PropertyName("isPresent")
    @set:PropertyName("isPresent")
    var isPresent: Boolean = false
)

data class AttendanceSheetRecord(
    val id: String = "",
    val userId: String = "",
    val classId: String = "",
    val className: String = "",
    val batch: String = "",
    val date: String = "",
    val fileUrl: String = "",
    val timestamp: Long = 0
)
