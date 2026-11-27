package com.example.orders.inventory.application;

import java.util.List;

import com.example.orders.sharedkernel.OrderId;

/**
 * 预占成功，携带被满足的全部行。
 */
public record Reserved(OrderId orderId, List<ReserveLine> lines) implements ReservationResult {

	public Reserved {
		lines = List.copyOf(lines);
	}

}
