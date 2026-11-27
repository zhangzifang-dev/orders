package com.example.orders.inventory.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.orders.inventory.domain.Reservation;
import com.example.orders.inventory.domain.StockItem;
import com.example.orders.inventory.domain.StockItemRepository;
import com.example.orders.sharedkernel.OrderId;
import com.example.orders.sharedkernel.Sku;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 基于 Spring Data JDBC 的 {@link StockItemRepository} 实现，手写领域对象与行记录之间的映射。
 */
@Repository
public class JdbcStockItemRepository implements StockItemRepository {

	private static final String FIND_BY_RESERVATION_ORDER_SQL = """
			SELECT sku FROM inventory_reservation WHERE order_id = :orderId
			""";

	private final StockItemJdbcDao dao;

	private final JdbcClient jdbcClient;

	public JdbcStockItemRepository(StockItemJdbcDao dao, JdbcClient jdbcClient) {
		this.dao = dao;
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void save(StockItem stockItem) {
		String sku = stockItem.sku().value();
		Long version = dao.existsById(sku) ? Long.valueOf(stockItem.version()) : null;
		StockItemRecord saved = dao.save(toRecord(stockItem, version));
		stockItem.assignVersion(saved.version());
	}

	@Override
	public Optional<StockItem> findById(Sku sku) {
		return dao.findById(sku.value()).map(this::toDomain);
	}

	@Override
	public List<StockItem> findAll() {
		List<StockItem> items = new ArrayList<>();
		dao.findAll().forEach(record -> items.add(toDomain(record)));
		return List.copyOf(items);
	}

	@Override
	public List<StockItem> findByReservationOrderId(OrderId orderId) {
		return jdbcClient.sql(FIND_BY_RESERVATION_ORDER_SQL)
			.param("orderId", orderId.value())
			.query(String.class)
			.list()
			.stream()
			.map(sku -> dao.findById(sku))
			.flatMap(Optional::stream)
			.map(this::toDomain)
			.toList();
	}

	private StockItemRecord toRecord(StockItem stockItem, Long version) {
		Set<ReservationRecord> reservations = stockItem.reservations()
			.stream()
			.map(this::toReservationRecord)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return new StockItemRecord(stockItem.sku().value(), stockItem.productName(), stockItem.onHand(), version,
				reservations);
	}

	private ReservationRecord toReservationRecord(Reservation reservation) {
		return new ReservationRecord(reservation.orderId().value(), reservation.quantity());
	}

	private StockItem toDomain(StockItemRecord record) {
		List<Reservation> reservations = record.reservations()
			.stream()
			.map(reservation -> new Reservation(OrderId.of(reservation.orderId()), reservation.quantity()))
			.toList();
		return StockItem.rehydrate(Sku.of(record.sku()), record.productName(), record.onHand(), reservations,
				record.version());
	}

}
