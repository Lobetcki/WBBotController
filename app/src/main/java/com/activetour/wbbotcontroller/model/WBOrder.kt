package com.activetour.wbbotcontroller.model

import kotlinx.serialization.Serializable

@Serializable
data class WBOrder(
    val id: Long,
    val article: String,
    val createdAt: String,
    val nmId: Long = 0,
    val price: Long = 0,
    val quantity: Int = 1
)