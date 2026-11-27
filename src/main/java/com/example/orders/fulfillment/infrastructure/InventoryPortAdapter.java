package com.example.orders.fulfillment.infrastructure;

import java.util.List;

import com.example.orders.fulfillment.application.port.InventoryPort;
import com.example.orders.fulfillment.application.port.ReservationResult;
import com.example.orders.fulfillment.application.port.Reserved;
import com.example.orders.fulfillment.application.port.StockShortage;
import com.example.orders.inventory.application.InventoryApplicationService;
import com.example.orders.inventory.application.InventoryReservationFacade;
import com.example.orders.inventory.application.ReserveLine;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Service;

/**
 * 库存端口适配器：预占走带重试边界的 {@link InventoryReservationFacade}，释放直接调用库存应用服务。
 *
 * <p>负责把库存上下文的结果类型翻译成履约上下文自己的类型。
 */
@Service
public class InventoryPortAdapter implements InventoryPort {

	private final InventoryReservationFacade reservationFacade;

	private final InventoryApplicationService inventoryApplicationService;

	public InventoryPortAdapter(InventoryReservationFacade reservationFacade,
			InventoryApplicationService inventoryApplicationService) {
		this.reservationFacade = reservationFacade;
		this.inventoryApplicationService = inventoryApplicationService;
	}

	@Override
	public ReservationResult reserve(OrderId orderId, List<ReserveLine> lines) {
		com.example.orders.inventory.application.ReservationResult result = reservationFacade.reserve(orderId, lines);
		return switch (result) {
			case com.example.orders.inventory.application.Reserved reserved -> new Reserved(reserved.lines());
			case com.example.orders.inventory.application.StockShortage shortage -> new StockShortage(shortage.reason());
		};
	}

	@Override
	public void release(OrderId orderId) {
		inventoryApplicationService.release(orderId);
	}

}
