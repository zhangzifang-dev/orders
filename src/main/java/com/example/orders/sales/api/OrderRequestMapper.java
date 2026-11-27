package com.example.orders.sales.api;

import java.math.BigDecimal;
import java.util.List;

import com.example.orders.projections.OrderSummary;
import com.example.orders.sales.api.dto.AddLineRequest;
import com.example.orders.sales.api.dto.CreateOrderLineRequest;
import com.example.orders.sales.api.dto.CreateOrderRequest;
import com.example.orders.sales.api.dto.OrderLineResponse;
import com.example.orders.sales.api.dto.OrderResponse;
import com.example.orders.sales.api.dto.OrderSummaryResponse;
import com.example.orders.sales.application.AddOrderLineCommand;
import com.example.orders.sales.application.CreateOrderCommand;
import com.example.orders.sales.application.CreateOrderLineCommand;
import com.example.orders.sales.application.OrderView;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

import org.springframework.stereotype.Component;

/**
 * REST DTO 与共享内核值对象之间的翻译层。控制器只负责编排，不在此处之外做业务换算。
 */
@Component
public class OrderRequestMapper {

	/**
	 * 把创建订单请求翻译为应用层命令。
	 * @param request 请求体
	 * @return 创建订单命令
	 */
	public CreateOrderCommand toCommand(CreateOrderRequest request) {
		if (request.lines() == null || request.lines().stream().anyMatch(line -> line == null)) {
			throw new IllegalArgumentException("订单行不能为空");
		}
		Currency currency = Currency.of(request.currency());
		List<CreateOrderLineCommand> lines = request.lines()
			.stream()
			.map(line -> toCommand(line, currency))
			.toList();
		return new CreateOrderCommand(CustomerId.of(request.customerId()), currency, lines);
	}

	/**
	 * 把追加订单行请求翻译为应用层命令，币种沿用订单币种。
	 * @param request 请求体
	 * @param currency 订单币种
	 * @return 追加订单行命令
	 */
	public AddOrderLineCommand toCommand(AddLineRequest request, Currency currency) {
		return new AddOrderLineCommand(Sku.of(request.sku()), request.productName(),
				Money.of(request.unitPrice(), currency), Quantity.of(request.quantity()));
	}

	/**
	 * 把订单视图翻译为响应体。
	 * @param view 订单视图
	 * @return 订单响应体
	 */
	public OrderResponse toResponse(OrderView view) {
		List<OrderLineResponse> lines = view.lines().stream().map(this::toResponse).toList();
		return new OrderResponse(view.orderId().value(), view.customerId().value(), view.status().name(),
				view.currency().code(), view.total().amount(), view.discount().amount(), view.payable().amount(),
				lines, view.placedAt(), view.cancelReason());
	}

	/**
	 * 把读模型摘要翻译为列表响应体。
	 * @param summary 订单摘要
	 * @return 订单摘要响应体
	 */
	public OrderSummaryResponse toResponse(OrderSummary summary) {
		return new OrderSummaryResponse(summary.orderId().value(), summary.customerId().value(),
				summary.status().name(), summary.total().amount(), summary.total().currency().code(),
				summary.lineCount(), summary.updatedAt());
	}

	private CreateOrderLineCommand toCommand(CreateOrderLineRequest request, Currency currency) {
		return new CreateOrderLineCommand(Sku.of(request.sku()), request.productName(),
				Money.of(request.unitPrice(), currency), Quantity.of(request.quantity()));
	}

	private OrderLineResponse toResponse(OrderView.OrderLineView line) {
		BigDecimal unitPrice = line.unitPrice().amount();
		return new OrderLineResponse(line.lineId().value(), line.sku().value(), line.productName(), unitPrice,
				line.quantity().value(), line.subtotal().amount());
	}

}
