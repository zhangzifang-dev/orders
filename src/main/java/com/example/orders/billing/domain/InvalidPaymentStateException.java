package com.example.orders.billing.domain;

import com.example.orders.sharedkernel.DomainException;

/**
 * 支付/发票状态迁移非法时抛出的异常。
 */
public class InvalidPaymentStateException extends DomainException {

	private static final long serialVersionUID = 1L;

	public InvalidPaymentStateException(String message) {
		super(message);
	}

}
