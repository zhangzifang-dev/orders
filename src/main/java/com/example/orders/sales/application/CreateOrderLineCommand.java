package com.example.orders.sales.application;

import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

/**
 * 创建订单时的单个订单行命令。
 */
public record CreateOrderLineCommand(Sku sku, String productName, Money unitPrice, Quantity quantity) {
}
