package com.example.orders.sales.domain;

import java.util.List;
import java.util.Optional;

import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.OrderId;

/**
 * 订单聚合的持久化端口。
 */
public interface OrderRepository {

	/**
	 * 保存订单聚合（含订单行），并同步乐观锁版本。
	 * @param order 待保存的订单
	 */
	void save(Order order);

	Optional<Order> findById(OrderId orderId);

	List<Order> findByCustomerId(CustomerId customerId);

	/**
	 * 返回全部订单，供读模型重建等只读场景使用。
	 * @return 全部订单
	 */
	List<Order> findAll();

}
