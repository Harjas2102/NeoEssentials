package com.zerog.neoessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zerog.neoessentials.kits.Kit;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the /delkit command for deleting kits.
 */
public class DelKitCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DelKitCommand.class);
    
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_KITS = 
        (context, builder) -> suggestKits(context, builder);
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Check if kit module is enabled
        if (!com.zerog.neoessentials.config.ConfigManager.isKitSystemEnabled()) {
            return; // Don't register kit commands if module is disabled
        }
        
        registerDelKitCommand(dispatcher, "delkit");
        registerDelKitCommand(dispatcher, "deletekit");
        registerDelKitCommand(dispatcher, "removekit");
        registerDelKitCommand(dispatcher, "rkit");
    }
    
    private static void registerDelKitCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .requires(source -> {
                if (source.getEntity() instanceof ServerPlayer player) {
                    return PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.delete");
                }
                return source.hasPermission(4); // Console/OP fallback
            })
            .then(Commands.argument("kitname", StringArgumentType.word())
                .suggests(SUGGEST_KITS)
                .executes(DelKitCommand::deleteKit)
                .then(Commands.literal("confirm")
                    .executes(DelKitCommand::confirmDeleteKit)
                )
            )
        );
    }
    
    private static CompletableFuture<Suggestions> suggestKits(CommandContext<CommandSourceStack> context, 
                                                            SuggestionsBuilder builder) {
        try {
            KitManager kitManager = KitManager.getInstance();
            Set<String> kitNames = kitManager.getAllKitNames();
            Set<String> normalizedKitNames = kitNames.stream()
                .map(Kit::normalizeKitName)
                .collect(java.util.stream.Collectors.toSet());
            return SharedSuggestionProvider.suggest(normalizedKitNames, builder);
        } catch (Exception e) {
            LOGGER.warn("Error suggesting kits for delkit command: {}", e.getMessage());
            return builder.buildFuture();
        }
    }
    
    private static int deleteKit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String kitName = StringArgumentType.getString(context, "kitname");
        String normalizedKitName = Kit.normalizeKitName(kitName);
        
        try {
            KitManager kitManager = KitManager.getInstance();
            Kit kit = kitManager.getKit(normalizedKitName);
            
            if (kit == null) {
                source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.not_found", normalizedKitName));
                return 0;
            }
            
            // Show kit info and ask for confirmation
            source.sendSuccess(() -> MessageUtil.warning("commands.neoessentials.delkit.confirm_prompt", 
                normalizedKitName, kit.getDisplayName(), kit.getItems().size()), false);
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.delkit.confirm_instructions", 
                normalizedKitName), false);
            
            return 1;
            
        } catch (Exception e) {
            LOGGER.error("Error processing delkit command for kit '{}': {}", kitName, e.getMessage(), e);
            source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.error"));
            return 0;
        }
    }
    
    private static int confirmDeleteKit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String kitName = StringArgumentType.getString(context, "kitname");
        String normalizedKitName = Kit.normalizeKitName(kitName);
        
        try {
            // Check and deduct delkit command cost if economy is enabled
            var entity = source.getEntity();
            if (entity instanceof ServerPlayer player) {
                int cost = (int) com.zerog.neoessentials.config.ConfigManager.getKitCommandCost("delkit");
                if (cost > 0 && com.zerog.neoessentials.economy.managers.EconomyManager.getInstance().isEnabled()) {
                    var eco = com.zerog.neoessentials.economy.managers.EconomyManager.getInstance();
                    var bal = eco.getBalance(player.getUUID());
                    if (bal.doubleValue() < cost) {
                        source.sendFailure(com.zerog.neoessentials.util.MessageUtil.error("commands.neoessentials.delkit.not_enough_money", cost));
                        return 0;
                    }
                    if (!eco.subtractBalance(player.getUUID(), java.math.BigDecimal.valueOf(cost))) {
                        source.sendFailure(com.zerog.neoessentials.util.MessageUtil.error("commands.neoessentials.delkit.charge_failed"));
                        return 0;
                    }
                }
            }
            KitManager kitManager = KitManager.getInstance();
            Kit kit = kitManager.getKit(normalizedKitName);
            
            if (kit == null) {
                source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.not_found", normalizedKitName));
                return 0;
            }
            
            boolean success = kitManager.deleteKit(normalizedKitName);
            
            if (success) {
                source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.delkit.deleted", 
                    normalizedKitName), false);
                
                // Log the deletion
                String playerName = "Console";
                if (source.getEntity() instanceof ServerPlayer player) {
                    playerName = player.getName().getString();
                }
                LOGGER.info("Kit '{}' deleted by {}", normalizedKitName, playerName);
                
                return 1;
            } else {
                source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.failed"));
                return 0;
            }
            
        } catch (Exception e) {
            LOGGER.error("Error confirming delkit for kit '{}': {}", kitName, e.getMessage(), e);
            source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.error"));
            return 0;
        }
    }
}
