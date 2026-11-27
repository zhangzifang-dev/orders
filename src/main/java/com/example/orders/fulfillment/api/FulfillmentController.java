package com.example.orders.fulfillment.api;

import java.util.UUID;

import com.example.orders.fulfillment.application.FulfillmentQueryService;
import com.example.orders.fulfillment.application.FulfillmentView;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 履约上下文 REST 入口：查询订单对应的履约流程快照。
 */
@RestController
@RequestMapping("/api/fulfillment")
public class FulfillmentController {

	private final FulfillmentQueryService fulfillmentQueryService;

	public FulfillmentController(FulfillmentQueryService fulfillmentQueryService) {
		this.fulfillmentQueryService = fulfillmentQueryService;
	}

	@GetMapping("/{orderId}")
	public FulfillmentView get(@PathVariable UUID orderId) {
		return fulfillmentQueryService.get(OrderId.of(orderId));
	}

}
