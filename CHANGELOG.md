# Changelog

## VSS 1.0.0

- publish the current VSS source as the first stable release
- keep the server-side LOD networking, generation, disk-read, dirty-column, config, command, and Voxy integration implementation in source
- package the shared common implementation as a source-built nested jar
- verify the built jar does not bundle Voxy, Sodium, or ModMenu classes

## VSS 0.2.4+mc1.21.10

- replace the placeholder implementation with the full VSS source from the reference artifact
- build the VSS networking, payload, disk-read, generation, dirty-column, config, command, and Voxy integration classes from tracked source
- package the shared common implementation as a source-built nested jar
- verify the built jar does not bundle Voxy, Sodium, or ModMenu classes

## VSS 0.2.3+mc1.21.10

- publish the source-built VSS jar under a new release version
- keep the same first public Minecraft 1.21.10 target and Fabric metadata
- continue excluding any redistributed Voxy client jar

## VSS 0.2.2+mc1.21.10

- initial VSS artifact for Minecraft 1.21.10
- build VSS from tracked Fabric source instead of a checked-in jar artifact
- runs as a Fabric mod with mod id `vss`
- starts the VSS request processing service on dedicated servers
- supports the `vsslod stats` and `vsslod diag` server commands
- does not bundle or redistribute the Voxy client mod
