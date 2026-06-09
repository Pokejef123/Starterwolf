package com.pokejef143.starterwolf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "starterwolf", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerJoinEventHandler {

    private static final String PLAYER_WOLF_TAG = "HasReceivedStarterWolf";
    private static final String ENTITY_WOLF_TAG = "IsStarterWolf";

    // --- FORGE'S SECRET VAULT ---
    // Forces the game to natively protect our data across deaths and dimensions
    private static CompoundTag getProtectData(Player player) {
        CompoundTag base = player.getPersistentData();
        if (!base.contains("PlayerPersisted")) {
            base.put("PlayerPersisted", new CompoundTag());
        }
        return base.getCompound("PlayerPersisted");
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {

            // Route all player data into the Vault
            CompoundTag playerData = getProtectData(player);

            if (!playerData.getBoolean(PLAYER_WOLF_TAG)) {
                List<Wolf> existingWolves = serverLevel.getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(32.0D),
                        w -> w.isTame() && w.isOwnedBy(player) && w.getPersistentData().getBoolean(ENTITY_WOLF_TAG));

                if (!existingWolves.isEmpty()) {
                    playerData.putBoolean(PLAYER_WOLF_TAG, true);
                } else {
                    Wolf starterWolf = EntityType.WOLF.create(serverLevel);
                    if (starterWolf != null) {
                        starterWolf.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                        starterWolf.tame(player);

                        String playerName = player.getName().getString();
                        starterWolf.setCustomName(Component.literal(playerName + "'s Pal Wolf"));
                        starterWolf.setCustomNameVisible(true);

                        String activeID = UUID.randomUUID().toString();
                        playerData.putString("ActiveAlphaID", activeID);

                        CompoundTag wolfData = starterWolf.getPersistentData();
                        wolfData.putBoolean(ENTITY_WOLF_TAG, true);
                        wolfData.putString("AlphaID", activeID);
                        wolfData.putInt("RPGLevel", 1);
                        wolfData.putInt("RPGXP", 0);

                        starterWolf.setCollarColor(DyeColor.RED);
                        serverLevel.addFreshEntity(starterWolf);

                        playerData.putBoolean(PLAYER_WOLF_TAG, true);
                        playerData.putLong("AlphaLastSyncTime", serverLevel.getGameTime());

                        player.sendSystemMessage(Component.literal("§a\u2728 A Pal Wolf has joined your adventure!"));
                    }
                }
            }

            List<Wolf> allLoadedAlphas = serverLevel.getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(128.0D),
                    w -> w.isTame() && w.isOwnedBy(player) && w.getPersistentData().getBoolean(ENTITY_WOLF_TAG));

            if (allLoadedAlphas.size() > 1) {
                allLoadedAlphas.sort((w1, w2) -> {
                    int lvl1 = w1.getPersistentData().getInt("RPGLevel");
                    int lvl2 = w2.getPersistentData().getInt("RPGLevel");
                    return Integer.compare(lvl2, lvl1);
                });

                int removedCount = 0;
                for (int i = 1; i < allLoadedAlphas.size(); i++) {
                    allLoadedAlphas.get(i).discard();
                    removedCount++;
                }
                player.sendSystemMessage(Component.literal("§e\u2699 Auto-Cleanup: Removed " + removedCount + " duplicate Alpha(s)."));
            }
        }
    }
}