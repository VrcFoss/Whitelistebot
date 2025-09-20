# WhitelistBot - Minecraft Plugin with Integrated Discord Bot

A comprehensive Minecraft plugin (Paper/Spigot 1.20+) that integrates a Discord bot to automatically manage whitelist requests through a modern interface with embeds, buttons, and --ticket-- system.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [Commands](#commands)
- [Permissions](#permissions)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Features

### Core Functionality
- **Integrated Discord Bot** - Everything runs from the plugin, no external applications required
- **Modern Discord UI** - Interactive embeds with buttons and modals
- **Automatic Verification** - NameMC profile links for verification
- **SQLite Database** - Local storage for requests and Discord ↔ Minecraft linking
- **Role-based Permissions** - Control via Discord roles
- **Administrative Commands** - `/lookup` for searching linked accounts

### Advanced Features
- **Ticket System** - Private channels for detailed discussions
- **Pagination** - Handle large lists with navigation buttons
- **Duplicate Protection** - Prevents multiple requests from same user/username
- **Status Management** - Dynamic status updates (Pending, Approved, Denied, Ticket)
- **Private Messages** - Automatic DM notifications for status changes
- **Anti-spam** - Prevents duplicate request messages

### Security Features
- **Permission Validation** - Only authorized roles can manage requests
- **Request Validation** - Checks for existing approvals and duplicates
- **Secure Ticket Channels** - Private channels with controlled access
- **Audit Logging** - Detailed logs for all actions

## Prerequisites

### Server Requirements
- **Minecraft Server**: Paper/Spigot/Purpur 1.20.1+
- **Java**: JDK 17 or higher
- **RAM**: Minimum 512MB additional for Discord bot

### Development Requirements
- **Maven**: 3.8+ for compilation
- **Git**: For version control
- **IDE**: IntelliJ IDEA or Eclipse recommended

### Discord Setup
- Discord bot created on [Discord Developer Portal](https://discord.com/developers/applications)
- Bot token
- Server with appropriate channels and categories
- Admin roles configured

## Installation

### 1. Download or Compile

#### Option A: Download Release
Download the latest JAR from the [Releases](https://github.com/VrcFoss/Whitelistebot/releases) page.

#### Option B: Compile from Source
```bash
git clone https://github.com/yourusername/WhitelistBot.git
cd WhitelistBot
mvn clean package
```

The compiled JAR will be in `target/WhitelistBot-1.0.0.jar` (~50-60MB with dependencies).

### 2. Server Installation
1. Place the JAR in your server's `plugins/` folder
2. Start the server (creates configuration files)
3. Stop the server
4. Configure `plugins/WhitelistBot/config.yml`
5. Restart the server

### 3. Discord Bot Setup

#### Create Discord Application
1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create new application
3. Go to "Bot" section, create bot and copy token
4. Enable these intents:
   - `MESSAGE_CONTENT_INTENT`
   - `GUILD_MESSAGES`
   - `GUILD_MEMBERS`

#### Bot Permissions
Invite your bot with these permissions:
```
Manage Channels     - Create/delete ticket channels
Manage Permissions  - Set channel permissions
View Channels       - Access server channels
Send Messages       - Post embeds and responses
Embed Links         - Rich embeds
Use Slash Commands  - Admin commands
```

**Invite URL Template:**
```
https://discord.com/api/oauth2/authorize?client_id=YOUR_BOT_ID&permissions=268528656&scope=bot%20applications.commands
```

## Configuration

### Basic Configuration

Edit `plugins/WhitelistBot/config.yml`:

```yaml
# Discord bot token (required)
discord-token: "YOUR_BOT_TOKEN_HERE"

# Channel where players make requests (required)
discord-channel-request: "123456789012345678"

# Admin channel for request management (required)  
discord-channel-admin: "987654321098765432"

# Category for ticket channels (required)
discord-ticket-category: "123456789012345678"

# Authorized admin/moderator role IDs
allowed-roles:
  - "123456789012345678"  # @Admin
  - "456789012345678901"  # @Moderator

# Console log prefix
prefix: "[WhitelistBot]"
```

### Advanced Configuration

```yaml
# Custom messages
messages:
  request-sent: "✅ Your request has been sent to administrators!"
  request-approved: "🎉 Your whitelist request has been approved!"
  request-denied: "❌ Your whitelist request has been denied."
  invalid-username: "❌ The provided Minecraft username is not valid."
  already-requested: "⚠️ You already have a pending request."

# Database settings
database:
  filename: "whitelist.db"
  debug-sql: false

# Discord UI colors (hex format)
discord:
  interaction-timeout: 300
  colors:
    pending: "#FF8C00"    # Orange
    approved: "#00FF00"   # Green  
    denied: "#FF0000"     # Red
    ticket: "#00BFFF"     # Sky Blue
```

### Getting Discord IDs

To get channel/role/category IDs:
1. Enable Discord Developer Mode: `User Settings` → `Advanced` → `Developer Mode`
2. Right-click channel/role → `Copy ID`
3. For categories: Right-click category name → `Copy ID`

## Usage

### For Players

1. **Request Whitelist**
   - Go to the request channel
   - Click "Request Whitelist" button
   - Enter your Minecraft username
   - Verify your NameMC profile
   - Confirm your request

2. **Wait for Review**
   - Admins will review your request
   - You'll receive a DM with the decision
   - If approved, you can join the server immediately

### For Administrators

1. **Review Requests**
   - Check the admin channel for new requests
   - Review NameMC profile and Discord roles
   - Use buttons to Approve, Deny, or Create Ticket

2. **Manage Tickets**
   - Private channels created for complex cases
   - Discuss with player directly
   - Close ticket when resolved

3. **Use Commands**
   - `/whitelist-list` - View all requests with pagination
   - `/whitelist-remove username:PLAYER` - Remove player from whitelist

## Commands

### Discord Slash Commands

| Command | Description | Permission Required |
|---------|-------------|-------------------|
| `/whitelist-list` | View paginated list of all requests | Admin roles |
| `/whitelist-remove username:PLAYER` | Remove player from whitelist & database | Admin roles |

### Minecraft Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/lookup <username>` | View Discord info for Minecraft player | `whitelistbot.lookup` |

## Permissions

### Minecraft Permissions

```yaml
whitelistbot.*:
  description: Full access to WhitelistBot
  default: op
  children:
    whitelistbot.lookup: true
    whitelistbot.admin: true

whitelistbot.lookup:
  description: Use /lookup command
  default: op

whitelistbot.admin:
  description: Administrative functions
  default: op
```

### Discord Permissions

Configure in `config.yml`:
- `allowed-roles` - List of Discord role IDs that can manage requests
- Empty list = no restrictions (not recommended)
- Only these roles can use slash commands and manage tickets

## Database

The plugin uses SQLite with this schema:

```sql
CREATE TABLE whitelist_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    discord_id TEXT UNIQUE NOT NULL,
    discord_tag TEXT NOT NULL,
    minecraft_username TEXT NOT NULL,
    request_time TEXT NOT NULL,
    status TEXT DEFAULT 'PENDING',
    processed_by TEXT,
    processed_time TEXT
);
```

**Database Location:** `plugins/WhitelistBot/whitelist.db`

## Troubleshooting

### Common Issues

#### Bot Not Connecting
- **Check token**: Verify Discord token in config.yml
- **Check intents**: Enable required intents in Discord Developer Portal
- **Check logs**: Look for connection errors in server console

#### Slash Commands Not Appearing
- **Wait time**: Commands can take up to 1 hour to sync
- **Permissions**: Ensure bot has `Use Slash Commands` permission
- **Restart**: Try restarting Discord client

#### Buttons Not Working
- **Bot permissions**: Verify bot can send messages and embeds
- **Channel IDs**: Double-check channel IDs in configuration
- **Role permissions**: Ensure allowed roles are correctly configured

#### Ticket Creation Failing
- **Category ID**: Verify ticket category ID in config
- **Bot permissions**: Bot needs `Manage Channels` and `Manage Permissions`
- **Category permissions**: Bot must have access to the category

### Error Messages

| Error | Cause | Solution |
|-------|--------|-----------|
| "Request channel not found" | Invalid channel ID | Check `discord-channel-request` ID |
| "Admin channel not found" | Invalid channel ID | Check `discord-channel-admin` ID |
| "Ticket category not found" | Invalid category ID | Check `discord-ticket-category` ID |
| "Failed to create ticket channel" | Missing permissions | Grant `Manage Channels` permission |
| "You don't have permission" | User lacks required role | Add user to `allowed-roles` |

### Debug Mode

Enable detailed logging by adding to config.yml:
```yaml
database:
  debug-sql: true
```

### Reset Database

To reset all data:
1. Stop server
2. Delete `plugins/WhitelistBot/whitelist.db`
3. Start server (creates fresh database)

## Development

### Project Structure

```
src/main/java/fr/yourserver/whitelistbot/
├── WhitelistBot.java              # Main plugin class
├── commands/
│   └── LookupCommand.java         # /lookup command
├── database/
│   ├── DatabaseManager.java      # SQLite management
│   └── WhitelistRequest.java     # Data model
└── discord/
    └── DiscordBotManager.java     # Discord bot logic
```

### Building from Source

```bash
# Clone repository
git clone https://github.com/yourusername/WhitelistBot.git
cd WhitelistBot

# Compile
mvn clean package

# The JAR will be in target/WhitelistBot-1.0.0.jar
```

### Dependencies

Major dependencies included:
- **JDA 5.0.0-beta.18** - Discord API
- **SQLite JDBC 3.44.1.0** - Database
- **Paper API 1.20.4** - Minecraft API
- **SLF4J 1.7.36** - Logging

### API Usage

The plugin provides events for other plugins:
- `WhitelistRequestEvent` - When request is submitted
- `WhitelistApprovedEvent` - When request is approved
- `WhitelistDeniedEvent` - When request is denied

### Custom Package Name

To use your own package name:
1. Rename all `fr.yourserver` packages to your domain
2. Update `pom.xml` groupId
3. Update all import statements
4. Recompile with `mvn clean package`

## Version Compatibility

| Minecraft Version | Plugin Version | Status |
|------------------|----------------|--------|
| 1.20.1 | 1.0.0+ | ✅ Tested |
| 1.20.4 | 1.0.0+ | ✅ Recommended |
| 1.20.6 | 1.0.0+ | ✅ Compatible |
| 1.21.x | 1.0.0+ | ✅ Compatible |

## Performance

### Resource Usage
- **Memory**: ~50MB additional RAM usage
- **Storage**: ~1MB database for 1000 requests
- **Network**: Minimal Discord API calls

### Optimization Tips
- Use Paper instead of Spigot for better performance
- Regularly clean old database entries
- Monitor Discord API rate limits

## Security

### Best Practices
- **Restrict roles**: Only give necessary Discord roles admin access
- **Regular backups**: Backup database regularly
- **Monitor logs**: Watch for suspicious activity
- **Update regularly**: Keep plugin and dependencies updated

### Data Protection
- Player data stored locally in SQLite
- No data sent to external services except Discord
- Database contains: Discord ID, username, Minecraft username, timestamps

## Contributing

Contributions are welcome! Please follow these guidelines:

### Reporting Issues
- Use the issue tracker
- Include server/plugin versions
- Provide error logs and configuration (remove sensitive data)
- Describe steps to reproduce

### Pull Requests
- Fork the repository
- Create feature branch: `git checkout -b feature/amazing-feature`
- Make your changes
- Add tests if applicable
- Update documentation
- Commit: `git commit -m 'Add amazing feature'`
- Push: `git push origin feature/amazing-feature`
- Create Pull Request

### Code Style
- Follow Java naming conventions
- Add JavaDoc comments for public methods
- Keep methods focused and small
- Handle exceptions appropriately

## Changelog

### v1.0.0 (Latest)
- ✅ Initial release
- ✅ Discord bot integration
- ✅ Interactive embeds and buttons
- ✅ SQLite database
- ✅ Ticket system
- ✅ Slash commands
- ✅ Anti-spam protection
- ✅ Private message notifications

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 WhitelistBot Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Support

- **Documentation**: This README
- **Issues**: [GitHub Issues](https://github.com/VrcFoss/Whitelistebot/issues)

## Acknowledgments

- **JDA Team** - Java Discord API
- **PaperMC** - Modern Minecraft server software
- **SQLite** - Lightweight database engine
- **Minecraft Community** - Inspiration and feedback

---

**Made with ❤️ for the Minecraft community**
