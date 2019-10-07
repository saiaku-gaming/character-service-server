package com.valhallagame.characterserviceserver.service;

import com.valhallagame.characterserviceclient.message.EquippedItemParameter;
import com.valhallagame.characterserviceclient.model.Items;
import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.repository.CharacterRepository;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;
import com.valhallagame.common.rabbitmq.RabbitSender;
import com.valhallagame.currencyserviceclient.CurrencyServiceClient;
import com.valhallagame.currencyserviceclient.model.CurrencyType;
import com.valhallagame.recipeserviceclient.RecipeServiceClient;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import com.valhallagame.traitserviceclient.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class CharacterService {
    private final CharacterRepository characterRepository;

    private final RabbitSender rabbitSender;

    private final TraitServiceClient traitServiceClient;

    private final CurrencyServiceClient currencyServiceClient;

    private final RecipeServiceClient recipeServiceClient;

    private static Logger logger = LoggerFactory.getLogger(CharacterService.class);

    @Autowired
    public CharacterService(CharacterRepository characterRepository, RabbitSender rabbitSender,
							TraitServiceClient traitServiceClient, CurrencyServiceClient currencyServiceClient,
							RecipeServiceClient recipeServiceClient) {
        this.characterRepository = characterRepository;
        this.rabbitSender = rabbitSender;
        this.traitServiceClient = traitServiceClient;
        this.currencyServiceClient = currencyServiceClient;
        this.recipeServiceClient = recipeServiceClient;
    }

    public Character saveCharacter(Character character) {
    	logger.info("Saving character: {}", character);
		return characterRepository.save(character);
	}

	public Optional<Character> getCharacter(String characterName) {
    	logger.info("Getting character with name: {}", characterName);
		return characterRepository.findByCharacterName(characterName.toLowerCase());
	}

	public List<Character> getCharacters(String username) {
    	logger.info("Getting characters for username: {}", username);
		return characterRepository.findByOwnerUsername(username.toLowerCase());
	}

	public void setSelectedCharacter(String owner, String characterName) {
    	logger.info("Setting selected character for user {} to {}", owner, characterName);
		characterRepository.setSelectedCharacter(owner.toLowerCase(), characterName.toLowerCase());
		rabbitSender.sendMessage(RabbitMQRouting.Exchange.CHARACTER,
				RabbitMQRouting.Character.SELECT.name(), new NotificationMessage(owner, "Changed selected character"));
	}

	public Optional<Character> getSelectedCharacter(String owner) {
    	logger.info("Getting selected character for user {}", owner);
		return characterRepository.getSelectedCharacter(owner.toLowerCase());
	}

	public void deleteCharacter(Character local) {
    	logger.info("Deleting character: {}", local);
		characterRepository.delete(local);
	}

	public Character createCharacter(String username, String displayCharacterName, String startingClass) throws IOException {
    	logger.info("Creating character for user {} with name {} and starting class {}", username, displayCharacterName, startingClass);
		Character character = new Character();
		character.setOwnerUsername(username);
		character.setDisplayCharacterName(displayCharacterName);

		String characterName = displayCharacterName.toLowerCase();
		character.setCharacterName(characterName);

		if(!AllowedClasses.has(startingClass)) {
			return null;
		}

		switch (AllowedClasses.get(startingClass)) {
			case WARRIOR:
				equipAsWarrior(character, characterName);
				break;
			case SHAMAN:
				equipAsShaman(character, characterName);
				break;
			case RANGER:
				equipAsRanger(character, characterName);
				break;
			case DEBUG:
				equipAsDebug(character, characterName);
				break;
		}

		unlockTrait(characterName, TraitType.DODGE);
		purchaseTrait(characterName, TraitType.DODGE);
		
		unlockTrait(characterName, TraitType.SHIELD_BREAKER);
		unlockTrait(characterName, TraitType.HEMORRHAGE);
		unlockTrait(characterName, TraitType.GUNGNIRS_WRATH);
		unlockTrait(characterName, TraitType.ONEHANDED_SPECIALIZATION);
		unlockTrait(characterName, TraitType.FROST_BLAST);
		unlockTrait(characterName, TraitType.SEIDHRING);
		unlockTrait(characterName, TraitType.PETRIFY);
		unlockTrait(characterName, TraitType.FRIGGS_INTERVENTION);
		unlockTrait(characterName, TraitType.SHIELD_BASH);
		unlockTrait(characterName, TraitType.RECOVER);
		unlockTrait(characterName, TraitType.TAUNT);
		unlockTrait(characterName, TraitType.KICK);

		SkillTraitParameter skillTraitParameter = new SkillTraitParameter(characterName, TraitType.DODGE, AttributeType.AGILITY, 0);
		traitServiceClient.skillTrait(skillTraitParameter);

		currencyServiceClient.addCurrency(characterName, CurrencyType.GOLD, 50);

		character = saveCharacter(character);
		setSelectedCharacter(character.getOwnerUsername(), character.getCharacterName());
		addDefaultRecipes(characterName);
		return character;
	}

	public Character equipItem(String characterName, EquippedItemParameter itemToEquip) {
		logger.info("Equip item for character {} with {}", characterName, itemToEquip);
		Optional<Character> optCharacter = getCharacter(characterName);

		if(!optCharacter.isPresent()) {
			return null;
		}

		Character character = optCharacter.get();

		equipCharacter(character, itemToEquip);

		return saveCharacter(character);
	}

	public Character unequipItem(String characterName, String itemSlot) {
		logger.info("Unequip item for character {} with {}", characterName, itemSlot);
		Optional<Character> optCharacter = getCharacter(characterName);

		if(!optCharacter.isPresent()) {
			return null;
		}

		Character character = optCharacter.get();

		unequipCharacter(character, itemSlot);

		return saveCharacter(character);
	}

	private void equipAsDebug(Character character, String characterName) {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
		character.setChestItem("HEAVY_HIDE_CHESTPIECE");
		character.setHandsItem("NONE");
		character.setLegsItem("REINFORCED_LEATHER_PANTS");
		character.setFeetItem("FORTIFIED_BOOTS");

		character.setMainhandArmament(Items.BLUNT_HAND_AXE.name());
		character.setOffHandArmament(Items.CUMBERSOME_SMALL_SHIELD.name());

		Arrays.stream(TraitType.values()).forEach(val -> {
			try {
				unlockTrait(characterName, val);
			} catch (IOException e) {
				logger.error("failed to populate debug character with " + val, e);
			}
		});
	}

	private void equipAsWarrior(Character character, String characterName) throws IOException {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
    	character.setChestItem("HEAVY_HIDE_CHESTPIECE");
		character.setHandsItem("NONE");
        character.setLegsItem("WORN_RAGS");
        character.setFeetItem("NONE");

		character.setMainhandArmament(Items.BLUNT_HAND_AXE.name());
		character.setOffHandArmament(Items.CUMBERSOME_SMALL_SHIELD.name());

		purchaseTrait(characterName, TraitType.SHIELD_BASH);
		purchaseTrait(characterName, TraitType.RECOVER);
		purchaseTrait(characterName, TraitType.TAUNT);
		purchaseTrait(characterName, TraitType.KICK);
	}

	private void equipAsShaman(Character character, String characterName) throws IOException {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
    	character.setChestItem("CLOTH_TUNIC");
		character.setHandsItem("NONE");
        character.setLegsItem("WORN_RAGS");
        character.setFeetItem("NONE");

		character.setMainhandArmament(Items.BLUNT_HAND_AXE.name());
		character.setOffHandArmament(Items.CUMBERSOME_SMALL_SHIELD.name());

		purchaseTrait(characterName, TraitType.FROST_BLAST);
		purchaseTrait(characterName, TraitType.SEIDHRING);
		purchaseTrait(characterName, TraitType.PETRIFY);
		purchaseTrait(characterName, TraitType.FRIGGS_INTERVENTION);
	}

	private void equipAsRanger(Character character, String characterName) throws IOException {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
		character.setChestItem("RANGERS_HARNESS");
		character.setHandsItem("NONE");
        character.setLegsItem("WORN_RAGS");
        character.setFeetItem("NONE");

		character.setMainhandArmament(Items.BLUNT_HAND_AXE.name());
		character.setOffHandArmament(Items.CUMBERSOME_SMALL_SHIELD.name());

		purchaseTrait(characterName, TraitType.SHIELD_BREAKER);
		purchaseTrait(characterName, TraitType.HEMORRHAGE);
		purchaseTrait(characterName, TraitType.GUNGNIRS_WRATH);
		purchaseTrait(characterName, TraitType.ONEHANDED_SPECIALIZATION);
	}

	private void unlockTrait(String characterName, TraitType traitType) throws IOException {
		traitServiceClient.unlockTrait(new UnlockTraitParameter(characterName, traitType));
	}

	private void purchaseTrait(String characterName, TraitType traitType) throws IOException {
		traitServiceClient.purchaseTrait(new PurchaseTraitParameter(characterName, traitType));
	}

	private void addDefaultRecipes(String characterName) throws IOException {
		recipeServiceClient.addRecipe(characterName, "SWORD");
		recipeServiceClient.addRecipe(characterName, "HAND_AXE");
		recipeServiceClient.addRecipe(characterName, "LONGSWORD");
		recipeServiceClient.addRecipe(characterName, "DAGGER");
		recipeServiceClient.addRecipe(characterName, "WARHAMMER");
		recipeServiceClient.addRecipe(characterName, "GREATAXE");
		recipeServiceClient.addRecipe(characterName, "SMALL_SHIELD");
		recipeServiceClient.addRecipe(characterName, "MEDIUM_SHIELD");
		recipeServiceClient.addRecipe(characterName, "LARGE_SHIELD");
		recipeServiceClient.addRecipe(characterName, "STEEL_SHIELD");
		recipeServiceClient.addRecipe(characterName, "TORCH");
		recipeServiceClient.addRecipe(characterName, "HUNTING_BOW");
	}

	private enum AllowedClasses {
		WARRIOR,
		SHAMAN,
		RANGER,
		DEBUG;

		static AllowedClasses get(String enumStringValue){
			return AllowedClasses.valueOf(enumStringValue.toUpperCase());
		}

		static boolean has(String enumStringValue){
			try{
				get(enumStringValue);
				return true;
			} catch (IllegalArgumentException e){
				return false;
			}
		}
	}

	public void equipCharacter(Character character, EquippedItemParameter equippedItem) {
		String item = equippedItem.getItem();
		String itemSlot = equippedItem.getItemSlot();
		String metaData = equippedItem.getMetaData();
		switch (itemSlot) {
			case "MAINHAND":
				character.setMainhandArmament(item);
				character.setMainhandArmamentMetaData(metaData);
				break;
			case "OFFHAND":
				character.setOffHandArmament(item);
				character.setOffHandArmamentMetaData(metaData);
				break;
			case "HEAD":
				character.setHeadItem(item);
				character.setHeadItemMetaData(metaData);
				break;
			case "BEARD":
				character.setBeardItem(item);
				character.setBeardItemMetaData(metaData);
				break;
			case "CHEST":
				character.setChestItem(item);
				character.setChestItemMetaData(metaData);
				break;
			case "HANDS":
				character.setHandsItem(item);
				character.setHandsItemMetaData(metaData);
				break;
			case "LEGS":
				character.setLegsItem(item);
				character.setLegsItemMetaData(metaData);
				break;
			case "FEET":
				character.setFeetItem(item);
				character.setFeetItemMetaData(metaData);
				break;
			default:
				logger.error("{} DOES NOT EXIST AS A SLOT!", itemSlot);
				break;
		}
	}

	public void unequipCharacter(Character character, String itemSlot) {
		switch (itemSlot) {
			case "MAINHAND":
				character.setMainhandArmament("None");
				character.setMainhandArmamentMetaData(null);
				break;
			case "OFFHAND":
				character.setOffHandArmament("None");
				character.setOffHandArmamentMetaData(null);
				break;
			case "HEAD":
				character.setHeadItem("None");
				character.setHeadItemMetaData(null);
				break;
			case "BEARD":
				character.setBeardItem("None");
				character.setBeardItemMetaData(null);
				break;
			case "CHEST":
				character.setChestItem("None");
				character.setChestItemMetaData(null);
				break;
			case "HANDS":
				character.setHandsItem("None");
				character.setHandsItemMetaData(null);
				break;
			case "LEGS":
				character.setLegsItem("None");
				character.setLegsItemMetaData(null);
				break;
			case "FEET":
				character.setFeetItem("None");
				character.setFeetItemMetaData(null);
				break;
			default:
				logger.error("{} DOES NOT EXIST AS A SLOT!", itemSlot);
				break;
		}
	}
}
