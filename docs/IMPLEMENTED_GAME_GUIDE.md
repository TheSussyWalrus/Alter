# Alter Player and Developer Guide

Regenerated from the current source snapshot on May 10, 2026.

This guide has two jobs:

- player view: what you can actually do in game right now
- developer view: what is implemented, what is still a shell, and where the code still differs from Old School RuneScape

I only describe something as live when I found concrete source for it in the current tree. If a feature is marked partial or missing, that means I found a stub, commented-out path, runtime exception, hardcoded placeholder, or no live player loop at all.

## Player Guide

### What Alter Is Right Now

Alter is a playable OSRS-inspired sandbox. It is not feature-complete Old School RuneScape, but it already supports a real login-to-play loop.

You can currently:

- log in and start playing immediately
- move, click-to-walk, click the minimap, and interact with NPCs, objects, ground items, and other players
- fight with melee, ranged, and magic
- use prayers, quick prayers, and prayer unlock scrolls
- bank, trade, use shops, and browse a large slice of the familiar gameframe
- train a meaningful set of classic skills
- use a small set of player commands

### Getting Started

- New accounts auto-register on first login.
- The first login stores the password as a bcrypt hash for that username.
- New players spawn at the configured home tile in `game.yml`.
- New accounts receive a starter kit on first login.
- The starter kit currently includes 5 logs, 1 tinderbox, 5 bread, 1 bronze pickaxe, 1 bronze dagger, and 1 knife.
- The login username becomes the initial in-game display name.
- The character-creation flow is still commented out, so new accounts do not go through a forced appearance setup.
- I did not find a live tutorial island flow in this snapshot.

### Movement And World Interaction

- You can walk and run.
- You can click to move.
- You can click the minimap to move.
- You can interact with NPCs, objects, ground items, and other players.
- Common object interaction paths such as doors, gates, ladders, banks, and deposit boxes are wired up.
- Run energy works and can be toggled from the minimap orb or the settings tab.
- Run energy recovery is affected by your Agility level, even though Agility itself does not have a live training loop yet.
- The world map opens and tracks your current tile.

### Combat

- Melee, ranged, and magic combat are playable.
- Attack styles work.
- Autocast support exists.
- Combat formulas exist for melee, ranged, and magic.
- Special attacks are implemented for a limited weapon set.

Live special attack support found in source:

- dragon dagger
- abyssal dagger
- abyssal bludgeon
- Armadyl godsword
- dragon pickaxe

The combat layer also has item-specific behavior hooks, but it is not trying to be full OSRS parity yet.

### Prayer

- You can toggle prayers on and off.
- Quick prayers work.
- Prayer drains over time.
- Protect Item works.
- Rigour, Augury, and Preserve can be unlocked from prayer scrolls.
- Prayer turns off on death and logout.

Prayer scrolls behave as consumable unlocks:

- Dexterous prayer scroll unlocks Rigour
- Arcane prayer scroll unlocks Augury
- Torn prayer scroll unlocks Preserve

Chivalry and Piety are present, but they are still gated by a quest-state varbit rather than a real quest system.

### Magic And Teleports

- The magic system loads spell metadata from cache and checks spellbook, level, and rune requirements.
- Standard, ancient, lunar, and Arceuus teleport spells are present.
- Teleport tablets work for a large set of destinations.
- Teleports are blocked in the wilderness when the teleport type says they should be.

Teleport tabs currently cover:

- Varrock
- Falador
- Lumbridge
- Camelot
- Ardougne
- Watchtower
- Rimmington
- Taverley
- Pollnivneach
- Hosidius
- Rellekka
- Brimhaven
- Yanille
- Trollheim
- Catherby
- Barbarian
- Kharyrll
- Lumberyard
- Draynor Manor
- Fishing Guild
- Khazard
- Mind Altar

### Banking, Trading, And Shops

- Banks open from banker NPCs and bank booths.
- Deposit boxes work.
- You can deposit inventory and equipment.
- You can withdraw items as items or notes.
- Quantity modes and withdraw-X are supported.
- Bank placeholders are supported.
- Bank tabs can be rearranged.
- The incinerator toggle exists.
- Bank PIN is only a UI shell. It opens, but there is no setup, verification, or enforcement yet.

Player-to-player trade works as a two-step trade.

- you can request trades
- you can offer and remove items
- you can accept or decline
- you move from the offer screen to the confirmation screen

NPC shops are usable.

- you can buy and sell with coins
- shop UIs support value checks and item examination
- some shops use item-based currency instead of coins

### Skills

The strongest skill coverage is in the classic gathering, processing, and thieving loops.

Live skill loops found in this snapshot:

- woodcutting
- mining
- fishing
- cooking
- crafting
- fletching
- firemaking
- herblore
- smithing
- slayer
- thieving

Thieving is especially fleshed out. You can:

- pickpocket supported NPCs
- steal from stalls
- loot supported chests
- search and disarm trapped chests
- use the thieving test area

What the live skill loops currently cover in practice:

- Woodcutting, mining, fishing, firemaking, and cooking are production-style loops with level checks, animations, and rewards.
- Crafting covers several production families, including gems, leather, jewelry, stringing, spinning, and pottery.
- Fletching covers log cutting, stringing, and assembly recipes.
- Herblore covers cleaning herbs, unfinished potions, and finished potions.
- Smithing covers smelting and forging.
- Slayer has a full task assignment and kill-tracking loop with streaks and points.

Two skill areas have package scaffolding but no live player loop:

- agility
- farming

I did not find live runecrafting, construction, or hunter gameplay loops in this snapshot, even though some UI pieces and command aliases can still suggest broader coverage.

### Items And Special Mechanics

- Food consumption works for a broad food table.
- Prayer scrolls unlock prayers.
- Teleport tabs work for many destinations.
- Ring of wealth teleports are implemented.
- Dwarven rock cake behaves like the OSRS item and damages you when eaten or guzzled.
- The looting bag interface exists, but actual bag storage is disabled.
- Amulet of Glory teleports are not live.
- The Ancient Wyvern Shield plugin is empty.
- Elemental and mind shields only play equip animation and graphic hooks.
- The shattered cane has emote options when worn with the full tier 3 shattered relic hunter outfit.

### Interface Tabs

A lot of the OSRS gameframe is present.

Live or meaningfully wired tabs include:

- combat options
- inventory
- worn equipment
- prayer
- magic
- emotes
- friends and ignores
- account management
- character summary
- settings and keybinds
- world map
- XP drops and XP settings
- skill guides
- item sets
- tournament supplies
- price guide
- kept-on-death view

Useful caveat: several of these tabs are interface scaffolding first and gameplay second. They open, but not every button or counter is backed by full OSRS behavior.

The most useful tabs right now are:

- skill guides, which switch between the available skill guide interfaces
- price guide, which supports search and item value lookup
- world map, which opens and updates to your current position
- kept-on-death, which shows the death-related UI but does not imply OSRS item-loss parity is complete
- character summary, which shows quest and diary panes but still uses placeholder counts for progression

### Social

Public chat exists.

- The friends and ignores tabs open.
- Friend and ignore add/remove handlers exist.
- Friend and ignore entries are checked against saved account data.
- The friend list push path is still partially stubbed.
- Private messages are not a finished social system.
- Clan chat join/leave is wired to the network layer, but the handler currently throws a runtime exception.

Treat social features as partial, not OSRS-complete.

### Commands

Player-facing shortcuts currently wired:

- `::home` teleports you to the configured home tile
- `::thieving` teleports you to the thieving test area
- `::yell <message>` broadcasts a server-wide message with rank formatting
- `::empty` clears your inventory

## Developer Guide

### What Is Implemented

The current codebase has real implementations for:

- login and world registration
- automatic first-login account creation
- starter items for new accounts
- default gameframe setup on login
- movement, routing, and interaction handlers
- combat targeting, attack styles, formulas, ranged/magic/melee strategies, and a limited set of special attacks
- prayer activation, drain, quick prayers, and unlock scrolls
- run energy drain and toggling
- item containers, inventory/equipment/bank synchronization, and note handling
- player trading with staged confirmation
- shops with coins and alternate currencies
- public chat and basic friend/ignore list plumbing
- a large set of item interactions and object/NPC handlers
- many interface tabs and modal overlays
- a plugin-driven content architecture
- slayer assignment, task tracking, and reward progression
- persistent state models used by some gameplay systems

This is enough to support a real game loop, even though a lot of OSRS parity is still missing.

### What Is Partial Or Shell-Only

These are the clearest gaps found in the current snapshot:

- Appearance setup is commented out, so new accounts do not go through a forced character creation flow.
- Private messaging is only partially wired; message delivery is not finished.
- Clan chat join/leave currently throws `RuntimeException("Unhandled.")`.
- Bank PIN is a UI shell only.
- Looting bag storage is disabled because the store path returns `false`.
- Amulet of Glory teleport logic is commented out.
- The Ancient Wyvern Shield plugin is empty.
- Elemental and mind shields only play VFX hooks.
- Agility and farming have package scaffolding but no live player loop.
- I did not find a live runecrafting skill loop at all.
- Character summary quest and diary counters are hardcoded placeholders rather than real progression counts.
- Some interface tabs exist without complete backend behavior.
- Some bank tab menu actions are still unimplemented.

### What Is Not Implemented Yet

These are the biggest OSRS systems that are not fully present in this snapshot:

- quests as a real progression system
- achievement diaries as a real progression system
- tutorial island or equivalent onboarding
- the Grand Exchange
- full private messaging
- clan chat
- full construction
- full hunter
- full runecrafting
- full PvP and wilderness parity
- OSRS-accurate item loss on death

### OSRS Parity Notes

The project is clearly aiming at OSRS-like behavior, but it is not trying to be fully equivalent yet.

Implemented well enough to feel familiar:

- combat flow
- prayer drain and quick-prayer behavior
- banking with placeholders, tabs, notes, and quantity modes
- trading
- shops
- most of the common movement and object interaction patterns
- many familiar item interactions and teleports

Still divergent from OSRS:

- some systems are placeholders rather than full mechanics
- some interfaces exist without complete backend behavior
- some item interactions are represented only by effects or commentary
- some content is hardcoded for test or developer convenience

### Concrete Source Notes

- The login pipeline sets up the default gameframe and UI state in `game-plugins/src/main/kotlin/org/alter/plugins/content/OSRSPlugin.kt`.
- The starter kit is in `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/starter/StarterKitPlugin.kt`.
- Appearance setup is commented out in `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/appearance/LoginAppearancePlugin.kt` and `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/appearance/AppearanceInterfacePlugin.kt`.
- Bank PIN is only a UI initializer in `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/bankpin/BankPinPlugin.kt`.
- Looting bag storage is intentionally stubbed in `game-plugins/src/main/kotlin/org/alter/plugins/content/items/lootingbag/LootingBagPlugin.kt`.
- Glory teleports are commented out in `game-plugins/src/main/kotlin/org/alter/plugins/content/items/amuletofglory/AmuletOfGloryPlugin.kt`.
- The dragon pickaxe special attack is registered in `game-plugins/src/main/kotlin/org/alter/plugins/content/items/DragonPickaxePlugin.kt`.
- The main combat special-attacks registry lives in `game-plugins/src/main/kotlin/org/alter/plugins/content/combat/specialattack/SpecialAttacks.kt`.
- Prayer drain, quick prayers, and unlock checks live in `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/prayer/Prayers.kt` and `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/prayer/PrayersPlugin.kt`.
- Run energy drain and regeneration live in `game-plugins/src/main/kotlin/org/alter/plugins/content/mechanics/run/RunEnergy.kt`.
- Teleport tabs and teleport spell metadata are in `game-plugins/src/main/kotlin/org/alter/plugins/content/items/consumables/teletabs/TeleportTabPlugin.kt` and `game-plugins/src/main/kotlin/org/alter/plugins/content/magic/teleports/TeleportSpell.kt`.
- Private messages are still incomplete in `game-server/src/main/kotlin/org/alter/game/model/social/Social.kt` and `game-server/src/main/kotlin/org/alter/game/message/handler/MessagePrivateSenderHandler.kt`.
- Clan chat is explicitly unhandled in `game-server/src/main/kotlin/org/alter/game/message/handler/ClanJoinChatLeaveHandler.kt`.
- Slayer tasks are implemented in `game-plugins/src/main/kotlin/org/alter/plugins/content/skills/slayer/SlayerPlugin.kt`.
- Pickpocket thieving is implemented in `game-plugins/src/main/kotlin/org/alter/plugins/content/skills/thieving/pickpocket/PickpocketPlugin.kt`.
- Stall thieving is implemented in `game-plugins/src/main/kotlin/org/alter/plugins/content/skills/thieving/stall/StallThievingPlugin.kt`.
- Chest thieving is implemented in `game-plugins/src/main/kotlin/org/alter/plugins/content/skills/thieving/chest/ChestThievingPlugin.kt`.
- The price guide is functional in `game-plugins/src/main/kotlin/org/alter/plugins/content/interfaces/gameframe/tabs/worn_equipment/priceguide/PriceGuidePlugin.kt`.
- Kept-on-death is mostly a shell in `game-plugins/src/main/kotlin/org/alter/plugins/content/interfaces/gameframe/tabs/worn_equipment/kod/KeptOnDeathPlugin.kt`.

### What To Watch Next

If you are using this guide as a development checklist, the next high-value gaps are:

- finish or replace the bank PIN flow
- implement real private messaging and clan chat
- decide whether looting bag storage should stay disabled or be completed
- either implement or remove the commented-out appearance and glory systems
- add or explicitly document the missing skill loops, especially runecrafting, agility, farming, construction, and hunter
