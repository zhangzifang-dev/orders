package com.example.orders.sharedkernel;

/**
 * 请求的领域对象不存在。
 */
public class NotFoundException extends DomainException {

	private static final long serialVersionUID = 1L;

	public NotFoundException(String message) {
		super(message);
	}

	public NotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}
