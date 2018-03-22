package com.valhallagame.characterserviceserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;

@Configuration
@Profile("default")
public class DefaultConfig {
	@Bean
	public WardrobeServiceClient wardrobeServiceClient() {
		return WardrobeServiceClient.get();
	}
}
