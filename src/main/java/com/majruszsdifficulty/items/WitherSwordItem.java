package com.majruszsdifficulty.items;

import com.majruszsdifficulty.Registries;
import com.mlib.Utility;
import com.mlib.annotations.AutoInstance;
import com.mlib.gamemodifiers.GameModifier;
import com.mlib.gamemodifiers.configs.EffectConfig;
import com.mlib.gamemodifiers.contexts.OnDamaged;
import com.mlib.items.ItemHelper;
import com.mlib.mobeffects.MobEffectHelper;
import com.mlib.text.TextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

public class WitherSwordItem extends SwordItem {
	final static String TOOLTIP_TRANSLATION_KEY = "item.majruszsdifficulty.wither_sword.tooltip";

	public WitherSwordItem() {
		super( CustomItemTier.WITHER, 3, -2.4f, new Properties().rarity( Rarity.UNCOMMON ) );
	}

	@Override
	@OnlyIn( Dist.CLIENT )
	public void appendHoverText( ItemStack itemStack, @Nullable Level world, List< Component > tooltip, TooltipFlag flag ) {
		if( !flag.isAdvanced() )
			return;

		String amplifier = TextHelper.toRoman( Effect.WITHER.getAmplifier() + 1 );
		String duration = TextHelper.minPrecision( Utility.ticksToSeconds( Effect.WITHER.getDuration() ) );
		tooltip.add( Component.translatable( TOOLTIP_TRANSLATION_KEY, amplifier, duration ).withStyle( ChatFormatting.GRAY ) );
	}

	@AutoInstance
	public static class Effect extends GameModifier {
		static final EffectConfig WITHER = new EffectConfig( "", ()->MobEffects.WITHER, 1, 6.0 );

		public Effect() {
			super( Registries.Modifiers.DEFAULT, "WitherSwordEffect", "Wither Sword inflicts wither effect." );

			OnDamaged.Context onDamaged = new OnDamaged.Context( this::applyWither );
			onDamaged.addCondition( data->ItemHelper.hasInMainHand( data.attacker, WitherSwordItem.class ) )
				.addCondition( data->data.source.getDirectEntity() == data.attacker )
				.addConfig( WITHER );

			this.addContext( onDamaged );
		}

		private void applyWither( OnDamaged.Data data ) {
			MobEffectHelper.tryToApply( data.target, MobEffects.WITHER, WITHER.getDuration(), WITHER.getAmplifier() );
		}
	}

}
