package com.example.orders.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.NamingStrategy;

/**
 * 让 Spring Data JDBC 使用未加引号的标识符，以匹配 {@code schema.sql} 中未加引号的表/列名。
 *
 * <p>Spring Data JDBC 4 默认强制引用标识符，而 H2 会把未加引号的 DDL 标识符折叠为大写，
 * 两者混用会导致表/列找不到。此配置属于整个持久化层，而不是某个限界上下文。
 */
@Configuration(proxyBeanMethods = false)
public class JdbcMappingConfiguration {

	@Bean
	JdbcMappingContext jdbcMappingContext(Optional<NamingStrategy> namingStrategy,
			JdbcCustomConversions customConversions, RelationalManagedTypes managedTypes) {
		JdbcMappingContext context = JdbcMappingContext
			.forPlainIdentifiers(namingStrategy.orElse(DefaultNamingStrategy.INSTANCE));
		context.setSimpleTypeHolder(customConversions.getSimpleTypeHolder());
		context.setManagedTypes(managedTypes);
		return context;
	}

}
