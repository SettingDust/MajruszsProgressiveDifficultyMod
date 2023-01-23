package com.majruszsdifficulty.items;

import com.majruszsdifficulty.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class EndShardItem extends Item {
	public EndShardItem() {
		super( new Properties().tab( Registries.ITEM_GROUP ).rarity( Rarity.UNCOMMON ) );
	}
}
