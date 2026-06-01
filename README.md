<p align="center">
  <img src="docs/images/vss-logo.png" alt="VSS logo" width="180">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/fabric-server%20%2B%20client-000000?style=flat-square" alt="fabric server and client badge">
  <img src="https://img.shields.io/badge/server--side%20lod-voxy%20compatible-000000?style=flat-square" alt="server-side LOD badge">
  <a href="https://modrinth.com/mod/vss"><img src="https://img.shields.io/badge/modrinth-VSS-000000?style=flat-square" alt="modrinth VSS badge"></a>
</p>

---

VSS is a Fabric client/server mod that sends server-side LOD chunk data to
[Voxy](https://modrinth.com/mod/voxy)-compatible clients. players on multiplayer
servers can see distant terrain without first exploring the map locally, while
the actual Voxy client mod stays a separate install.

players without VSS and Voxy are unaffected. VSS does not bundle or redistribute
the Voxy client mod.

## how it works

when a VSS client joins a VSS server, the client and server perform a handshake.
the server sends session limits such as LOD distance, bandwidth limits, and
generation settings. the client scans outward from the player in a spiral and
batch-requests missing LOD columns.

the server then reads chunk data from disk or memory, can generate missing
chunks on demand when enabled, serializes the section data, and streams voxel
columns back to the client. as chunks change, the server broadcasts dirty-column
updates so clients can refresh stale LOD data.

## installation

### fabric server

1. install Fabric Loader.
2. install [Fabric API](https://modrinth.com/mod/fabric-api).
3. place the VSS jar in the server's `mods/` folder.
4. start the server. config is generated at `config/vss-server-config.json`.

### fabric client

1. install Fabric Loader.
2. install [Fabric API](https://modrinth.com/mod/fabric-api).
3. install [Voxy](https://modrinth.com/mod/voxy).
4. place the VSS jar in the client's `mods/` folder.
5. join a server running VSS. config is generated at `config/vss-client-config.json`.

## requirements

| side | requirement |
| --- | --- |
| server | Fabric Loader, Fabric API, Java 21 |
| client | Fabric Loader, Fabric API, Voxy, Java 21 |

use matching VSS and Minecraft versions on the client and server. VSS is
currently Fabric-only and does not provide a Paper or Purpur plugin.

## commands

### server

the `/vsslod` commands require permission level `2`.

| command | description |
| --- | --- |
| `/vsslod stats` | show per-player transfer stats, handshake state, request counts, pending queues, and bytes sent |
| `/vsslod diag` | show server diagnostics, bandwidth, disk reader status, generation status, and queue depths |

### client

| command | description |
| --- | --- |
| `/vss clearcache` | clear the local VSS column cache so LOD data is requested again |
| `/vss diag` | show client-side VSS connection, queue, throughput, request, and scan diagnostics |

## configuration

### server

server config is generated at `config/vss-server-config.json`.

| setting | default | description |
| --- | ---: | --- |
| `enabled` | `true` | enable VSS LOD distribution |
| `lodDistanceChunks` | `256` | maximum LOD request distance in chunks |
| `bytesPerSecondLimitPerPlayer` | `20971520` | per-player pre-compression bandwidth cap |
| `bytesPerSecondLimitGlobal` | `104857600` | global pre-compression bandwidth cap |
| `diskReaderThreads` | `5` | async disk reader thread count |
| `sendQueueLimitPerPlayer` | `4000` | max queued sections per player |
| `enableChunkGeneration` | `true` | allow missing chunks to generate for LOD data |
| `generationConcurrencyLimitGlobal` | `32` | max chunks generating server-wide at once |
| `generationTimeoutSeconds` | `60` | timeout for pending generation requests |
| `dirtyBroadcastIntervalSeconds` | `10` | interval for pushing dirty-column notifications |
| `syncOnLoadRateLimitPerPlayer` | `800` | sync request rate limit per player |
| `syncOnLoadConcurrencyLimitPerPlayer` | `200` | max in-flight sync requests per player |
| `generationRateLimitPerPlayer` | `80` | generation request rate limit per player |
| `generationConcurrencyLimitPerPlayer` | `16` | max in-flight generation requests per player |
| `perDimensionTimestampCacheSizeMB` | `32` | per-dimension timestamp cache budget |

### client

client config is generated at `config/vss-client-config.json`.

| setting | default | description |
| --- | ---: | --- |
| `receiveServerLods` | `true` | receive LOD data from VSS servers |
| `lodDistanceChunks` | `0` | client LOD distance override, where `0` uses the server limit |
| `offThreadSectionProcessing` | `true` | process received sections off the render thread |

## parity

snapshot from project docs and Modrinth metadata on 2026-05-31:

| feature | VSS | [Voxy Server](https://modrinth.com/mod/voxyserver) | [Voxy Server Side](https://modrinth.com/mod/voxy-server-side) | [soxy](https://modrinth.com/mod/soxy) |
| --- | --- | --- | --- | --- |
| Fabric server support | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| Fabric client companion | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| Paper or Purpur server support | :x: | :x: | :white_check_mark: | :x: |
| Live LOD streaming while players move | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| Reads existing world data from server disk | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| Generates missing chunks on demand | :white_check_mark: | :x: | :x: | :x: |
| Pushes dirty column or block-change updates | :white_check_mark: | :white_check_mark: | :x: | :x: |
| Per-player transfer limits or queue controls | :white_check_mark: | :white_check_mark: | :x: | :x: |
| Admin diagnostics or stats command | :white_check_mark: | :x: | :x: | :x: |
| Client clear-cache command | :white_check_mark: | :x: | :white_check_mark: | :x: |
| Generates distributable `.voxy` cache files | :x: | :x: | :x: | :white_check_mark: |
| Open-source | :white_check_mark: | :white_check_mark: | :x: | :white_check_mark: |

cells are marked only when the capability is documented publicly or visible in
the current VSS implementation.

## troubleshooting

- make sure the server and client are using matching VSS and Minecraft versions.
- make sure the server has Fabric API and VSS installed.
- make sure clients that want server LODs have Fabric API, VSS, and Voxy
  installed.
- use `/vsslod stats` to confirm the server received a VSS handshake.
- use `/vsslod diag` and `/vss diag` to inspect queues, bandwidth, generation,
  and request activity.
- run `/vss clearcache` if client-side LOD data looks stale.

## building

VSS is built from the tracked source in this repository:

```bash
./gradlew build
```

the output jar is written to `build/libs/`.

fabric metadata:

```text
id: vss
name: VSS
package: dev.micr.vss
```

## distribution policy

this repository may publish VSS artifacts. it must not publish or bundle the
actual Voxy client mod from `https://modrinth.com/mod/voxy`.

## license

MIT
