package com.activetour.wbbotcontroller.model

import kotlinx.serialization.Serializable

@Serializable
data class WBOrder(
    val id: Long = 0,
    val article: String? = null,
    val createdAt: String? = null,
    val nmId: Long = 0,
    val price: Long = 0,
    val quantity: Int = 1
)

@Serializable
data class WBOrderResponse(
    val orders: List<WBOrder>
)