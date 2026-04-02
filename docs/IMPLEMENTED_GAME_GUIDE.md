# Alter Implemented Game Guide

This document is based on the current code in this repository, not on intended features or older setup docs.

It is meant to serve two purposes:

- player guide: what you can actually do in-game right now
- developer guide: what is implemented, what is partial, and what is still missing compared to OSRS

## High-level summary

The current playable slice is a sandbox-style OSRS server with:

- automatic account creation on first login
- a Lumbridge-centered spawn experience
- working movement, interaction, inventory, equipment, combat, banking, shops, prayer, run energy, and death handling
- player trading
- meaningful Thieving content
- selected item mechanics
- a large amount of UI scaffolding that is only partly backed by gameplay logic

It is not a full OSRS recreation. The strongest implemented areas are engine fundamentals plus selected systems, not broad questing or full skilling coverage.

## What a player can do right now

### Create an account and log in

- Log in with any unused username and password.
- Your account is created automatically on first successful login.
- Your login name becomes your in-game display name.
- You spawn at the configured home tile from `game.yml`, default `3218, 3218, 0`.

### Start with a small starter kit

New accounts currently receive:

- 5 logs
- 1 tinderbox
- 5 bread
- 1 bronze pickaxe
- 1 bronze dagger
- 1 knife

### Move around and interact with the world

- Walk, run, click the minimap, and use the world map.
- Toggle run energy.
- Open and close many doors and gates.
- Use ladders, stairs, and at least one trapdoor route.
- Cross the Wilderness ditch with proper forced movement.
- Pay 10 coins to use the Al Kharid gate shortcut.

### Fight things

Core combat is implemented for:

- player versus NPC combat
- player versus player attack initiation
- melee
- ranged with weapon and ammo mappings
- magic combat spells
- attack styles
- auto-retaliate
- special attack energy restoration

Special attack implementations exist for:

- abyssal bludgeon
- abyssal dagger
- Armadyl godsword
- dragon dagger

There is also weapon-specific code for Osmumten's fang.

Magic combat includes the standard elemental combat line through surge and Ancient Magicks combat spells through barrage. Autocast wiring is present through the attack tab.

### Use prayers

Prayer support is fairly deep:

- normal prayer toggling
- overhead icons
- quick prayers
- prayer drain over time
- protect item state
- reset on death and logout

Unlockable prayers implemented in code:

- Preserve
- Rigour
- Augury

Prayer scroll items can unlock those prayers.

### Eat and survive

- Food consumption works through the food table.
- Death restores stats and returns you home unless you are in an instance.
- NPC respawns are handled through combat definitions.
- Poison support exists in the mechanics layer.

### Bank, deposit, and trade

Banking is one of the more complete systems:

- bank booths and banker NPCs open the bank
- deposit boxes work
- deposit inventory and equipment work
- withdraw as item or note works
- quantity modes and withdraw-X work
- placeholders work
- bank tabs and item movement logic work

Player trading is also implemented:

- request/accept flow
- item offering and removal
- first and second confirmation screens
- decline on close and logout

### Use shops

The shop system is implemented, and Lumbridge currently includes at least:

- Lumbridge General Store
- Bob's Brilliant Axes

### Thieve

Thieving is the clearest full skill loop in the repo today.

Implemented content includes:

- pickpocketing
- stall thieving
- chest thieving with traps and disarm chances

The data files currently define pickpocket targets ranging from men up to heroes, plus multiple stall tiers and chest tiers.

There is also a dedicated `::thieving` command that teleports you to a test area containing representative NPCs, stalls, and chests.

### Use selected item mechanics

Working or mostly working item interactions include:

- teleport tablets
- essence pouches
- prayer scrolls
- mystery box
- food items
- dwarven rock cake
- spade

Teleport tabs explicitly support many destinations, including Varrock, Falador, Lumbridge, Camelot, Ardougne, Watchtower, Rimmington, Taverley, Pollnivneach, Hosidius, Rellekka, Brimhaven, Yanille, Trollheim, Catherby, Barbarian, Draynor Manor, Fishing Guild, Khazard, and Mind Altar.

### Use gameframe interfaces

A lot of OSRS-style interface behavior is present, including:

- attack tab
- inventory tab
- worn equipment tab
- prayer tab
- magic tab filtering
- emotes tab
- account tab
- character summary tab
- settings tab
- world map
- XP drops and XP settings
- skill guide openings

Some of these are genuinely useful. Others are mostly presentation shells.

## Implemented content by area

### Lumbridge and nearby

The repo explicitly spawns content in and around Lumbridge:

- men and women
- rats, sheep, rams, imps, goblins, spiders, and zombie rats
- banker interactions
- Bob and general store NPCs
- tutors and other Lumbridge NPC dialogue plugins
- local item spawns
- an altar object

### Test content

There is a hard-coded thieving test area populated by the repo itself, not just by data files. It is the best place to validate the current Thieving feature set.

### World data

The repository also includes a very large `data/cfg/spawns/item_spawns.yml`, so there are many additional ground item spawns beyond the small hand-authored Lumbridge examples.

## What is partial, placeholder, or misleading

### Automatic account creation is real, but onboarding is incomplete

- New accounts are created automatically.
- The first-login appearance flow exists as plugin scaffolding, but the login appearance prompt is commented out.
- In practice, creation is easier than OSRS-style onboarding but much less polished.

### Bank PIN is UI-only

The bank PIN interface opens and shows settings text, but there is no actual PIN setup, verification, or enforcement flow.

### Kept on Death is placeholder

The interface opens, but it currently fills the screen with dummy items and TODO text rather than real death-loss calculations.

### Looting bag is incomplete

- The looting bag interface opens.
- Checking and bank-depositing the bag are implemented.
- Actual storage into the bag is currently disabled because the storage method returns `false`.

So from a player perspective, looting bag behavior is not complete.

### Social features are mostly not ready

Friends, ignores, private messaging, and clan chat should all be treated as incomplete:

- friend and ignore list UI switching exists
- backend social syncing is stubbed or commented out
- private messages are logged but not delivered
- clan join/leave handling throws an unhandled runtime exception

### Some item plugins exist only as shells

Examples:

- Amulet of Glory teleporting is commented out
- water container behavior is commented out

A plugin file existing in the tree is not proof that the mechanic is fully playable.

### UI presence does not mean feature completion

The project has a lot of interface code. That does not mean the matching gameplay is done.

Examples:

- account tab exists without a broader account-management feature set
- skill guides open even though most skills are not implemented end to end
- character summary exists without a broader progression system around it

## What is not implemented compared to OSRS

Based on the reviewed code, you should assume these are missing or far from complete unless new content lands:

- quests
- diaries
- minigames
- slayer
- farming
- woodcutting
- firemaking
- fishing
- cooking as a full skill loop
- mining and smithing as full skill loops
- runecrafting altars and full crafting loop
- crafting as a full skill loop
- fletching
- herblore
- agility
- hunter
- construction
- clue scrolls
- achievement systems
- Grand Exchange
- functioning friend chat and clan systems
- real kept-on-death and item-loss parity
- full wilderness and PvP rules parity
- complete jewelry teleport behavior
- tutorial island or equivalent onboarding

The codebase contains pieces of some of these areas, but not enough to document them as live game features.

## Developer notes

### Module split

- `game-server`: engine, login, save system, world, handlers, entity logic
- `game-plugins`: most gameplay content
- `game-api`: generated/shared config references
- `plugins`: filestore and tooling support modules

### Save model

JSON is the default save format if `saveFormat` is not set in `game.yml`.

Important save collections:

- `accounts`
- `details`
- `skills`
- `containers`
- `attributes`
- `timers`
- `varps`
- `appearance`

### Server startup expectations

The launcher expects:

- `../data/api.yml`
- `../data/cache`
- `../game.yml`
- optional `../dev-settings.yml`

The install flow also generates RSA material and decrypts the world map before a clean first boot.

### Default player-facing commands

Commands available without elevated powers in the reviewed content:

- `::home`
- `::thieving`
- `::yell`
- `::empty`

This is useful for local testing, but not safe as a production command set.

### Revision and networking

- default revision: `228`
- default game port: `43594`
- desktop-client oriented network setup through rsprot

## Recommended next priorities

If the goal is to turn this into a coherent playable server, the highest-leverage next steps are:

1. Decide whether the project is a Lumbridge sandbox, a thieving-focused test server, or a broader OSRS base.
2. Remove or restrict player-facing debug commands like `::empty`.
3. Finish first-login appearance and onboarding.
4. Replace placeholder interfaces like Bank PIN and Kept on Death with real logic or hide them.
5. Either complete or disable incomplete social systems.
6. Pick the next full skill loop after Thieving and implement it end to end.
