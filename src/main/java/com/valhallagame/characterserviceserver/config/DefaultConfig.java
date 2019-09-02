package com.valhallagame.characterserviceserver.config;

import com.valhallagame.currencyserviceclient.CurrencyServiceClient;
import com.valhallagame.recipeserviceclient.RecipeServiceClient;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("default")
public class DefaultConfig {
	@Bean
	public TraitServiceClient traitServiceClient() {
		return TraitServiceClient.get();
	}

	@Bean
	public CurrencyServiceClient currencyServiceClient() {
		return CurrencyServiceClient.get();
	}

	@Bean
	public RecipeServiceClient recipeServiceClient() {
		return RecipeServiceClient.get();
	}
}
