package com.example.examplemod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

@Mod("starterwolf")
public class ExampleMod {

    // ==========================================
    // 1. THE CONFIGURATION BUILDER
    // ==========================================
    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class CommonConfig {
        public final ForgeConfigSpec.IntValue alphaMaxLevel;
        public final ForgeConfigSpec.IntValue warriorMaxLevel;
        public final ForgeConfigSpec.IntValue baseXpRequirement;
        public final ForgeConfigSpec.IntValue xpPerLevelMultiplier;

        public final ForgeConfigSpec.DoubleValue combatXpMultiplier;
        public final ForgeConfigSpec.DoubleValue miningXpMultiplier;
        public final ForgeConfigSpec.BooleanValue enableSharedPackXp;

        public final ForgeConfigSpec.BooleanValue enableDevour;
        public final ForgeConfigSpec.DoubleValue devourHealPercentage;

        public final ForgeConfigSpec.BooleanValue enablePlayerBuffs;
        public final ForgeConfigSpec.BooleanValue enablePackTactics;

        // NEW MOUNTING TOGGLE
        public final ForgeConfigSpec.BooleanValue enableMounting;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("Leveling Rules");
            alphaMaxLevel = builder.comment("Maximum level for Alpha Wolves").defineInRange("alphaMaxLevel", 20, 1, 100);
            warriorMaxLevel = builder.comment("Maximum level for Warrior Wolves").defineInRange("warriorMaxLevel", 10, 1, 100);
            baseXpRequirement = builder.comment("Base XP required for level 1").defineInRange("baseXpRequirement", 50, 1, 1000);
            xpPerLevelMultiplier = builder.comment("XP added to the requirement per level (Curve modifier)").defineInRange("xpPerLevelMultiplier", 25, 1, 1000);
            builder.pop();

            builder.push("XP Yields");
            combatXpMultiplier = builder.comment("Multiplier for combat XP (Target Max HP * Multiplier)").defineInRange("combatXpMultiplier", 2.0, 0.0, 100.0);
            miningXpMultiplier = builder.comment("Multiplier for mining XP (Ore XP * Multiplier)").defineInRange("miningXpMultiplier", 0.5, 0.0, 100.0);
            enableSharedPackXp = builder.comment("If true, all nearby pack wolves share XP on kills. If false, only the killer gets XP.").define("enableSharedPackXp", true);
            builder.pop();

            builder.push("Abilities");
            enableDevour = builder.comment("Enable the Devour healing ability for max level Alphas").define("enableDevour", true);
            devourHealPercentage = builder.comment("Percentage of target's max HP healed by Devour on kill").defineInRange("devourHealPercentage", 0.3, 0.0, 1.0);

            enablePlayerBuffs = builder.comment("Enable Wolf's Blessing (Mining Speed, Temp Hearts, and Regen for the player)").define("enablePlayerBuffs", true);
            enablePackTactics = builder.comment("Enable Pack Tactics (Bonus attack damage for wolves when in a pack)").define("enablePackTactics", true);

            enableMounting = builder.comment("Enable players to mount max-level Alpha Wolves with a Saddle").define("enableMounting", true);
            builder.pop();
        }
    }

    // ==========================================
    // 2. THE MOD CONSTRUCTOR
    // ==========================================
    public ExampleMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }
}