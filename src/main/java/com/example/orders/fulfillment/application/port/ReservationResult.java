package com.example.orders.fulfillment.application.port;

/**
 * 履约上下文自己的库存预占结果：要么整单成功，要么因库存不足整单失败。
 *
 * <p>刻意不复用库存上下文的结果类型，适配器负责翻译，以保持上下文边界。
 */
public sealed interface ReservationResult permits Reserved, StockShortage {
}
