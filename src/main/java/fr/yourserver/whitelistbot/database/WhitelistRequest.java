package fr.yourserver.whitelistbot.database;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WhitelistRequest {
    
    private String discordId;
    private String discordTag;
    private String minecraftUsername;
    private LocalDateTime requestTime;
    private String status;
    private String processedBy;
    private LocalDateTime processedTime;
    
    // Constructeur pour nouvelle demande
    public WhitelistRequest(String discordId, String discordTag, String minecraftUsername, LocalDateTime requestTime) {
        this.discordId = discordId;
        this.discordTag = discordTag;
        this.minecraftUsername = minecraftUsername;
        this.requestTime = requestTime;
        this.status = "EN_ATTENTE";
    }
    
    // Constructeur complet pour récupération depuis la base de données
    public WhitelistRequest(String discordId, String discordTag, String minecraftUsername, 
                          LocalDateTime requestTime, String status, String processedBy, LocalDateTime processedTime) {
        this.discordId = discordId;
        this.discordTag = discordTag;
        this.minecraftUsername = minecraftUsername;
        this.requestTime = requestTime;
        this.status = status;
        this.processedBy = processedBy;
        this.processedTime = processedTime;
    }
    
    // Getters
    public String getDiscordId() {
        return discordId;
    }
    
    public String getDiscordTag() {
        return discordTag;
    }
    
    public String getMinecraftUsername() {
        return minecraftUsername;
    }
    
    public LocalDateTime getRequestTime() {
        return requestTime;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getProcessedBy() {
        return processedBy;
    }
    
    public LocalDateTime getProcessedTime() {
        return processedTime;
    }
    
    // Setters
    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }
    
    public void setDiscordTag(String discordTag) {
        this.discordTag = discordTag;
    }
    
    public void setMinecraftUsername(String minecraftUsername) {
        this.minecraftUsername = minecraftUsername;
    }
    
    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }
    
    public void setProcessedTime(LocalDateTime processedTime) {
        this.processedTime = processedTime;
    }
    
    // Méthodes utilitaires
    public String getFormattedRequestTime() {
        return requestTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
    
    public String getFormattedProcessedTime() {
        if (processedTime == null) return "N/A";
        return processedTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
    
    public boolean isPending() {
        return "EN_ATTENTE".equals(status);
    }
    
    public boolean isApproved() {
        return status != null && status.contains("APPROUVÉ");
    }
    
    public boolean isDenied() {
        return status != null && status.contains("REFUSÉ");
    }
    
    public boolean isTicket() {
        return status != null && status.contains("TICKET");
    }
    
    @Override
    public String toString() {
        return "WhitelistRequest{" +
                "discordId='" + discordId + '\'' +
                ", discordTag='" + discordTag + '\'' +
                ", minecraftUsername='" + minecraftUsername + '\'' +
                ", requestTime=" + requestTime +
                ", status='" + status + '\'' +
                ", processedBy='" + processedBy + '\'' +
                ", processedTime=" + processedTime +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        WhitelistRequest that = (WhitelistRequest) obj;
        return discordId != null ? discordId.equals(that.discordId) : that.discordId == null;
    }
    
    @Override
    public int hashCode() {
        return discordId != null ? discordId.hashCode() : 0;
    }
}