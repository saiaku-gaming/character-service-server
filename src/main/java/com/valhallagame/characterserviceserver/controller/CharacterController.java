package com.valhallagame.characterserviceserver.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.valhallagame.characterserviceserver.message.CharacterNameAndOwnerUsernameParameter;
import com.valhallagame.characterserviceserver.message.CharacterNameParameter;
import com.valhallagame.characterserviceserver.message.SavedEquippedItemsParameter;
import com.valhallagame.characterserviceserver.message.UsernameParameter;
import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.service.CharacterService;
import com.valhallagame.common.JS;
import com.valhallagame.common.RestResponse;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;

@Controller
@RequestMapping(path = "/v1/character")
public class CharacterController {

	@Autowired
	private CharacterService characterService;

	@RequestMapping(path = "/get-character-without-owner-validation", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> getCharacterWithoutOwnerValidation(@RequestBody CharacterNameParameter character) {
		Optional<Character> optcharacter = characterService.getCharacter(character.getCharacterName());
		if (!optcharacter.isPresent()) {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
		}
		return JS.message(HttpStatus.OK, optcharacter.get());
	}
	
	@RequestMapping(path = "/get-character", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> getCharacter(@RequestBody CharacterNameAndOwnerUsernameParameter characterAndOwner) {
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
	public ResponseEntity<?> getAll(@RequestBody UsernameParameter username) {
		return JS.message(HttpStatus.OK, characterService.getCharacters(username.getUsername()));
	}

	@RequestMapping(path = "/create", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> create(@RequestBody CharacterNameAndOwnerUsernameParameter characterData) throws IOException {
		System.out.println("CREATE!!");
		String charName = characterData.getCharacterName();
		Optional<Character> localOpt = characterService.getCharacter(charName);
		if (!localOpt.isPresent()) {
			Character c = new Character();
			c.setOwnerUsername(characterData.getOwnerUsername());
			c.setDisplayCharacterName(charName);
			String charNameLower = characterData.getCharacterName().toLowerCase();
			c.setCharacterName(charNameLower);
			c.setChestItem("LeatherArmor");
			c.setMainhandArmament("Sword");
			c.setOffHandArmament("MediumShield");
			
			WardrobeServiceClient wardrobeServiceClient = WardrobeServiceClient.get();
			wardrobeServiceClient.addWardrobeItem(charNameLower, "LeatherArmor");
			wardrobeServiceClient.addWardrobeItem(charNameLower, "Sword");
			wardrobeServiceClient.addWardrobeItem(charNameLower, "MediumShield");
			
			c = characterService.saveCharacter(c);
			characterService.setSelectedCharacter(c.getOwnerUsername(), c.getCharacterName());
		} else {
			return JS.message(HttpStatus.CONFLICT, "Character already exists.");
		}
		return JS.message(HttpStatus.OK, "OK");
	}

	@RequestMapping(path = "/delete", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> delete(@RequestBody CharacterNameAndOwnerUsernameParameter characterAndOwner) {
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
			return JS.message(HttpStatus.OK, "Deleted character");
		} else {
			return JS.message(HttpStatus.FORBIDDEN, "No access");
		}
	}

	@RequestMapping(path = "/character-available", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> characterAvailable(@RequestBody CharacterNameParameter input) {
		if (input.getCharacterName() == null || input.getCharacterName().isEmpty()) {
			return JS.message(HttpStatus.BAD_REQUEST, "Missing characterName field");
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
	public ResponseEntity<?> selectCharacter(@RequestBody CharacterNameAndOwnerUsernameParameter characterAndOwner) {
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
	public ResponseEntity<?> getSelectedCharacter(@RequestBody UsernameParameter username) {
		Optional<Character> selectedCharacter = characterService.getSelectedCharacter(username.getUsername());
		if (selectedCharacter.isPresent()) {
			return JS.message(HttpStatus.OK, selectedCharacter.get());
		} else {
			return JS.message(HttpStatus.NOT_FOUND, "No character selected");
		}
	}
	
	@RequestMapping(path = "/save-equipped-items", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> saveEquippedItems(@RequestBody SavedEquippedItemsParameter input) throws IOException{
		Optional<Character> selectedCharacterOpt = characterService.getCharacter(input.getCharacterName());
		if(selectedCharacterOpt.isPresent()) {
			Character character = selectedCharacterOpt.get();
			WardrobeServiceClient wardrobeServiceClient = WardrobeServiceClient.get();
			RestResponse<List<String>> wardrobeItems = wardrobeServiceClient.getWardrobeItems(character.getCharacterName());
			
			List<String> items = wardrobeItems.getResponse().orElse(new ArrayList<String>());
			for (SavedEquippedItemsParameter.EquippedItem equippedItem : input.getEquippedItems()) {
				
				String armament = equippedItem.getArmament();
				String armor = equippedItem.getArmor();
				
				switch(equippedItem.getItemSlot()) {
				case "Main Hand":
					if(armament != null) {
						if(!items.contains(armament)) {
							System.err.println("wardrobe does not have armament" + armament + " in " + items);
						} else {
							character.setMainhandArmament(armament);
						}
					}
				case "Offhand":
					if(armament != null) {
						if(!items.contains(armament)) {
							System.err.println("wardrobe does not have armament" + armament + " in " + items);
						} else {
							character.setOffHandArmament(armament);
						}
					}
				case "Chest":
					if(armor != null) {
						if( !items.contains(armor)) {
							System.err.println("wardrobe does not have armor " + armor + " in " + items);
						} else {
							character.setChestItem(armor);
						}
					}
				}
			}
			Character saveCharacter = characterService.saveCharacter(character);
			return JS.message(HttpStatus.OK, saveCharacter);
		} else {
			return JS.message(HttpStatus.NOT_FOUND, "No character with that id");
		}
	}

}
