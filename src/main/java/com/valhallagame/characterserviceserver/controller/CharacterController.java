package com.valhallagame.characterserviceserver.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
import com.valhallagame.characterserviceclient.message.CharacterAvailableParameter;
import com.valhallagame.characterserviceclient.message.CreateCharacterParameter;
import com.valhallagame.characterserviceclient.message.CreateDebugCharacterParameter;
import com.valhallagame.characterserviceclient.message.DeleteCharacterParameter;
import com.valhallagame.characterserviceclient.message.EquippedItemParameter;
import com.valhallagame.characterserviceclient.message.GetAllCharactersParameter;
import com.valhallagame.characterserviceclient.message.GetCharacterParameter;
import com.valhallagame.characterserviceclient.message.GetOwnedCharacterParameter;
import com.valhallagame.characterserviceclient.message.GetSelectedCharacterParameter;
import com.valhallagame.characterserviceclient.message.SaveEquippedItemsParameter;
import com.valhallagame.characterserviceclient.message.SelectCharacterParameter;
import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.service.CharacterService;
import com.valhallagame.common.JS;
import com.valhallagame.common.RestResponse;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;
import com.valhallagame.wardrobeserviceclient.message.AddWardrobeItemParameter;
import com.valhallagame.wardrobeserviceclient.message.WardrobeItem;

@Controller
@RequestMapping(path = "/v1/character")
public class CharacterController {

	private static final Logger logger = LoggerFactory.getLogger(CharacterController.class);

	@Autowired
	private CharacterService characterService;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private WardrobeServiceClient wardrobeServiceClient;

	@RequestMapping(path = "/get-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getCharacterWithoutOwnerValidation(
			@Valid @RequestBody GetCharacterParameter input) {

		Optional<Character> optcharacter = characterService.getCharacter(input.getCharacterName());
		if (!optcharacter.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
		}
		return JS.message(HttpStatus.OK, optcharacter.get());
	}

	@RequestMapping(path = "/get-owned-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getCharacterWithOwner(@Valid @RequestBody GetOwnedCharacterParameter input) {
		Optional<Character> optcharacter = characterService.getCharacter(input.getCharacterName());
		if (!optcharacter.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
		}

		Character character = optcharacter.get();
		if (!character.getOwnerUsername().equals(input.getUsername())) {
			return JS.message(HttpStatus.NOT_FOUND, "Wrong owner!");
		}
		return JS.message(HttpStatus.OK, optcharacter.get());
	}

	@RequestMapping(path = "/get-all-characters", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getAllCharacters(@Valid @RequestBody GetAllCharactersParameter input) {
		return JS.message(HttpStatus.OK, characterService.getCharacters(input.getUsername()));
	}

	@RequestMapping(path = "/create-debug-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> createDebugCharacter(@Valid @RequestBody CreateDebugCharacterParameter input)
			throws IOException {
		String charName = input.getDisplayCharacterName().toLowerCase();
		Optional<Character> localOpt = characterService.getCharacter(charName);
		if (!localOpt.isPresent()) {

			String characterDisplayName = input.getDisplayCharacterName().chars().mapToObj(c -> String.valueOf((char) c))
					.map(c -> Math.random() < 0.5 ? c.toUpperCase() : c.toLowerCase()).collect(Collectors.joining());

			input.setDisplayCharacterName(characterDisplayName);

			CreateCharacterParameter out = new CreateCharacterParameter(input.getDisplayCharacterName(),
					input.getUsername());

			return createCharacter(out);
		} else {
			Character character = localOpt.get();
			character.setOwnerUsername(input.getUsername());
			characterService.saveCharacter(character);
		}
		return JS.message(HttpStatus.OK, "OK");
	}

	@RequestMapping(path = "/create-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> createCharacter(@Valid @RequestBody CreateCharacterParameter input)
			throws IOException {

		String charName = input.getDisplayCharacterName();
		if (charName.contains("#")) {
			return JS.message(HttpStatus.BAD_REQUEST, "# is not allowed in character name");
		}
		Optional<Character> localOpt = characterService.getCharacter(charName.toLowerCase());
		if (!localOpt.isPresent()) {
			Character c = new Character();
			c.setOwnerUsername(input.getUsername());
			c.setDisplayCharacterName(charName);
			String charNameLower = input.getDisplayCharacterName().toLowerCase();
			c.setCharacterName(charNameLower);
			c.setChestItem(WardrobeItem.LEATHER_ARMOR.name());
			c.setMainhandArmament(WardrobeItem.SWORD.name());
			c.setOffHandArmament(WardrobeItem.MEDIUM_SHIELD.name());

			wardrobeServiceClient
					.addWardrobeItem(new AddWardrobeItemParameter(charNameLower, WardrobeItem.LEATHER_ARMOR));
			wardrobeServiceClient.addWardrobeItem(new AddWardrobeItemParameter(charNameLower, WardrobeItem.SWORD));
			wardrobeServiceClient
					.addWardrobeItem(new AddWardrobeItemParameter(charNameLower, WardrobeItem.MEDIUM_SHIELD));

			c = characterService.saveCharacter(c);
			characterService.setSelectedCharacter(c.getOwnerUsername(), c.getCharacterName());
		} else {
			return JS.message(HttpStatus.CONFLICT, "Character already exists.");
		}
		return JS.message(HttpStatus.OK, "OK");
	}

	@RequestMapping(path = "/delete-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> deleteCharacter(@Valid @RequestBody DeleteCharacterParameter input) {
		String owner = input.getUsername();
		Optional<Character> localOpt = characterService.getCharacter(input.getCharacterName());
		if (!localOpt.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "Not found");
		}

		Character local = localOpt.get();

		if (owner.equals(local.getOwnerUsername())) {
			// Randomly(ish) select a new character as default character if the
			// person has one.
			Optional<Character> selectedCharacterOpt = characterService.getSelectedCharacter(owner);
			if (selectedCharacterOpt.isPresent() && selectedCharacterOpt.get().equals(local)) {
				characterService.getCharacters(owner).stream().filter(x -> !x.equals(local)).findAny()
						.ifPresent(ch -> characterService.setSelectedCharacter(owner, ch.getCharacterName()));
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
	public ResponseEntity<JsonNode> characterAvailable(@Valid @RequestBody CharacterAvailableParameter input) {
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
	public ResponseEntity<JsonNode> selectCharacter(@Valid @RequestBody SelectCharacterParameter input) {
		Optional<Character> localOpt = characterService.getCharacter(input.getCharacterName());
		if (!localOpt.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND,
					"Character with name " + input.getCharacterName() + " was not found.");
		} else {
			if (!localOpt.get().getOwnerUsername().equals(input.getUsername())) {
				return JS.message(HttpStatus.FORBIDDEN, "You don't own that character.");
			}
			characterService.setSelectedCharacter(input.getUsername(), input.getCharacterName());
			return JS.message(HttpStatus.OK, "Character selected");
		}
	}

	@RequestMapping(path = "/get-selected-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> getSelectedCharacter(@Valid @RequestBody GetSelectedCharacterParameter input) {
		Optional<Character> selectedCharacter = characterService.getSelectedCharacter(input.getUsername());
		if (selectedCharacter.isPresent()) {
			return JS.message(HttpStatus.OK, selectedCharacter.get());
		} else {
			return JS.message(HttpStatus.NOT_FOUND, "No character selected");
		}
	}

	@RequestMapping(path = "/save-equipped-items", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<JsonNode> saveEquippedItems(@Valid @RequestBody SaveEquippedItemsParameter input)
			throws IOException {

		logger.info("Saving equipment {}", input);

		Optional<Character> selectedCharacterOpt = characterService.getCharacter(input.getCharacterName());
		if (selectedCharacterOpt.isPresent()) {
			Character character = selectedCharacterOpt.get();
			RestResponse<List<String>> wardrobeItems = wardrobeServiceClient
					.getWardrobeItems(character.getOwnerUsername());

			List<String> items = wardrobeItems.getResponse().orElse(new ArrayList<String>());
			items.add("NONE");
			for (EquippedItemParameter equippedItem : input.getEquippedItems()) {
				equippCharacter(character, items, equippedItem);
			}
			Character saveCharacter = characterService.saveCharacter(character);
			return JS.message(HttpStatus.OK, saveCharacter);
		} else {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that id");
		}
	}

	private void equippCharacter(Character character, List<String> items, EquippedItemParameter equippedItem) {
		String armament = equippedItem.getArmament();
		String armor = equippedItem.getArmor();
		String itemSlot = equippedItem.getItemSlot();
		switch (itemSlot) {
		case "MAINHAND":
			if (items.contains(armament)) {
				character.setMainhandArmament(armament);
			} else {
				logger.error("wardrobe does not have armament {} in {} ", armament, items);
			}
			break;
		case "OFFHAND":
			if (items.contains(armament)) {
				character.setOffHandArmament(armament);
			} else {
				logger.error("wardrobe does not have armament {} in {}", armament, items);
			}
			break;
		case "CHEST":
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
