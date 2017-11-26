package com.valhallagame.characterserviceserver.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterNameAndOwnerUsernameParameter {
	private String characterName;
	private String ownerUsername;
}
