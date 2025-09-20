package fr.yourserver.whitelistbot.database;

import fr.yourserver.whitelistbot.WhitelistBot;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    
    private final WhitelistBot plugin;
    private Connection connection;
    private final String dbPath;
    
    public DatabaseManager(WhitelistBot plugin) throws SQLException {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + "whitelist.db";
        
        // Créer le dossier du plugin s'il n'existe pas
        plugin.getDataFolder().mkdirs();
        
        initializeDatabase();
    }
    
    private void initializeDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        
        // Créer la table des demandes de whitelist
        String createTable = """
            CREATE TABLE IF NOT EXISTS whitelist_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                discord_id TEXT UNIQUE NOT NULL,
                discord_tag TEXT NOT NULL,
                minecraft_username TEXT NOT NULL,
                request_time TEXT NOT NULL,
                status TEXT DEFAULT 'EN_ATTENTE',
                processed_by TEXT,
                processed_time TEXT
            )
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(createTable)) {
            stmt.executeUpdate();
        }
        
        plugin.getLogger().info(plugin.getPrefix() + " Base de données SQLite initialisée : " + dbPath);
    }
    
    public void saveWhitelistRequest(WhitelistRequest request) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO whitelist_requests 
            (discord_id, discord_tag, minecraft_username, request_time, status) 
            VALUES (?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, request.getDiscordId());
            stmt.setString(2, request.getDiscordTag());
            stmt.setString(3, request.getMinecraftUsername());
            stmt.setString(4, request.getRequestTime().toString());
            stmt.setString(5, "EN_ATTENTE");
            stmt.executeUpdate();
        }
    }
    
    public WhitelistRequest getWhitelistRequest(String discordId) throws SQLException {
        String sql = "SELECT * FROM whitelist_requests WHERE discord_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new WhitelistRequest(
                        rs.getString("discord_id"),
                        rs.getString("discord_tag"),
                        rs.getString("minecraft_username"),
                        LocalDateTime.parse(rs.getString("request_time")),
                        rs.getString("status"),
                        rs.getString("processed_by"),
                        rs.getString("processed_time") != null ? 
                            LocalDateTime.parse(rs.getString("processed_time")) : null
                    );
                }
            }
        }
        
        return null;
    }
    
    public WhitelistRequest getRequestByMinecraftUsername(String minecraftUsername) throws SQLException {
        String sql = "SELECT * FROM whitelist_requests WHERE minecraft_username = ? COLLATE NOCASE";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, minecraftUsername);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new WhitelistRequest(
                        rs.getString("discord_id"),
                        rs.getString("discord_tag"),
                        rs.getString("minecraft_username"),
                        LocalDateTime.parse(rs.getString("request_time")),
                        rs.getString("status"),
                        rs.getString("processed_by"),
                        rs.getString("processed_time") != null ? 
                            LocalDateTime.parse(rs.getString("processed_time")) : null
                    );
                }
            }
        }
        
        return null;
    }
    
    public void updateRequestStatus(String discordId, String status) throws SQLException {
        updateRequestStatus(discordId, status, null);
    }
    
    public void updateRequestStatus(String discordId, String status, String processedBy) throws SQLException {
        String sql = """
            UPDATE whitelist_requests 
            SET status = ?, processed_by = ?, processed_time = ? 
            WHERE discord_id = ?
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, processedBy);
            stmt.setString(3, LocalDateTime.now().toString());
            stmt.setString(4, discordId);
            stmt.executeUpdate();
        }
    }
    
    public List<WhitelistRequest> getAllRequests() throws SQLException {
        List<WhitelistRequest> requests = new ArrayList<>();
        String sql = "SELECT * FROM whitelist_requests ORDER BY request_time DESC";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                requests.add(new WhitelistRequest(
                    rs.getString("discord_id"),
                    rs.getString("discord_tag"),
                    rs.getString("minecraft_username"),
                    LocalDateTime.parse(rs.getString("request_time")),
                    rs.getString("status"),
                    rs.getString("processed_by"),
                    rs.getString("processed_time") != null ? 
                        LocalDateTime.parse(rs.getString("processed_time")) : null
                ));
            }
        }
        
        return requests;
    }
    
    public List<WhitelistRequest> getRequestsByStatus(String status) throws SQLException {
        List<WhitelistRequest> requests = new ArrayList<>();
        String sql = "SELECT * FROM whitelist_requests WHERE status = ? ORDER BY request_time DESC";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    requests.add(new WhitelistRequest(
                        rs.getString("discord_id"),
                        rs.getString("discord_tag"),
                        rs.getString("minecraft_username"),
                        LocalDateTime.parse(rs.getString("request_time")),
                        rs.getString("status"),
                        rs.getString("processed_by"),
                        rs.getString("processed_time") != null ? 
                            LocalDateTime.parse(rs.getString("processed_time")) : null
                    ));
                }
            }
        }
        
        return requests;
    }
    
    public boolean deleteRequest(String discordId) throws SQLException {
        String sql = "DELETE FROM whitelist_requests WHERE discord_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            return stmt.executeUpdate() > 0;
        }
    }
    
    public int getRequestCount() throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM whitelist_requests";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt("count");
            }
        }
        
        return 0;
    }
    
    public int getRequestCountByStatus(String status) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM whitelist_requests WHERE status = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        
        return 0;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info(plugin.getPrefix() + " Connexion à la base de données fermée.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(plugin.getPrefix() + " Erreur lors de la fermeture de la base de données : " + e.getMessage());
        }
    }
    
    // Méthode utilitaire pour vérifier la connexion
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    // Méthode pour reconnecter en cas de problème
    public void reconnect() throws SQLException {
        close();
        initializeDatabase();
    }
}