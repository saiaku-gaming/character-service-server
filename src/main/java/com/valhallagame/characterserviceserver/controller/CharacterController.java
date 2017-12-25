package com.valhallagame.characterserviceserver.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.valhallagame.characterserviceclient.message.CharacterNameAndOwnerUsernameParameter;
import com.valhallagame.characterserviceclient.message.CharacterNameParameter;
import com.valhallagame.characterserviceclient.message.EqippedItemsParameter;
import com.valhallagame.characterserviceclient.message.EquippedItem;
import com.valhallagame.characterserviceclient.message.UsernameParameter;
import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.service.CharacterService;
import com.valhallagame.common.JS;
import com.valhallagame.common.RestResponse;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;

@Controller
@RequestMapping(path = "/v1/character")
public class CharacterController {

	private static final Logger logger = LoggerFactory.getLogger(CharacterController.class);

	@Autowired
	private CharacterService characterService;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@RequestMapping(path = "/get-character-without-owner-validation", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getCharacterWithoutOwnerValidation(@Valid @RequestBody CharacterNameParameter character) {

		Optional<Character> optcharacter = characterService.getCharacter(character.getCharacterName());
		if (!optcharacter.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
		}
		return JS.message(HttpStatus.OK, optcharacter.get());
	}

	@RequestMapping(path = "/get-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getCharacter(@Valid @RequestBody CharacterNameAndOwnerUsernameParameter characterAndOwner) {
		Optional<Character> optcharacter = characterService.getCharacter(characterAndOwner.getCharacterName());
		if (!optcharacter.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
		}

		Character character = optcharacter.get();
		if (!character.getOwnerUsername().equals(characterAndOwner.getOwnerUsername())) {
			return JS.message(HttpStatus.NOT_FOUND, "Wrong owner!");
		}
		return JS.message(HttpStatus.OK, optcharacter.get());
	}

	@RequestMapping(path = "/get-all", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getAll(@Valid @RequestBody UsernameParameter username) {
		return JS.message(HttpStatus.OK, characterService.getCharacters(username.getUsername()));
	}

	@RequestMapping(path = "/create-debug-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> createDebugCharacter(@Valid @RequestBody CharacterNameAndOwnerUsernameParameter characterData)
			throws IOException {
		String charName = characterData.getCharacterName();
		Optional<Character> localOpt = characterService.getCharacter(charName);
		if (!localOpt.isPresent()) {
			return create(characterData);
		} else {
			Character character = localOpt.get();
			character.setOwnerUsername(characterData.getOwnerUsername());
			characterService.saveCharacter(character);
		}
		return JS.message(HttpStatus.OK, "OK");
	}

	@RequestMapping(path = "/create", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> create(@Valid @RequestBody CharacterNameAndOwnerUsernameParameter characterData)
			throws IOException {

		String charName = characterData.getCharacterName();
		if (charName.contains("#")) {
			return JS.message(HttpStatus.BAD_REQUEST, "# is not allowed in character name");
		}
		Optional<Character> localOpt = characterService.getCharacter(charName);
		if (!localOpt.isPresent()) {
			Character c = new Character();
			c.setOwnerUsername(characterData.getOwnerUsername());
			c.setDisplayCharacterName(charName);
			String charNameLower = characterData.getCharacterName().toLowerCase();
			c.setCharacterName(charNameLower);
			c.setChestItem("Leather_Armor");
			c.setMainhandArmament("Sword");
			c.setOffHandArmament("Medium_Shield");

			WardrobeServiceClient wardrobeServiceClient = WardrobeServiceClient.get();
			wardrobeServiceClient.addWardrobeItem(charNameLower, "Leather_Armor");
			wardrobeServiceClient.addWardrobeItem(charNameLower, "Sword");
			wardrobeServiceClient.addWardrobeItem(charNameLower, "Medium_Shield");

			c = characterService.saveCharacter(c);
			characterService.setSelectedCharacter(c.getOwnerUsername(), c.getCharacterName());
		} else {
			return JS.message(HttpStatus.CONFLICT, "Character already exists.");
		}
		return JS.message(HttpStatus.OK, "OK");
	}

	@RequestMapping(path = "/delete", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> delete(@Valid @RequestBody CharacterNameAndOwnerUsernameParameter characterAndOwner) {
		String owner = characterAndOwner.getOwnerUsername();
		Optional<Character> localOpt = characterService.getCharacter(characterAndOwner.getCharacterName());
		if (!localOpt.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "Not found");
		}

		Character local = localOpt.get();

		if (owner.equals(local.getOwnerUsername())) {
			// Randomly(ish) select a new character as default character if the
			// person has one.
			Optional<Character> selectedCharacterOpt = characterService.getSelectedCharacter(owner);
			if (selectedCharacterOpt.isPresent() && selectedCharacterOpt.get().equals(local)) {
				characterService.getCharacters(owner).stream().filter(x -> !x.equals(local)).findAny().ifPresent(ch -> {
					characterService.setSelectedCharacter(owner, ch.getCharacterName());
				});
			}
			characterService.deleteCharacter(local);

			NotificationMessage notificationMessage = new NotificationMessage("", "A character was deleted");
			notificationMessage.addData("characterName", local.getCharacterName());

			rabbitTemplate.convertAndSend(RabbitMQRouting.Exchange.CHARACTER.name(),
					RabbitMQRouting.Character.DELETE.name(), notificationMessage);

			return JS.message(HttpStatus.OK, "Deleted character");
		} else {
			return JS.message(HttpStatus.FORBIDDEN, "No access");
		}
	}

	@RequestMapping(path = "/character-available", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> characterAvailable(@Valid @RequestBody CharacterNameParameter input) {
		if (input.getCharacterName() == null || input.getCharacterName().isEmpty()) {
			return JS.message(HttpStatus.BAD_REQUEST, "Missing characterName field");
		}

		if (input.getCharacterName().contains("#")) {
			return JS.message(HttpStatus.BAD_REQUEST, "# is not allowed in character name");
		}

		Optional<Character> localOpt = characterService.getCharacter(input.getCharacterName());
		if (localOpt.isPresent()) {
			return JS.message(HttpStatus.CONFLICT, "Character not available");
		} else {
			return JS.message(HttpStatus.OK, "Character available");
		}
	}

	@RequestMapping(path = "/select-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> selectCharacter(@Valid @RequestBody CharacterNameAndOwnerUsernameParameter characterAndOwner) {
		Optional<Character> localOpt = characterService.getCharacter(characterAndOwner.getCharacterName());
		if (!localOpt.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND,
					"Character with name " + characterAndOwner.getCharacterName() + " was not found.");
		} else {
			if (!localOpt.get().getOwnerUsername().equals(characterAndOwner.getOwnerUsername())) {
				return JS.message(HttpStatus.FORBIDDEN, "You don't own that character.");
			}
			characterService.setSelectedCharacter(characterAndOwner.getOwnerUsername(),
					characterAndOwner.getCharacterName());
			return JS.message(HttpStatus.OK, "Character selected");
		}
	}

	@RequestMapping(path = "/get-selected-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getSelectedCharacter(@Valid @RequestBody UsernameParameter username) {
		Optional<Character> selectedCharacter = characterService.getSelectedCharacter(username.getUsername());
		if (selectedCharacter.isPresent()) {
			return JS.message(HttpStatus.OK, selectedCharacter.get());
		} else {
			return JS.message(HttpStatus.NOT_FOUND, "No character selected");
		}
	}

	@RequestMapping(path = "/save-equipped-items", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> saveEquippedItems(@Valid @RequestBody EqippedItemsParameter input) throws IOException {
		Optional<Character> selectedCharacterOpt = characterService.getCharacter(input.getCharacterName());
		if (selectedCharacterOpt.isPresent()) {
			Character character = selectedCharacterOpt.get();
			WardrobeServiceClient wardrobeServiceClient = WardrobeServiceClient.get();
			RestResponse<List<String>> wardrobeItems = wardrobeServiceClient
					.getWardrobeItems(character.getCharacterName());

			List<String> items = wardrobeItems.getResponse().orElse(new ArrayList<String>());
			for (EquippedItem equippedItem : input.getEquippedItems()) {
				equippCharacter(character, items, equippedItem);
			}
			Character saveCharacter = characterService.saveCharacter(character);
			return JS.message(HttpStatus.OK, saveCharacter);
		} else {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that id");
		}
	}

	private void equippCharacter(Character character, List<String> items,
			EquippedItem equippedItem) {
		String armament = equippedItem.getArmament();
		String armor = equippedItem.getArmor();
		String itemSlot = equippedItem.getItemSlot();
		switch (itemSlot) {
		case "Mainhand":
			if (items.contains(armament)) {
				character.setMainhandArmament(armament);
			} else {
				logger.error("wardrobe does not have armament {} in {} ", armament, items);
			}
			break;
		case "Offhand":
			if (items.contains(armament)) {
				character.setOffHandArmament(armament);
			} else {
				logger.error("wardrobe does not have armament {} in {}", armament, items);
			}
			break;
		case "Chest":
			if (items.contains(armor)) {
				character.setChestItem(armor);
			} else {
				logger.error("wardrobe does not have armor {} in {}", armor, items);
			}
			break;
		default:
			logger.error("{} DOES NOT EXIST AS A SLOT!", itemSlot);
			break;
		}
	}

}
