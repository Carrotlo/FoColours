![FoColours cover](docs/assets/cover.png)

# FoColours

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/focolours) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/focolours/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Configurable chat colours with player selection and administrator controls.

*Developed by Carrotio.*

## Features

- Gives players a GUI for selecting unlocked chat colours.
- Supports configurable solid colours, rainbow formatting, and a default colour.
- Lets administrators grant or remove colours and edit the available choices.
- Stores player selections and unlocks persistently.
- Includes optional PlaceholderAPI parsing and EzColors conversion controls.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/focolours). Check the [versions page](https://modrinth.com/plugin/focolours/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `PlaceholderAPI`

## Screenshots

![Manage FoColours settings.](docs/assets/screenshots/focolours-admin-editor.png)

*Manage FoColours settings.*

![Edit the available chat colours.](docs/assets/screenshots/focolours-colour-manager.png)

*Edit the available chat colours.*

![Let players select an unlocked colour.](docs/assets/screenshots/focolours-player-menu.png)

*Let players select an unlocked colour.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/focolours [reset|colour]` — Open the FoColours player menu or select a chat colour directly. Aliases: `/colours`, `/chatcolour`, `/chatcolor`.
- `/focoloursadmin <version|reload|editor|give|take|convertezcolors>` — Open the FoColours admin tools. Aliases: `/chatcolouradmin`.

### Permissions

- `focolours.use` — Allows using the FoColours player command. Default: `true`.
- `focolours.admin` — Allows using FoColours admin commands and editor features. Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

When PlaceholderAPI is installed, `%focolours_colour%` supplies the player's
current chat colour format code. Rainbow has no single format code; use the
Java API's `renderText` method for a rainbow-aware integration.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `features`, `chat`, `native-dialogs`, `compatibility`, `player-data`, `rainbow`, `editor`, `gui`, `colours`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

FoColours 1.8 exposes `me.foesio.foColours.api.FoColoursApi` through Bukkit's
`ServicesManager`. The service is registered after FoColours loads its colours and
player data, and is unregistered on disable. Obtain it when your plugin enables;
reacquire it after a plugin disable/enable cycle. Keep FoColours class references
in a separate integration class if FoColours is optional.

Build against the FoColours jar as a **provided** dependency; do not shade it.
The `me.foesio:FoColours:1.8` coordinates are from this project's `pom.xml`, not
a promise of a public Maven repository. Build and install the matching provider
locally, or use its jar in your own build setup.

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoColours</artifactId>
  <version>1.8</version>
  <scope>provided</scope>
</dependency>
```

For a `plugin.yml` consumer, use `softdepend: [FoColours]` when integration is
optional, or `depend: [FoColours]` when required. An optional consumer should
check for FoColours before loading the class that imports `FoColoursApi`.

```java
RegisteredServiceProvider<FoColoursApi> registration =
        Bukkit.getServicesManager().getRegistration(FoColoursApi.class);
if (registration != null) {
    FoColoursApi colours = registration.getProvider();
    String key = colours.playerColours(player).effectiveKey();
    ColourChangeResult result = colours.grantColour(player, "sunset");
}
```

### Queries and actions

- `enabledColours()` and `findEnabledColour(keyOrAlias)` return immutable
  definition snapshots. A removed, disabled, or unknown colour is absent.
- `playerColours(player)` returns the stored choice, the effective enabled key,
  and currently usable enabled keys for an **online** player. `effectiveKey()`
  is empty when no colours are enabled. Live EzColors permissions may affect
  access, so stored choice and effective colour can differ.
- `grantColour` and `takeColour` accept an `OfflinePlayer`. Grants are durable;
  taking a colour also revokes permission-derived access currently visible to
  FoColours. The configured default colour cannot be taken.
- `selectColour` and `resetColour` accept an online `Player`. Selection requires
  an enabled, unlocked colour. Reset honours `features.reset-enabled`.
- `renderText(player, text)` applies the player's effective colour, including
  rainbow, regardless of `chat.mode`. It strips existing colour codes first,
  matching FoColours chat formatting. With no enabled colours it returns the
  input unchanged.

Actions send no messages or sounds and do not check the **calling plugin's**
permissions. They return `ColourChangeResult`; a repeated action is `NO_CHANGE`.
Storage failure throws `IllegalStateException`, so it never reports success.
Calls on a disabled service throw `IllegalStateException`.

Call methods that touch a player from that player's owning server context.
Do not call them from an asynchronous chat callback. On regionized servers,
schedule with the target player's scheduler. Offline writes also require an
appropriate server scheduler context. The service does not block or hop threads.

### Events

`PlayerColourSelectionChangedEvent` carries UUID, old and new **stored** keys,
and `ColourChangeCause`. It fires after a changed selection is saved, including
selection, reset, revocation fallback, EzColors fallback, and reload fallback.

`PlayerColourAccessChangedEvent` carries UUID, colour key, new stored access
state (`isUnlocked()`), and cause. It fires after a stored grant, revocation, or
sync change is saved. It does not report every live permission change. Startup
data recovery, unchanged actions, and failed saves fire neither event.

Events are observational and cannot be cancelled. They run on the context that
performed the mutation; `isAsynchronous()` reflects dispatch context. A UUID
may belong to an offline player. Listeners must schedule Bukkit player/world
work onto the relevant owning context. Event payloads contain immutable values
and no internal data objects. The access event fires before the selection event
when one operation both revokes access and resets the selection.

### Event listener example

Inside a Bukkit `Listener` registered by your plugin (import this event class, `org.bukkit.event.EventHandler`, and `org.bukkit.Bukkit` where used):

```java
@EventHandler
public void onPlayerColourSelectionChanged(PlayerColourSelectionChangedEvent event) {
    Bukkit.getLogger().info("Selected colour: " + event.getNewKey());
}
```

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoColoursApi`

```java
List<ColourSnapshot> enabledColours();
Optional<ColourSnapshot> findEnabledColour(String);
PlayerColourSnapshot playerColours(Player);
ColourChangeResult grantColour(OfflinePlayer, String);
ColourChangeResult takeColour(OfflinePlayer, String);
ColourChangeResult selectColour(Player, String);
ColourChangeResult resetColour(Player);
String renderText(Player, String);
```

**Other public contract types:** `ColourChangeCause`, `ColourChangeResult`, `ColourSnapshot`, `ColourType`, `PlayerColourSnapshot`.

### Complete Bukkit event index

- `PlayerColourAccessChangedEvent`.
- `PlayerColourSelectionChangedEvent`.

</details>
