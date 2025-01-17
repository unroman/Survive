package com.stereowalker.survive.needs;

import java.util.List;
import java.util.Random;

import com.stereowalker.survive.Survive;
import com.stereowalker.survive.compat.PamsHarvestcraftCompat;
import com.stereowalker.survive.config.ServerConfig;
import com.stereowalker.survive.core.SurviveEntityStats;
import com.stereowalker.survive.json.ConsummableJsonHolder;
import com.stereowalker.survive.world.DataMaps;
import com.stereowalker.survive.world.effect.SMobEffects;
import com.stereowalker.survive.world.item.SItems;
import com.stereowalker.unionlib.util.RegistryHelper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class WaterData extends SurviveData {
	private int waterLevel = 20;
	private float waterHydrationLevel;
	private float waterExhaustionLevel;
	private int waterTimer;
	@SuppressWarnings("unused")
	private int prevWaterLevel = 20;
	private int uncleanConsumption = 0;

	public WaterData() {
		this.waterHydrationLevel = 5.0F;
	}

	/**
	 * Add water stats.
	 */
	public void drink(int waterLevelIn, float waterHydrationModifier, boolean isUnclean) {
		this.waterLevel = Math.min(waterLevelIn + this.waterLevel, ServerConfig.stomachCapacity());
		this.waterHydrationLevel = Math.min(this.waterHydrationLevel + (float)waterLevelIn * waterHydrationModifier * 2.0F, (float)this.waterLevel);
		if (isUnclean) {
			uncleanConsumption++;
		}
	}

	public void drink(Item pItem, ItemStack pStack, LivingEntity entity) {
		if (entity != null && entity instanceof ServerPlayer) {
			ServerPlayer player = (ServerPlayer)entity;
			if ((pItem == Items.POTION || pItem == SItems.FILLED_CANTEEN) && DataMaps.Server.potionDrink.containsKey(PotionUtils.getPotion(pStack).getRegistryName())) {
				ConsummableJsonHolder drinkData = DataMaps.Server.potionDrink.get(PotionUtils.getPotion(pStack).getRegistryName());
				drink(drinkData.getThirstAmount(), drinkData.getHydrationAmount(), applyThirst(entity, drinkData.getThirstChance()));
				if (drinkData.isHeated())entity.addEffect(new MobEffectInstance(SMobEffects.HEATED, 30*20));
				if (drinkData.isChilled())entity.addEffect(new MobEffectInstance(SMobEffects.CHILLED, 30*20));
				if (drinkData.isEnergizing())entity.addEffect(new MobEffectInstance(SMobEffects.ENERGIZED, 60*20*5));
			}
			else if (DataMaps.Server.consummableItem.containsKey(pItem.getRegistryName())) {
				ConsummableJsonHolder drinkData = DataMaps.Server.consummableItem.get(pItem.getRegistryName());
				drink(drinkData.getThirstAmount(), drinkData.getHydrationAmount(), applyThirst(entity, drinkData.getThirstChance()));
				if (drinkData.isHeated())entity.addEffect(new MobEffectInstance(SMobEffects.HEATED, 30*20));
				if (drinkData.isChilled())entity.addEffect(new MobEffectInstance(SMobEffects.CHILLED, 30*20));
				if (drinkData.isEnergizing())entity.addEffect(new MobEffectInstance(SMobEffects.ENERGIZED, 60*20*5));
			}

			save(player);
		}

	}

	/**
	 * Handles the water game logic.
	 */
	//TODO: Figure out something else that hydration can do apart from healing
	public void tick(Player player) {
		if (Survive.THIRST_CONFIG.idle_thirst_tick_rate > -1) {
			if (player.tickCount%Survive.THIRST_CONFIG.idle_thirst_tick_rate == Survive.THIRST_CONFIG.idle_thirst_tick_rate-1) {
				addExhaustion(player, Survive.THIRST_CONFIG.idle_thirst_exhaustion);
			}
		}
		
		Difficulty difficulty = player.level.getDifficulty();
		this.prevWaterLevel = this.waterLevel;
		
		if (!player.hasEffect(SMobEffects.UPSET_STOMACH) || player.getEffect(SMobEffects.UPSET_STOMACH).getDuration() <= 10)
			if (this.waterLevel > 36)
				player.addEffect(new MobEffectInstance(SMobEffects.UPSET_STOMACH, 300, 4));
			else if (this.waterLevel > 32)
				player.addEffect(new MobEffectInstance(SMobEffects.UPSET_STOMACH, 300, 3));
			else if (this.waterLevel > 28)
				player.addEffect(new MobEffectInstance(SMobEffects.UPSET_STOMACH, 300, 2));
			else if (this.waterLevel > 24)
				player.addEffect(new MobEffectInstance(SMobEffects.UPSET_STOMACH, 300, 1));
			else if (this.waterLevel > 20)
				player.addEffect(new MobEffectInstance(SMobEffects.UPSET_STOMACH, 300, 0));
		
		if (this.waterExhaustionLevel > 4.0F) {
			this.waterExhaustionLevel -= 4.0F;
			if (this.waterHydrationLevel > 0.0F) {
				this.waterHydrationLevel = Math.max(this.waterHydrationLevel - 1.0F, 0.0F);
			} else if (difficulty != Difficulty.PEACEFUL) {
				this.waterLevel = Math.max(this.waterLevel - 1, 0);
			}
		}

		boolean flag = player.level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
		if (this.waterLevel >= 40) {
			++this.waterTimer;
			if (this.waterTimer >= 10) {
				if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
					player.hurt(SDamageSource.OVERHYDRATE, 1.0F);
				}

				this.waterTimer = 0;
			}
		} else if (flag && this.waterHydrationLevel > 0.0F && player.isHurt() && this.waterLevel >= 20) {
			++this.waterTimer;
			if (this.waterTimer >= 10) {
				float f = Math.min(this.waterHydrationLevel, 6.0F);
				//player.heal(f / 12.0F);
				this.addExhaustion(f);
				this.waterTimer = 0;
			}
		} else if (flag && this.waterLevel >= 18 && player.isHurt()) {
			++this.waterTimer;
			if (this.waterTimer >= 80) {
				//player.heal(0.5F);
				this.addExhaustion(6.0F);
				this.waterTimer = 0;
			}
		} else if (this.waterLevel <= 0) {
			++this.waterTimer;
			if (this.waterTimer >= 80) {
				if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
					player.hurt(SDamageSource.DEHYDRATE, 1.0F);
				}

				this.waterTimer = 0;
			}
		} else {
			this.waterTimer = 0;
		}

		if (Survive.WELLBEING_CONFIG.enabled) {
			WellbeingData wellbeing = SurviveEntityStats.getWellbeingStats(player);
			//Essentially causes the player to get ill when drinking bad water
			if (uncleanConsumption >= 3) {
				wellbeing.setTimer(2400, 6000);
				uncleanConsumption = 0;
				wellbeing.save(player);
			}
		}

	}

	/**
	 * Reads the water data for the player.
	 */
	public void read(CompoundTag compound) {
		if (compound.contains("waterLevel", 99)) {
			this.waterLevel = compound.getInt("waterLevel");
			this.waterTimer = compound.getInt("waterTickTimer");
			this.waterHydrationLevel = compound.getFloat("waterHydrationLevel");
			this.waterExhaustionLevel = compound.getFloat("waterExhaustionLevel");
			this.uncleanConsumption = compound.getInt("uncleanComsumption");
		}

	}

	/**
	 * Writes the water data for the player.
	 */
	public void write(CompoundTag compound) {
		compound.putInt("waterLevel", this.waterLevel);
		compound.putInt("waterTickTimer", this.waterTimer);
		compound.putFloat("waterHydrationLevel", this.waterHydrationLevel);
		compound.putFloat("waterExhaustionLevel", this.waterExhaustionLevel);
		compound.putInt("uncleanComsumption", this.uncleanConsumption);
	}

	/**
	 * Get the player's water level.
	 */
	public int getWaterLevel() {
		return this.waterLevel;
	}

	/**
	 * Get whether the player must drink water.
	 */
	public boolean needWater() {
		return this.waterLevel < ServerConfig.stomachCapacity();
	}

	/**
	 * adds input to waterExhaustionLevel to a max of 40
	 */
	private void addExhaustion(float exhaustion) {
		this.waterExhaustionLevel = Math.min(this.waterExhaustionLevel + exhaustion, 40.0F);
	}

	/**
	 * increases exhaustion level by supplied amount
	 */
	public void addExhaustion(Player player, float exhaustion) {
		if (!player.getAbilities().invulnerable) {
			if (!player.level.isClientSide) {
				this.addExhaustion(exhaustion);
				save(player);
			}

		}
	}

	/**
	 * Get the player's water hydration level.
	 */
	public float getHydrationLevel() {
		return this.waterHydrationLevel;
	}

	public void setWaterLevel(int waterLevelIn) {
		this.waterLevel = waterLevelIn;
	}

	@OnlyIn(Dist.CLIENT)
	public void setWaterHydrationLevel(float waterHydrationLevelIn) {
		this.waterHydrationLevel = waterHydrationLevelIn;
	}

	@Override
	public void save(LivingEntity player) {
		SurviveEntityStats.setWaterStats(player, this);
	}

	@Override
	public boolean shouldTick() {
		return Survive.THIRST_CONFIG.enabled;
	}

	/////-----------EVENTS-----------/////

	public static float getHydrationFromList(ItemStack stack, List<String> list) {
		for (String containerList : list) {
			String[] container = containerList.split(",");
			if (RegistryHelper.matchesRegisteredEntry(container[0], stack.getItem())) {
				return Float.parseFloat(container[2]);
			}
		}
		return 0;
	}

	public static float getHydrationFill(ItemStack stack) {
		float amount = 0.0f;
		amount+=getHydrationFromList(stack, PamsHarvestcraftCompat.normalPamHCDrinks());
		amount+=getHydrationFromList(stack, PamsHarvestcraftCompat.uncleanPamHCDrinks());
		amount+=getHydrationFromList(stack, PamsHarvestcraftCompat.chilledPamHCDrinks());
		amount+=getHydrationFromList(stack, PamsHarvestcraftCompat.heatedPamHCDrinks());
		amount+=getHydrationFromList(stack, PamsHarvestcraftCompat.stimulatingPamHCDrinks());

		if (stack.getItem() == Items.POTION && PotionUtils.getPotion(stack) == Potions.WATER) {
			amount+=2.0F;}
		if (stack.getItem() == Items.POTION && PotionUtils.getPotion(stack) != Potions.WATER && PotionUtils.getPotion(stack) != Potions.EMPTY)
			amount+=1.0F;
		return amount;
	}

	public static boolean applyThirst(LivingEntity entity, float probabiltiy) {
		if (probabiltiy > 0) {
			Random rand = new Random();
			if (rand.nextFloat() < probabiltiy) {
				entity.addEffect(new MobEffectInstance(SMobEffects.THIRST, 30*20));
				return true;
			}
		}
		return false;
	}
}
