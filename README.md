# Alter

Alter is a Kotlin-based OSRS private server built as a modular fork of RSMod. The core engine lives in `game-server`, most gameplay content lives in `game-plugins`, and extra support modules live under `plugins`.

This README focuses on how to boot the current codebase, create accounts, and log in. For the gameplay and implementation audit, see [docs/IMPLEMENTED_GAME_GUIDE.md](docs/IMPLEMENTED_GAME_GUIDE.md).

## Requirements

- JDK 17
- IntelliJ IDEA or another Gradle-capable IDE
- A local Gradle installation
  - This repository does not include a `gradlew` wrapper.
- A revision 228 OSRS cache in `data/cache`
- Matching XTEA keys in `data/xteas.json`

## Required files

The launcher expects these paths to exist:

- `data/api.yml`
- `data/cache/`
- `data/xteas.json`
- `game.yml`
- `dev-settings.yml`

`data/api.yml` is already committed. The root config files are generated from the example files by the install task.

## First-time setup

1. Install JDK 17 and make sure your IDE uses it for this project.
2. Put the revision 228 cache into `data/cache`.
3. Put the matching XTEA dump at `data/xteas.json`.
4. From the repository root, run:

```powershell
gradle :game-server:install
```

That task:

- generates the RSA key used by login
- runs the map decrypter
- copies `game.example.yml` to `game.yml`
- copies `dev-settings.example.yml` to `dev-settings.yml`

If you prefer IntelliJ, run the Gradle task `game-server > Tasks > other > install`.

## Starting the server

From the repository root, run:

```powershell
gradle :game-server:run
```

The successful boot path ends with the server listening on the port from `game.yml`, which defaults to `43594`.

Important runtime notes:

- `game.yml` controls the game name, revision, home tile, services, privileges, and port.
- If `saveFormat` is omitted, the server defaults to JSON saves.
- Player save data is written under `data/saves/`.

## Login flow

This repository currently auto-registers accounts on first login.

- If a login username does not exist yet, the server creates it automatically.
- The password used on first login is bcrypt-hashed and saved for that username.
- Later logins must use the same username and password.
- The login username becomes the initial in-game display name.

Relevant save directories include:

- `data/saves/accounts/`
- `data/saves/details/`
- `data/saves/skills/`
- `data/saves/containers/`

## Connecting a client

The server speaks revision `228` and listens on the port from `game.yml`, default `43594`.

Use a compatible revision 228 OSRS client workflow that can target your local server. This repository does not include a client binary or a one-click launcher.

If you are using RSProx or another proxy/redirection setup, make sure:

- the revision matches `game.yml`
- the client points at your local game port
- the modulus matches the RSA material generated during install

## Useful defaults for local testing

The example config currently uses:

- home tile: `3218, 3218, 0`
- game port: `43594`
- privileges: player, moderator, administrator, owner

Several player-accessible commands are currently implemented:

- `::home`
- `::thieving`
- `::yell <message>`
- `::empty`

The last two are development-oriented conveniences, not production-safe player commands.

## Notes

- The Docker files in this repository appear stale relative to the current module names and were not validated in this documentation pass.
- `dev-settings.yml` is optional and only used for debug toggles such as packet, button, spell, and item-action logging.
- The install task fails early if the cache or `xteas.json` is missing.

## Credits

- [RSMod](https://github.com/Tomm0017/rsmod)
- [OpenRune-FileStore](https://github.com/OpenRune/OpenRune-FileStore)
- [rsmod routefinder](https://github.com/rsmod/rsmod/tree/main/engine/routefinder)
