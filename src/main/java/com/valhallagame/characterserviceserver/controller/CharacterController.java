package com.valhallagame.characterserviceserver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.valhallagame.characterserviceclient.message.*;
import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.service.CharacterService;
import com.valhallagame.common.JS;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;
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

import javax.validation.Valid;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping(path = "/v1/character")
public class CharacterController {

    private static final Logger logger = LoggerFactory.getLogger(CharacterController.class);

    @Autowired
    private CharacterService characterService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RequestMapping(path = "/get-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> getCharacterWithoutOwnerValidation(
            @Valid @RequestBody GetCharacterParameter input) {
        logger.info("Get Character called with {}", input);
        Optional<Character> optCharacter = characterService.getCharacter(input.getCharacterName());
        if (!optCharacter.isPresent()) {
            return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
        }
        return JS.message(HttpStatus.OK, optCharacter.get());
    }

    @RequestMapping(path = "/get-owned-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> getCharacterWithOwner(@Valid @RequestBody GetOwnedCharacterParameter input) {
        logger.info("Get Character called with {}", input);
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
        logger.info("Get All Character called with {}", input);
        return JS.message(HttpStatus.OK, characterService.getCharacters(input.getUsername()));
    }

    @RequestMapping(path = "/create-debug-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> createDebugCharacter(@Valid @RequestBody CreateDebugCharacterParameter input)
            throws IOException {
        logger.info("Create Debug Character called with {}", input);
        String charName = input.getDisplayCharacterName().toLowerCase();
        Optional<Character> localOpt = characterService.getCharacter(charName);
        if (!localOpt.isPresent()) {

            String characterDisplayName = input.getDisplayCharacterName().chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .map(c -> Math.random() < 0.5 ? c.toUpperCase() : c.toLowerCase()).collect(Collectors.joining());

            input.setDisplayCharacterName(characterDisplayName);

            CreateCharacterParameter out = new CreateCharacterParameter(input.getDisplayCharacterName(),
                    input.getUsername(), "debug");

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
        logger.info("Create Character called with {}", input);
        String displayCharacterName = input.getDisplayCharacterName();
        if (displayCharacterName.contains("#")) {
            return JS.message(HttpStatus.BAD_REQUEST, "# is not allowed in character name");
        }
        Optional<Character> localOpt = characterService.getCharacter(displayCharacterName.toLowerCase());
        if (!localOpt.isPresent()) {
            characterService.createCharacter(input.getUsername(), input.getDisplayCharacterName(), input.getStartingClass());
        } else {
            return JS.message(HttpStatus.CONFLICT, "Character already exists.");
        }
        return JS.message(HttpStatus.OK, "OK");
    }

    @RequestMapping(path = "/delete-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> deleteCharacter(@Valid @RequestBody DeleteCharacterParameter input) {
        logger.info("Delete Character called with {}", input);
        String owner = input.getUsername();
        Optional<Character> localOpt = characterService.getCharacter(input.getDisplayCharacterName().toLowerCase());
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

            NotificationMessage notificationMessage = new NotificationMessage(owner, "A character was deleted");
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
        logger.info("Character Available called with {}", input);
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
        logger.info("Select Character called with {}", input);
        String characterName = input.getDisplayCharacterName().toLowerCase();
        Optional<Character> localOpt = characterService.getCharacter(characterName);
        if (!localOpt.isPresent()) {
            return JS.message(HttpStatus.NOT_FOUND,
                    "Character with name " + characterName + " was not found.");
        } else {
            if (!localOpt.get().getOwnerUsername().equals(input.getUsername())) {
                return JS.message(HttpStatus.FORBIDDEN, "You don't own that character.");
            }
            characterService.setSelectedCharacter(input.getUsername(), characterName);
            return JS.message(HttpStatus.OK, "Character selected");
        }
    }

    @RequestMapping(path = "/get-selected-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> getSelectedCharacter(@Valid @RequestBody GetSelectedCharacterParameter input) {
        logger.info("Get Selected Character called with {}", input);
        Optional<Character> selectedCharacter = characterService.getSelectedCharacter(input.getUsername());
        if (selectedCharacter.isPresent()) {
            return JS.message(HttpStatus.OK, selectedCharacter.get());
        } else {
            return JS.message(HttpStatus.NOT_FOUND, "No character selected");
        }
    }

    @RequestMapping(path = "/save-equipped-items", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> saveEquippedItems(@Valid @RequestBody SaveEquippedItemsParameter input) {
        logger.info("Save Equipped Items called with {}", input);

        Optional<Character> selectedCharacterOpt = characterService.getCharacter(input.getCharacterName());
        if (selectedCharacterOpt.isPresent()) {
            Character character = selectedCharacterOpt.get();

            for (EquippedItemParameter equippedItem : input.getEquippedItems()) {
                equippCharacter(character, equippedItem);
            }
            Character saveCharacter = characterService.saveCharacter(character);
            return JS.message(HttpStatus.OK, saveCharacter);
        } else {
            return JS.message(HttpStatus.NOT_FOUND, "No character with that id");
        }
    }

    private void equippCharacter(Character character, EquippedItemParameter equippedItem) {
        String armament = equippedItem.getArmament();
        String armor = equippedItem.getArmor();
        String itemSlot = equippedItem.getItemSlot();
        switch (itemSlot) {
            case "MAINHAND":
                character.setMainhandArmament(armament);
                break;
            case "OFFHAND":
                character.setOffHandArmament(armament);
                break;
            case "HEAD":
                character.setHeadItem(armor);
                break;
            case "BEARD":
                character.setBeardItem(armor);
                break;
            case "CHEST":
                character.setChestItem(armor);
                break;
            case "HANDS":
                character.setHandsItem(armor);
                break;
            case "LEGS":
                character.setLegsItem(armor);
                break;
            case "FEET":
                character.setFeetItem(armor);
                break;
            default:
                logger.error("{} DOES NOT EXIST AS A SLOT!", itemSlot);
                break;
        }
    }
}
