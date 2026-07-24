package com.activetour.wbbotcontroller.model

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class WBOrder(
    val id: Long,           // № задания
    val article: String, // Артикул продавца (Наименование)
    val createdAt: String,
    val colorCode: String?,      // код цвета
//    val nmId: Long = 0,     // внутренний идентификатор карточки товара в WB.
    val supplyId: String?,  // Поставка
//    val price: Long = 0,
//    val quantity: Int = 1
)

@Serializable
data class StickerForOrder(
    val orderId: Long,
    val partA: String,
    val partB: String,
    val barcode: String,
    val file: File
)