# In-Game Developer And Admin Tools

Commands are entered in-game with the `::` prefix. Access is controlled by privilege powers such as `admin` and `dev`; owner accounts normally have the broadest access.

This document covers the command plugins currently present in `game-plugins`.

## Common Commands

These are available without admin/dev power unless noted in code.

| Command | Usage | Description |
| --- | --- | --- |
| `::home` | `::home` | Teleports to the configured home tile. |
| `::thieving` | `::thieving` | Teleports to the thieving test area. |
| `::yell` | `::yell message` | Broadcasts a chat message to all players with rank formatting. |
| `::empty` | `::empty` | Clears your inventory. |
| `::tabreset` | `::tabreset` | Resets bank tab varbits back to the main tab. |
| `::getdist` | `::getdist x z` | Prints distance from your tile to a target tile. |
| `::resetface` | `::resetface` | Resets facing and interaction state. |
| `::getvarp` | `::getvarp varpId` | Reads the current value of a varp. |

## Admin Tools

These require `admin` power.

| Command | Usage | Description |
| --- | --- | --- |
| `::item` | `::item itemId [amount]` | Spawns an item by cache id. If parsing fails, opens the item search UI. |
| `::spawn` | `::spawn` | Opens the tradeable item-spawn search UI. |
| `::spawn2` | `::spawn2` | Opens the item-spawn search UI including untradeables. |
| `::food` | `::food` | Fills free inventory slots with manta rays. |
| `::npc` | `::npc npcId` | Spawns an NPC by cache id on your tile. |
| `::tele` | `::tele x z [height]` | Teleports to coordinates. |
| `::mypos` | `::mypos` | Prints your current tile and region. |
| `::coords` | `::coords` | Alias for `::mypos`. |
| `::pos` | `::pos` | Alias for `::mypos`. |
| `::edge` | `::edge` | Teleports to Edgeville. |
| `::varrock` | `::varrock` | Teleports to Varrock. |
| `::falador` | `::falador` | Teleports to Falador. |
| `::lumbridge` | `::lumbridge` | Teleports to Lumbridge. |
| `::yanille` | `::yanille` | Teleports to Yanille. |
| `::gnome` | `::gnome` | Teleports to Gnome Stronghold. |
| `::seers` | `::seers` | Teleports to Seers' Village. |
| `::slayer_tower` | `::slayer_tower` | Teleports near the Slayer Tower. |
| `::legends` | `::legends` | Teleports near the Legends' Guild. |
| `::shop` | `::shop shopIdOrName` | Opens a shop by id or name. |
| `::openshop` | `::openshop shopIdOrName` | Alias for `::shop`. |
| `::store` | `::store shopIdOrName` | Alias for `::shop`. |
| `::broadcast` | `::broadcast message` | Sends a broadcast message to all players. |
| `::setrunenergy` | `::setrunenergy amount` | Sets your run energy. |
| `::transmog` | `::transmog npcId` | Sets your transmog NPC id. |
| `::obank` | `::obank` | Opens your bank. |
| `::tournament` | `::tournament` | Opens the tournament supplies interface. |
| `::img` | `::img id` | Shows a chat image by id. |
| `::slayer` | `::slayer`, `::slayer check`, `::slayer turael` | Checks your current Slayer task or assigns one from `turael`, `mazchna`, or `duradel`. |

## Developer Tools

These require `dev` power.

### Account And Movement

| Command | Usage | Description |
| --- | --- | --- |
| `::setlvl` | `::setlvl skill level` | Sets a skill level. Skill can be an id or name; aliases include `slay`, `pray`, `mage`, `fish`, `herb`, `rc`, and `fm`. |
| `::master` | `::master` | Masters the account. |
| `::reset` | `::reset` | Resets all skills to 1, except Hitpoints to 10. |
| `::openbank` | `::openbank` | Opens your bank. |
| `::emptybank` | `::emptybank` | Clears your bank and resets bank tab roots. |
| `::noclip` | `::noclip` | Toggles no-clip movement. |
| `::invisible` | `::invisible` | Toggles player invisibility. |
| `::teler` | `::teler regionId` | Teleports to the base tile of a region. |
| `::emotes` | `::emotes` | Unlocks all emotes. |
| `::spellbook` | `::spellbook id` | Sets spellbook varbit `4070`; ids above `3` are rejected. |

### World And Entity Debugging

| Command | Usage | Description |
| --- | --- | --- |
| `::find` | `::find item keyword`, `::find npc keyword`, `::find object keyword` | Searches cache definitions by name. Aliases: `i`, `n`, `o`, `obj`. |
| `::search` | `::search item keyword` | Alias for `::find`. |
| `::obj` | `::obj objectId [type] [rot]` | Spawns a dynamic object on your tile. Defaults to type `10`, rot `0`. |
| `::removeobj` | `::removeobj` | Removes the first static/dynamic object on your tile. |
| `::aboutobj` | `::aboutobj` | Prints object id, type, and rotation for an object on your tile. |
| `::removenpc` | `::removenpc` | Removes the first NPC on your tile. |
| `::clip` | `::clip` | Prints tile collision flags and neighboring walk/projectile blocking. |
| `::col_grid` | `::col_grid player_name` | Logs a 25x25 collision grid around a player. Use underscores for spaces. |
| `::reloaditems` | `::reloaditems` | Reloads item metadata definitions. |

### Combat And Resources

| Command | Usage | Description |
| --- | --- | --- |
| `::hitme` | `::hitme hitType [damage]` | Applies a hitsplat to yourself. |
| `::max` | `::max` | Prints max hit and accuracy for melee, ranged, and magic against your current target, or yourself if no target exists. |
| `::infhp` | `::infhp` | Toggles infinite HP. |
| `::infpray` | `::infpray` | Toggles infinite prayer. |
| `::infrun` | `::infrun` | Toggles infinite run energy. |
| `::infrunes` | `::infrunes` | Toggles infinite runes. |

### Client, Interface, And Asset Debugging

| Command | Usage | Description |
| --- | --- | --- |
| `::anim` | `::anim id` | Plays an animation on your player. |
| `::gfx` | `::gfx id [height]` | Plays a graphic on your player. Defaults height to `100`. |
| `::sound` | `::sound id` | Plays a sound effect. |
| `::song` | `::song id` | Plays a music track. |
| `::chatanim` | `::chatanim animationId npcId` | Opens a test NPC chat dialogue with the chosen head animation. |
| `::interface` | `::interface interfaceId` | Opens an interface on the main screen. |
| `::openinterface` | `::openinterface interfaceId parent child [clickable] [modal]` | Opens an interface at a specific parent/child slot. |
| `::script` | `::script scriptId [args...]` | Runs a client script. Numeric args are passed as integers; others as strings. |
| `::openurl` | `::openurl url` | Opens a URL in the client, adding `https://` if no scheme is detected. |
| `::inv` | `::inv inventoryId itemId...` | Sends a custom inventory update packet to the client. |
| `::sets` | `::sets` | Opens the item sets interface. |

### Varps And Varbits

| Command | Usage | Description |
| --- | --- | --- |
| `::varp` | `::varp varpId value` | Sets a varp. |
| `::varbit` | `::varbit varbitId value` | Sets a varbit. |
| `::getvarbit` | `::getvarbit varbitId` | Reads a varbit. |
| `::getvarbits` | `::getvarbits varpId` | Lists varbits backed by a varp, including bit ranges and current values. |
| `::logchanges` | `::logchanges` | Toggles in-game varp/varbit change logging. |

### Item Discovery Helpers

| Command | Usage | Description |
| --- | --- | --- |
| `::getitems` | `::getitems keyword` | Adds 10 of each matching item to your bank, matching item name or examine text. |
| `::getitemstype` | `::getitemstype equipSlotId` | Adds 10 of each item for an equipment slot to your bank. |
| `::getitemlist` | `::getitemlist` | Registered but currently empty. |

### Server Debugging

Use these carefully on a live development server.

| Command | Usage | Description |
| --- | --- | --- |
| `::reboot` | `::reboot cycles` | Starts the reboot timer. |
| `::shutdown` | `::shutdown cycles` | Starts the reboot timer, disconnects players, then exits the server process. |
| `::gc` | `::gc` | Requests JVM garbage collection. |
| `::heap` | `::heap` | Writes a heap dump to `../dump.hprof`. |
| `::qutest` | `::qutest` | Stress-test command that queues 10,000,000 coin-add actions. Avoid during normal testing. |

## Quick Testing Recipes

Useful combinations while building content:

| Goal | Commands |
| --- | --- |
| Find an item id and spawn it | `::find item small fishing net`, then `::item 303 1` |
| Move to Yanille hub | `::yanille` |
| Spawn a test NPC | `::npc npcId` |
| Check your current coordinates | `::pos` |
| Set up a skill test | `::setlvl slay 50`, `::slayer mazchna` |
| Inspect object bindings | Stand on the object tile, then `::aboutobj` |
| Check movement blocking | `::clip` |

