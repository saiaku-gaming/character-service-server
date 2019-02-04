package com.valhallagame.characterserviceserver.service;

import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.repository.CharacterRepository;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;
import com.valhallagame.currencyserviceclient.CurrencyServiceClient;
import com.valhallagame.currencyserviceclient.model.CurrencyType;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import com.valhallagame.traitserviceclient.message.AttributeType;
import com.valhallagame.traitserviceclient.message.SkillTraitParameter;
import com.valhallagame.traitserviceclient.message.TraitType;
import com.valhallagame.traitserviceclient.message.UnlockTraitParameter;
import com.valhallagame.wardrobeserviceclient.message.WardrobeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class CharacterService {
    private final CharacterRepository characterRepository;

    private final RabbitTemplate rabbitTemplate;

    private final TraitServiceClient traitServiceClient;

    private final CurrencyServiceClient currencyServiceClient;

    private static Logger logger = LoggerFactory.getLogger(CharacterService.class);

    @Autowired
    public CharacterService(CharacterRepository characterRepository, RabbitTemplate rabbitTemplate, TraitServiceClient traitServiceClient, CurrencyServiceClient currencyServiceClient) {
        this.characterRepository = characterRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.traitServiceClient = traitServiceClient;
        this.currencyServiceClient = currencyServiceClient;
    }

    public Character saveCharacter(Character character) {
		return characterRepository.save(character);
	}

	public Optional<Character> getCharacter(String characterName) {
		return characterRepository.findByCharacterName(characterName.toLowerCase());
	}

	public List<Character> getCharacters(String username) {
		return characterRepository.findByOwnerUsername(username.toLowerCase());
	}

	public void setSelectedCharacter(String owner, String characterName) {
		characterRepository.setSelectedCharacter(owner.toLowerCase(), characterName.toLowerCase());
		rabbitTemplate.convertAndSend(RabbitMQRouting.Exchange.CHARACTER.name(),
				RabbitMQRouting.Character.SELECT.name(), new NotificationMessage(owner, "Changed selected character"));
	}

	public Optional<Character> getSelectedCharacter(String owner) {
		return characterRepository.getSelectedCharacter(owner.toLowerCase());
	}

	public void deleteCharacter(Character local) {
		characterRepository.delete(local);
	}

	public Character createCharacter(String username, String displayCharacterName, String startingClass) throws IOException {
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

		addTrait(characterName, TraitType.DODGE);
		SkillTraitParameter skillTraitParameter = new SkillTraitParameter(characterName, TraitType.DODGE, AttributeType.AGILITY, 0);
		traitServiceClient.skillTrait(skillTraitParameter);

		currencyServiceClient.addCurrency(characterName, CurrencyType.GOLD, 50);

		character = saveCharacter(character);
		setSelectedCharacter(character.getOwnerUsername(), character.getCharacterName());
		return character;
	}

	private void equipAsDebug(Character character, String characterName) {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
		character.setChestItem("HEAVY_HIDE_CHESTPIECE");
		character.setHandsItem("NONE");
		character.setLegsItem("REINFORCED_LEATHER_PANTS");
		character.setFeetItem("FORTIFIED_BOOTS");

		character.setMainhandArmament(WardrobeItem.SWORD.name());
		character.setOffHandArmament(WardrobeItem.MEDIUM_SHIELD.name());

		Arrays.stream(TraitType.values()).forEach(val -> {
			try {
				addTrait(characterName, val);
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
		character.setLegsItem("REINFORCED_LEATHER_PANTS");
		character.setFeetItem("FORTIFIED_BOOTS");

		character.setMainhandArmament(WardrobeItem.SWORD.name());
		character.setOffHandArmament(WardrobeItem.MEDIUM_SHIELD.name());

		addTrait(characterName, TraitType.SHIELD_BASH);
		addTrait(characterName, TraitType.RECOVER);
		addTrait(characterName, TraitType.TAUNT);
		addTrait(characterName, TraitType.KICK);
	}

	private void equipAsShaman(Character character, String characterName) throws IOException {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
    	character.setChestItem("CLOTH_TUNIC");
		character.setHandsItem("NONE");
		character.setLegsItem("SILKEN_PANTS");
		character.setFeetItem("APPRENTICES_BOOTS");

		character.setMainhandArmament(WardrobeItem.SWORD.name());
		character.setOffHandArmament("NONE");

		addTrait(characterName, TraitType.FROST_BLAST);
		addTrait(characterName, TraitType.SEIDHRING);
		addTrait(characterName, TraitType.PETRIFY);
		addTrait(characterName, TraitType.FRIGGS_INTERVENTION);
	}

	private void equipAsRanger(Character character, String characterName) throws IOException {
		character.setHeadItem("NONE");
		character.setBeardItem("NONE");
		character.setChestItem("RANGERS_HARNESS");
		character.setHandsItem("NONE");
		character.setLegsItem("LEATHER_BREECHES");
		character.setFeetItem("LEATHER_BOOTS");

		character.setMainhandArmament(WardrobeItem.LONGSWORD.name());
		character.setOffHandArmament("NONE");

		addTrait(characterName, TraitType.SHIELD_BREAKER);
		addTrait(characterName, TraitType.HEMORRHAGE);
        addTrait(characterName, TraitType.GUNGNIRS_WRATH);
		addTrait(characterName, TraitType.PARRY);
	}

	private void addTrait(String characterName, TraitType traitType) throws IOException {
		traitServiceClient.unlockTrait(new UnlockTraitParameter(characterName, traitType));
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
}
