package com.example.orders.inventory.application;

/**
 * 库存预占结果：要么整单成功，要么因库存不足整单失败。
 */
public sealed interface ReservationResult permits Reserved, StockShortage {
}
