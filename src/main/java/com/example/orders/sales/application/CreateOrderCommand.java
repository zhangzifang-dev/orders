package com.example.orders.sales.application;

import java.util.List;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;

/**
 * 创建草稿订单的命令，可一次携带若干订单行。
 */
public record CreateOrderCommand(CustomerId customerId, Currency currency, List<CreateOrderLineCommand> lines) {

	public CreateOrderCommand {
		lines = lines == null ? List.of() : List.copyOf(lines);
	}

}
