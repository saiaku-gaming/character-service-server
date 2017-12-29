package com.valhallagame.characterserviceserver.service;

import java.util.List;
import java.util.Optional;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.valhallagame.characterserviceserver.model.Character;
import com.valhallagame.characterserviceserver.repository.CharacterRepository;
import com.valhallagame.common.rabbitmq.NotificationMessage;
import com.valhallagame.common.rabbitmq.RabbitMQRouting;

@Service
public class CharacterService {
	@Autowired
	private CharacterRepository characterRepository;

	@Autowired
	private RabbitTemplate rabbitTemplate;

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
}
