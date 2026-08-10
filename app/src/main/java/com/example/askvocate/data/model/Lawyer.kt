package com.example.askvocate.data.model

data class Lawyer(
    val id: String,
    val name: String,
    val specialty: String,
    val rating: Double,
    val reviewCount: Int,
    val location: String,
    val imageUrl: String? = null,
    val bio: String,
    val yearsExperience: Int,
    val isVerified: Boolean = false,
    val consultationFee: Int = 1500
)

data class Category(
    val id: String,
    val name: String,
    val isSelected: Boolean = false
)

data class Review(
    val id: String,
    val reviewerName: String,
    val rating: Double,
    val date: String,
    val comment: String
)
