package com.valhallagame.characterserviceserver.config;

import com.valhallagame.currencyserviceclient.CurrencyServiceClient;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("default")
public class DefaultConfig {
	@Bean
	public WardrobeServiceClient wardrobeServiceClient() {
		return WardrobeServiceClient.get();
	}

	@Bean
	public TraitServiceClient traitServiceClient() {
		return TraitServiceClient.get();
	}

	@Bean
	public CurrencyServiceClient currencyServiceClient() {
		return CurrencyServiceClient.get();
	}
}
