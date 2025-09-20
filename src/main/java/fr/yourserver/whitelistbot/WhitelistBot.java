package fr.yourserver.whitelistbot;

import org.bukkit.plugin.java.JavaPlugin;
import fr.yourserver.whitelistbot.database.DatabaseManager;
import fr.yourserver.whitelistbot.discord.DiscordBotManager;
import fr.yourserver.whitelistbot.commands.LookupCommand;

public class WhitelistBot extends JavaPlugin {
    
    private static WhitelistBot instance;
    private DatabaseManager databaseManager;
    private DiscordBotManager discordBotManager;
    private String prefix;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Charger la configuration
        saveDefaultConfig();
        loadConfig();
        
        // Initialiser la base de données
        initDatabase();
        
        // Initialiser le bot Discord
        initDiscordBot();
        
        // Enregistrer les commandes
        registerCommands();
        
        getLogger().info(prefix + " Plugin activé avec succès !");
    }
    
    @Override
    public void onDisable() {
        if (discordBotManager != null) {
            discordBotManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info(prefix + " Plugin désactivé !");
    }
    
    private void loadConfig() {
        prefix = getConfig().getString("prefix", "[WhitelistBot]");
        
        // Vérifier la présence du token Discord
        String token = getConfig().getString("discord-token");
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            getLogger().severe(prefix + " Token Discord manquant dans config.yml !");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Vérifier les autres paramètres obligatoires
        if (getConfig().getString("discord-channel-request") == null ||
            getConfig().getString("discord-channel-admin") == null) {
            getLogger().severe(prefix + " Configuration Discord incomplète !");
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void initDatabase() {
        try {
            databaseManager = new DatabaseManager(this);
            getLogger().info(prefix + " Base de données SQLite initialisée !");
        } catch (Exception e) {
            getLogger().severe(prefix + " Erreur lors de l'initialisation de la base de données : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void initDiscordBot() {
        try {
            discordBotManager = new DiscordBotManager(this);
            getLogger().info(prefix + " Bot Discord démarré avec succès !");
        } catch (Exception e) {
            getLogger().severe(prefix + " Erreur lors du démarrage du bot Discord : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void registerCommands() {
        getCommand("lookup").setExecutor(new LookupCommand(this));
    }
    
    // Getters
    public static WhitelistBot getInstance() {
        return instance;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }
    
    public String getPrefix() {
        return prefix;
    }
}