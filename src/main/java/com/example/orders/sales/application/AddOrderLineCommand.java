package com.example.orders.sales.application;

import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

/**
 * 向草稿订单追加订单行的命令。
 */
public record AddOrderLineCommand(Sku sku, String productName, Money unitPrice, Quantity quantity) {
}
