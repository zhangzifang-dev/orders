package com.example.orders.fulfillment.application.port;

import java.util.List;

/**
 * 履约上下文视角的预占成功结果。
 */
public record Reserved(List<ReserveLine> lines) implements ReservationResult {

	public Reserved {
		lines = List.copyOf(lines);
	}

}
