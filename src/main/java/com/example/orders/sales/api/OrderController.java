package com.example.orders.sales.api;

import java.util.List;
import java.util.UUID;

import com.example.orders.projections.OrderSummary;
import com.example.orders.projections.OrderSummaryQuery;
import com.example.orders.sales.api.dto.AddLineRequest;
import com.example.orders.sales.api.dto.CancelOrderRequest;
import com.example.orders.sales.api.dto.CreateOrderRequest;
import com.example.orders.sales.api.dto.OrderResponse;
import com.example.orders.sales.api.dto.OrderSummaryResponse;
import com.example.orders.sales.application.OrderApplicationService;
import com.example.orders.sales.domain.LineId;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售上下文 REST 入口。控制器只做 ID/DTO 翻译与用例调用，不承载业务规则。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderApplicationService orderApplicationService;

	private final OrderSummaryQuery orderSummaryQuery;

	private final OrderRequestMapper mapper;

	public OrderController(OrderApplicationService orderApplicationService, OrderSummaryQuery orderSummaryQuery,
			OrderRequestMapper mapper) {
		this.orderApplicationService = orderApplicationService;
		this.orderSummaryQuery = orderSummaryQuery;
		this.mapper = mapper;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse create(@RequestBody CreateOrderRequest request) {
		OrderId orderId = orderApplicationService.create(mapper.toCommand(request));
		return mapper.toResponse(orderApplicationService.get(orderId));
	}

	@PostMapping("/{orderId}/lines")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void addLine(@PathVariable UUID orderId, @RequestBody AddLineRequest request) {
		OrderId id = OrderId.of(orderId);
		Currency currency = orderApplicationService.get(id).currency();
		orderApplicationService.addLine(id, mapper.toCommand(request, currency));
	}

	@DeleteMapping("/{orderId}/lines/{lineId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeLine(@PathVariable UUID orderId, @PathVariable UUID lineId) {
		orderApplicationService.removeLine(OrderId.of(orderId), LineId.of(lineId));
	}

	@PostMapping("/{orderId}/place")
	public OrderResponse place(@PathVariable UUID orderId) {
		OrderId id = OrderId.of(orderId);
		orderApplicationService.place(id);
		return mapper.toResponse(orderApplicationService.get(id));
	}

	@PostMapping("/{orderId}/cancel")
	public OrderResponse cancel(@PathVariable UUID orderId, @RequestBody CancelOrderRequest request) {
		OrderId id = OrderId.of(orderId);
		orderApplicationService.cancel(id, request.reason());
		return mapper.toResponse(orderApplicationService.get(id));
	}

	@GetMapping("/{orderId}")
	public OrderResponse get(@PathVariable UUID orderId) {
		return mapper.toResponse(orderApplicationService.get(OrderId.of(orderId)));
	}

	@GetMapping
	public List<OrderSummaryResponse> list(@RequestParam UUID customerId) {
		List<OrderSummary> summaries = orderSummaryQuery.byCustomer(CustomerId.of(customerId));
		return summaries.stream().map(mapper::toResponse).toList();
	}

}
