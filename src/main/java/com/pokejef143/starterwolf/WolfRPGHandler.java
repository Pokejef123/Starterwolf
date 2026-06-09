package com.pokejef143.starterwolf;

import com.example.examplemod.ExampleMod; // Ensure this import points to your new unified file
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "starterwolf", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WolfRPGHandler {

    private static final UUID HEALTH_MOD_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-56789abcdef0");
    private static final UUID ATTACK_MOD_UUID = UUID.fromString("0fedcba9-8765-4321-0987-654321fedcba");
    private static final UUID PACK_ATTACK_UUID = UUID.fromString("99887766-5544-3322-1100-aabbccddeeff");

    private static CompoundTag getProtectData(Player player) {
        CompoundTag base = player.getPersistentData();
        if (!base.contains("PlayerPersisted")) {
            base.put("PlayerPersisted", new CompoundTag());
        }
        return base.getCompound("PlayerPersisted");
    }

    private static boolean isStarterWolf(Wolf wolf) {
        return wolf.getPersistentData().getBoolean("IsStarterWolf");
    }

    private static boolean isWarriorWolf(Wolf wolf) {
        return wolf.getPersistentData().getBoolean("IsWarriorWolf");
    }

    private static boolean isRPGWolf(Wolf wolf) {
        if (wolf.level() != null && wolf.level().isClientSide()) {
            AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
            return health != null && health.getModifier(HEALTH_MOD_UUID) != null;
        }
        return isStarterWolf(wolf) || isWarriorWolf(wolf);
    }

    private static void enterGhostState(Wolf wolf, ServerPlayer owner, String deathMessage) {
        CompoundTag wolfData = wolf.getPersistentData();

        wolf.setHealth(1.0F);
        wolfData.putBoolean("IsGhost", true);
        wolfData.putBoolean("IsBerserk", false);
        wolf.setTarget(null);
        wolf.setOrderedToSit(false);

        wolf.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 999999, 0, false, false));

        int currentLevel = wolfData.getInt("RPGLevel");
        int penalizedLevel = Math.max(1, currentLevel - 1);
        wolfData.putInt("RPGLevel", penalizedLevel);
        wolfData.putInt("RPGXP", 0);
        applyStats(wolf, penalizedLevel);

        if (owner != null) {
            owner.sendSystemMessage(Component.literal(deathMessage));
            owner.removeEffect(MobEffects.DIG_SPEED);
            owner.removeEffect(MobEffects.REGENERATION);
            owner.setAbsorptionAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void onMobDamaged(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Wolf wolf && isRPGWolf(wolf)) {
            if (wolf.getPersistentData().getBoolean("IsGhost")) {
                event.setCanceled(true);
                return;
            }
            LivingEntity target = event.getEntity();
            CompoundTag targetData = target.getPersistentData();
            targetData.putString("DamagedByWolfUUID", wolf.getUUID().toString());
            float currentDmg = targetData.getFloat("WolfDamageTaken");
            targetData.putFloat("WolfDamageTaken", currentDmg + event.getAmount());
        }

        if (event.getEntity() instanceof Wolf wolf && isRPGWolf(wolf)) {
            CompoundTag wolfData = wolf.getPersistentData();
            if (wolfData.getBoolean("IsGhost")) {
                event.setCanceled(true);
                return;
            }

            if (event.getAmount() >= wolf.getHealth()) {
                if (wolfData.getBoolean("IsStarterWolf")) {
                    event.setCanceled(true);
                    enterGhostState(wolf, (ServerPlayer) wolf.getOwner(), "§c\u2620 Your Alpha has fallen and become a wandering spirit!");
                }
                else if (wolfData.getBoolean("IsWarriorWolf")) {
                    if (wolf.getOwner() instanceof ServerPlayer owner) {
                        String wName = wolf.hasCustomName() ? wolf.getCustomName().getString() : "A Warrior Wolf";
                        owner.sendSystemMessage(Component.literal("§4\u2620 " + wName + " has fallen permanently in battle!"));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        LivingEntity deadMob = event.getEntity();
        Entity killer = event.getSource().getEntity();
        ServerPlayer packOwner = null;

        if (killer instanceof Wolf killerWolf && killerWolf.isTame() && isRPGWolf(killerWolf)) {
            if (killerWolf.getOwner() instanceof ServerPlayer p) packOwner = p;

            if (ExampleMod.COMMON.enableDevour.get()) {
                CompoundTag wolfData = killerWolf.getPersistentData();
                int alphaMax = ExampleMod.COMMON.alphaMaxLevel.get();
                if (wolfData.getInt("RPGLevel") >= alphaMax && wolfData.getBoolean("IsBerserk") && !wolfData.getBoolean("IsGhost")) {
                    float healAmount = deadMob.getMaxHealth() * ExampleMod.COMMON.devourHealPercentage.get().floatValue();
                    killerWolf.heal(healAmount);
                    if (killerWolf.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, killerWolf.getX(), killerWolf.getY(), killerWolf.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        serverLevel.sendParticles(ParticleTypes.HEART, killerWolf.getX(), killerWolf.getY() + 1.0, killerWolf.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
                    }
                }
            }
        }
        else if (killer instanceof ServerPlayer p) {
            packOwner = p;
        }

        if (packOwner != null) {
            int expReward = Math.max(2, (int) (deadMob.getMaxHealth() * ExampleMod.COMMON.combatXpMultiplier.get()));
            final ServerPlayer finalOwner = packOwner;

            if (ExampleMod.COMMON.enableSharedPackXp.get()) {
                List<Wolf> pack = finalOwner.level().getEntitiesOfClass(Wolf.class, finalOwner.getBoundingBox().inflate(32.0D),
                        w -> w.isTame() && w.isOwnedBy(finalOwner) && isRPGWolf(w) && !w.getPersistentData().getBoolean("IsGhost"));
                for (Wolf wolf : pack) {
                    addXp(wolf, expReward);
                }
            } else if (killer instanceof Wolf killerWolf) {
                addXp(killerWolf, expReward);
            }
            return;
        }

        CompoundTag targetData = deadMob.getPersistentData();
        if (targetData.contains("DamagedByWolfUUID")) {
            try {
                UUID wolfUUID = UUID.fromString(targetData.getString("DamagedByWolfUUID"));
                if (deadMob.level() instanceof ServerLevel serverLevel) {
                    Entity entity = serverLevel.getEntity(wolfUUID);
                    if (entity instanceof Wolf wolf && isRPGWolf(wolf) && !wolf.getPersistentData().getBoolean("IsGhost")) {
                        float damageDealt = targetData.getFloat("WolfDamageTaken");
                        addXp(wolf, Math.max(2, (int)(damageDealt * ExampleMod.COMMON.combatXpMultiplier.get())));
                    }
                }
            } catch (Exception e) {}
        }
    }

    private static int getXpRequired(int currentLevel) {
        int maxAlpha = ExampleMod.COMMON.alphaMaxLevel.get();
        if (currentLevel >= maxAlpha) return Integer.MAX_VALUE;
        return ExampleMod.COMMON.baseXpRequirement.get() + (currentLevel * ExampleMod.COMMON.xpPerLevelMultiplier.get());
    }

    private static void addXp(Wolf wolf, int xpGained) {
        CompoundTag data = wolf.getPersistentData();
        if (data.getBoolean("IsGhost")) return;

        boolean isWarrior = data.getBoolean("IsWarriorWolf");
        int maxLevel = isWarrior ? ExampleMod.COMMON.warriorMaxLevel.get() : ExampleMod.COMMON.alphaMaxLevel.get();

        int currentLevel = data.getInt("RPGLevel");
        if (currentLevel == 0) currentLevel = 1;
        if (currentLevel >= maxLevel) return;

        if (isWarrior) xpGained = Math.max(1, xpGained / 2);

        int currentXp = data.getInt("RPGXP") + xpGained;
        int xpNeeded = getXpRequired(currentLevel);
        boolean leveledUp = false;

        while (currentXp >= xpNeeded && currentLevel < maxLevel) {
            currentXp -= xpNeeded;
            currentLevel++;
            xpNeeded = getXpRequired(currentLevel);
            leveledUp = true;
        }

        data.putInt("RPGLevel", currentLevel);
        data.putInt("RPGXP", currentXp);
        if (leveledUp) applyStats(wolf, currentLevel);
    }

    private static void applyStats(Wolf wolf, int level) {
        int bonusLevels = level - 1;
        boolean isWarrior = wolf.getPersistentData().getBoolean("IsWarriorWolf");
        double multiplier = isWarrior ? 0.5 : 1.0;

        AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(40.0);
            health.removeModifier(HEALTH_MOD_UUID);
            health.addPermanentModifier(new AttributeModifier(HEALTH_MOD_UUID, "RPGHealth", bonusLevels * 1.0 * multiplier, AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance attack = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(4.0);
            attack.removeModifier(ATTACK_MOD_UUID);
            attack.addPermanentModifier(new AttributeModifier(ATTACK_MOD_UUID, "RPGAttack", bonusLevels * 1.0 * multiplier, AttributeModifier.Operation.ADDITION));
        }

        if (wolf.getHealth() > wolf.getMaxHealth() || level == 1) {
            wolf.setHealth(wolf.getMaxHealth());
        }

        try {
            float newScale = 1.0f + (bonusLevels * 0.10f * (float)multiplier);
            ScaleData scaleData = ScaleTypes.BASE.getScaleData(wolf);
            scaleData.setTargetScale(newScale);
        } catch (Exception e) {}

        int alphaMax = ExampleMod.COMMON.alphaMaxLevel.get();
        if (level >= alphaMax && alphaMax > 1) wolf.setCollarColor(DyeColor.PURPLE);
        else if (level >= alphaMax * 0.75 && alphaMax > 1) wolf.setCollarColor(DyeColor.LIGHT_BLUE);
        else if (level >= alphaMax * 0.5 && alphaMax > 1) wolf.setCollarColor(DyeColor.YELLOW);
        else if (level >= alphaMax * 0.25 && alphaMax > 1) wolf.setCollarColor(DyeColor.GREEN);
        else wolf.setCollarColor(DyeColor.RED);

        if (wolf.getOwner() instanceof ServerPlayer owner && level > 1) {
            String wolfName = wolf.hasCustomName() ? wolf.getCustomName().getString() : (isWarrior ? "Your Warrior Wolf" : "Your Alpha");
            owner.sendSystemMessage(Component.literal("§a\u2b50 " + wolfName + " has reached Level " + level + "!"));
            if (level == alphaMax && !isWarrior && ExampleMod.COMMON.enableDevour.get()) {
                owner.sendSystemMessage(Component.literal("§5\u2620 " + wolfName + " has unlocked the DEVOUR ability!"));
            }
        }
    }

    @SubscribeEvent
    public static void onWolfInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() && event.getTarget() instanceof Wolf wolf) {
            Player player = event.getEntity();
            String wolfName = wolf.hasCustomName() ? wolf.getCustomName().getString() : "Your Wolf";
            CompoundTag data = wolf.getPersistentData();

            if (data.getBoolean("IsGhost") && player.getItemInHand(event.getHand()).getItem() != Items.STICK) {
                event.setCanceled(true); return;
            }

            if (player.getItemInHand(event.getHand()).getItem() == Items.STICK) {
                if (wolf.isTame() && wolf.isOwnedBy(player)) {

                    if (!isRPGWolf(wolf)) {
                        if (player.isCrouching()) {
                            wolf.getPersistentData().putBoolean("IsWarriorWolf", true);
                            wolf.getPersistentData().putInt("RPGLevel", 1);
                            applyStats(wolf, 1);
                            player.sendSystemMessage(Component.literal("§d\u2694 " + wolfName + " has been promoted to a Warrior Wolf!"));
                            event.setCanceled(true);
                        }
                        return;
                    }

                    boolean isWarrior = data.getBoolean("IsWarriorWolf");
                    int maxLevel = isWarrior ? ExampleMod.COMMON.warriorMaxLevel.get() : ExampleMod.COMMON.alphaMaxLevel.get();
                    int alphaMax = ExampleMod.COMMON.alphaMaxLevel.get();
                    int level = data.getInt("RPGLevel");
                    if (level == 0) level = 1;

                    int currentXp = data.getInt("RPGXP");
                    int xpNeeded = (level >= maxLevel) ? 0 : getXpRequired(level);
                    float currentHealth = wolf.getHealth();
                    float maxHealth = wolf.getMaxHealth();
                    boolean isBerserk = data.getBoolean("IsBerserk");

                    player.sendSystemMessage(Component.literal("§6=== " + wolfName + "'s Stats ==="));
                    player.sendSystemMessage(Component.literal("§eJob: " + (isWarrior ? "§bWarrior" : "§6Alpha")));
                    player.sendSystemMessage(Component.literal("§eLevel: §f" + level + (level >= maxLevel ? " (MAX)" : "")));
                    player.sendSystemMessage(Component.literal("§cHealth: §f" + String.format("%.1f", currentHealth) + " / " + String.format("%.1f", maxHealth)));
                    if (level < maxLevel) player.sendSystemMessage(Component.literal("§bEXP: §f" + currentXp + " / " + xpNeeded));

                    int devourHealNum = (int)(ExampleMod.COMMON.devourHealPercentage.get() * 100);

                    if (!ExampleMod.COMMON.enableDevour.get()) {
                        player.sendSystemMessage(Component.literal("§8Ability: Disabled by Server Config"));
                    } else if (isWarrior) {
                        player.sendSystemMessage(Component.literal("§8Ability: None (Alpha Only)"));
                    } else if (level < alphaMax) {
                        player.sendSystemMessage(Component.literal("§8Ability: Devour (Unlocks at Lv " + alphaMax + ")")
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§7[Devour]\n§8Heals the wolf for " + devourHealNum + "% of the target's max health on a killing blow.\nRequires Berserk Mode.")))));
                    } else {
                        player.sendSystemMessage(Component.literal("§5Ability: Devour (Active in Berserk Mode)")
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§d[Devour]\n§fHeals the wolf for " + devourHealNum + "% of the target's max health on a killing blow.\nRequires Berserk Mode.")))));
                    }

                    if (data.getBoolean("IsGhost")) player.sendSystemMessage(Component.literal("§7State: §b\u2620 WANDERING SPIRIT"));
                    else player.sendSystemMessage(Component.literal("§7State: " + (isBerserk ? "§4\u2694 BERSERK" : "§a\u262E Peaceful")));

                    event.setCanceled(true);
                }
            }
            else if (player.getItemInHand(event.getHand()).getItem() == Items.BONE && !player.isCrouching()) {
                if (wolf.isTame() && wolf.isOwnedBy(player) && isRPGWolf(wolf)) {
                    boolean isBerserk = data.getBoolean("IsBerserk");
                    data.putBoolean("IsBerserk", !isBerserk);

                    if (!isBerserk) player.sendSystemMessage(Component.literal("§4\u2694 " + wolfName + " has entered BERSERK MODE!"));
                    else { player.sendSystemMessage(Component.literal("§a\u2694 " + wolfName + " has calmed down.")); wolf.setTarget(null); }
                    event.setCanceled(true);
                }
            }
            // CONFIG TOGGLE: MOUNTING CHECK
            else if (player.getItemInHand(event.getHand()).getItem() == Items.SADDLE) {
                if (wolf.isTame() && wolf.isOwnedBy(player) && isStarterWolf(wolf)) {

                    if (!ExampleMod.COMMON.enableMounting.get()) {
                        player.sendSystemMessage(Component.literal("§c\u26a0 Mounting has been disabled by the server config!"));
                        event.setCanceled(true);
                        return;
                    }

                    int level = data.getInt("RPGLevel");
                    if (level >= ExampleMod.COMMON.alphaMaxLevel.get()) {
                        player.startRiding(wolf);
                        player.sendSystemMessage(Component.literal("§d\u265e You mount your companion!"));
                    } else player.sendSystemMessage(Component.literal("§c\u26a0 " + wolfName + " must be Level " + ExampleMod.COMMON.alphaMaxLevel.get() + " to carry a rider!"));
                    event.setCanceled(true);
                } else if (wolf.isTame() && wolf.isOwnedBy(player) && isWarriorWolf(wolf)) {
                    player.sendSystemMessage(Component.literal("§c\u26a0 Warrior Wolves are too small to carry a rider!"));
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWhistle(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player.isCrouching() && player.getItemInHand(event.getHand()).getItem() == Items.BONE) {

            CompoundTag playerData = getProtectData(player);
            ServerLevel playerLevel = (ServerLevel) player.level();
            UUID ownerUUID = player.getUUID();
            boolean handledInLoadedChunk = false;

            for (ServerLevel level : player.level().getServer().getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof Wolf wolf && isStarterWolf(wolf) && ownerUUID.equals(wolf.getOwnerUUID())) {

                        String myID = wolf.getPersistentData().getString("AlphaID");
                        String activeID = playerData.getString("ActiveAlphaID");
                        if (!activeID.isEmpty() && !myID.isEmpty() && !myID.equals(activeID)) continue;

                        if (level == playerLevel) {
                            if (wolf.getPersistentData().getBoolean("IsGhost")) {
                                wolf.teleportTo(player.getX(), player.getY(), player.getZ());
                                wolf.getPersistentData().putBoolean("IsGhost", false);
                                wolf.removeEffect(MobEffects.INVISIBILITY);
                                wolf.setHealth(wolf.getMaxHealth());
                                playerData.putLong("AlphaLastSyncTime", playerLevel.getGameTime());

                                level.sendParticles(ParticleTypes.LARGE_SMOKE, wolf.getX(), wolf.getY(), wolf.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
                                level.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                                player.sendSystemMessage(Component.literal("§d\u2728 Your Alpha has been restored to the physical realm!"));
                            } else {
                                wolf.teleportTo(player.getX(), player.getY(), player.getZ());
                                player.sendSystemMessage(Component.literal("§a\u2728 Your Alpha rushed to your side!"));
                            }
                            handledInLoadedChunk = true;
                        }
                        break;
                    }
                }
                if (handledInLoadedChunk) break;
            }

            if (!handledInLoadedChunk) {
                int savedLevel = playerData.getInt("AlphaSyncLevel");

                if (savedLevel > 0) {
                    String newID = UUID.randomUUID().toString();
                    playerData.putString("ActiveAlphaID", newID);

                    Wolf ghostWolf = EntityType.WOLF.create(playerLevel);
                    if (ghostWolf != null) {
                        ghostWolf.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                        ghostWolf.tame(player);

                        String savedName = playerData.getString("AlphaSyncName");
                        ghostWolf.setCustomName(Component.literal(savedName.isEmpty() ? player.getName().getString() + "'s Pal Wolf" : savedName));
                        ghostWolf.setCustomNameVisible(true);

                        CompoundTag ghostData = ghostWolf.getPersistentData();
                        ghostData.putBoolean("IsStarterWolf", true);
                        ghostData.putString("AlphaID", newID);

                        ghostData.putInt("RPGLevel", savedLevel);
                        ghostData.putInt("RPGXP", playerData.getInt("AlphaSyncXP"));

                        ghostWolf.setHealth(1.0F);
                        ghostData.putBoolean("IsGhost", true);
                        ghostData.putBoolean("IsBerserk", false);
                        ghostWolf.setTarget(null);
                        ghostWolf.setOrderedToSit(false);
                        ghostWolf.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 999999, 0, false, false));
                        applyStats(ghostWolf, savedLevel);

                        playerLevel.addFreshEntity(ghostWolf);

                        player.sendSystemMessage(Component.literal("§5\u2728 Your Alpha's spirit was summoned from afar!"));
                        playerData.putLong("AlphaLastSyncTime", playerLevel.getGameTime());
                    }
                } else {
                    player.sendSystemMessage(Component.literal("§c\u26a0 Could not locate your Alpha! Make sure it was spawned correctly."));
                }
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onWolfTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Wolf wolf) {

            if (wolf.isTame() && isRPGWolf(wolf)) {
                CompoundTag data = wolf.getPersistentData();
                long currentTime = wolf.level().getGameTime();

                if (isStarterWolf(wolf) && wolf.getOwner() instanceof ServerPlayer owner) {

                    CompoundTag ownerData = getProtectData(owner);
                    String myID = data.getString("AlphaID");
                    String activeID = ownerData.getString("ActiveAlphaID");

                    if (myID.isEmpty()) {
                        if (activeID.isEmpty()) {
                            myID = UUID.randomUUID().toString();
                            activeID = myID;
                            data.putString("AlphaID", myID);
                            ownerData.putString("ActiveAlphaID", activeID);
                        } else {
                            wolf.discard();
                            return;
                        }
                    } else if (!activeID.isEmpty() && !myID.equals(activeID)) {
                        wolf.discard();
                        return;
                    }

                    if (wolf.level() == owner.level() && wolf.distanceTo(owner) <= 64.0D && !data.getBoolean("IsGhost")) {
                        ownerData.putLong("AlphaLastSyncTime", currentTime);
                        ownerData.putInt("AlphaSyncLevel", data.getInt("RPGLevel"));
                        ownerData.putInt("AlphaSyncXP", data.getInt("RPGXP"));
                        ownerData.putString("AlphaSyncName", wolf.hasCustomName() ? wolf.getCustomName().getString() : "Pal Wolf");

                        ownerData.putInt("LastAlphaX", (int)wolf.getX());
                        ownerData.putInt("LastAlphaY", (int)wolf.getY());
                        ownerData.putInt("LastAlphaZ", (int)wolf.getZ());
                        ownerData.putString("LastAlphaDim", wolf.level().dimension().location().getPath());
                    }

                    if (wolf.level() == owner.level() && wolf.distanceTo(owner) <= 64.0D) {
                        data.putLong("LastTimeNearOwner", currentTime);
                    } else {
                        long lastTime = data.getLong("LastTimeNearOwner");
                        if (lastTime == 0) {
                            data.putLong("LastTimeNearOwner", currentTime);
                        } else if (currentTime - lastTime >= 24000) {
                            enterGhostState(wolf, owner, "§c\u2620 Your Alpha felt abandoned, perished in the wilderness, and returned as a wandering spirit!");
                            wolf.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                            data.putLong("LastTimeNearOwner", currentTime);
                            return;
                        }
                    }
                }

                if (data.getBoolean("IsGhost")) {
                    wolf.setTarget(null);
                    if (!wolf.hasEffect(MobEffects.INVISIBILITY)) wolf.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 999999, 0, false, false));
                    if (wolf.tickCount % 5 == 0 && wolf.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.SOUL, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
                    }
                    if (wolf.getY() < wolf.level().getMinBuildHeight() && wolf.getOwner() != null) {
                        wolf.teleportTo(wolf.getOwner().getX(), wolf.getOwner().getY(), wolf.getOwner().getZ());
                    }
                    return;
                }

                if (data.getBoolean("IsBerserk")) {
                    if (wolf.tickCount % 4 == 0) {
                        ((ServerLevel) wolf.level()).sendParticles(ParticleTypes.LARGE_SMOKE, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 2, 0.4, 0.2, 0.4, 0.01);
                        ((ServerLevel) wolf.level()).sendParticles(ParticleTypes.ANGRY_VILLAGER, wolf.getX(), wolf.getY() + 1.2, wolf.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
                    }
                    if (wolf.tickCount % 80 == 0) wolf.level().playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 1.0F, 0.7F);
                    if (wolf.tickCount % 20 == 0) {
                        if (wolf.getTarget() == null || !wolf.getTarget().isAlive()) {
                            AABB searchBox = wolf.getBoundingBox().inflate(32.0D);
                            List<Mob> targets = wolf.level().getEntitiesOfClass(Mob.class, searchBox, mob -> {
                                if (mob instanceof TamableAnimal pet && pet.isOwnedBy(wolf.getOwner())) return false;
                                if (mob instanceof Creeper) return false;
                                return true;
                            });
                            if (!targets.isEmpty()) {
                                targets.sort(Comparator.comparingDouble(wolf::distanceToSqr));
                                wolf.setTarget(targets.get(0));
                            }
                        }
                    }
                }
            }

            if (wolf.tickCount % 20 == 0 && wolf.isTame() && isRPGWolf(wolf) && !wolf.getPersistentData().getBoolean("IsGhost")) {
                int extraWolves = 0;

                if (wolf.getOwner() instanceof ServerPlayer player && wolf.distanceTo(player) <= 10.0D) {
                    List<Wolf> pack = player.level().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(10.0D), w -> w.isTame() && w.isOwnedBy(player));
                    if (pack.size() > 1) {
                        extraWolves = pack.size() - 1;
                    }

                    if (ExampleMod.COMMON.enablePlayerBuffs.get()) {
                        DyeColor collar = wolf.getCollarColor();
                        int hasteLevel = 0;
                        if (collar == DyeColor.PURPLE) hasteLevel = 3;
                        else if (collar == DyeColor.LIGHT_BLUE || collar == DyeColor.YELLOW) hasteLevel = 2;
                        else if (collar == DyeColor.GREEN) hasteLevel = 1;
                        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, hasteLevel, false, false, true));

                        if (extraWolves > 0) {
                            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
                            if (player.getAbsorptionAmount() < extraWolves * 1.0F) player.setAbsorptionAmount(extraWolves * 1.0F);
                        }
                    }
                }

                AttributeInstance attack = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attack != null) {
                    attack.removeModifier(PACK_ATTACK_UUID);
                    if (ExampleMod.COMMON.enablePackTactics.get() && extraWolves > 0) {
                        double packBonus = isWarriorWolf(wolf) ? 0.25 : 0.5;
                        attack.addTransientModifier(new AttributeModifier(PACK_ATTACK_UUID, "PackAttackBonus", extraWolves * packBonus, AttributeModifier.Operation.ADDITION));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!ExampleMod.COMMON.enablePlayerBuffs.get()) return;

        Player player = event.getEntity();
        List<Wolf> wolves = player.level().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(10.0D),
                w -> w.isTame() && w.isOwnedBy(player) && isRPGWolf(w) && !w.getPersistentData().getBoolean("IsGhost"));
        if (!wolves.isEmpty()) {
            float speedBoost = 0f;
            for (Wolf w : wolves) {
                DyeColor collar = w.getCollarColor();
                float b = (collar == DyeColor.PURPLE) ? 8.0f : (collar == DyeColor.LIGHT_BLUE || collar == DyeColor.YELLOW) ? 6.0f : 4.0f;
                if (b > speedBoost) speedBoost = b;
            }
            event.setNewSpeed(event.getNewSpeed() + speedBoost);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().level().isClientSide()) {
            int xp = event.getExpToDrop();
            if (xp > 0) {
                List<Wolf> wolves = event.getPlayer().level().getEntitiesOfClass(Wolf.class, event.getPlayer().getBoundingBox().inflate(10.0D),
                        w -> w.isTame() && w.isOwnedBy(event.getPlayer()) && isRPGWolf(w) && !w.getPersistentData().getBoolean("IsGhost"));
                for (Wolf wolf : wolves) addXp(wolf, Math.max(1, (int)(xp * ExampleMod.COMMON.miningXpMultiplier.get())));
            }
        }
    }

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("starterwolf").requires(s -> s.hasPermission(2))
                .then(Commands.literal("summon").executes(c -> {
                    if (c.getSource().getEntity() instanceof ServerPlayer p) {
                        Wolf w = EntityType.WOLF.create(p.level());

                        CompoundTag playerData = getProtectData(p);
                        String activeID = UUID.randomUUID().toString();
                        playerData.putString("ActiveAlphaID", activeID);

                        w.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                        w.tame(p);
                        w.setCustomName(Component.literal("Starter Pet"));

                        CompoundTag wolfData = w.getPersistentData();
                        wolfData.putBoolean("IsStarterWolf", true);
                        wolfData.putString("AlphaID", activeID);
                        wolfData.putInt("RPGLevel", 1);

                        applyStats(w, 1);
                        p.level().addFreshEntity(w);
                    }
                    return 1;
                }))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    int amount = IntegerArgumentType.getInteger(c, "amount");
                                    if (c.getSource().getEntity() instanceof ServerPlayer p) {
                                        List<Wolf> wolves = p.level().getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(32.0D), w -> w.isTame() && w.isOwnedBy(p) && isRPGWolf(w));
                                        if (wolves.isEmpty()) {
                                            c.getSource().sendFailure(Component.literal("§cNo nearby RPG Wolves found!"));
                                            return 0;
                                        }
                                        for (Wolf wolf : wolves) addXp(wolf, amount);
                                        c.getSource().sendSuccess(() -> Component.literal("§aAdded " + amount + " EXP to your pack!"), false);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("cleanup_alphas").executes(c -> {
                    CommandSourceStack source = c.getSource();
                    java.util.Map<UUID, Wolf> uniqueAlphas = new java.util.HashMap<>();
                    int removedCount = 0;
                    for (ServerLevel level : source.getServer().getAllLevels()) {
                        for (Entity entity : level.getAllEntities()) {
                            if (entity instanceof Wolf wolf && isStarterWolf(wolf) && wolf.getOwnerUUID() != null) {
                                UUID owner = wolf.getOwnerUUID();
                                if (uniqueAlphas.containsKey(owner)) {
                                    Wolf keptWolf = uniqueAlphas.get(owner);
                                    int keptLevel = keptWolf.getPersistentData().getInt("RPGLevel");
                                    int thisLevel = wolf.getPersistentData().getInt("RPGLevel");
                                    if (thisLevel > keptLevel) {
                                        keptWolf.discard();
                                        uniqueAlphas.put(owner, wolf);
                                        removedCount++;
                                    } else {
                                        wolf.discard();
                                        removedCount++;
                                    }
                                } else {
                                    uniqueAlphas.put(owner, wolf);
                                }
                            }
                        }
                    }
                    final String resultMsg = "§aCleanup complete! Purged " + removedCount + " duplicate Alphas from the server.";
                    source.sendSuccess(() -> Component.literal(resultMsg), true);
                    return 1;
                }))
        );
    }

    // ========================================================================
    // --- 8. CLIENT-ONLY UI RENDERER (CUSTOM HUD) ---
    // ========================================================================
    @Mod.EventBusSubscriber(modid = "starterwolf", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientUIOverlay {
        @SubscribeEvent
        public static void onRenderHUD(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                if (player == null) return;
                AABB searchBox = player.getBoundingBox().inflate(10.0D);
                List<Wolf> starterWolves = player.level().getEntitiesOfClass(Wolf.class, searchBox, w -> w.isTame() && w.isOwnedBy(player) && isRPGWolf(w) && !w.getPersistentData().getBoolean("IsGhost"));
                List<Wolf> fullPack = player.level().getEntitiesOfClass(Wolf.class, searchBox, w -> w.isTame() && w.isOwnedBy(player));

                if (!starterWolves.isEmpty()) {
                    GuiGraphics graphics = event.getGuiGraphics();
                    int x = 5;
                    int y = 5;

                    if (ExampleMod.COMMON.enablePlayerBuffs.get()) {
                        float speedBoost = 0f;
                        String buffTier = "Wood Tier";
                        for (Wolf w : starterWolves) {
                            DyeColor collar = w.getCollarColor();
                            float currentBoost = 2.0f;
                            String currentTier = "Wood Tier";
                            if (collar == DyeColor.PURPLE) { currentBoost = 8.0f; currentTier = "Diamond Tier"; }
                            else if (collar == DyeColor.LIGHT_BLUE || collar == DyeColor.YELLOW) { currentBoost = 6.0f; currentTier = "Iron Tier"; }
                            else if (collar == DyeColor.GREEN) { currentBoost = 4.0f; currentTier = "Stone Tier"; }
                            if (currentBoost > speedBoost) { speedBoost = currentBoost; buffTier = currentTier; }
                        }

                        graphics.drawString(mc.font, "§6\u26cf Wolf's Blessing", x, y, 0xFFFFFF, true);
                        graphics.drawString(mc.font, "§eMining Speed: +" + (int)speedBoost + " (" + buffTier + ")", x, y + 12, 0xFFFFFF, true);

                        if (fullPack.size() > 1) {
                            float packBonusHearts = (fullPack.size() - 1) * 0.5f;
                            graphics.drawString(mc.font, "§c\u2764 Alpha Pack: +" + String.format("%.1f", packBonusHearts) + " Temp Hearts", x, y + 24, 0xFFFFFF, true);
                        }
                    }

                    if (ExampleMod.COMMON.enablePackTactics.get() && fullPack.size() > 1) {
                        float packBonusDamage = (fullPack.size() - 1) * 0.5f;
                        int yOffset = ExampleMod.COMMON.enablePlayerBuffs.get() ? 36 : 12;

                        if (!ExampleMod.COMMON.enablePlayerBuffs.get()) {
                            graphics.drawString(mc.font, "§4\u2694 Pack Tactics Active", x, y, 0xFFFFFF, true);
                        }
                        graphics.drawString(mc.font, "§4\u2694 Damage Bonus: +" + String.format("%.1f", packBonusDamage) + " Alpha DMG", x, y + yOffset, 0xFFFFFF, true);
                    }
                }
            }
        }
    }
}