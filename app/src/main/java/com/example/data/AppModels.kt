package com.example.data

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val role: String = "user"
)

data class Product(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val categoryId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Post(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isPublished: Boolean = true
)
