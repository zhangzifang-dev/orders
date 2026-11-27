package com.example.orders.fulfillment.infrastructure;

import java.util.Optional;

import com.example.orders.fulfillment.domain.FulfillmentProcess;
import com.example.orders.fulfillment.domain.FulfillmentProcessRepository;
import com.example.orders.fulfillment.domain.FulfillmentStatus;
import com.example.orders.fulfillment.domain.ProcessId;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Repository;

/**
 * 基于 Spring Data JDBC 的 {@link FulfillmentProcessRepository} 实现，手写领域对象与行记录之间的映射。
 */
@Repository
public class JdbcFulfillmentProcessRepository implements FulfillmentProcessRepository {

	private final FulfillmentProcessJdbcDao dao;

	public JdbcFulfillmentProcessRepository(FulfillmentProcessJdbcDao dao) {
		this.dao = dao;
	}

	@Override
	public void save(FulfillmentProcess process) {
		Long version = dao.existsById(process.processId().value()) ? Long.valueOf(process.version()) : null;
		FulfillmentProcessRecord saved = dao.save(toRecord(process, version));
		process.assignVersion(saved.version());
	}

	@Override
	public Optional<FulfillmentProcess> findById(ProcessId processId) {
		return dao.findById(processId.value()).map(this::toDomain);
	}

	@Override
	public Optional<FulfillmentProcess> findByOrderId(OrderId orderId) {
		return dao.findByOrderId(orderId.value()).map(this::toDomain);
	}

	private FulfillmentProcessRecord toRecord(FulfillmentProcess process, Long version) {
		return new FulfillmentProcessRecord(process.processId().value(), process.orderId().value(),
				process.status().name(), process.failureReason(), process.updatedAt(), version);
	}

	private FulfillmentProcess toDomain(FulfillmentProcessRecord record) {
		return FulfillmentProcess.rehydrate(ProcessId.of(record.id()), OrderId.of(record.orderId()),
				FulfillmentStatus.valueOf(record.status()), record.failureReason(), record.updatedAt(),
				record.version());
	}

}
