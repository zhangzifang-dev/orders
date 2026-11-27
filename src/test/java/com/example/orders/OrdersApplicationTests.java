package com.example.orders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrdersApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void mockitoIsNotOnClasspath() {
		assertThatThrownBy(() -> Class.forName("org.mockito.Mockito"))
			.isInstanceOf(ClassNotFoundException.class);
	}

}
