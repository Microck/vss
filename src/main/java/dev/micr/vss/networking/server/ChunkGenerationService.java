package dev.micr.vss.networking.server;

import dev.micr.vss.common.VSSConstants;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.processing.LoadedColumnData;
import dev.micr.vss.common.processing.TickSnapshot;
import dev.micr.vss.config.VSSServerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.class_1923;
import net.minecraft.class_1937;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3230;
import net.minecraft.class_5321;

public class ChunkGenerationService {
   private static final class_3230 VSS_GEN_TICKET = new class_3230(0L, 2);
   private final LinkedHashMap<ChunkGenerationService.PendingGenerationKey, ChunkGenerationService.PendingGeneration> active = new LinkedHashMap<>();
   private final Map<UUID, Integer> perPlayerActiveCount = new HashMap<>();
   private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<ChunkDiskReader.ReadResult>> playerResults = new ConcurrentHashMap<>();
   private final int maxConcurrent;
   private final int maxPerPlayerActive;
   private final int timeoutTicks;
   private volatile long totalSubmitted = 0L;
   private volatile long totalCompleted = 0L;
   private volatile long totalTimeouts = 0L;

   public ChunkGenerationService(VSSServerConfig config) {
      this.maxConcurrent = config.generationConcurrencyLimitGlobal;
      this.maxPerPlayerActive = config.generationConcurrencyLimitPerPlayer;
      this.timeoutTicks = config.generationTimeoutSeconds * 20;
   }

   public boolean submitGeneration(UUID playerUuid, int requestId, class_3218 level, int cx, int cz, long submissionOrder) {
      ChunkGenerationService.PendingGenerationKey key = new ChunkGenerationService.PendingGenerationKey(level.method_27983(), cx, cz);
      ChunkGenerationService.PendingGeneration existing = this.active.get(key);
      if (existing != null) {
         existing.callbacks.add(new ChunkGenerationService.GenerationCallback(playerUuid, requestId, submissionOrder));
         incrementCount(this.perPlayerActiveCount, playerUuid);
         return true;
      } else {
         int playerActive = this.perPlayerActiveCount.getOrDefault(playerUuid, 0);
         if (this.active.size() < this.maxConcurrent && playerActive < this.maxPerPlayerActive) {
            class_1923 pos = new class_1923(cx, cz);
            level.method_14178().method_66009(VSS_GEN_TICKET, pos, 0);
            ChunkGenerationService.PendingGeneration gen = new ChunkGenerationService.PendingGeneration(pos, level);
            gen.callbacks.add(new ChunkGenerationService.GenerationCallback(playerUuid, requestId, submissionOrder));
            this.active.put(key, gen);
            incrementCount(this.perPlayerActiveCount, playerUuid);
            this.totalSubmitted++;
            return true;
         } else {
            return false;
         }
      }
   }

   public List<TickSnapshot.GenerationReadyData> tick() {
      if (this.active.isEmpty()) {
         return List.of();
      }

      List<TickSnapshot.GenerationReadyData> ready = null;
      Iterator<Entry<ChunkGenerationService.PendingGenerationKey, ChunkGenerationService.PendingGeneration>> iter = this.active.entrySet().iterator();

      while (iter.hasNext()) {
         Entry<ChunkGenerationService.PendingGenerationKey, ChunkGenerationService.PendingGeneration> entry = iter.next();
         ChunkGenerationService.PendingGeneration gen = entry.getValue();
         gen.ticksWaiting++;
         if (gen.ticksWaiting > this.timeoutTicks) {
            VSSLogger.debug(
               "Generation timeout for chunk "
                  + gen.pos.field_9181
                  + ","
                  + gen.pos.field_9180
                  + " after "
                  + gen.ticksWaiting
                  + " ticks ("
                  + gen.callbacks.size()
                  + " callbacks)"
            );

            for (ChunkGenerationService.GenerationCallback cb : gen.callbacks) {
               this.addResult(
                  cb.playerUuid, ChunkDiskReader.emptyResult(cb.playerUuid, cb.requestId, gen.pos.field_9181, gen.pos.field_9180, cb.submissionOrder)
               );
               decrementCount(this.perPlayerActiveCount, cb.playerUuid);
            }

            gen.level.method_14178().method_66010(VSS_GEN_TICKET, gen.pos, 0);
            iter.remove();
            this.totalTimeouts++;
         } else {
            class_2818 chunk = gen.level.method_14178().method_21730(gen.pos.field_9181, gen.pos.field_9180);
            if (chunk != null) {
               try {
                  long columnTimestamp = VSSConstants.epochSeconds();
                  LoadedColumnData columnData = SectionSerializer.serializeColumn(gen.level, chunk, gen.pos.field_9181, gen.pos.field_9180);

                  for (ChunkGenerationService.GenerationCallback cb : gen.callbacks) {
                     if (ready == null) {
                        ready = new ArrayList<>();
                     }

                     ready.add(new TickSnapshot.GenerationReadyData(cb.playerUuid, cb.requestId, columnData, columnTimestamp, cb.submissionOrder));
                     decrementCount(this.perPlayerActiveCount, cb.playerUuid);
                  }

                  this.totalCompleted++;
               } catch (Exception e) {
                  VSSLogger.error("Failed to extract primitives for generated chunk at " + gen.pos.field_9181 + ", " + gen.pos.field_9180, e);

                  for (ChunkGenerationService.GenerationCallback cb : gen.callbacks) {
                     this.addResult(
                        cb.playerUuid, ChunkDiskReader.emptyResult(cb.playerUuid, cb.requestId, gen.pos.field_9181, gen.pos.field_9180, cb.submissionOrder)
                     );
                     decrementCount(this.perPlayerActiveCount, cb.playerUuid);
                  }
               }

               gen.level.method_14178().method_66010(VSS_GEN_TICKET, gen.pos, 0);
               iter.remove();
            }
         }
      }

      return ready != null ? ready : List.of();
   }

   void registerPlayer(UUID playerUuid) {
      this.playerResults.computeIfAbsent(playerUuid, k -> new ConcurrentLinkedQueue<>());
   }

   void addResult(UUID playerUuid, ChunkDiskReader.ReadResult result) {
      ConcurrentLinkedQueue<ChunkDiskReader.ReadResult> queue = this.playerResults.get(playerUuid);
      if (queue != null) {
         queue.add(result);
      }
   }

   public ConcurrentLinkedQueue<ChunkDiskReader.ReadResult> getPlayerQueue(UUID playerUuid) {
      return this.playerResults.get(playerUuid);
   }

   public void removePlayerResults(UUID playerUuid) {
      this.playerResults.remove(playerUuid);
   }

   public void removePlayer(UUID playerUuid) {
      this.removePlayerResults(playerUuid);
      this.perPlayerActiveCount.remove(playerUuid);
      Iterator<Entry<ChunkGenerationService.PendingGenerationKey, ChunkGenerationService.PendingGeneration>> iter = this.active.entrySet().iterator();

      while (iter.hasNext()) {
         ChunkGenerationService.PendingGeneration gen = iter.next().getValue();
         gen.callbacks.removeIf(cb -> cb.playerUuid.equals(playerUuid));
         if (gen.callbacks.isEmpty()) {
            gen.level.method_14178().method_66010(VSS_GEN_TICKET, gen.pos, 0);
            iter.remove();
         }
      }
   }

   public void shutdown() {
      for (ChunkGenerationService.PendingGeneration gen : this.active.values()) {
         gen.level.method_14178().method_66010(VSS_GEN_TICKET, gen.pos, 0);
      }

      this.active.clear();
      this.perPlayerActiveCount.clear();
      this.playerResults.clear();
   }

   public String getDiagnostics() {
      return String.format(
         "submitted=%d, completed=%d, active=%d, timeouts=%d", this.totalSubmitted, this.totalCompleted, this.active.size(), this.totalTimeouts
      );
   }

   public long getTotalSubmitted() {
      return this.totalSubmitted;
   }

   public long getTotalCompleted() {
      return this.totalCompleted;
   }

   public long getTotalTimeouts() {
      return this.totalTimeouts;
   }

   private static void incrementCount(Map<UUID, Integer> map, UUID uuid) {
      map.merge(uuid, 1, Integer::sum);
   }

   private static void decrementCount(Map<UUID, Integer> map, UUID uuid) {
      Integer count = map.get(uuid);
      if (count != null) {
         if (count <= 1) {
            map.remove(uuid);
         } else {
            map.put(uuid, count - 1);
         }
      }
   }

   record GenerationCallback(UUID playerUuid, int requestId, long submissionOrder) {
   }

   static class PendingGeneration {
      final class_1923 pos;
      final class_3218 level;
      final List<ChunkGenerationService.GenerationCallback> callbacks = new ArrayList<>();
      int ticksWaiting = 0;

      PendingGeneration(class_1923 pos, class_3218 level) {
         this.pos = pos;
         this.level = level;
      }
   }

   private record PendingGenerationKey(class_5321<class_1937> dimension, int cx, int cz) {
   }
}
