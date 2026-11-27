package com.example.orders.fulfillment.domain;

import com.example.orders.sharedkernel.DomainException;

/**
 * 履约流程状态迁移非法时抛出的异常。
 */
public class InvalidFulfillmentStateException extends DomainException {

	private static final long serialVersionUID = 1L;

	public InvalidFulfillmentStateException(String message) {
		super(message);
	}

}
