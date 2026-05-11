# Alter Client Strategy

Alter is moving toward an official desktop client first, with a web client later. The first desktop client should be a branded RuneLite-derived distribution that connects directly to Alter without RSProx and only ships curated, tested plugins.

## Runtime Contract

The server exposes the launcher/client contract at:

```text
http://127.0.0.1:4567/client_manifest.json
```

The JSON schema is exposed at:

```text
http://127.0.0.1:4567/client_manifest.schema.json
```

The same payload is also available for tooling under:

```text
http://127.0.0.1:4567/world-editor/client-manifest
```

The manifest is backed by `data/cfg/client/client_manifest.json` and enriched at runtime with the active game name, revision, home tile, cache build id, and RSA modulus.

Use `tools/client/validate-client-manifest.ps1` to verify the endpoint while the server is running, and `tools/client/bootstrap-alter-client.ps1` to create or populate the initial `Dodian-Client` repository from a RuneLite base.

## Desktop Client MVP

Create a separate `Dodian-Client` repository from a revision-compatible RuneLite/client base pinned to Alter revision `228`.

The MVP client should:

- Fetch `client_manifest.json` on startup.
- Use manifest login and JS5 endpoints instead of RSProx.
- Fail clearly when revision, cache build, RSA, or minimum client version is incompatible.
- Bundle a JRE and ship a Windows installer first.
- Disable broad Plugin Hub access by default.
- Load only the curated plugin allowlist from the manifest.
- Preserve RuneLite-style QoL where it is stable and useful.

## Curated Plugins

The first supported plugin family should be first-party Alter plugins:

- `alter-world-teleports`
- `alter-boss-helpers`
- `alter-drop-overlay`
- `alter-map-markers`
- `alter-web-editor-links`
- `alter-qa-tools`

Treat community Plugin Hub support as a later explicit decision, not a default.

## Cache-Native Custom Visuals

Custom boss visuals should start in the cache so native, RuneLite-derived, and future web clients can share the same baseline assets.

Prioritize source-controlled tooling for:

- Models and NPC definitions.
- Animations, spotanims, and projectiles.
- Interface and sprite packs.
- Map and object patches.
- Sounds and music.
- Build reports that identify changed cache archives.

Client-only visual effects such as shaders or particles can be layered on later for marquee encounters, but should not be required for baseline gameplay.

## Web Client Later

The web client should be a sibling project, not a RuneLite plugin reuse target. Browsers cannot connect to the raw game TCP protocol directly, so the web client will need a WebSocket gateway plus a WebGL/cache renderer.

The web client should reuse the same manifest shape and cache build ids, but should reimplement plugin-like features as first-party browser UI.
