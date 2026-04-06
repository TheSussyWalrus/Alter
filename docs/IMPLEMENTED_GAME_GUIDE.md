# Alter Implemented Game Guide

This document reflects the code that is actually present in this repository as of 2026-04-06. It is intentionally split into two views:

- player guide: what you can do in-game right now
- developer guide: what is implemented, partially implemented, or still missing compared to Old School RuneScape

If a system is mentioned here, it is because I found code for it. If a system is called out as partial or missing, that means the current code either stubs it, comments it out, or only implements a thin slice of the OSRS behavior.

## Quick Summary

The current game is a Lumbridge-centered OSRS sandbox with a strong engine foundation and a narrow set of live gameplay loops:

- automatic account creation on first login
- movement, routing, interaction, object handling, NPC interaction, and combat
- banking, trading, shops, prayer, run energy, and death handling
- thieving as the clearest full skill loop
- several item mechanics such as teleport tabs, food, prayer scrolls, essence pouches, rock cake, mystery boxes, and a few equipment effects
- a lot of interface scaffolding that looks complete but is not always backed by real gameplay

This is not a full OSRS recreation yet. It is a playable base with some genuinely finished systems and a large amount of partial UI or placeholder logic around them.

## Player Guide

### Getting Started

- Log in with any unused username and password.
- The first successful login auto-creates your account.
- Your login name becomes your in-game display name.
- You spawn at the home tile configured in `game.yml`, which defaults to `3218, 3218, 0`.
- New accounts receive a starter kit:
  - 5 logs
  - 1 tinderbox
  - 5 bread
  - 1 bronze pickaxe
  - 1 bronze dagger
  - 1 knife

### Movement and Interaction

You can already do the normal OSRS basics:

- walk and run
- click the minimap and world map
- interact with NPCs, objects, doors, gates, ladders, stairs, and some trapdoors
- cross the Wilderness ditch
- use the Al Kharid gate shortcut by paying 10 coins

Run energy is live. You can toggle it from the minimap orb or the settings tab.

### Combat

Combat is broadly working for both players and NPCs.

What is currently supported:

- melee, ranged, and magic combat logic
- attack styles and auto-retaliate
- target acquisition and pathing toward combat targets
- special attacks for:
  - abyssal bludgeon
  - abyssal dagger
  - Armadyl godsword
  - dragon dagger
  - dragon pickaxe
- Osmumten's fang has weapon-specific handling

You can attack players with the normal `Attack` interaction, and NPC aggression/response is wired into the combat loop.

### Prayer

Prayer is one of the more complete subsystems.

You can:

- toggle normal prayers
- use quick prayers
- drain prayer over time
- keep Protect Item active
- unlock and use the prayers:
  - Preserve
  - Rigour
  - Augury

Prayer scrolls are implemented as read-and-consume unlock items:

- Dexterous prayer scroll unlocks Rigour
- Arcane prayer scroll unlocks Augury
- Torn prayer scroll unlocks Preserve

Prayers are turned off on death and logout.

### Banking

Banking is functional and fairly complete.

You can:

- open banks from booths and banker NPCs
- use deposit boxes
- deposit inventory and equipment
- withdraw as item or note
- use quantity modes, withdraw-X, placeholders, and bank tabs
- move items between tabs

Banking looks close to the real interface, but the bank PIN screen is only a UI shell right now.

### Trading

Player-to-player trading works.

You can:

- request trades
- accept and decline trades
- offer and remove items
- use the standard two-step confirmation flow

The trade closes on logout or if the interface is dismissed.

### Shops

The shop system works.

Known Lumbridge shops currently wired in:

- Lumbridge General Store
- Bob's Brilliant Axes

You can open shops from NPC interactions and buy/sell with coin currency.

### Thieving

Thieving is the clearest full skill loop currently implemented.

You can:

- pickpocket supported NPCs
- steal from stalls
- loot chests
- search for traps on trapped chests

The dedicated `::thieving` command sends you to a test area with representative NPCs, stalls, and chests.

That test area includes examples such as:

- men, farmers, HAM members, warriors, rogues, cave goblins, master farmers, guards, menaphite thugs, knights, paladins, and heroes
- vegetable, baker's, tea, silk, market, seed, fur, fish, silver, spice, and gem stalls
- multiple chest tiers

### Items and Consumables

Several item behaviors are live:

- food consumption works for a broad food table
- prayer scrolls unlock prayers
- teleport tabs work for many destinations
- essence pouches can be filled, checked, and emptied
- the dwarven rock cake behaves as expected for damage-based self-harm
- mystery boxes give random items
- spade digging advances a hardcoded clue-style item chain
- some equipment pieces play custom visual effects when equipped
- ring of wealth has a few equipment options and teleport handling
- dragon pickaxe has its special attack
- shattered cane has its emotes when the full outfit is worn

Teleport tabs currently cover destinations like Varrock, Falador, Lumbridge, Camelot, Ardougne, Watchtower, Rimmington, Taverley, Pollnivneach, Hosidius, Rellekka, Brimhaven, Yanille, Trollheim, Catherby, Barbarian, Draynor Manor, Fishing Guild, Khazard, and Mind Altar.

### Interface Tabs

A lot of the OSRS gameframe is present and usable:

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

Important caveat: several of these tabs are mostly interface scaffolding and do not yet represent full OSRS gameplay depth.

### Commands You Can Use

Player-facing commands currently found in the codebase:

- `::home` teleports you home
- `::thieving` teleports you to the thieving test area
- `::yell <message>` broadcasts a global message with rank formatting
- `::empty` clears your inventory

There are also utility/debug commands such as `::getdist`, `::gc`, `::heap`, and `::qutest`, plus admin/dev commands for spawning, teleports, bank resets, and similar testing tasks. Treat those as test tooling, not production gameplay.

## Developer Guide

### What Is Implemented

The strongest live systems in the codebase are:

- login, account bootstrap, and save serialization
- world loading and plugin dispatch
- movement, route finding, interaction handling, and object/NPC clicks
- melee, ranged, magic, and special attacks
- prayer toggles, drain, and unlock scrolls
- banking with tabs and placeholders
- player trading
- shops
- run energy
- food and several consumable item behaviors
- thieving pickpocketing, stalls, and chests
- a small set of Lumbridge area NPCs, shops, and object interactions

### What Is Partial Or Stubbed

These systems exist, but not as full OSRS mechanics:

- Bank PIN is UI only
  - the interface opens
  - there is no real PIN setup, verification, or enforcement
- Kept on Death is a placeholder
  - it renders dummy items and placeholder text
  - it does not calculate actual risk or item loss
- Looting bag storage is disabled
  - the UI opens
  - check/deposit/bank flows exist
  - actually storing items into the bag returns `false`
- Social systems are incomplete
  - friends/ignores have partial list handling
  - private messaging logs intent but does not deliver messages
  - clan chat join/leave currently throws an unhandled runtime exception
- First-login appearance onboarding is commented out
  - the login appearance flow is not active
- Water-container logic is commented out
  - the plugin file exists, but the behavior is disabled
- Amulet of Glory teleporting is commented out
  - the file is present, but the actual teleport mechanic is not live
- Ancient Wyvern Shield combination logic is commented out
  - only the file shell remains
- Some UI tabs are decorative or incomplete
  - character summary uses hardcoded quest/diary counts
  - skill guides open, but do not imply full skill implementation
  - account tab buttons mostly report placeholder information
  - equipment stats still contains placeholder text for some fields

### What Is Missing Compared To OSRS

Do not treat these as implemented unless new code lands:

- quests
- quest progression
- diaries
- slayer
- minigames
- Grand Exchange
- tutorial island or equivalent real onboarding
- clan chat
- real private messaging
- most social features beyond simple local list bookkeeping
- most skill loops outside thieving
- farming
- woodcutting
- firemaking
- fishing
- cooking as a full progression loop
- mining and smithing as full progression loops
- runecrafting as a full progression loop
- crafting as a full progression loop
- fletching
- herblore
- agility
- hunter
- construction
- clue scroll system beyond the hardcoded spade chain
- full wilderness and PvP parity
- full item-loss parity on death
- broad jewelry teleport parity
- broader special-item coverage

The repository does contain fragments of some of these areas, but not enough to call them live OSRS equivalents.

### Module Split

- `game-server`: engine, login, save system, world, message handling, entity logic
- `game-plugins`: gameplay content and most feature logic
- `game-api`: shared/generated config references
- `plugins`: support modules such as filestore/tooling

### Save Model

JSON is the default save format if `saveFormat` is omitted in `game.yml`.

The current save structure includes collections for things like:

- accounts
- details
- skills
- containers
- attributes
- timers
- varps
- appearance

### Useful Development Notes

- The server targets revision `228`.
- The default game port is `43594`.
- The install flow generates RSA material and runs the map decrypter.
- `dev-settings.yml` is for debug toggles such as packet, button, spell, and item-action logging.
- Some Docker files appear stale relative to the current module names.

### Recommended Next Priorities

If the goal is to turn this into a more coherent playable server, the highest-value next steps are:

1. Decide whether the project is primarily a Lumbridge sandbox, a thieving-focused test server, or a broader OSRS base.
2. Replace placeholder UI screens like Bank PIN and Kept on Death with real logic or hide them.
3. Finish first-login appearance/onboarding.
4. Fix the social stack or remove the broken pieces.
5. Either complete or disable incomplete item mechanics such as amulet teleports and water containers.
6. Pick one more full skill loop after Thieving and implement it end to end.
