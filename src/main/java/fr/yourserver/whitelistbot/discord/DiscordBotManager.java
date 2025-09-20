package fr.yourserver.whitelistbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import fr.yourserver.whitelistbot.WhitelistBot;
import fr.yourserver.whitelistbot.database.WhitelistRequest;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DiscordBotManager extends ListenerAdapter {
    
    private final WhitelistBot plugin;
    private JDA jda;
    private String requestChannelId;
    private String adminChannelId;
    private String ticketCategoryId;
    private List<String> allowedRoles;
    private final ConcurrentHashMap<String, WhitelistRequest> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> listPages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> ticketChannels = new ConcurrentHashMap<>(); // discordId -> channelId
    private boolean requestEmbedSent = false;
    
    public DiscordBotManager(WhitelistBot plugin) throws Exception {
        this.plugin = plugin;
        this.requestChannelId = plugin.getConfig().getString("discord-channel-request");
        this.adminChannelId = plugin.getConfig().getString("discord-channel-admin");
        this.ticketCategoryId = plugin.getConfig().getString("discord-ticket-category");
        this.allowedRoles = plugin.getConfig().getStringList("allowed-roles");
        
        String token = plugin.getConfig().getString("discord-token");
        this.jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(this)
                .build();
        
        this.jda.awaitReady();
    }
    
    @Override
    public void onReady(ReadyEvent event) {
        plugin.getLogger().info(plugin.getPrefix() + " Discord bot connected: " + event.getJDA().getSelfUser().getName());
        
        // Register slash commands globally
        jda.updateCommands().addCommands(
            Commands.slash("whitelist-list", "View all whitelist requests with pagination"),
            Commands.slash("whitelist-remove", "Remove a player from whitelist and database")
                    .addOption(OptionType.STRING, "username", "Minecraft username to remove", true)
        ).queue(success -> {
            plugin.getLogger().info(plugin.getPrefix() + " Slash commands registered successfully!");
        }, error -> {
            plugin.getLogger().severe(plugin.getPrefix() + " Failed to register slash commands: " + error.getMessage());
        });
        
        setupRequestChannel();
    }
    
    private void setupRequestChannel() {
        if (requestEmbedSent) return;
        
        TextChannel channel = jda.getTextChannelById(requestChannelId);
        if (channel == null) {
            plugin.getLogger().severe(plugin.getPrefix() + " Request channel not found!");
            return;
        }
        
        channel.getHistory().retrievePast(20).queue(messages -> {
            boolean botMessageExists = messages.stream()
                    .anyMatch(msg -> msg.getAuthor().equals(jda.getSelfUser()) && 
                             msg.getEmbeds().stream().anyMatch(embed -> 
                                 embed.getTitle() != null && embed.getTitle().contains("Whitelist Request")));
            
            if (!botMessageExists) {
                sendRequestEmbed(channel);
            } else {
                plugin.getLogger().info(plugin.getPrefix() + " Request message already exists, no spam.");
            }
        });
        
        requestEmbedSent = true;
    }
    
    private void sendRequestEmbed(TextChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎮 Minecraft Whitelist Request")
                .setDescription("Click the button below to request access to our Minecraft server!")
                .setColor(Color.BLUE)
                .setFooter("Whitelist Bot", jda.getSelfUser().getAvatarUrl());
        
        Button requestButton = Button.primary("request_whitelist", "Request Whitelist");
        
        channel.sendMessageEmbeds(embed.build())
                .setActionRow(requestButton)
                .queue(success -> {
                    plugin.getLogger().info(plugin.getPrefix() + " Request message sent successfully!");
                }, error -> {
                    plugin.getLogger().severe(plugin.getPrefix() + " Error sending message: " + error.getMessage());
                });
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "whitelist-list":
                handleWhitelistListCommand(event);
                break;
            case "whitelist-remove":
                handleWhitelistRemoveCommand(event);
                break;
        }
    }
    
    private void handleWhitelistListCommand(SlashCommandInteractionEvent event) {
        if (!hasPermission(event.getMember())) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<WhitelistRequest> requests = plugin.getDatabaseManager().getAllRequests();
                sendWhitelistList(event, requests, 0);
            } catch (Exception e) {
                plugin.getLogger().severe("Error fetching whitelist requests: " + e.getMessage());
                event.getHook().sendMessage("❌ Error fetching data from database.").queue();
            }
        });
    }
    
    private void handleWhitelistRemoveCommand(SlashCommandInteractionEvent event) {
        if (!hasPermission(event.getMember())) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        String username = event.getOption("username").getAsString();
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WhitelistRequest request = plugin.getDatabaseManager().getRequestByMinecraftUsername(username);
                
                if (request == null) {
                    event.getHook().sendMessage("❌ Player `" + username + "` not found in whitelist database.").queue();
                    return;
                }
                
                // Remove from Minecraft whitelist
                Bukkit.getScheduler().runTask(plugin, () -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(username);
                    if (player.isWhitelisted()) {
                        player.setWhitelisted(false);
                        plugin.getLogger().info(plugin.getPrefix() + " " + username + " removed from whitelist by " + event.getUser().getAsTag());
                    }
                });
                
                // Remove from database
                boolean removed = plugin.getDatabaseManager().deleteRequest(request.getDiscordId());
                
                if (removed) {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("✅ Player Removed Successfully")
                            .setDescription("**Minecraft Username:** " + username + "\n" +
                                          "**Discord User:** " + request.getDiscordTag() + "\n" +
                                          "**Original Request Date:** " + request.getFormattedRequestTime() + "\n" +
                                          "**Removed by:** " + event.getUser().getAsTag() + "\n" +
                                          "**Removal Date:** " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")))
                            .setColor(Color.RED)
                            .setFooter("Whitelist Bot", jda.getSelfUser().getAvatarUrl());
                    
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                    
                    // Send DM to the removed player
                    sendRemovalDM(request, event.getUser().getAsTag());
                    
                } else {
                    event.getHook().sendMessage("❌ Failed to remove player from database.").queue();
                }
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error removing player from whitelist: " + e.getMessage());
                e.printStackTrace();
                event.getHook().sendMessage("❌ Error removing player from whitelist.").queue();
            }
        });
    }
    
    private void sendRemovalDM(WhitelistRequest request, String removedBy) {
        jda.retrieveUserById(request.getDiscordId()).queue(user -> {
            user.openPrivateChannel().queue(dmChannel -> {
                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setTitle("🚫 Whitelist Removal Notice")
                        .setDescription("**Your whitelist access has been removed.**\n\n" +
                                      "**Minecraft Username:** " + request.getMinecraftUsername() + "\n" +
                                      "**Removed by:** " + removedBy + "\n" +
                                      "**Date:** " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) + "\n\n" +
                                      "If you believe this was done in error, please contact our staff team.")
                        .setColor(Color.RED)
                        .setFooter("Whitelist Bot", jda.getSelfUser().getAvatarUrl());
                
                dmChannel.sendMessageEmbeds(dmEmbed.build()).queue(
                    success -> plugin.getLogger().info("Removal DM sent to " + request.getDiscordTag()),
                    error -> plugin.getLogger().warning("Failed to send removal DM to " + request.getDiscordTag() + ": " + error.getMessage())
                );
            }, error -> plugin.getLogger().warning("Failed to open DM channel with " + request.getDiscordTag()));
        }, error -> plugin.getLogger().warning("Failed to retrieve user for removal DM: " + request.getDiscordId()));
    }
    
    private void sendWhitelistList(SlashCommandInteractionEvent event, List<WhitelistRequest> requests, int page) {
        if (requests.isEmpty()) {
            event.getHook().sendMessage("📋 No whitelist requests found.").queue();
            return;
        }
        
        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) requests.size() / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, requests.size());
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Whitelist Requests List")
                .setColor(Color.CYAN)
                .setFooter("Page " + (page + 1) + "/" + totalPages + " • Total: " + requests.size() + " requests", 
                          jda.getSelfUser().getAvatarUrl());
        
        StringBuilder description = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            WhitelistRequest request = requests.get(i);
            String statusEmoji = getStatusEmoji(request.getStatus());
            
            description.append("**").append(i + 1).append(".** ")
                      .append(statusEmoji).append(" **").append(request.getMinecraftUsername()).append("**\n")
                      .append("└ Discord: ").append(request.getDiscordTag()).append("\n")
                      .append("└ Date: ").append(request.getFormattedRequestTime());
            
            if (request.getProcessedBy() != null) {
                description.append("\n└ Processed by: ").append(request.getProcessedBy())
                          .append(" at ").append(request.getFormattedProcessedTime());
            }
            
            description.append("\n\n");
        }
        
        embed.setDescription(description.toString());
        
        // Navigation buttons
        ActionRow buttons = createNavigationButtons(page, totalPages, "list");
        
        event.getHook().sendMessageEmbeds(embed.build())
                .addComponents(buttons)
                .queue();
        
        // Store page info for navigation
        listPages.put(event.getUser().getId(), page);
    }
    
    private void updateWhitelistList(ButtonInteractionEvent event, int newPage) {
        event.deferEdit().queue();
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<WhitelistRequest> requests = plugin.getDatabaseManager().getAllRequests();
                sendWhitelistListUpdate(event, requests, newPage);
            } catch (Exception e) {
                plugin.getLogger().severe("Error updating whitelist list: " + e.getMessage());
                event.getHook().sendMessage("❌ Error updating list.").queue();
            }
        });
    }
    
    private void sendWhitelistListUpdate(ButtonInteractionEvent event, List<WhitelistRequest> requests, int page) {
        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) requests.size() / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, requests.size());
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Whitelist Requests List")
                .setColor(Color.CYAN)
                .setFooter("Page " + (page + 1) + "/" + totalPages + " • Total: " + requests.size() + " requests", 
                          jda.getSelfUser().getAvatarUrl());
        
        StringBuilder description = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            WhitelistRequest request = requests.get(i);
            String statusEmoji = getStatusEmoji(request.getStatus());
            
            description.append("**").append(i + 1).append(".** ")
                      .append(statusEmoji).append(" **").append(request.getMinecraftUsername()).append("**\n")
                      .append("└ Discord: ").append(request.getDiscordTag()).append("\n")
                      .append("└ Date: ").append(request.getFormattedRequestTime());
            
            if (request.getProcessedBy() != null) {
                description.append("\n└ Processed by: ").append(request.getProcessedBy())
                          .append(" at ").append(request.getFormattedProcessedTime());
            }
            
            description.append("\n\n");
        }
        
        embed.setDescription(description.toString());
        
        ActionRow buttons = createNavigationButtons(page, totalPages, "list");
        
        event.getHook().editOriginalEmbeds(embed.build())
                .setComponents(buttons)
                .queue();
        
        listPages.put(event.getUser().getId(), page);
    }
    
    private ActionRow createNavigationButtons(int currentPage, int totalPages, String type) {
        Button prevButton = Button.secondary("nav_" + type + "_prev_" + currentPage, "◀ Previous")
                .withDisabled(currentPage == 0);
        
        Button nextButton = Button.secondary("nav_" + type + "_next_" + currentPage, "Next ▶")
                .withDisabled(currentPage >= totalPages - 1);
        
        Button pageInfo = Button.secondary("page_info", "Page " + (currentPage + 1) + "/" + totalPages)
                .withDisabled(true);
        
        return ActionRow.of(prevButton, pageInfo, nextButton);
    }
    
    private String getStatusEmoji(String status) {
        if (status == null) return "⚪";
        
        if (status.contains("APPROVED") || status.contains("APPROUVÉ")) {
            return "🟢";
        } else if (status.contains("DENIED") || status.contains("REFUSÉ")) {
            return "🔴";
        } else if (status.contains("TICKET")) {
            return "🔵";
        } else {
            return "🟠"; // PENDING
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getId();
        
        if (buttonId.startsWith("nav_list_")) {
            handleListNavigation(event, buttonId);
        } else {
            switch (buttonId) {
                case "request_whitelist":
                    handleWhitelistRequest(event);
                    break;
                case "confirm_request":
                    handleConfirmRequest(event);
                    break;
                case "cancel_request":
                    handleCancelRequest(event);
                    break;
                case "close_ticket":
                    handleCloseTicket(event);
                    break;
                default:
                    if (buttonId.startsWith("admin_")) {
                        handleAdminAction(event);
                    }
                    break;
            }
        }
    }
    
    private void handleListNavigation(ButtonInteractionEvent event, String buttonId) {
        String[] parts = buttonId.split("_");
        String direction = parts[2]; // prev or next
        int currentPage = Integer.parseInt(parts[3]);
        
        int newPage = direction.equals("prev") ? currentPage - 1 : currentPage + 1;
        updateWhitelistList(event, newPage);
    }
    
    private void handleWhitelistRequest(ButtonInteractionEvent event) {
        // Check if user already has an approved request
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<WhitelistRequest> userRequests = plugin.getDatabaseManager().getAllRequests().stream()
                        .filter(req -> req.getDiscordId().equals(event.getUser().getId()))
                        .collect(Collectors.toList());
                
                // Check for approved request
                boolean hasApproved = userRequests.stream()
                        .anyMatch(req -> req.getStatus() != null && 
                                 (req.getStatus().contains("APPROVED") || req.getStatus().contains("APPROUVÉ")));
                
                if (hasApproved) {
                    event.reply("✅ You are already whitelisted on our server! No need to request again.")
                            .setEphemeral(true).queue();
                    return;
                }
                
                // Check for pending request
                boolean hasPending = userRequests.stream()
                        .anyMatch(req -> req.getStatus() != null && 
                                 (req.getStatus().contains("PENDING") || req.getStatus().contains("EN_ATTENTE")));
                
                if (hasPending) {
                    event.reply("⏳ You already have a pending whitelist request. Please wait for admin review.")
                            .setEphemeral(true).queue();
                    return;
                }
                
                // Proceed with modal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextInput minecraftUsername = TextInput.create("minecraft_username", "Minecraft Username", TextInputStyle.SHORT)
                            .setPlaceholder("Enter your exact Minecraft username")
                            .setMinLength(3)
                            .setMaxLength(16)
                            .setRequired(true)
                            .build();
                    
                    Modal modal = Modal.create("whitelist_modal", "Whitelist Request")
                            .addActionRows(ActionRow.of(minecraftUsername))
                            .build();
                    
                    event.replyModal(modal).queue();
                });
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error checking existing requests: " + e.getMessage());
                event.reply("❌ Error checking your existing requests. Please try again.")
                        .setEphemeral(true).queue();
            }
        });
    }
    
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("whitelist_modal")) {
            String username = event.getValue("minecraft_username").getAsString();
            
            // Check if this Minecraft username is already approved for another Discord user
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    WhitelistRequest existingRequest = plugin.getDatabaseManager().getRequestByMinecraftUsername(username);
                    
                    if (existingRequest != null && 
                        (existingRequest.getStatus().contains("APPROVED") || existingRequest.getStatus().contains("APPROUVÉ")) &&
                        !existingRequest.getDiscordId().equals(event.getUser().getId())) {
                        
                        event.reply("❌ This Minecraft username is already whitelisted by another Discord user!")
                                .setEphemeral(true).queue();
                        return;
                    }
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        WhitelistRequest request = new WhitelistRequest(
                                event.getUser().getId(),
                                event.getUser().getAsTag(),
                                username,
                                LocalDateTime.now()
                        );
                        
                        pendingRequests.put(event.getUser().getId(), request);
                        
                        EmbedBuilder confirmEmbed = new EmbedBuilder()
                                .setTitle("🔍 Verification")
                                .setDescription("**Minecraft Username:** " + username + "\n\n" +
                                        "**NameMC Profile:** [Click here](https://namemc.com/profile/" + username + ")\n\n" +
                                        "Please verify that the information is correct before confirming your request.")
                                .setColor(Color.ORANGE)
                                .setFooter("Check your NameMC profile", event.getJDA().getSelfUser().getAvatarUrl());
                        
                        Button confirmButton = Button.success("confirm_request", "✅ Confirm Request");
                        Button cancelButton = Button.danger("cancel_request", "❌ Cancel");
                        
                        event.reply("Verifying your request:")
                                .addEmbeds(confirmEmbed.build())
                                .addActionRow(confirmButton, cancelButton)
                                .setEphemeral(true)
                                .queue();
                    });
                    
                } catch (Exception e) {
                    plugin.getLogger().severe("Error checking existing username: " + e.getMessage());
                    event.reply("❌ Error processing your request. Please try again.")
                            .setEphemeral(true).queue();
                }
            });
        }
    }
    
    private void handleConfirmRequest(ButtonInteractionEvent event) {
        WhitelistRequest request = pendingRequests.get(event.getUser().getId());
        if (request == null) {
            event.reply("❌ Error: Request expired or not found.").setEphemeral(true).queue();
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().saveWhitelistRequest(request);
                sendToAdminChannel(request);
                
                event.reply("✅ Your whitelist request has been sent to administrators!").setEphemeral(true).queue();
                pendingRequests.remove(event.getUser().getId());
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving request: " + e.getMessage());
                event.reply("❌ Error sending your request. Please contact an administrator.").setEphemeral(true).queue();
            }
        });
    }
    
    private void handleCancelRequest(ButtonInteractionEvent event) {
        pendingRequests.remove(event.getUser().getId());
        event.reply("❌ Request cancelled.").setEphemeral(true).queue();
    }
    
    private void sendToAdminChannel(WhitelistRequest request) {
        TextChannel adminChannel = jda.getTextChannelById(adminChannelId);
        if (adminChannel == null) {
            plugin.getLogger().severe("Admin channel not found!");
            return;
        }
        
        Guild guild = adminChannel.getGuild();
        Member member = guild.getMemberById(request.getDiscordId());
        String userRoles = "None";
        if (member != null) {
            userRoles = member.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.joining(", "));
            if (userRoles.isEmpty()) {
                userRoles = "None";
            }
        }
        
        EmbedBuilder adminEmbed = new EmbedBuilder()
                .setTitle("📋 New Whitelist Request")
                .addField("🎮 Minecraft Username", request.getMinecraftUsername(), true)
                .addField("👤 Discord User", request.getDiscordTag(), true)
                .addField("🏷️ Discord Roles", userRoles, true)
                .addField("🕐 Request Time", request.getRequestTime().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")), true)
                .addField("🔗 NameMC Profile", "[View Profile](https://namemc.com/profile/" + request.getMinecraftUsername() + ")", true)
                .addField("📊 Status", "🟠 Pending", true)
                .setColor(Color.ORANGE)
                .setFooter("Awaiting admin review", jda.getSelfUser().getAvatarUrl());
        
        Button approveButton = Button.success("admin_approve_" + request.getDiscordId(), "✅ Approve");
        Button denyButton = Button.danger("admin_deny_" + request.getDiscordId(), "❌ Deny");
        Button ticketButton = Button.primary("admin_ticket_" + request.getDiscordId(), "🎫 [DEV]Create Ticket");
        
        adminChannel.sendMessageEmbeds(adminEmbed.build())
                .setActionRow(approveButton, denyButton, ticketButton)
                .queue();
    }
    
    private void handleAdminAction(ButtonInteractionEvent event) {
        if (!hasPermission(event.getMember())) {
            event.reply("❌ You don't have permission to perform this action.").setEphemeral(true).queue();
            return;
        }
        
        String buttonId = event.getButton().getId();
        String[] parts = buttonId.split("_");
        if (parts.length < 3) {
            event.reply("❌ Invalid button ID.").setEphemeral(true).queue();
            return;
        }
        
        String action = parts[1];
        String discordId = parts[2];
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WhitelistRequest request = plugin.getDatabaseManager().getWhitelistRequest(discordId);
                if (request == null) {
                    event.reply("❌ Request not found.").setEphemeral(true).queue();
                    return;
                }
                
                processAdminAction(event, request, action);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing admin action: " + e.getMessage());
                e.printStackTrace();
                event.reply("❌ Error processing action.").setEphemeral(true).queue();
            }
        });
    }
    
    private void processAdminAction(ButtonInteractionEvent event, WhitelistRequest request, String action) {
        String status;
        Color embedColor;
        String responseMessage;
        String statusEmoji;
        String dmMessage;
        
        switch (action) {
            case "approve":
                Bukkit.getScheduler().runTask(plugin, () -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(request.getMinecraftUsername());
                    player.setWhitelisted(true);
                    plugin.getLogger().info(plugin.getPrefix() + " " + request.getMinecraftUsername() + " added to whitelist by " + event.getUser().getAsTag());
                });
                
                status = "✅ APPROVED";
                statusEmoji = "🟢";
                embedColor = Color.GREEN;
                responseMessage = "✅ Request approved! Player added to whitelist.";
                dmMessage = "🎉 **Great news!** Your whitelist request has been **approved**!\n\n" +
                           "**Minecraft Username:** " + request.getMinecraftUsername() + "\n" +
                           "**Approved by:** " + event.getUser().getAsTag() + "\n" +
                           "**Date:** " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) + "\n\n" +
                           "You can now join our Minecraft server! Welcome aboard! 🎮";
                break;
                
            case "deny":
                status = "❌ DENIED";
                statusEmoji = "🔴";
                embedColor = Color.RED;
                responseMessage = "❌ Request denied.";
                dmMessage = "😔 **We're sorry to inform you** that your whitelist request has been **denied**.\n\n" +
                           "**Minecraft Username:** " + request.getMinecraftUsername() + "\n" +
                           "**Denied by:** " + event.getUser().getAsTag() + "\n" +
                           "**Date:** " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) + "\n\n" +
                           "If you have questions, please contact our staff team.";
                break;
                
            case "ticket":
                // Create ticket channel
                createTicketChannel(event, request);
                
                status = "🎫 TICKET CREATED";
                statusEmoji = "🔵";
                embedColor = Color.CYAN;
                responseMessage = "🎫 Ticket channel created successfully.";
                dmMessage = "🎫 **Your whitelist request requires additional review.**\n\n" +
                           "**Minecraft Username:** " + request.getMinecraftUsername() + "\n" +
                           "**Staff Member:** " + event.getUser().getAsTag() + "\n" +
                           "**Date:** " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")) + "\n\n" +
                           "A ticket channel has been created for discussion. You will be mentioned in the channel.";
                break;
                
            default:
                return;
        }
        
        // Send DM to user
        jda.retrieveUserById(request.getDiscordId()).queue(user -> {
            user.openPrivateChannel().queue(dmChannel -> {
                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setTitle("📋 Whitelist Request Update")
                        .setDescription(dmMessage)
                        .setColor(embedColor)
                        .setFooter("Whitelist Bot", jda.getSelfUser().getAvatarUrl());
                
                dmChannel.sendMessageEmbeds(dmEmbed.build()).queue(
                    success -> plugin.getLogger().info("DM sent to " + request.getDiscordTag()),
                    error -> plugin.getLogger().warning("Failed to send DM to " + request.getDiscordTag() + ": " + error.getMessage())
                );
            }, error -> plugin.getLogger().warning("Failed to open DM channel with " + request.getDiscordTag()));
        }, error -> plugin.getLogger().warning("Failed to retrieve user: " + request.getDiscordId()));
        
        updateAdminEmbed(event, request, status, statusEmoji, embedColor);
        
        try {
            plugin.getDatabaseManager().updateRequestStatus(request.getDiscordId(), status, event.getUser().getAsTag());
        } catch (Exception e) {
            plugin.getLogger().severe("Error updating request status: " + e.getMessage());
        }
        
        event.reply(responseMessage).setEphemeral(true).queue();
    }
    
    private void updateAdminEmbed(ButtonInteractionEvent event, WhitelistRequest request, String status, String statusEmoji, Color color) {
        MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
        
        EmbedBuilder updatedEmbed = new EmbedBuilder()
                .setTitle(originalEmbed.getTitle())
                .setColor(color)
                .setFooter("Processed by " + event.getUser().getAsTag() + " • " + 
                          LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")), 
                          jda.getSelfUser().getAvatarUrl());
        
        for (MessageEmbed.Field field : originalEmbed.getFields()) {
            if (field.getName().equals("📊 Status")) {
                updatedEmbed.addField("📊 Status", statusEmoji + " " + status, field.isInline());
            } else {
                updatedEmbed.addField(field.getName(), field.getValue(), field.isInline());
            }
        }
        
        event.getMessage().editMessageEmbeds(updatedEmbed.build())
                .setComponents()
                .queue();
    }
    
    private boolean hasPermission(Member member) {
        if (member == null) return false;
        if (allowedRoles.isEmpty()) return false; // Changed: Now requires roles by default
        
        return member.getRoles().stream()
                .anyMatch(role -> allowedRoles.contains(role.getId()));
    }
    
    private void createTicketChannel(ButtonInteractionEvent event, WhitelistRequest request) {
        Guild guild = event.getGuild();
        if (guild == null) {
            plugin.getLogger().severe("Guild is null when creating ticket");
            return;
        }
        
        Category ticketCategory = guild.getCategoryById(ticketCategoryId);
        if (ticketCategory == null) {
            plugin.getLogger().severe("Ticket category not found: " + ticketCategoryId);
            event.reply("❌ Ticket category not found. Please check configuration.").setEphemeral(true).queue();
            return;
        }
        
        // Check if ticket already exists
        if (ticketChannels.containsKey(request.getDiscordId())) {
            String existingChannelId = ticketChannels.get(request.getDiscordId());
            TextChannel existingChannel = guild.getTextChannelById(existingChannelId);
            if (existingChannel != null) {
                event.reply("❌ A ticket channel already exists for this user: " + existingChannel.getAsMention())
                        .setEphemeral(true).queue();
                return;
            }
        }
        
        String channelName = "ticket-" + request.getMinecraftUsername().toLowerCase();
        
        // Get the requester user
        guild.retrieveMemberById(request.getDiscordId()).queue(requesterMember -> {
            // Create channel with permissions
            ticketCategory.createTextChannel(channelName)
                    .addPermissionOverride(guild.getPublicRole(), 
                            null, // No allowed permissions
                            EnumSet.of(Permission.VIEW_CHANNEL)) // Deny view
                    .addPermissionOverride(requesterMember, 
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), 
                            null)
                    .queue(ticketChannel -> {
                        // Add admin role permissions
                        for (String roleId : allowedRoles) {
                            Role adminRole = guild.getRoleById(roleId);
                            if (adminRole != null) {
                                ticketChannel.getManager().putPermissionOverride(adminRole, 
                                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, 
                                                Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL), 
                                        null).queue();
                            }
                        }
                        
                        // Store ticket channel
                        ticketChannels.put(request.getDiscordId(), ticketChannel.getId());
                        
                        // Send initial message in ticket
                        sendTicketWelcomeMessage(ticketChannel, request, event.getUser());
                        
                    }, error -> {
                        plugin.getLogger().severe("Failed to create ticket channel: " + error.getMessage());
                        event.reply("❌ Failed to create ticket channel.").setEphemeral(true).queue();
                    });
        }, error -> {
            plugin.getLogger().severe("Failed to retrieve member for ticket: " + error.getMessage());
            event.reply("❌ Failed to retrieve user information.").setEphemeral(true).queue();
        });
    }
    
    private void sendTicketWelcomeMessage(TextChannel ticketChannel, WhitelistRequest request, User staffMember) {
        EmbedBuilder welcomeEmbed = new EmbedBuilder()
                .setTitle("🎫 Whitelist Request Ticket")
                .setDescription("**Welcome to your whitelist discussion ticket!**\n\n" +
                               "This ticket was created to discuss your whitelist request in detail.")
                .addField("🎮 Minecraft Username", request.getMinecraftUsername(), true)
                .addField("👤 Discord User", "<@" + request.getDiscordId() + ">", true)
                .addField("⏰ Request Date", request.getFormattedRequestTime(), true)
                .addField("👨‍💼 Ticket Created By", staffMember.getAsMention(), true)
                .addField("🔗 NameMC Profile", "[View Profile](https://namemc.com/profile/" + request.getMinecraftUsername() + ")", true)
                .addField("📊 Current Status", "🔵 Under Review", true)
                .setColor(Color.CYAN)
                .setFooter("Use the button below to close this ticket", ticketChannel.getJDA().getSelfUser().getAvatarUrl());
        
        Button closeTicketButton = Button.danger("close_ticket", "🗑️ Close Ticket");
        
        ticketChannel.sendMessage("<@" + request.getDiscordId() + "> " + staffMember.getAsMention())
                .addEmbeds(welcomeEmbed.build())
                .setActionRow(closeTicketButton)
                .queue(success -> {
                    plugin.getLogger().info("Ticket channel created: " + ticketChannel.getName());
                }, error -> {
                    plugin.getLogger().severe("Failed to send welcome message in ticket: " + error.getMessage());
                });
    }
    
    private void handleCloseTicket(ButtonInteractionEvent event) {
    if (!hasPermission(event.getMember()) && !isTicketCreator(event)) {
        event.reply("❌ You don't have permission to close this ticket.")
                .setEphemeral(true).queue();
        return;
    }

    EmbedBuilder confirmEmbed = new EmbedBuilder()
            .setTitle("🚨 Confirm Ticket Closure")
            .setDescription("Are you sure you want to close this ticket?\n\n" +
                           "**This action cannot be undone and the channel will be deleted.**")
            .setColor(Color.RED)
            .setFooter("Ticket Closure Confirmation", event.getJDA().getSelfUser().getAvatarUrl());

    Button confirmClose = Button.danger("confirm_close_ticket", "✅ Yes, Close Ticket");
    Button cancelClose = Button.secondary("cancel_close_ticket", "❌ Cancel");

    // Message public dans le ticket (PAS éphémère)
    event.replyEmbeds(confirmEmbed.build())
            .addActionRow(confirmClose, cancelClose)
            .queue();
}

private void handleCancelCloseTicket(ButtonInteractionEvent event) {
    // Vérification : seul créateur ou staff
    if (!hasPermission(event.getMember()) && !isTicketCreator(event)) {
        event.reply("❌ You cannot cancel this ticket closure.")
                .setEphemeral(true).queue();
        return;
    }

    event.reply("❌ Ticket closure cancelled.")
            .setEphemeral(true).queue();
}

private void handleConfirmCloseTicket(ButtonInteractionEvent event) {
    // Vérification : seul créateur ou staff
    if (!hasPermission(event.getMember()) && !isTicketCreator(event)) {
        event.reply("❌ You don't have permission to close this ticket.")
                .setEphemeral(true).queue();
        return;
    }

    TextChannel channel = event.getChannel().asTextChannel();
    String channelName = channel.getName();

    // Remove from tracking
    ticketChannels.values().remove(channel.getId());

    // Send final message
    EmbedBuilder finalEmbed = new EmbedBuilder()
            .setTitle("🗑️ Ticket Closed")
            .setDescription("This ticket has been closed by " + event.getUser().getAsMention() + "\n\n" +
                           "**Channel will be deleted in 5 seconds.**")
            .setColor(Color.RED)
            .setTimestamp(java.time.Instant.now())
            .setFooter("Whitelist Bot", event.getJDA().getSelfUser().getAvatarUrl());

    event.replyEmbeds(finalEmbed.build())
            .queue(success -> {
                // Delete channel after 5 seconds
                channel.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS,
                    deleteSuccess -> {
                        plugin.getLogger().info("Ticket channel deleted: " + channelName + " by " + event.getUser().getAsTag());
                    },
                    deleteError -> {
                        plugin.getLogger().severe("Failed to delete ticket channel: " + deleteError.getMessage());
                    }
                );
            });
}

    
    private boolean isTicketCreator(ButtonInteractionEvent event) {
        // Check if the user is the original requester of this ticket
        String channelId = event.getChannel().getId();
        return ticketChannels.containsValue(channelId) && 
               ticketChannels.entrySet().stream()
                   .anyMatch(entry -> entry.getValue().equals(channelId) && 
                            entry.getKey().equals(event.getUser().getId()));
    }
    
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info(plugin.getPrefix() + " Discord bot stopped.");
        }
    }
    
    public JDA getJDA() {
        return jda;
    }
}