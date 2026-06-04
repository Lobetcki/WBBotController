package com.activetour.wbbotcontroller.model

import kotlinx.serialization.Serializable

@Serializable
data class WBOrder(
    val id: Long?,           // № задания
    val article: String?, // Артикул продавца (Наименование)
    val createdAt: String?,
    val colorCode: String?,      // код цвета
//    val nmId: Long = 0,     // внутренний идентификатор карточки товара в WB.
    val supplyId: String?,  // Поставка
//    val price: Long = 0,
//    val quantity: Int = 1
)