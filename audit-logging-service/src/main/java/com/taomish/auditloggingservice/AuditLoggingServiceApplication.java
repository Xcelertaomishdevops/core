package com.taomish.auditloggingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication
public class AuditLoggingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditLoggingServiceApplication.class, args);
	}

}
