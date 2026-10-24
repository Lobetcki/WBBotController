package com.activetour.wbbotcontroller.model

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class WBOrder(
    val id: Long,
    val article: String,
    val createdAt: String,
    val nmId: Long = 0,
    val price: Long = 0,
    val quantity: Int = 1,
    val colorCode: String? = null,
    val supplyId: String? = null
)

@Serializable
data class WBOrderResponse(
    val orders: List<WBOrder>
)

data class StickerForOrder(
    val orderId: Long,
    val partA: String,
    val partB: String,
    val barcode: String,
    val file: File
)