package com.example.orders.billing.application;

import java.util.Optional;

import com.example.orders.billing.domain.Invoice;
import com.example.orders.billing.domain.InvoiceRepository;
import com.example.orders.billing.domain.Payment;
import com.example.orders.billing.domain.PaymentRepository;
import com.example.orders.sharedkernel.DomainException;
import com.example.orders.sharedkernel.OrderId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计费查询服务：把发票与支付聚合投影为只读快照。
 */
@Service
public class BillingQueryService {

	private final InvoiceRepository invoiceRepository;

	private final PaymentRepository paymentRepository;

	public BillingQueryService(InvoiceRepository invoiceRepository, PaymentRepository paymentRepository) {
		this.invoiceRepository = invoiceRepository;
		this.paymentRepository = paymentRepository;
	}

	/**
	 * 按订单查询发票视图。
	 * @param orderId 订单 ID
	 * @return 发票视图，若该订单尚未开票则为空
	 */
	@Transactional(readOnly = true)
	public Optional<InvoiceView> getByOrder(OrderId orderId) {
		if (orderId == null) {
			throw new DomainException("订单 ID 不能为空");
		}
		return invoiceRepository.findByOrderId(orderId).map(this::toView);
	}

	private InvoiceView toView(Invoice invoice) {
		Optional<Payment> payment = paymentRepository.findByInvoiceId(invoice.invoiceId());
		return new InvoiceView(invoice.invoiceId(), invoice.orderId(), invoice.amount(), invoice.status(),
				payment.map(Payment::status).orElse(null), payment.map(Payment::gatewayRef).orElse(null),
				payment.map(Payment::failureReason).orElse(null));
	}

}
