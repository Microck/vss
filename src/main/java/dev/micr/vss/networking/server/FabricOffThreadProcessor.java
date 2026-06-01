package dev.micr.vss.networking.server;

import dev.micr.vss.common.processing.OffThreadProcessor;
import dev.micr.vss.networking.payloads.VoxelColumnS2CPayload;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.class_1937;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_5321;
import net.minecraft.class_7924;

public class FabricOffThreadProcessor extends OffThreadProcessor<PlayerRequestState, ChunkDiskReader.ReadResult> {
   private final ChunkDiskReader diskReader;
   private final ChunkGenerationService generationService;
   private final ConcurrentHashMap<String, class_3218> dimensionLevelMap = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, class_5321<class_1937>> dimensionKeyCache = new ConcurrentHashMap<>();

   public FabricOffThreadProcessor(
      Map<UUID, PlayerRequestState> players,
      ChunkDiskReader diskReader,
      ChunkGenerationService generationService,
      Path dataDir,
      int perDimensionTimestampCacheSizeMB
   ) {
      super(players, diskReader != null, generationService != null, dataDir, perDimensionTimestampCacheSizeMB);
      this.diskReader = diskReader;
      this.generationService = generationService;
   }

   public void updateDimensionContext(String dimension, class_3218 level) {
      this.dimensionLevelMap.putIfAbsent(dimension, level);
   }

   protected ChunkDiskReader.ReadResult pollDiskResult(PlayerRequestState state) {
      if (this.diskReader == null) {
         return null;
      }

      ConcurrentLinkedQueue<ChunkDiskReader.ReadResult> queue = this.diskReader.getPlayerQueue(state.getPlayerUUID());
      return queue == null ? null : queue.poll();
   }

   protected ChunkDiskReader.ReadResult pollGenerationResult(PlayerRequestState state) {
      if (this.generationService == null) {
         return null;
      }

      ConcurrentLinkedQueue<ChunkDiskReader.ReadResult> queue = this.generationService.getPlayerQueue(state.getPlayerUUID());
      return queue == null ? null : queue.poll();
   }

   protected void enqueueResultPayloads(PlayerRequestState state, ChunkDiskReader.ReadResult result) {
      if (result.sectionBytes() != null) {
         this.buildAndEnqueueColumnPayload(
            state,
            result.chunkX(),
            result.chunkZ(),
            result.dimension(),
            result.requestId(),
            result.columnTimestamp(),
            result.submissionOrder(),
            result.sectionBytes(),
            result.estimatedBytes()
         );
      }
   }

   @Override
   protected void submitDiskRead(UUID playerUuid, int requestId, String dimension, int cx, int cz, long submissionOrder) {
      if (this.diskReader != null) {
         class_3218 level = this.dimensionLevelMap.get(dimension);
         if (level != null) {
            this.diskReader.submitReadDirect(playerUuid, requestId, level, cx, cz, submissionOrder);
         }
      }
   }

   protected void buildAndEnqueueColumnPayload(
      PlayerRequestState state,
      int cx,
      int cz,
      String dimension,
      int requestId,
      long columnTimestamp,
      long submissionOrder,
      byte[] sectionBytes,
      int estimatedBytes
   ) {
      class_5321<class_1937> dimensionKey = this.dimensionKeyCache
         .computeIfAbsent(dimension, d -> class_5321.method_29179(class_7924.field_41223, class_2960.method_60654(d)));
      VoxelColumnS2CPayload payload = new VoxelColumnS2CPayload(requestId, cx, cz, dimensionKey, columnTimestamp, sectionBytes);
      state.addReadyPayload(new PlayerRequestState.QueuedPayload(payload, requestId, estimatedBytes, submissionOrder));
   }

   @Override
   public void shutdown() {
      super.shutdown();
      this.dimensionLevelMap.clear();
   }
}
