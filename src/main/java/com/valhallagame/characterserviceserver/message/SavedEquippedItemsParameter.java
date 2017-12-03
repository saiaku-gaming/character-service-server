package com.valhallagame.characterserviceserver.message;

import java.util.List;

import lombok.Data;

@Data
public class SavedEquippedItemsParameter {
	String characterName;
	List<EquippedItem> equippedItems;
	
	@Data
	public static class EquippedItem {
		String itemSlot;
		String armament;
		String armor;
	}
}
