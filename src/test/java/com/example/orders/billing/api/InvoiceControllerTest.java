package com.example.orders.billing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.example.orders.billing.domain.Invoice;
import com.example.orders.billing.domain.InvoiceId;
import com.example.orders.billing.domain.InvoiceRepository;
import com.example.orders.billing.domain.Payment;
import com.example.orders.billing.domain.PaymentId;
import com.example.orders.billing.domain.PaymentRepository;
import com.example.orders.sharedkernel.Currency;
import com.example.orders.sharedkernel.CustomerId;
import com.example.orders.sharedkernel.Money;
import com.example.orders.sharedkernel.OrderId;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 发票查询 REST API 的端到端测试：真实 Spring 上下文与 H2，不使用 mock。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceControllerTest {

	private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Test
	void returnsInvoiceAndPaymentStatus() throws Exception {
		OrderId orderId = OrderId.newId();
		Invoice invoice = paidInvoice(orderId);
		Payment payment = capturedPayment(invoice);
		invoiceRepository.save(invoice);
		paymentRepository.save(payment);

		mockMvc.perform(get("/api/invoices/{orderId}", orderId.value()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.invoiceId").value(invoice.invoiceId().toString()))
			.andExpect(jsonPath("$.orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.amount").value(199.00))
			.andExpect(jsonPath("$.currency").value("CNY"))
			.andExpect(jsonPath("$.invoiceStatus").value("PAID"))
			.andExpect(jsonPath("$.paymentStatus").value("CAPTURED"))
			.andExpect(jsonPath("$.gatewayRef").value("GW-REF-1"));
	}

	@Test
	void unknownOrderReturns404() throws Exception {
		mockMvc.perform(get("/api/invoices/{orderId}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

	private Invoice paidInvoice(OrderId orderId) {
		Invoice invoice = Invoice.issue(InvoiceId.newId(), orderId, CustomerId.of(UUID.randomUUID()),
				Money.of("199.00", Currency.CNY), NOW);
		invoice.markPaid(NOW);
		return invoice;
	}

	private Payment capturedPayment(Invoice invoice) {
		Payment payment = Payment.request(PaymentId.newId(), invoice.invoiceId(), invoice.amount(), NOW);
		payment.capture("GW-REF-1", NOW);
		return payment;
	}

}
