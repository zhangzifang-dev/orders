package com.example.orders.fulfillment.application;

import java.time.Instant;

import com.example.orders.fulfillment.domain.FulfillmentProcess;
import com.example.orders.fulfillment.domain.ProcessId;
import com.example.orders.sharedkernel.OrderId;

/**
 * 履约流程的只读视图。
 */
public record FulfillmentView(ProcessId processId, OrderId orderId, String status, String failureReason,
		Instant updatedAt) {

	/**
	 * 从聚合投影视图。
	 * @param process 履约流程聚合
	 * @return 只读快照
	 */
	public static FulfillmentView from(FulfillmentProcess process) {
		return new FulfillmentView(process.processId(), process.orderId(), process.status().name(),
				process.failureReason(), process.updatedAt());
	}

}
