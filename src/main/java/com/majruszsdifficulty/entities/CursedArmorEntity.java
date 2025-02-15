package com.majruszsdifficulty.entities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.majruszsdifficulty.PacketHandler;
import com.majruszsdifficulty.Registries;
import com.mlib.Random;
import com.mlib.Utility;
import com.mlib.annotations.AutoInstance;
import com.mlib.blocks.BlockHelper;
import com.mlib.config.DoubleConfig;
import com.mlib.config.StringConfig;
import com.mlib.effects.ParticleHandler;
import com.mlib.effects.SoundHandler;
import com.mlib.entities.EntityHelper;
import com.mlib.gamemodifiers.Condition;
import com.mlib.gamemodifiers.ContextBase;
import com.mlib.gamemodifiers.GameModifier;
import com.mlib.gamemodifiers.contexts.*;
import com.mlib.math.Range;
import com.mlib.math.VectorHelper;
import com.mlib.network.NetworkMessage;
import com.mlib.text.TextHelper;
import com.mlib.time.Time;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class CursedArmorEntity extends Monster {
	public static final String GROUP_ID = "CursedArmor";
	public static final int ASSEMBLE_DURATION = Utility.secondsToTicks( 2.5 );
	SoundHandler assembleSound = null;
	int assembleTicksLeft = 0;

	static {
		GameModifier.addNewGroup( Registries.Modifiers.MOBS, GROUP_ID ).name( "CursedArmor" );
	}

	public static Supplier< EntityType< CursedArmorEntity > > createSupplier() {
		return ()->EntityType.Builder.of( CursedArmorEntity::new, MobCategory.MONSTER )
			.sized( 0.5f, 1.9f )
			.build( "cursed_armor" );
	}

	public static AttributeSupplier getAttributeMap() {
		return Mob.createMobAttributes()
			.add( Attributes.MAX_HEALTH, 30.0 )
			.add( Attributes.MOVEMENT_SPEED, 0.23 )
			.add( Attributes.ATTACK_DAMAGE, 3.0 )
			.add( Attributes.FOLLOW_RANGE, 35.0 )
			.add( Attributes.KNOCKBACK_RESISTANCE, 0.23 )
			.add( Attributes.ARMOR, 4.0 )
			.build();
	}

	public CursedArmorEntity( EntityType< ? extends CursedArmorEntity > type, Level world ) {
		super( type, world );
	}

	@Override
	public int getExperienceReward() {
		return Random.nextInt( 7 );
	}

	@Override
	public void tick() {
		super.tick();

		this.tryToPlaySfx();
		this.assembleTicksLeft = Math.max( this.assembleTicksLeft - 1, 0 );
		if( this.goalSelector.getAvailableGoals().isEmpty() ) {
			this.registerGoals();
		}
	}

	public void startAssembling( float yRot ) {
		this.assembleTicksLeft = ASSEMBLE_DURATION;
		this.setYRot( yRot );
		this.setYHeadRot( yRot );
		this.setYBodyRot( yRot );
		if( this.level instanceof ServerLevel ) {
			Time.nextTick( ()->PacketHandler.CHANNEL.send( PacketDistributor.DIMENSION.with( ()->this.level.dimension() ), new AssembleMessage( this, yRot ) ) );
		}
	}

	public void setAssembleSound( SoundEvent soundEvent ) {
		this.assembleSound = new SoundHandler( soundEvent, SoundSource.HOSTILE, SoundHandler.randomized( 1.2f ) );
	}

	public boolean isAssembling() {
		return this.assembleTicksLeft > 0;
	}

	public float getAssembleTime() {
		return ( float )Utility.ticksToSeconds( ASSEMBLE_DURATION - this.assembleTicksLeft );
	}

	@Override
	protected void registerGoals() {
		if( this.isAssembling() || this.tickCount <= ASSEMBLE_DURATION / 3 )
			return;

		this.goalSelector.addGoal( 2, new MeleeAttackGoal( this, 1.0D, false ) );
		this.goalSelector.addGoal( 3, new WaterAvoidingRandomStrollGoal( this, 1.0D ) );
		this.goalSelector.addGoal( 4, new LookAtPlayerGoal( this, Player.class, 8.0f ) );
		this.goalSelector.addGoal( 4, new RandomLookAroundGoal( this ) );
		this.targetSelector.addGoal( 2, new NearestAttackableTargetGoal<>( this, Player.class, true ) );
		this.targetSelector.addGoal( 3, new NearestAttackableTargetGoal<>( this, IronGolem.class, true ) );
	}

	private void tryToPlaySfx() {
		if( !( this.level instanceof ServerLevel level ) )
			return;

		if( this.assembleTicksLeft == ASSEMBLE_DURATION ) {
			SoundHandler.ENCHANT.play( level, this.position() );
		} else if( this.assembleTicksLeft == ASSEMBLE_DURATION - 75 ) {
			this.assembleSound.play( level, this.position() );
		}
	}

	@AutoInstance
	public static class Spawn extends GameModifier {
		static final String MAIN_TAG = "cursed_armor";
		static final String LOOT_TABLE_TAG = "loot";
		static final String SOUND_TAG = "sound";
		static final String CHANCE_TAG = "chance";
		static final Map< ResourceLocation, Data > DATA_MAP = new HashMap<>();
		final DoubleConfig dropChance = new DoubleConfig( 0.1, Range.CHANCE );
		final StringConfig name = new StringConfig( "Freshah" );

		public Spawn() {
			super( GROUP_ID );

			new OnLoot.Context( this::spawnCursedArmor )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( OnLoot.HAS_ORIGIN )
				.addCondition( data->BlockHelper.getBlockEntity( data.level, data.origin ) instanceof ChestBlockEntity )
				.addCondition( this::hasLootDefined )
				.addConfig( this.dropChance.name( "drop_chance" ).comment( "Chance for each equipped item to drop when killed." ) )
				.insertTo( this );

			new OnLootTableCustomLoad.Context( this::loadCursedArmorLoot )
				.addCondition( data->data.jsonObject.has( MAIN_TAG ) )
				.insertTo( this );

			new OnSpawned.Context( this::setCustomName )
				.name( "CustomName" )
				.comment( "Makes some Cursed Armors have a custom name." )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( new Condition.Chance<>( 0.025 ) )
				.addCondition( new OnSpawned.IsNotLoadedFromDisk<>() )
				.addCondition( data->data.target instanceof CursedArmorEntity )
				.addConfigs( this.name.name( "name" ) )
				.insertTo( this );

			new OnSpawned.ContextSafe( this::giveRandomArmor )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( new OnSpawned.IsNotLoadedFromDisk<>() )
				.addCondition( data->data.target instanceof CursedArmorEntity )
				.insertTo( this );

			new OnSpawned.Context( this::startAssembling )
				.addCondition( new OnSpawned.IsNotLoadedFromDisk<>() )
				.addCondition( data->data.target instanceof CursedArmorEntity cursedArmor && !cursedArmor.isAssembling() )
				.insertTo( this );

			new OnPreDamaged.Context( OnPreDamaged.CANCEL )
				.addCondition( data->data.target instanceof CursedArmorEntity cursedArmor && cursedArmor.isAssembling() )
				.insertTo( this );
		}

		private void spawnCursedArmor( OnLoot.Data data ) {
			CursedArmorEntity cursedArmor = EntityHelper.spawn( Registries.CURSED_ARMOR, data.level, this.getSpawnPosition( data ) );
			if( cursedArmor != null ) {
				float yRot = BlockHelper.getBlockState( data.level, data.origin )
					.getValue( ChestBlock.FACING )
					.toYRot();

				cursedArmor.startAssembling( yRot );
				this.equipSet( DATA_MAP.get( data.context.getQueriedLootTableId() ), cursedArmor, data.origin );
				if( data.entity instanceof ServerPlayer player ) {
					Time.nextTick( player::closeContainer );
				}
			}
		}

		private Vec3 getSpawnPosition( OnLoot.Data data ) {
			ServerLevel level = data.level;
			Vec3 origin = data.origin;
			Function< Float, Boolean > isAir = y->BlockHelper.getBlockState( level, origin.add( 0.0, y, 0.0 ) ).isAir();
			if( isAir.apply( 1.0f ) && isAir.apply( 2.0f ) ) {
				return origin.add( 0.0, 0.5, 0.0 );
			} else {
				Vec3i offset = BlockHelper.getBlockState( data.level, data.origin ).getValue( ChestBlock.FACING ).getNormal();
				return origin.add( offset.getX(), offset.getY(), offset.getZ() );
			}
		}

		private void loadCursedArmorLoot( OnLootTableCustomLoad.Data data ) {
			JsonObject object = data.jsonObject.get( MAIN_TAG ).getAsJsonObject();
			ResourceLocation sound = new ResourceLocation( object.has( SOUND_TAG ) ? object.get( SOUND_TAG )
				.getAsString() : "item.armor.equip_generic" );
			double chance = object.has( CHANCE_TAG ) ? object.get( CHANCE_TAG ).getAsDouble() : 1.0;
			JsonElement ids = object.get( LOOT_TABLE_TAG );
			if( ids.isJsonArray() ) {
				JsonArray array = ids.getAsJsonArray();
				array.forEach( id->DATA_MAP.put( new ResourceLocation( id.getAsString() ), new Data( data.table, sound, chance ) ) );
			} else {
				DATA_MAP.put( new ResourceLocation( ids.getAsString() ), new Data( data.table, sound, chance ) );
			}
		}

		private boolean hasLootDefined( OnLoot.Data data ) {
			ResourceLocation lootTableId = data.context.getQueriedLootTableId();

			return DATA_MAP.containsKey( lootTableId ) && Random.tryChance( DATA_MAP.get( lootTableId ).chance );
		}

		private void setCustomName( OnSpawned.Data data ) {
			data.target.setCustomName( this.name.asLiteral() );
		}

		private void giveRandomArmor( OnSpawned.Data data ) {
			CursedArmorEntity cursedArmor = ( CursedArmorEntity )data.target;
			if( cursedArmor.getArmorCoverPercentage() > 0.0f )
				return;

			this.equipSet( Random.nextRandom( DATA_MAP ).getValue(), cursedArmor, cursedArmor.position() );
		}

		private void equipSet( Data data, CursedArmorEntity cursedArmor, Vec3 position ) {
			LootContext lootContext = new LootContext.Builder( ( ServerLevel )cursedArmor.level )
				.withParameter( LootContextParams.ORIGIN, position )
				.withParameter( LootContextParams.THIS_ENTITY, cursedArmor )
				.create( LootContextParamSets.GIFT );

			data.lootTable.getRandomItems( lootContext )
				.forEach( cursedArmor::equipItemIfPossible );

			Arrays.stream( EquipmentSlot.values() )
				.forEach( slot->cursedArmor.setDropChance( slot, this.dropChance.asFloat() ) );

			cursedArmor.setAssembleSound( ForgeRegistries.SOUND_EVENTS.getValue( data.sound ) );
		}

		private void startAssembling( OnSpawned.Data data ) {
			CursedArmorEntity cursedArmor = ( CursedArmorEntity )data.target;
			cursedArmor.startAssembling( 0.0f );
		}

		private record Data( LootTable lootTable, ResourceLocation sound, double chance ) {}
	}

	@AutoInstance
	public static class Effects {
		public Effects() {
			this.createOnTick( this::spawnIdleParticles );

			this.createOnTick( this::spawnAssemblingParticles )
				.addCondition( data->data.entity instanceof CursedArmorEntity cursedArmor && cursedArmor.isAssembling() );
		}

		private ContextBase< OnEntityTick.Data > createOnTick( Consumer< OnEntityTick.Data > consumer ) {
			return new OnEntityTick.Context( consumer )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( new Condition.Cooldown< OnEntityTick.Data >( 0.2, Dist.DEDICATED_SERVER ).configurable( false ) )
				.addCondition( data->data.entity instanceof CursedArmorEntity );
		}

		private void spawnIdleParticles( OnEntityTick.Data data ) {
			CursedArmorEntity cursedArmor = ( CursedArmorEntity )data.entity;

			this.spawnParticles( data, new Vec3( 0.0, cursedArmor.getBbHeight() * 0.5, 0.0 ), 0.3, 1 );
		}

		private void spawnAssemblingParticles( OnEntityTick.Data data ) {
			this.spawnParticles( data, new Vec3( 0.0, 0.0, 0.0 ), 0.6, 5 );
		}

		private void spawnParticles( OnEntityTick.Data data, Vec3 emitterOffset, double offsetMultiplier, int particlesCount ) {
			CursedArmorEntity cursedArmor = ( CursedArmorEntity )data.entity;
			Vec3 position = cursedArmor.position().add( emitterOffset );
			Vec3 offset = VectorHelper.multiply( new Vec3( cursedArmor.getBbWidth(), cursedArmor.getBbHeight(), cursedArmor.getBbWidth() ), offsetMultiplier );
			ParticleHandler.ENCHANTED_GLYPH.spawn( data.level, position, particlesCount, ()->offset, ()->0.5f );
		}
	}

	@AutoInstance
	public static class TooltipUpdater {
		public TooltipUpdater() {
			new OnItemTooltip.Context( this::addSpawnInfo )
				.addCondition( data->data.itemStack.getItem().equals( Registries.CURSED_ARMOR_SPAWN_EGG.get() ) );
		}

		private void addSpawnInfo( OnItemTooltip.Data data ) {
			List< Component > components = data.tooltip;
			components.add( Component.translatable( "item.majruszsdifficulty.cursed_armor_spawn_egg.locations" )
				.withStyle( ChatFormatting.GRAY ) );

			Spawn.DATA_MAP.forEach( ( location, spawnData )->{
				String chance = TextHelper.percent( ( float )spawnData.chance );
				components.add( Component.literal( " - " )
					.append( Component.literal( location.toString() ) )
					.append( Component.literal( " " ) )
					.append( Component.literal( chance ).withStyle( ChatFormatting.DARK_GRAY ) )
					.withStyle( ChatFormatting.GRAY )
				);
			} );
		}
	}

	public static class AssembleMessage extends NetworkMessage {
		final int entityId;
		final float yRot;

		public AssembleMessage( Entity entity, float yRot ) {
			this.entityId = this.write( entity );
			this.yRot = this.write( yRot );
		}

		public AssembleMessage( FriendlyByteBuf buffer ) {
			this.entityId = this.readEntity( buffer );
			this.yRot = this.readFloat( buffer );
		}

		@Override
		@OnlyIn( Dist.CLIENT )
		public void receiveMessage( NetworkEvent.Context context ) {
			Level level = Minecraft.getInstance().level;
			if( level != null && level.getEntity( this.entityId ) instanceof CursedArmorEntity cursedArmor ) {
				cursedArmor.startAssembling( this.yRot );
			}
		}
	}
}
