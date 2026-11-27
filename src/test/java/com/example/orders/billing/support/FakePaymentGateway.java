package com.example.orders.billing.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.example.orders.billing.domain.CaptureRequest;
import com.example.orders.billing.domain.CaptureResult;
import com.example.orders.billing.domain.PaymentGateway;

/**
 * 测试用 {@link PaymentGateway} 实现：可配置结果队列（或单一固定结果），并记录收到的捕获请求。
 *
 * <p>队列耗尽后再次调用会抛出 {@link IllegalStateException}，以避免测试意外复用结果。
 * 需要每次返回同一结果时使用 {@link #alwaysReturning(CaptureResult)}。
 */
public class FakePaymentGateway implements PaymentGateway {

	private final Deque<CaptureResult> results = new ArrayDeque<>();

	private final List<CaptureRequest> requests = new ArrayList<>();

	private final CaptureResult repeatedResult;

	public FakePaymentGateway(CaptureResult... results) {
		for (CaptureResult result : results) {
			this.results.add(result);
		}
		this.repeatedResult = null;
	}

	private FakePaymentGateway(CaptureResult repeatedResult, boolean repeat) {
		this.repeatedResult = repeatedResult;
	}

	/**
	 * 每次调用都返回同一个结果。
	 * @param result 要返回的捕获结果
	 * @return 固定结果的测试网关
	 */
	public static FakePaymentGateway alwaysReturning(CaptureResult result) {
		return new FakePaymentGateway(result, true);
	}

	/**
	 * 追加一个待返回结果。
	 * @param result 捕获结果
	 */
	public void enqueue(CaptureResult result) {
		this.results.add(result);
	}

	@Override
	public CaptureResult capture(CaptureRequest request) {
		requests.add(request);
		if (repeatedResult != null) {
			return repeatedResult;
		}
		CaptureResult next = results.poll();
		if (next == null) {
			throw new IllegalStateException("FakePaymentGateway 没有更多预设结果");
		}
		return next;
	}

	/**
	 * 按调用顺序返回所有收到的捕获请求（只读副本）。
	 * @return 捕获请求列表
	 */
	public List<CaptureRequest> capturedRequests() {
		return List.copyOf(requests);
	}

	/**
	 * 返回最近一次捕获请求。
	 * @return 最近一次请求，若尚未调用则为 {@code null}
	 */
	public CaptureRequest lastRequest() {
		return requests.isEmpty() ? null : requests.get(requests.size() - 1);
	}

}
