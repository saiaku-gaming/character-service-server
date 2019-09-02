package com.valhallagame.characterserviceserver.config;

import com.valhallagame.common.DefaultServicePortMappings;
import com.valhallagame.currencyserviceclient.CurrencyServiceClient;
import com.valhallagame.recipeserviceclient.RecipeServiceClient;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"production", "development"})
public class ServiceConfig {
	@Bean
	public TraitServiceClient traitServiceClient() {
		TraitServiceClient.init("http://trait-service:" + DefaultServicePortMappings.TRAIT_SERVICE_PORT);
		return TraitServiceClient.get();
	}

	@Bean
	public CurrencyServiceClient currencyServiceClient() {
		CurrencyServiceClient.init("http://currency-service:" + DefaultServicePortMappings.CURRENCY_SERVICE_PORT);
		return CurrencyServiceClient.get();
	}

	@Bean
	public RecipeServiceClient recipeServiceClient() {
		RecipeServiceClient.init("http://recipe-service:" + DefaultServicePortMappings.RECIPE_SERVICE_PORT);
		return RecipeServiceClient.get();
	}
}
