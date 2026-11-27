package com.example.orders.sales.application;

import java.util.List;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainException;

/**
 * 创建草稿订单的命令，可一次携带若干订单行。
 */
public record CreateOrderCommand(CustomerId customerId, Currency currency, List<CreateOrderLineCommand> lines) {

	public CreateOrderCommand {
		if (lines == null) {
			lines = List.of();
		}
		else {
			for (CreateOrderLineCommand line : lines) {
				if (line == null) {
					throw new DomainException("订单行不能为空");
				}
			}
			lines = List.copyOf(lines);
		}
	}

}
