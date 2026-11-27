package com.example.orders.sales.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.orders.sales.domain.LineId;
import com.example.orders.sales.domain.Order;
import com.example.orders.sales.domain.OrderLine;
import com.example.orders.sales.domain.OrderRepository;
import com.example.orders.sales.domain.OrderStatus;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Quantity;
import com.example.orders.sharedkernel.Sku;

import org.springframework.stereotype.Repository;

/**
 * 基于 Spring Data JDBC 的 {@link OrderRepository} 实现，手写领域对象与行记录之间的映射。
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

	private final OrderJdbcDao dao;

	public JdbcOrderRepository(OrderJdbcDao dao) {
		this.dao = dao;
	}

	@Override
	public void save(Order order) {
		UUID id = order.orderId().value();
		Long version = dao.existsById(id) ? Long.valueOf(order.version()) : null;
		OrderRecord saved = dao.save(toRecord(order, version));
		order.assignVersion(saved.version());
	}

	@Override
	public Optional<Order> findById(OrderId orderId) {
		return dao.findById(orderId.value()).map(this::toDomain);
	}

	@Override
	public List<Order> findByCustomerId(CustomerId customerId) {
		return dao.findByCustomerId(customerId.value()).stream().map(this::toDomain).toList();
	}

	@Override
	public List<Order> findAll() {
		List<Order> orders = new ArrayList<>();
		dao.findAll().forEach(record -> orders.add(toDomain(record)));
		return List.copyOf(orders);
	}

	private OrderRecord toRecord(Order order, Long version) {
		Set<OrderLineRecord> lines = order.lines()
			.stream()
			.map(this::toLineRecord)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return new OrderRecord(order.orderId().value(), order.customerId().value(), order.status().name(),
				order.currency().code(), order.discount().amount(), order.placedAt(), order.cancelReason(), version,
				lines);
	}

	private OrderLineRecord toLineRecord(OrderLine line) {
		return new OrderLineRecord(line.lineId().value(), line.sku().value(), line.productName(),
				line.unitPrice().amount(), line.quantity().value());
	}

	private Order toDomain(OrderRecord record) {
		Currency currency = Currency.of(record.currency());
		Money discount = Money.of(record.discountAmount(), currency);
		List<OrderLine> lines = record.lines().stream().map(line -> toLine(line, currency)).toList();
		Order order = Order.rehydrate(OrderId.of(record.id()), CustomerId.of(record.customerId()), currency, lines,
				OrderStatus.valueOf(record.status()), discount, record.placedAt(), record.cancelReason());
		order.assignVersion(record.version());
		return order;
	}

	private OrderLine toLine(OrderLineRecord record, Currency currency) {
		return new OrderLine(LineId.of(record.id()), Sku.of(record.sku()), record.productName(),
				Money.of(record.unitPriceAmount(), currency), Quantity.of(record.quantity()));
	}

}
