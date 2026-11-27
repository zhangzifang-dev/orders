package com.example.orders.sales.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.example.orders.sales.domain.LineId;
import com.example.orders.sales.domain.Order;
import com.example.orders.sales.domain.OrderCancelled;
import com.example.orders.sales.domain.OrderConfirmed;
import com.example.orders.sales.domain.OrderPlaced;
import com.example.orders.sales.domain.OrderRepository;
import com.example.orders.sales.domain.PricingPolicy;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.DomainEvent;
import com.example.orders.sharedkernel.NotFoundException;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.outbox.IntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderCancelledIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderConfirmedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OrderPlacedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.OutboxAppender;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售上下文的应用服务：编排订单用例，并在写事务内把领域事件写入 outbox。
 *
 * <p>命令中的 ID 由本层生成后显式传给领域对象；时间统一来自注入的 {@link Clock}。
 */
@Service
public class OrderApplicationService {

	private final OrderRepository orderRepository;

	private final PricingPolicy pricingPolicy;

	private final OutboxAppender outboxAppender;

	private final Clock clock;

	public OrderApplicationService(OrderRepository orderRepository, PricingPolicy pricingPolicy,
			OutboxAppender outboxAppender, Clock clock) {
		this.orderRepository = orderRepository;
		this.pricingPolicy = pricingPolicy;
		this.outboxAppender = outboxAppender;
		this.clock = clock;
	}

	@Transactional
	public OrderId create(CreateOrderCommand command) {
		OrderId orderId = OrderId.of(UUID.randomUUID());
		Order order = Order.draft(orderId, command.customerId(), command.currency());
		for (CreateOrderLineCommand line : command.lines()) {
			order.addLine(LineId.of(UUID.randomUUID()), line.sku(), line.productName(), line.unitPrice(),
					line.quantity(), clock.instant());
		}
		orderRepository.save(order);
		return orderId;
	}

	@Transactional
	public void addLine(OrderId orderId, AddOrderLineCommand command) {
		Order order = load(orderId);
		order.addLine(LineId.of(UUID.randomUUID()), command.sku(), command.productName(), command.unitPrice(),
				command.quantity(), clock.instant());
		orderRepository.save(order);
	}

	@Transactional
	public void removeLine(OrderId orderId, LineId lineId) {
		Order order = load(orderId);
		order.removeLine(lineId, clock.instant());
		orderRepository.save(order);
	}

	@Transactional
	public void place(OrderId orderId) {
		Order order = load(orderId);
		order.place(pricingPolicy, clock.instant());
		orderRepository.save(order);
		appendIntegrationEvents(order);
	}

	@Transactional
	public void cancel(OrderId orderId, String reason) {
		Order order = load(orderId);
		order.cancel(reason, clock.instant());
		orderRepository.save(order);
		appendIntegrationEvents(order);
	}

	/**
	 * 确认订单，由履约上下文调用。
	 * @param orderId 订单 ID
	 */
	@Transactional
	public void confirmOrder(OrderId orderId) {
		Order order = load(orderId);
		order.confirm(clock.instant());
		orderRepository.save(order);
		appendIntegrationEvents(order);
	}

	@Transactional(readOnly = true)
	public OrderView get(OrderId orderId) {
		return toView(load(orderId));
	}

	@Transactional(readOnly = true)
	public List<OrderView> listAll() {
		return orderRepository.findAll().stream().map(this::toView).toList();
	}

	/**
	 * 按客户查询订单视图。Task 19 会把 REST 列表端点切换到 CQRS 读模型。
	 * @param customerId 客户 ID
	 * @return 该客户的订单视图
	 */
	@Transactional(readOnly = true)
	public List<OrderView> listByCustomer(CustomerId customerId) {
		return orderRepository.findByCustomerId(customerId).stream().map(this::toView).toList();
	}

	private Order load(OrderId orderId) {
		return orderRepository.findById(orderId)
			.orElseThrow(() -> new NotFoundException("订单不存在: " + orderId));
	}

	private void appendIntegrationEvents(Order order) {
		for (DomainEvent event : order.pullDomainEvents()) {
			outboxAppender.append(toIntegrationEvent(event));
		}
	}

	private IntegrationEvent toIntegrationEvent(DomainEvent event) {
		return switch (event) {
			case OrderPlaced placed -> new OrderPlacedIntegrationEvent(placed.eventId(), placed.occurredAt(),
					placed.orderId(), placed.customerId(), placed.currency(), placed.total(), placed.discount(),
					placed.payable(), placed.lines()
						.stream()
						.map(line -> new OrderPlacedIntegrationEvent.OrderLinePayload(line.sku(), line.productName(),
								line.unitPrice(), line.quantity().value()))
						.toList());
			case OrderConfirmed confirmed -> new OrderConfirmedIntegrationEvent(confirmed.eventId(),
					confirmed.occurredAt(), confirmed.orderId());
			case OrderCancelled cancelled -> new OrderCancelledIntegrationEvent(cancelled.eventId(),
					cancelled.occurredAt(), cancelled.orderId(), cancelled.reason());
			default -> throw new IllegalStateException("不支持的领域事件: " + event.eventType());
		};
	}

	private OrderView toView(Order order) {
		List<OrderView.OrderLineView> lines = order.lines()
			.stream()
			.map(line -> new OrderView.OrderLineView(line.lineId(), line.sku(), line.productName(), line.unitPrice(),
					line.quantity(), line.subtotal()))
			.toList();
		return new OrderView(order.orderId(), order.customerId(), order.status(), order.currency(), order.total(),
				order.discount(), order.payable(), lines, order.placedAt(), order.cancelReason());
	}

}
