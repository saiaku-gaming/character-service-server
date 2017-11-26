package com.valhallagame.characterserviceserver.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "character")
public class Character {

	@Id
	@Column(unique = true, name = "character_name")
	private String characterName;

	@Column(name = "owner")
	private String ownerUsername;

	@Column(unique = true, name = "display_character_name")
	private String displayCharacterName;

	@Column(name = "chest_item")
	private String chestItem;

	@Column(name = "mainhand_armament")
	private String mainhandArmament;

	@Column(name = "off_hand_armament")
	private String offHandArmament;

}
