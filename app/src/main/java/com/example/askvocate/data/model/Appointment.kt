package com.example.askvocate.data.model

data class Appointment(
    val id: String,
    val lawyerName: String,
    val date: String,
    val time: String,
    val type: String, // Online, In-person
    val status: String,
    val lawyerImageUrl: String? = null,
    val lawyerImageResId: Int? = null
)
