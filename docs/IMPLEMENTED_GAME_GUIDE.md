# Alter Implemented Game Guide

This document reflects the code that exists in this repository as of 2026-04-06. It is split into two views:

- Player guide: what you can actually do in-game right now
- Developer guide: what is implemented, partial, or still missing compared to Old School RuneScape

If a system is described as live, it is backed by code I found in this repo. If it is described as partial or missing, treat that as the current state unless you verify new changes later.

## Quick Status

Alter is best described as an OSRS sandbox with a solid engine and a small set of fully usable gameplay loops.

Live or broadly usable:

- login, account bootstrap, and save serialization
- movement, routing, object interaction, NPC interaction, and combat
- banking, trading, shops, prayer, run energy, and death handling
- thieving, which is the clearest fully playable skill loop
- several item mechanics such as teleport tabs, food, prayer scrolls, essence pouches, mystery boxes, and the spade dig chain
- a number of skilling modules for gathering and processing

Not OSRS-complete:

- quests, diaries, tutorial island, and most social systems
- Grand Exchange
- full skill parity across the whole game
- full item-loss parity on death
- clan chat and private messaging

## Player Guide

### Getting Started

- Log in with any username and password that is not already registered.
- The first successful login creates the account automatically.
- Your login name becomes your in-game display name.
- You spawn at the home tile configured in `game.yml`.
- New accounts receive a starter kit:
  - 5 logs
  - 1 tinderbox
  - 5 bread
  - 1 bronze pickaxe
  - 1 bronze dagger
  - 1 knife

### Movement and Interaction

You can already do the basic OSRS movement and click flow:

- walk and run
- click the minimap
- interact with NPCs and objects
- open and pass through many doors, gates, ladders, stairs, and trapdoors
- cross the Wilderness ditch
- use the Al Kharid gate shortcut by paying 10 coins

Run energy is active and can be toggled from the minimap orb or the settings tab.

### Combat

Combat is functional for both players and NPCs.

You can:

- melee, ranged, and magic attack
- use attack styles and auto-retaliate
- chase and path toward combat targets
- trigger special attacks for:
  - abyssal bludgeon
  - abyssal dagger
  - Armadyl godsword
  - dragon dagger
  - dragon pickaxe
- use Osmumten's fang handling that is wired separately

The combat loop supports normal player attacks and NPC aggression/response.

### Prayer

Prayer is one of the more complete subsystems.

You can:

- toggle prayers on and off
- use quick prayers
- drain prayer over time
- keep Protect Item active
- unlock and use Preserve, Rigour, and Augury

Prayer scrolls are consumable unlock items:

- Dexterous prayer scroll unlocks Rigour
- Arcane prayer scroll unlocks Augury
- Torn prayer scroll unlocks Preserve

Prayer is turned off on death and logout.

### Banking

Banking is functional and fairly complete.

You can:

- open banks from banker NPCs and bank booths
- use deposit boxes
- deposit inventory and equipment
- withdraw as items or notes
- use quantity modes and withdraw-X
- use placeholders
- move items between bank tabs
- rearrange tabs and item order
- toggle the bank incinerator option

The bank PIN screen opens, but it is only a UI shell right now.

### Trading

Player-to-player trading works as a two-step trade.

You can:

- request trades
- offer and remove items
- accept or decline
- move from the offer screen to the confirmation screen
- see item values during the trade

Trade closes if the session is declined, completed, or interrupted.

### Shops

The shop system is present and usable.

Known Lumbridge shops wired in the code include:

- Lumbridge General Store
- Bob's Brilliant Axes

You can open shops from NPC interactions and buy or sell with coins.

### Thieving

Thieving is the clearest complete skill loop in the current codebase.

You can:

- pickpocket supported NPCs
- steal from stalls
- loot chests
- search for and disarm trapped chests

The `::thieving` command sends you to a test area with representative content.

That test area includes examples such as:

- men, farmers, HAM members, warriors, rogues, cave goblins, master farmers, guards, menaphite thugs, knights, paladins, and heroes
- vegetable, baker's, tea, silk, market, seed, fur, fish, silver, spice, and gem stalls
- multiple chest tiers with trap and respawn handling

### Skilling

There are active content modules for several skills beyond thieving.

Confirmed resource or processing loops in the codebase include:

- woodcutting
- mining
- fishing
- cooking
- crafting
- fletching

The repository also contains modules for agility, farming, firemaking, herblore, smithing, and slayer, but those are not as clearly complete from a player-facing standpoint as the systems above. Treat them as narrower or more data-driven unless you test the specific content.

### Items and Special Mechanics

Several item behaviors are live:

- food consumption works for a broad food table
- prayer scrolls unlock prayers
- teleport tabs work for many destinations
- essence pouches can be filled, checked, and emptied
- mystery boxes give random items
- spade digging advances a hardcoded clue-style item chain
- the dragon pickaxe special attack is implemented

Teleport tabs currently cover destinations such as:

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
- Draynor Manor
- Fishing Guild
- Khazard
- Mind Altar

Looting bag note:

- the interface and banking flows exist
- actual bag storage is disabled in the current code

Amulet of Glory note:

- the old teleport implementation is still commented out
- do not expect live glory teleport behavior yet

### Interface Tabs

A lot of the OSRS gameframe is present:

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

There are also utility, debug, and admin commands in the repo for teleporting, spawning, resetting banks, changing stats, opening interfaces, and similar testing tasks. Treat those as development tooling, not normal gameplay.

## Developer Guide

### What Is Implemented

The strongest live systems in the codebase are:

- login, account bootstrap, and save serialization
- world loading and plugin dispatch
- movement, route finding, interaction handling, and object/NPC clicks
- melee, ranged, magic, and special attacks
- prayer toggles, drain, and unlock scrolls
- banking with tabs, placeholders, deposit/withdraw, and equipment handling
- player trading
- shops
- run energy
- food and several consumable item behaviors
- thieving pickpocketing, stalls, and chests
- resource and processing skill loops for woodcutting, mining, fishing, cooking, crafting, and fletching
- a small set of Lumbridge-area NPCs, shops, objects, and test content

### What Is Partial Or Stubbed

These systems exist, but they are not full OSRS implementations:

- Bank PIN
  - the interface opens
  - there is no real PIN setup, verification, or enforcement
- Friends and ignores
  - friend/ignore lists are mostly local bookkeeping
  - list loading and status updates are incomplete
- Private messaging
  - message handling logs intent
  - actual delivery is not wired
- Clan chat
  - join/leave handling currently throws an unhandled runtime exception
- Looting bag storage
  - the UI and deposit/bank flows exist
  - actual bag storage returns `false`
- Amulet of Glory teleports
  - the implementation is commented out
- Some interface tabs
  - they open and display data
  - many buttons are placeholder or informational only
- Death parity
  - the server handles death and respawn
  - it does not match OSRS item-loss rules

### What Is Missing Compared To OSRS

Do not treat these as implemented unless new code lands:

- quests
- quest progression
- diaries
- tutorial island or equivalent onboarding
- Grand Exchange
- clan chat
- real private messaging
- most social features beyond simple friend/ignore bookkeeping
- most skill loops outside the ones listed above
- runecrafting as a full skill loop
- hunter
- construction
- full wilderness and PvP parity
- full item-loss parity on death
- broad jewelry teleport parity
- broader special-item coverage

The repository does contain fragments of some of these areas, but not enough to call them live OSRS-equivalent systems.

### Module Split

- `game-server`: engine, login, save system, world, message handling, entity logic
- `game-plugins`: gameplay content and most feature logic
- `game-api`: shared/generated config references
- `plugins`: support modules such as filestore and tooling

### Save Model

JSON is the default save format if `saveFormat` is omitted in `game.yml`.

The save structure includes collections for things like:

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
2. Replace placeholder UI screens like Bank PIN with real logic or hide them.
3. Finish first-login onboarding and appearance flow if it is still intended.
4. Fix the social stack or remove the broken pieces.
5. Either complete or disable incomplete item mechanics such as amulet teleports and bag storage.
6. Pick one more full skill loop after thieving and complete it end to end.
