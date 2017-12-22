package com.valhallagame.characterserviceserver.message;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterNameParameter {
	@NotNull
	private String characterName;
}
