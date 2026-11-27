package com.example.orders.fulfillment.application;

import com.example.orders.fulfillment.domain.FulfillmentProcess;
import com.example.orders.fulfillment.domain.FulfillmentProcessRepository;
import com.example.orders.sharedkernel.NotFoundException;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 履约查询服务：按订单 ID 返回流程快照。
 */
@Service
public class FulfillmentQueryService {

	private final FulfillmentProcessRepository processRepository;

	public FulfillmentQueryService(FulfillmentProcessRepository processRepository) {
		this.processRepository = processRepository;
	}

	@Transactional(readOnly = true)
	public FulfillmentView get(OrderId orderId) {
		FulfillmentProcess process = processRepository.findByOrderId(orderId)
			.orElseThrow(() -> new NotFoundException("履约流程不存在: " + orderId));
		return FulfillmentView.from(process);
	}

}
