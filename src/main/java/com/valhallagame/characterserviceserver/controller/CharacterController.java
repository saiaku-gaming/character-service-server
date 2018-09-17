package com.valhallagame.characterserviceserver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.valhallagame.characterserviceclient.message.*;
import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.service.CharacterService;
import com.valhallagame.common.JS;
import com.valhallagame.common.RestResponse;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;
import com.valhallagame.traitserviceclient.TraitServiceClient;
import com.valhallagame.traitserviceclient.message.TraitType;
import com.valhallagame.traitserviceclient.message.UnlockTraitParameter;
import com.valhallagame.wardrobeserviceclient.WardrobeServiceClient;
import com.valhallagame.wardrobeserviceclient.message.AddWardrobeItemParameter;
import com.valhallagame.wardrobeserviceclient.message.WardrobeItem;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    @Autowired
    private WardrobeServiceClient wardrobeServiceClient;

    @Autowired
    private TraitServiceClient traitServiceClient;

    @RequestMapping(path = "/get-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> getCharacterWithoutOwnerValidation(
            @Valid @RequestBody GetCharacterParameter input) {

        Optional<Character> optCharacter = characterService.getCharacter(input.getCharacterName());
        if (!optCharacter.isPresent()) {
            return JS.message(HttpStatus.NOT_FOUND, "No character with that character name was found!");
        }
        return JS.message(HttpStatus.OK, optCharacter.get());
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

    @RequestMapping(path = "/create-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> createCharacter(@Valid @RequestBody CreateCharacterParameter input)
            throws IOException {

        String displayCharacterName = input.getDisplayCharacterName();
        if (displayCharacterName.contains("#")) {
            return JS.message(HttpStatus.BAD_REQUEST, "# is not allowed in character name");
        }
        Optional<Character> localOpt = characterService.getCharacter(displayCharacterName.toLowerCase());
        if (!localOpt.isPresent()) {
            Character character = new Character();
            character.setOwnerUsername(input.getUsername());
            character.setDisplayCharacterName(displayCharacterName);

            String characterName = input.getDisplayCharacterName().toLowerCase();
            character.setCharacterName(characterName);

            if(!AllowedClasses.has(input.getStartingClass())){
                return JS.message(HttpStatus.BAD_REQUEST, input.getStartingClass() + " is not a starting class");
            }

            switch (AllowedClasses.get(input.getStartingClass())) {
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

            character = characterService.saveCharacter(character);
            characterService.setSelectedCharacter(character.getOwnerUsername(), character.getCharacterName());
        } else {
            return JS.message(HttpStatus.CONFLICT, "Character already exists.");
        }
        return JS.message(HttpStatus.OK, "OK");
    }

    private void equipAsDebug(Character character, String characterName) {
        character.setChestItem(WardrobeItem.MAIL_ARMOR.name());
        character.setMainhandArmament(WardrobeItem.SWORD.name());
        character.setOffHandArmament(WardrobeItem.MEDIUM_SHIELD.name());

        Arrays.stream(WardrobeItem.values())
                .filter(wardrobeItem -> !wardrobeItem.equals(WardrobeItem.NAKED))
                .forEach(wardrobeItem -> {
                    try {
                        addWardrobeItem(characterName, wardrobeItem);
                    } catch (IOException e) {
                        logger.error("failed to populate debug character with " + wardrobeItem, e);
                    }
                });

        Arrays.stream(TraitType.values()).forEach(val -> {
            try {
                addTrait(characterName, val);
            } catch (IOException e) {
                logger.error("failed to populate debug character with " + val, e);
            }
        });
    }

    private void equipAsWarrior(Character character, String characterName) throws IOException {
        character.setChestItem(WardrobeItem.MAIL_ARMOR.name());
        character.setMainhandArmament(WardrobeItem.SWORD.name());
        character.setOffHandArmament(WardrobeItem.MEDIUM_SHIELD.name());

        addWardrobeItem(characterName, WardrobeItem.MAIL_ARMOR);
        addWardrobeItem(characterName, WardrobeItem.SWORD);
        addWardrobeItem(characterName, WardrobeItem.MEDIUM_SHIELD);

        addTrait(characterName, TraitType.SHIELD_BASH);
        addTrait(characterName, TraitType.RECOVER);
        addTrait(characterName, TraitType.TAUNT);
        addTrait(characterName, TraitType.KICK);
    }

    private void equipAsShaman(Character character, String characterName) throws IOException {
        character.setChestItem(WardrobeItem.CLOTH_ARMOR.name());
        character.setMainhandArmament(WardrobeItem.SWORD.name());
        character.setOffHandArmament("NONE");

        addWardrobeItem(characterName, WardrobeItem.CLOTH_ARMOR);
        addWardrobeItem(characterName, WardrobeItem.SWORD);

        addTrait(characterName, TraitType.FROST_BLAST);
        addTrait(characterName, TraitType.SEIDHRING);
        addTrait(characterName, TraitType.PETRIFY);
        addTrait(characterName, TraitType.FRIGGS_INTERVENTION);
    }

    private void equipAsRanger(Character character, String characterName) throws IOException {
        character.setChestItem(WardrobeItem.LEATHER_ARMOR.name());
        character.setMainhandArmament(WardrobeItem.LONGSWORD.name());
        character.setOffHandArmament("NONE");

        addWardrobeItem(characterName, WardrobeItem.LEATHER_ARMOR);
        addWardrobeItem(characterName, WardrobeItem.LONGSWORD);

        addTrait(characterName, TraitType.SHIELD_BREAKER);
        addTrait(characterName, TraitType.HEMORRHAGE);
        addTrait(characterName, TraitType.DODGE);
        addTrait(characterName, TraitType.PARRY);
    }

    private void addWardrobeItem(String characterName, WardrobeItem wardrobeItem) throws IOException {
        wardrobeServiceClient.addWardrobeItem(new AddWardrobeItemParameter(characterName, wardrobeItem));
    }

    private void addTrait(String characterName, TraitType traitType) throws IOException {
        traitServiceClient.unlockTrait(new UnlockTraitParameter(characterName, traitType));
    }

    @RequestMapping(path = "/delete-character", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JsonNode> deleteCharacter(@Valid @RequestBody DeleteCharacterParameter input) {
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
                if (!equippCharacter(character, items, equippedItem)) {
                    return JS.message(HttpStatus.BAD_REQUEST,
                            "Your character %s tried to equip %s but can only equip items: %s", character, equippedItem,
                            items);
                }
            }
            Character saveCharacter = characterService.saveCharacter(character);
            return JS.message(HttpStatus.OK, saveCharacter);
        } else {
            return JS.message(HttpStatus.NOT_FOUND, "No character with that id");
        }
    }

    private boolean equippCharacter(Character character, List<String> items, EquippedItemParameter equippedItem) {
        String armament = equippedItem.getArmament();
        String armor = equippedItem.getArmor();
        String itemSlot = equippedItem.getItemSlot();
        switch (itemSlot) {
            case "MAINHAND":
                if (items.contains(armament)) {
                    character.setMainhandArmament(armament);
                    return true;
                } else {
                    logger.error("wardrobe does not have armament {} in {} ", armament, items);
                    return false;
                }
            case "OFFHAND":
                if (items.contains(armament)) {
                    character.setOffHandArmament(armament);
                    return true;
                } else {
                    logger.error("wardrobe does not have armament {} in {}", armament, items);
                    return false;
                }
            case "CHEST":
                if (items.contains(armor)) {
                    character.setChestItem(armor);
                    return true;
                } else {
                    logger.error("wardrobe does not have armor {} in {}", armor, items);
                    return false;
                }
            default:
                logger.error("{} DOES NOT EXIST AS A SLOT!", itemSlot);
                return false;
        }
    }

}
