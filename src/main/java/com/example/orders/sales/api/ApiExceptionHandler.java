package com.example.orders.sales.api;

import com.example.orders.sales.domain.InvalidOrderStateException;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.NotFoundException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把领域与应用层异常映射为 RFC 7807 {@code application/problem+json} 响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "资源不存在", ex.getMessage());
	}

	@ExceptionHandler(InvalidOrderStateException.class)
	public ResponseEntity<ProblemDetail> handleInvalidOrderState(InvalidOrderStateException ex) {
		return problem(HttpStatus.CONFLICT, "订单状态冲突", ex.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "并发冲突", "订单已被其他请求修改，请重试");
	}

	@ExceptionHandler({ DomainException.class, IllegalArgumentException.class })
	public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException ex) {
		return problem(HttpStatus.BAD_REQUEST, "请求参数非法", ex.getMessage());
	}

	private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
		problem.setTitle(title);
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

}
