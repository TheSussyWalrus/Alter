# Alter Client Bootstrap Tools

These scripts prepare the next repository: an official desktop client that consumes Alter's runtime manifest and connects without RSProx.

## Validate The Server Manifest

Start the Alter server and run:

```powershell
.\tools\client\validate-client-manifest.ps1
```

The script checks the local `client_manifest.json` endpoint for revision, login/JS5 endpoints, cache build id, RSA modulus, and curated plugin metadata.

## Bootstrap The Client Repo

From the Alter repository root:

```powershell
.\tools\client\bootstrap-alter-client.ps1 -TargetDir ..\alter-client
```

The script clones a RuneLite base into `..\alter-client` if it does not exist, fetches the local Alter manifest when the server is running, and writes `ALTER_CLIENT_BOOTSTRAP.md` into the client repo with the first patch checklist.

## Next Implementation Step

After the repo exists, patch the client startup path so it reads `http://127.0.0.1:4567/client_manifest.json` and uses those values for login, JS5, revision, RSA, cache build checks, and curated plugin behavior.
