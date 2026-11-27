package com.example.orders.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.NotFoundException;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;
import com.example.orders.sharedkernel.outbox.OutboxAppender;
import com.example.orders.sharedkernel.outbox.StockReleasedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.StockReservationFailedIntegrationEvent;
import com.example.orders.sharedkernel.outbox.StockReservedIntegrationEvent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存上下文的应用服务：编排跨多个 SKU 的预占、释放与补货，并在写事务内追加 outbox 事件。
 *
 * <p>预占是全有或全无：先加载并校验全部 SKU，任一缺失或不足时不修改任何聚合，只追加一条失败事件。
 */
@Service
public class InventoryApplicationService {

	private final StockItemRepository stockItemRepository;

	private final OutboxAppender outboxAppender;

	private final Clock clock;

	public InventoryApplicationService(StockItemRepository stockItemRepository, OutboxAppender outboxAppender,
			Clock clock) {
		this.stockItemRepository = stockItemRepository;
		this.outboxAppender = outboxAppender;
		this.clock = clock;
	}

	@Transactional
	public ReservationResult reserve(OrderId orderId, List<ReserveLine> lines) {
		requireOrderId(orderId);
		Instant occurredAt = clock.instant();
		Map<Sku, Integer> requested = mergeLines(lines);

		Map<Sku, StockItem> items = new LinkedHashMap<>();
		for (Map.Entry<Sku, Integer> entry : requested.entrySet()) {
			Sku sku = entry.getKey();
			int quantity = entry.getValue();
			Optional<StockItem> found = stockItemRepository.findById(sku);
			if (found.isEmpty()) {
				return fail(orderId, occurredAt, "库存不存在: " + sku.value());
			}
			StockItem item = found.get();
			int available = item.available();
			if (available < quantity) {
				return fail(orderId, occurredAt,
						"库存不足: SKU " + sku.value() + " 需要 " + quantity + "，可用 " + available);
			}
			items.put(sku, item);
		}

		items.forEach((sku, item) -> {
			item.reserve(orderId, requested.get(sku), occurredAt);
			stockItemRepository.save(item);
		});
		outboxAppender.append(reservedEvent(orderId, occurredAt, requested));
		return new Reserved(orderId, lines);
	}

	@Transactional
	public void release(OrderId orderId) {
		requireOrderId(orderId);
		Instant occurredAt = clock.instant();
		List<StockItem> items = stockItemRepository.findByReservationOrderId(orderId);
		for (StockItem item : items) {
			item.release(orderId, occurredAt);
			stockItemRepository.save(item);
		}
		outboxAppender.append(new StockReleasedIntegrationEvent(UUID.randomUUID(), occurredAt, orderId));
	}

	@Transactional
	public void restock(Sku sku, int quantity) {
		StockItem item = stockItemRepository.findById(sku)
			.orElseThrow(() -> new NotFoundException("库存不存在: " + sku.value()));
		item.restock(quantity);
		stockItemRepository.save(item);
	}

	private StockReservationFailedIntegrationEvent failureEvent(OrderId orderId, Instant occurredAt, String reason) {
		return new StockReservationFailedIntegrationEvent(UUID.randomUUID(), occurredAt, orderId, reason);
	}

	private ReservationResult fail(OrderId orderId, Instant occurredAt, String reason) {
		outboxAppender.append(failureEvent(orderId, occurredAt, reason));
		return new StockShortage(reason);
	}

	private StockReservedIntegrationEvent reservedEvent(OrderId orderId, Instant occurredAt,
			Map<Sku, Integer> requested) {
		List<StockReservedIntegrationEvent.StockLinePayload> payloads = requested.entrySet()
			.stream()
			.map(entry -> new StockReservedIntegrationEvent.StockLinePayload(entry.getKey(), entry.getValue()))
			.toList();
		return new StockReservedIntegrationEvent(UUID.randomUUID(), occurredAt, orderId, payloads);
	}

	private Map<Sku, Integer> mergeLines(List<ReserveLine> lines) {
		if (lines == null) {
			throw new DomainException("预占行不能为空");
		}
		Map<Sku, Integer> requested = new LinkedHashMap<>();
		for (ReserveLine line : lines) {
			if (line == null) {
				throw new DomainException("预占行不能为空");
			}
			requested.merge(line.sku(), line.quantity(), Integer::sum);
		}
		return requested;
	}

	private void requireOrderId(OrderId orderId) {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
	}

}
