package dev.micr.vss.networking.server;

import dev.micr.vss.common.PositionUtil;
import dev.micr.vss.common.SharedBandwidthLimiter;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.processing.IncomingRequest;
import dev.micr.vss.common.processing.LoadedColumnData;
import dev.micr.vss.common.processing.OffThreadProcessor;
import dev.micr.vss.common.processing.RateLimiterSet;
import dev.micr.vss.common.processing.SendAction;
import dev.micr.vss.common.processing.SendActionBatcher;
import dev.micr.vss.common.processing.TickDiagnostics;
import dev.micr.vss.common.processing.TickSnapshot;
import dev.micr.vss.common.tracking.DirtyColumnTracker;
import dev.micr.vss.config.VSSServerConfig;
import dev.micr.vss.networking.payloads.BandwidthUpdateC2SPayload;
import dev.micr.vss.networking.payloads.BatchChunkRequestC2SPayload;
import dev.micr.vss.networking.payloads.BatchResponseS2CPayload;
import dev.micr.vss.networking.payloads.CancelRequestC2SPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_5218;
import net.minecraft.server.MinecraftServer;

public class RequestProcessingService {
   private final Map<UUID, PlayerRequestState> players = new ConcurrentHashMap<>();
   private final MinecraftServer server;
   private final ChunkDiskReader diskReader;
   private final ChunkGenerationService generationService;
   private final SharedBandwidthLimiter bandwidthLimiter;
   private final FabricOffThreadProcessor offThreadProcessor;
   private final DirtyColumnTracker dirtyTracker;
   private final long startTimeNanos = System.nanoTime();
   private final DirtyColumnBroadcaster dirtyBroadcaster;
   private final Map<class_3218, String> dimensionStringCache = new HashMap<>();
   private int diagLogCounter = 0;
   private final TickDiagnostics diag = new TickDiagnostics();
   private final Map<UUID, TickSnapshot.PlayerTickData> reusablePlayerTickData = new HashMap<>();
   private final Map<UUID, Long2ObjectMap<LoadedColumnData>> reusableLoadedChunkProbes = new HashMap<>();
   private final SendActionBatcher sendActionBatcher = new SendActionBatcher();
   private static final int DIAG_LOG_INTERVAL_TICKS = 100;
   private static final int MAX_PROBES_PER_TICK_PER_PLAYER = 512;

   public RequestProcessingService(MinecraftServer server) {
      this.server = server;
      VSSServerConfig config = VSSServerConfig.CONFIG;
      this.dirtyTracker = new DirtyColumnTracker();
      this.diskReader = new ChunkDiskReader(config.diskReaderThreads);
      if (config.enableChunkGeneration) {
         this.generationService = new ChunkGenerationService(config);
      } else {
         this.generationService = null;
      }

      this.bandwidthLimiter = new SharedBandwidthLimiter(config.bytesPerSecondLimitGlobal);
      Path dataDir = server.method_27050(class_5218.field_24188).resolve("data");
      this.offThreadProcessor = new FabricOffThreadProcessor(
         this.players, this.diskReader, this.generationService, dataDir, config.perDimensionTimestampCacheSizeMB
      );
      this.offThreadProcessor.start();
      this.dirtyBroadcaster = new DirtyColumnBroadcaster(server, this.players, this.offThreadProcessor, this.dirtyTracker);
   }

   public PlayerRequestState registerPlayer(class_3222 player, int capabilities) {
      VSSServerConfig config = VSSServerConfig.CONFIG;
      PlayerRequestState state = this.players
         .computeIfAbsent(
            player.method_5667(),
            uuid -> new PlayerRequestState(
               player,
               config.syncOnLoadRateLimitPerPlayer,
               config.syncOnLoadConcurrencyLimitPerPlayer,
               config.generationRateLimitPerPlayer,
               config.generationConcurrencyLimitPerPlayer
            )
         );
      this.diskReader.registerPlayer(player.method_5667());
      if (this.generationService != null) {
         this.generationService.registerPlayer(player.method_5667());
      }

      state.setCapabilities(capabilities);
      state.markHandshakeComplete();
      return state;
   }

   public void removePlayer(UUID uuid) {
      this.players.remove(uuid);
      this.cleanupPlayerServices(uuid);
   }

   private void cleanupPlayerServices(UUID uuid) {
      this.diskReader.removePlayerResults(uuid);
      if (this.generationService != null) {
         this.generationService.removePlayer(uuid);
      }
   }

   public void handleBatchRequest(class_3222 player, BatchChunkRequestC2SPayload payload) {
      PlayerRequestState state = this.players.get(player.method_5667());
      if (state != null && state.hasCompletedHandshake()) {
         int playerCx = player.method_31477() >> 4;
         int playerCz = player.method_31479() >> 4;
         int maxDist = VSSServerConfig.CONFIG.lodDistanceChunks + 32;

         for (int i = 0; i < payload.count(); i++) {
            long packedPosition = payload.packedPositions()[i];
            int cx = PositionUtil.unpackX(packedPosition);
            int cz = PositionUtil.unpackZ(packedPosition);
            if (PositionUtil.chebyshevDistance(cx, cz, playerCx, playerCz) <= maxDist) {
               state.addRequest(payload.requestIds()[i], packedPosition, payload.clientTimestamps()[i]);
            }
         }
      }
   }

   public void handleCancel(class_3222 player, CancelRequestC2SPayload payload) {
      PlayerRequestState state = this.players.get(player.method_5667());
      if (state != null && state.hasCompletedHandshake()) {
         state.addCancel(payload.requestId());
      }
   }

   public void handleBandwidthUpdate(class_3222 player, BandwidthUpdateC2SPayload payload) {
      PlayerRequestState state = this.players.get(player.method_5667());
      if (state != null && state.hasCompletedHandshake()) {
         state.setDesiredBandwidth(payload.desiredRate());
      }
   }

   public void tick() {
      if (VSSServerConfig.CONFIG.enabled) {
         this.diag.reset(this.offThreadProcessor.getDiagnostics());
         VSSServerConfig config = VSSServerConfig.CONFIG;
         List<TickSnapshot.GenerationReadyData> generationReady = this.tickGenerationService();
         RequestProcessingService.LifecycleResult lifecycle = this.processPlayerLifecycle(config, generationReady);
         if (lifecycle.toRemove != null) {
            for (UUID uuid : lifecycle.toRemove) {
               this.removePlayer(uuid);
            }
         }

         this.postSnapshot(lifecycle, generationReady, config);
         this.drainSendActions();
         this.drainGenerationTicketRequests();
         this.flushSendQueues(lifecycle.activeCount, config);
         this.tickDirtyBroadcast(config);
         this.tickDiagnosticsLog(config);
      }
   }

   private List<TickSnapshot.GenerationReadyData> tickGenerationService() {
      return this.generationService == null ? List.of() : this.generationService.tick();
   }

   private RequestProcessingService.LifecycleResult processPlayerLifecycle(VSSServerConfig config, List<TickSnapshot.GenerationReadyData> generationReady) {
      this.reusablePlayerTickData.clear();
      this.reusableLoadedChunkProbes.clear();
      Map<UUID, TickSnapshot.PlayerTickData> playerTickData = this.reusablePlayerTickData;
      Map<UUID, Long2ObjectMap<LoadedColumnData>> loadedChunkProbes = this.reusableLoadedChunkProbes;
      Map<UUID, LongOpenHashSet> genReadyPositions = null;
      if (!generationReady.isEmpty()) {
         genReadyPositions = new HashMap<>();

         for (TickSnapshot.GenerationReadyData genData : generationReady) {
            genReadyPositions.computeIfAbsent(genData.playerUuid(), k -> new LongOpenHashSet())
               .add(PositionUtil.packPosition(genData.columnData().cx(), genData.columnData().cz()));
         }
      }

      int activeCount = 0;
      List<UUID> toRemove = null;

      for (PlayerRequestState state : this.players.values()) {
         if (state.hasCompletedHandshake()) {
            activeCount++;
            this.diag.updateQueuePeak(state.getSendQueueSize());
            boolean removed = false;
            boolean dimensionChanged = false;
            if (state.getPlayer().method_31481()) {
               class_3222 current = this.server.method_3760().method_14602(state.getPlayer().method_5667());
               if (current == null) {
                  if (toRemove == null) {
                     toRemove = new ArrayList<>();
                  }

                  toRemove.add(state.getPlayer().method_5667());
                  removed = true;
               } else {
                  state.updatePlayer(current);
               }
            }

            if (!removed) {
               if (state.checkDimensionChange()) {
                  state.onDimensionChange();
                  this.cleanupPlayerServices(state.getPlayer().method_5667());
                  this.diskReader.registerPlayer(state.getPlayer().method_5667());
                  if (this.generationService != null) {
                     this.generationService.registerPlayer(state.getPlayer().method_5667());
                  }

                  dimensionChanged = true;
               }

               class_3222 player = state.getPlayer();
               class_3218 level = player.method_51469();
               String dimension = this.dimensionStringCache.computeIfAbsent(level, l -> l.method_27983().method_29177().toString());
               this.offThreadProcessor.updateDimensionContext(dimension, level);
               playerTickData.put(player.method_5667(), new TickSnapshot.PlayerTickData(dimension, dimensionChanged));
               if (!dimensionChanged) {
                  LongOpenHashSet skipPositions = genReadyPositions != null ? genReadyPositions.get(player.method_5667()) : null;
                  Long2ObjectMap<LoadedColumnData> probes = this.probeLoadedChunks(state, level, skipPositions);
                  if (!probes.isEmpty()) {
                     loadedChunkProbes.put(player.method_5667(), probes);
                  }
               }
            }
         }
      }

      return new RequestProcessingService.LifecycleResult(playerTickData, loadedChunkProbes, activeCount, toRemove);
   }

   private void postSnapshot(RequestProcessingService.LifecycleResult lifecycle, List<TickSnapshot.GenerationReadyData> generationReady, VSSServerConfig config) {
      List<UUID> removed = lifecycle.toRemove != null ? lifecycle.toRemove : List.of();
      TickSnapshot snapshot = new TickSnapshot(
         lifecycle.playerTickData, lifecycle.loadedChunkProbes, generationReady, removed, config.sendQueueLimitPerPlayer, false
      );
      this.offThreadProcessor.postSnapshot(snapshot);
   }

   private void flushSendQueues(int activeCount, VSSServerConfig config) {
      long perPlayerAllocation = this.bandwidthLimiter.getPerPlayerAllocation(activeCount);
      long perPlayerCap = Math.min(perPlayerAllocation, config.bytesPerSecondLimitPerPlayer);

      for (PlayerRequestState state : this.players.values()) {
         if (state.hasCompletedHandshake()) {
            long effective = Math.min(perPlayerCap, Math.max(1L, state.getDesiredBandwidth()));
            this.flushSendQueue(state, effective);
         }
      }
   }

   private void tickDirtyBroadcast(VSSServerConfig config) {
      this.dirtyBroadcaster.tick(config);
   }

   private void tickDiagnosticsLog(VSSServerConfig config) {
      if (++this.diagLogCounter >= 100) {
         this.diagLogCounter = 0;
         if (VSSLogger.isDebugEnabled()) {
            long uptimeSec = this.getUptimeSeconds();
            long bwRate = uptimeSec > 0L ? this.bandwidthLimiter.getTotalBytesSent() / uptimeSec : 0L;
            VSSLogger.debug(this.diag.formatSummary(bwRate, config.bytesPerSecondLimitGlobal));

            for (PlayerRequestState state : this.players.values()) {
               if (state.hasCompletedHandshake()) {
                  RateLimiterSet rl = state.getRateLimiters();
                  VSSLogger.debug(
                     String.format(
                        "  %s: sq=%d, psync=%d, pgen=%d, syncCC=%d/%d, genCC=%d/%d, wq=%d",
                        state.getPlayer().method_5477().getString(),
                        state.getSendQueueSize(),
                        state.getPendingSyncCount(),
                        state.getPendingGenerationCount(),
                        rl.syncOnLoad().getCurrentConcurrency(),
                        rl.syncOnLoad().getMaxConcurrency(),
                        rl.generation().getCurrentConcurrency(),
                        rl.generation().getMaxConcurrency(),
                        state.getWaitingQueueSize()
                     )
                  );
               }
            }
         }
      }
   }

   private Long2ObjectMap<LoadedColumnData> probeLoadedChunks(PlayerRequestState state, class_3218 level, LongOpenHashSet skipPositions) {
      Long2ObjectOpenHashMap<LoadedColumnData> probes = new Long2ObjectOpenHashMap();
      int probed = 0;

      for (IncomingRequest req : state.getIncomingRequests()) {
         if (probed >= 512) {
            break;
         }

         long packed = PositionUtil.packPosition(req.cx(), req.cz());
         if (!probes.containsKey(packed) && (skipPositions == null || !skipPositions.contains(packed))) {
            class_2818 chunk = level.method_14178().method_21730(req.cx(), req.cz());
            if (chunk != null) {
               probes.put(packed, SectionSerializer.serializeColumn(level, chunk, req.cx(), req.cz()));
            }

            probed++;
         }
      }

      return probes;
   }

   private void drainGenerationTicketRequests() {
      if (this.generationService != null) {
         OffThreadProcessor.GenerationTicketRequest req;
         while ((req = this.offThreadProcessor.pollGenerationTicketRequest()) != null) {
            PlayerRequestState state = this.players.get(req.playerUuid());
            if (state != null && state.hasCompletedHandshake()) {
               class_3222 player = state.getPlayer();
               if (!player.method_31481()) {
                  class_3218 level = player.method_51469();
                  boolean accepted = this.generationService
                     .submitGeneration(req.playerUuid(), req.requestId(), level, req.cx(), req.cz(), req.submissionOrder());
                  if (!accepted) {
                     this.generationService
                        .addResult(req.playerUuid(), ChunkDiskReader.emptyResult(req.playerUuid(), req.requestId(), req.cx(), req.cz(), req.submissionOrder()));
                  }
               }
            }
         }
      }
   }

   private void drainSendActions() {
      this.sendActionBatcher.clear();

      SendAction action;
      while ((action = this.offThreadProcessor.pollSendAction()) != null) {
         PlayerRequestState state = this.players.get(action.playerUuid());
         if (state != null && state.hasCompletedHandshake()) {
            this.sendActionBatcher.add(action.playerUuid(), action.responseType(), action.requestId());
         }
      }

      if (!this.sendActionBatcher.isEmpty()) {
         this.sendActionBatcher.forEach((uuid, types, ids, count) -> {
            PlayerRequestState statex = this.players.get(uuid);
            if (statex != null && statex.hasCompletedHandshake()) {
               try {
                  ServerPlayNetworking.send(statex.getPlayer(), new BatchResponseS2CPayload(types, ids, count));
               } catch (Exception e) {
                  VSSLogger.error("Failed to send batch response to " + statex.getPlayer().method_5477().getString(), e);
               }
            }
         });
      }
   }

   private void flushSendQueue(PlayerRequestState state, long allocationBytes) {
      state.drainReadyPayloads();
      PriorityQueue<PlayerRequestState.QueuedPayload> queue = state.getSendQueue();

      while (!queue.isEmpty()) {
         if (!state.canSend(allocationBytes)) {
            return;
         }

         PlayerRequestState.QueuedPayload queued = queue.peek();

         try {
            ServerPlayNetworking.send(state.getPlayer(), queued.payload());
            queue.poll();
            state.recordSend(queued.estimatedBytes());
            this.bandwidthLimiter.recordSend(queued.estimatedBytes());
            this.diag.recordSectionSent(queued.estimatedBytes());
         } catch (Exception e) {
            VSSLogger.error(
               "Failed to send queued payload to " + state.getPlayer().method_5477().getString() + ", dropping remaining queue (" + queue.size() + " entries)",
               e
            );
            queue.clear();
            return;
         }
      }
   }

   public Map<UUID, PlayerRequestState> getPlayers() {
      return Collections.unmodifiableMap(this.players);
   }

   public ChunkDiskReader getDiskReader() {
      return this.diskReader;
   }

   public ChunkGenerationService getGenerationService() {
      return this.generationService;
   }

   public SharedBandwidthLimiter getBandwidthLimiter() {
      return this.bandwidthLimiter;
   }

   public long getUptimeSeconds() {
      return (System.nanoTime() - this.startTimeNanos) / 1000000000L;
   }

   public OffThreadProcessor<?, ?> getOffThreadProcessor() {
      return this.offThreadProcessor;
   }

   public DirtyColumnTracker getDirtyTracker() {
      return this.dirtyTracker;
   }

   public String getTickDiagnostics() {
      return this.diag.format(VSSServerConfig.CONFIG.sendQueueLimitPerPlayer);
   }

   public long getWindowBandwidthRate() {
      return this.diag.getWindowBytesPerSecond();
   }

   public void shutdown() {
      try {
         this.offThreadProcessor.shutdown();
      } catch (Exception e) {
         VSSLogger.error("Error shutting down off-thread processor", e);
      }

      this.players.clear();

      try {
         this.diskReader.shutdown();
      } catch (Exception e) {
         VSSLogger.error("Error shutting down disk reader", e);
      }

      try {
         if (this.generationService != null) {
            this.generationService.shutdown();
         }
      } catch (Exception e) {
         VSSLogger.error("Error shutting down generation service", e);
      }
   }

   private record LifecycleResult(
      Map<UUID, TickSnapshot.PlayerTickData> playerTickData,
      Map<UUID, Long2ObjectMap<LoadedColumnData>> loadedChunkProbes,
      int activeCount,
      List<UUID> toRemove
   ) {
   }
}
