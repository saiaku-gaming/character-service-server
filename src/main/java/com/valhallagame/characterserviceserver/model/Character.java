package com.valhallagame.characterserviceserver.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

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

	@Column(name = "head_item")
	private String headItem;

	@Column(name = "head_item_meta_data")
	private String headItemMetaData;

	@Column(name = "beard_item")
	private String beardItem;

	@Column(name = "beard_item_meta_data")
	private String beardItemMetaData;

	@Column(name = "chest_item")
	private String chestItem;

	@Column(name = "chest_item_meta_data")
	private String chestItemMetaData;

	@Column(name = "hands_item")
	private String handsItem;

	@Column(name = "hands_item_meta_data")
	private String handsItemMetaData;

	@Column(name = "legs_item")
	private String legsItem;

	@Column(name = "legs_item_meta_data")
	private String legsItemMetaData;

	@Column(name = "feet_item")
	private String feetItem;

	@Column(name = "feet_item_meta_data")
	private String feetItemMetaData;

	@Column(name = "mainhand_armament")
	private String mainhandArmament;

	@Column(name = "mainhand_armament_meta_data")
	private String mainhandArmamentMetaData;

	@Column(name = "off_hand_armament")
	private String offHandArmament;

	@Column(name = "off_hand_armament_meta_data")
	private String offHandArmamentMetaData;
}
