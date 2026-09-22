package com.example.askvocate.data.model

data class KnowledgeItem(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    val category: String
)
