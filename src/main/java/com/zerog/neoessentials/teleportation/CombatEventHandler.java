package com.zerog.neoessentials.teleportation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Basic combat event handler: marks players in combat on damage dealt or received.
 */
@EventBusSubscriber(modid = "neoessentials")
public class CombatEventHandler {
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatTracker.markInCombat(player);
        }

        if (event.getSource() != null && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            CombatTracker.markInCombat(attacker);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatTracker.clearCombat(player);
        }
    }
}
