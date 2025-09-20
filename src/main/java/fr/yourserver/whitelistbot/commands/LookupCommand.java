package fr.yourserver.whitelistbot.commands;

import fr.yourserver.whitelistbot.WhitelistBot;
import fr.yourserver.whitelistbot.database.WhitelistRequest;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LookupCommand implements CommandExecutor, TabCompleter {
    
    private final WhitelistBot plugin;
    
    public LookupCommand(WhitelistBot plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifier les permissions
        if (!sender.hasPermission("whitelistbot.lookup")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        
        // Vérifier les arguments
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /lookup <pseudo>");
            return true;
        }
        
        String minecraftUsername = args[0];
        
        // Rechercher de manière asynchrone
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WhitelistRequest request = plugin.getDatabaseManager().getRequestByMinecraftUsername(minecraftUsername);
                
                // Retourner au thread principal pour envoyer le message
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (request == null) {
                        sender.sendMessage(ChatColor.RED + "No requests found for the player: " + ChatColor.YELLOW + minecraftUsername);
                        return;
                    }
                    
                    // Afficher les informations
                    sender.sendMessage(ChatColor.GOLD + "========== " + ChatColor.WHITE + "Informations de " + minecraftUsername + ChatColor.GOLD + " ==========");
                    sender.sendMessage(ChatColor.AQUA + "Pseudo Minecraft: " + ChatColor.WHITE + request.getMinecraftUsername());
                    sender.sendMessage(ChatColor.AQUA + "User Discord: " + ChatColor.WHITE + request.getDiscordTag() + 
                                     ChatColor.GRAY + " (" + request.getDiscordId() + ")");
                    sender.sendMessage(ChatColor.AQUA + "Date of application: " + ChatColor.WHITE + request.getFormattedRequestTime());
                    
                    // Couleur du statut selon l'état
                    ChatColor statusColor = getStatusColor(request.getStatus());
                    sender.sendMessage(ChatColor.AQUA + "Statut: " + statusColor + request.getStatus());
                    
                    if (request.getProcessedBy() != null) {
                        sender.sendMessage(ChatColor.AQUA + "Treated by: " + ChatColor.WHITE + request.getProcessedBy());
                        sender.sendMessage(ChatColor.AQUA + "Processing date: " + ChatColor.WHITE + request.getFormattedProcessedTime());
                    }
                    
                    sender.sendMessage(ChatColor.GOLD + "================================================");
                });
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error while searching for " + minecraftUsername + ": " + e.getMessage());
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.RED + "Error during search. Check the console for more details.");
                });
            }
        });
        
        return true;
    }
    
    private ChatColor getStatusColor(String status) {
        if (status == null) return ChatColor.GRAY;
        
        if (status.contains("APPROVED")) {
            return ChatColor.GREEN;
        } else if (status.contains("REFUSED")) {
            return ChatColor.RED;
        } else if (status.contains("TICKET")) {
            return ChatColor.AQUA; // Changé de CYAN à AQUA
        } else {
            return ChatColor.YELLOW; // EN_ATTENTE
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Proposer les joueurs en ligne
            String partial = args[0].toLowerCase();
            plugin.getServer().getOnlinePlayers().forEach(player -> {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            });
        }
        
        return completions;
    }
}