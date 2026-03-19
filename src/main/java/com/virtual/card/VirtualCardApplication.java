package com.virtual.card;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VirtualCardApplication {

	public static void main(String[] args) {
		SpringApplication.run(VirtualCardApplication.class, args);
	}

}
