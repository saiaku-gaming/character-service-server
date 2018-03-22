package com.valhallagame.characterserviceserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.valhallagame.common.DefaultServicePortMappings;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;

@Configuration
@Profile("development")
public class DevConfig {
	@Bean
	public WardrobeServiceClient wardrobeServiceClient() {
		WardrobeServiceClient.init("http://dev-wardrobe-service:" + DefaultServicePortMappings.WARDROBE_SERVICE_PORT);
		return WardrobeServiceClient.get();
	}
}
