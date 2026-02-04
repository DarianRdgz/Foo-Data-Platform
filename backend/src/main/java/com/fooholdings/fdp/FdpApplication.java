package com.fooholdings.fdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.fooholdings.fdp.kroger.config.KrogerProperties;

@SpringBootApplication
@EnableConfigurationProperties(KrogerProperties.class)
public class FdpApplication {

	public static void main(String[] args) {
		SpringApplication.run(FdpApplication.class, args);
	}

}
