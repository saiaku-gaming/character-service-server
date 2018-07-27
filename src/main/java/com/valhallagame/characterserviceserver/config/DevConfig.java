package com.valhallagame.characterserviceserver.config;

import com.valhallagame.common.DefaultServicePortMappings;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("development")
public class DevConfig {
	@Bean
	public WardrobeServiceClient wardrobeServiceClient() {
		WardrobeServiceClient.init("http://dev-wardrobe-service:" + DefaultServicePortMappings.WARDROBE_SERVICE_PORT);
		return WardrobeServiceClient.get();
	}

	@Bean
	public TraitServiceClient traitServiceClient() {
		WardrobeServiceClient.init("http://dev-trait-service:" + DefaultServicePortMappings.TRAIT_SERVICE_PORT);
		return TraitServiceClient.get();
	}
}
