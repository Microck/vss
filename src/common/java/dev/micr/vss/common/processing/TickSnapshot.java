package dev.micr.vss.common.processing;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TickSnapshot(
   Map<UUID, TickSnapshot.PlayerTickData> players,
   Map<UUID, Long2ObjectMap<LoadedColumnData>> loadedChunkProbes,
   List<TickSnapshot.GenerationReadyData> generationReady,
   List<UUID> removedPlayers,
   int maxSendQueueSize,
   boolean shutdown
) {
   public static TickSnapshot shutdownSentinel() {
      return new TickSnapshot(Map.of(), Map.of(), List.of(), List.of(), 0, true);
   }

   public record GenerationReadyData(UUID playerUuid, int requestId, LoadedColumnData columnData, long columnTimestamp, long submissionOrder) {
   }

   public record PlayerTickData(String dimension, boolean dimensionChanged) {
   }
}
