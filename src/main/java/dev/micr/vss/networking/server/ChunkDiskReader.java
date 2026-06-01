package dev.micr.vss.networking.server;

import dev.micr.vss.common.VSSConstants;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.processing.AbstractChunkDiskReader;
import dev.micr.vss.common.processing.ReadResultAccess;
import dev.micr.vss.mixin.AccessorServerChunkCache;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.class_1937;
import net.minecraft.class_3218;
import net.minecraft.class_3898;
import net.minecraft.class_5321;
import net.minecraft.class_5455;

public class ChunkDiskReader extends AbstractChunkDiskReader<ChunkDiskReader.ReadResult> {
   static ChunkDiskReader.ReadResult emptyResult(UUID playerUuid, int requestId, int chunkX, int chunkZ, long submissionOrder) {
      return new ChunkDiskReader.ReadResult(playerUuid, requestId, chunkX, chunkZ, null, null, 0, 0L, true, false, submissionOrder);
   }

   static ChunkDiskReader.ReadResult saturatedResult(UUID playerUuid, int requestId, int chunkX, int chunkZ, long submissionOrder) {
      return new ChunkDiskReader.ReadResult(playerUuid, requestId, chunkX, chunkZ, null, null, 0, 0L, false, true, submissionOrder);
   }

   public ChunkDiskReader(int threadCount) {
      super(threadCount);
   }

   public void submitReadDirect(UUID playerUuid, int requestId, class_3218 level, int chunkX, int chunkZ, long submissionOrder) {
      if (!this.isShutdown()) {
         this.diag.recordSubmitted();
         class_5321<class_1937> dimension = level.method_27983();
         class_5455 registryAccess = level.method_30349();
         class_3898 chunkMap = ((AccessorServerChunkCache)level.method_14178()).getChunkMap();

         try {
            this.executor.submit(() -> {
               if (!this.isShutdown()) {
                  try {
                     this.readChunkNbtAndSerialize(playerUuid, requestId, chunkMap, chunkX, chunkZ, dimension, registryAccess, submissionOrder);
                  } catch (Exception e) {
                     VSSLogger.error("Failed to read chunk from disk at " + chunkX + ", " + chunkZ, e);
                     this.diag.recordError();
                     this.diag.recordCompleted(0L);
                     this.addResult(playerUuid, emptyResult(playerUuid, requestId, chunkX, chunkZ, submissionOrder));
                  }
               }
            });
         } catch (RejectedExecutionException e) {
            if (VSSLogger.isDebugEnabled()) {
               VSSLogger.debug("Disk reader executor saturated, returning rate-limited for " + chunkX + "," + chunkZ);
            }

            this.diag.recordSaturation();
            this.diag.recordCompleted(0L);
            this.addResult(playerUuid, saturatedResult(playerUuid, requestId, chunkX, chunkZ, submissionOrder));
         }
      }
   }

   private void readChunkNbtAndSerialize(
      UUID playerUuid,
      int requestId,
      class_3898 chunkMap,
      int chunkX,
      int chunkZ,
      class_5321<class_1937> dimension,
      class_5455 registryAccess,
      long submissionOrder
   ) {
      if (!this.isShutdown()) {
         long startNs = System.nanoTime();

         byte[] serializedSections;
         try {
            serializedSections = NbtSectionSerializer.readAndSerializeSections(chunkMap, registryAccess, chunkX, chunkZ);
         } catch (Exception e) {
            VSSLogger.error("Failed to read chunk NBT from disk at " + chunkX + ", " + chunkZ, e);
            this.diag.recordError();
            this.diag.recordCompleted(System.nanoTime() - startNs);
            this.addResult(playerUuid, emptyResult(playerUuid, requestId, chunkX, chunkZ, submissionOrder));
            return;
         }

         if (serializedSections == null) {
            this.diag.recordEmpty();
            this.diag.recordCompleted(System.nanoTime() - startNs);
            this.addResult(playerUuid, emptyResult(playerUuid, requestId, chunkX, chunkZ, submissionOrder));
         } else if (serializedSections.length == 0) {
            long columnTimestamp = VSSConstants.epochSeconds();
            String dimensionStr = dimension.method_29177().toString();
            this.diag.recordEmpty();
            this.diag.recordCompleted(System.nanoTime() - startNs);
            this.addResult(
               playerUuid,
               new ChunkDiskReader.ReadResult(playerUuid, requestId, chunkX, chunkZ, null, dimensionStr, 0, columnTimestamp, false, false, submissionOrder)
            );
         } else {
            long columnTimestamp = VSSConstants.epochSeconds();
            String dimensionStr = dimension.method_29177().toString();
            int estimatedBytes = serializedSections.length + 25;
            this.diag.recordCompleted(System.nanoTime() - startNs);
            this.addResult(
               playerUuid,
               new ChunkDiskReader.ReadResult(
                  playerUuid, requestId, chunkX, chunkZ, serializedSections, dimensionStr, estimatedBytes, columnTimestamp, false, false, submissionOrder
               )
            );
         }
      }
   }

   public record ReadResult(
      UUID playerUuid,
      int requestId,
      int chunkX,
      int chunkZ,
      byte[] sectionBytes,
      String dimension,
      int estimatedBytes,
      long columnTimestamp,
      boolean notFound,
      boolean saturated,
      long submissionOrder
   ) implements ReadResultAccess {
   }
}
