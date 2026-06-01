# Releasing VSS

Releases are driven by `.github/workflows/release.yml`.

## GitHub

Push a tag to create or update a GitHub Release:

```bash
git tag v0.2.4+mc1.21.10
git push origin v0.2.4+mc1.21.10
```

The workflow builds VSS from source, verifies the artifact metadata, runs a
clean Fabric server smoke test, and uploads `build/libs/vss-0.2.4+mc1.21.10.jar`
to the release.

## Modrinth

Set these repository settings before publishing to Modrinth:

| name | type | purpose |
| --- | --- | --- |
| `MODRINTH_PROJECT_ID` | Actions variable | target Modrinth project id, currently `WtJ9vGbd` |
| `MODRINTH_TOKEN` | Actions secret | Modrinth token with create-version access |

The Modrinth step is skipped until both values exist. It publishes the same VSS
jar to the `VSS` Modrinth project as a Fabric release for Minecraft `1.21.10`,
with Fabric API marked as a required dependency and Voxy marked as an optional
client-side companion.

Do not upload or attach the actual Voxy client jar to this repository or to the
VSS Modrinth version.
