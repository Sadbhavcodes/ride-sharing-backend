package com.rideshare.driverservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class DriverserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DriverserviceApplication.class, args);
	}

}
