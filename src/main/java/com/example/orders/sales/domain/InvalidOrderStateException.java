package com.example.orders.sales.domain;

import com.example.orders.sharedkernel.DomainException;

/**
 * 状态迁移非法时抛出的异常（映射为 HTTP 409）。
 */
public class InvalidOrderStateException extends DomainException {

	private static final long serialVersionUID = 1L;

	public InvalidOrderStateException(String message) {
		super(message);
	}

}
