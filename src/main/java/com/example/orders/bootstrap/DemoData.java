package com.example.orders.bootstrap;

import java.util.UUID;

import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.Sku;

/**
 * 演示场景使用的确定性数据：固定客户、两个 SKU、价格与数量。
 *
 * <p>每次演示都基于同一组业务输入，时间线只有 ID 与时间戳不同，便于观察成功路径与补偿路径。
 */
public final class DemoData {

	public static final CustomerId CUSTOMER_ID = CustomerId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

	public static final Currency CURRENCY = Currency.CNY;

	public static final Sku SUCCESS_SKU = Sku.of("DEMO-KEYBOARD");

	public static final String SUCCESS_PRODUCT = "演示机械键盘";

	public static final Money SUCCESS_PRICE = Money.of("199.00", CURRENCY);

	public static final int SUCCESS_QUANTITY = 2;

	public static final Sku FAILURE_SKU = Sku.of("DEMO-MOUSE");

	public static final String FAILURE_PRODUCT = "演示无线鼠标";

	public static final Money FAILURE_PRICE = Money.of("89.00", CURRENCY);

	public static final int FAILURE_QUANTITY = 3;

	/**
	 * 每个演示场景的补货数量，足以覆盖多次连续演示。
	 */
	public static final int RESTOCK_QUANTITY = 20;

	private DemoData() {
	}

}
