package dev.micr.vss.common.processing;

import dev.micr.vss.common.PositionUtil;
import dev.micr.vss.common.VSSConstants;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.voxel.ColumnTimestampCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class OffThreadProcessor<PlayerState extends PlayerStateAccess, ReadResult extends ReadResultAccess> {
   private static final int SNAPSHOT_POLL_MS = 50;
   private static final int SHUTDOWN_JOIN_MS = 5000;
   private static final int EVICTION_INTERVAL_CYCLES = 1200;
   private static final int SAVE_INTERVAL_CYCLES = 6000;
   private final Object snapshotLock = new Object();
   private TickSnapshot pendingSnapshot;
   private final ConcurrentLinkedQueue<SendAction> sendActions = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<OffThreadProcessor.GenerationTicketRequest> generationTicketRequests = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<OffThreadProcessor.TimestampInvalidation> timestampInvalidations = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<TickSnapshot.GenerationReadyData> droppedGenerationReady = new ConcurrentLinkedQueue<>();
   private final Thread processingThread;
   private final ColumnTimestampCache timestampCache;
   private final Map<UUID, PlayerState> players;
   private final boolean diskReadingAvailable;
   private final boolean generationAvailable;
   private final ProcessingContext ctx;
   private final IncomingRequestRouter<PlayerState> requestRouter;
   private final DedupTracker dedupTracker = new DedupTracker();
   private final Path dataDir;
   private int evictionCounter;
   private int saveCounter;
   private int consecutiveErrors;
   private long cycleNow;
   private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "VSS-TimestampSave");
      t.setDaemon(true);
      return t;
   });

   protected OffThreadProcessor(
      Map<UUID, PlayerState> players, boolean diskReadingAvailable, boolean generationAvailable, Path dataDir, int perDimensionTimestampCacheSizeMB
   ) {
      this.players = players;
      this.diskReadingAvailable = diskReadingAvailable;
      this.generationAvailable = generationAvailable;
      this.dataDir = dataDir;
      this.timestampCache = new ColumnTimestampCache(ColumnTimestampCache.mbToEntries(perDimensionTimestampCacheSizeMB));
      if (dataDir != null) {
         this.timestampCache.load(dataDir);
      }

      this.ctx = new ProcessingContext(this.sendActions, this.generationTicketRequests, new ProcessingDiagnostics(), new SequenceCounter());
      this.requestRouter = new IncomingRequestRouter<>(this.timestampCache, this.dedupTracker, diskReadingAvailable, generationAvailable, this.ctx);
      this.processingThread = new Thread(this::processingLoop, "VSS Processing Thread");
      this.processingThread.setDaemon(true);
      this.processingThread.setPriority(4);
   }

   public void start() {
      this.processingThread.start();
   }

   public void postSnapshot(TickSnapshot snapshot) {
      synchronized (this.snapshotLock) {
         if (this.pendingSnapshot != null) {
            for (TickSnapshot.GenerationReadyData genReady : this.pendingSnapshot.generationReady()) {
               this.droppedGenerationReady.add(genReady);
            }
         }

         this.pendingSnapshot = snapshot;
         this.snapshotLock.notifyAll();
      }
   }

   public SendAction pollSendAction() {
      return this.sendActions.poll();
   }

   public OffThreadProcessor.GenerationTicketRequest pollGenerationTicketRequest() {
      return this.generationTicketRequests.poll();
   }

   public void invalidateTimestamps(String dimension, long[] positions) {
      this.timestampInvalidations.add(new OffThreadProcessor.TimestampInvalidation(dimension, positions));
   }

   protected abstract ReadResult pollDiskResult(PlayerState var1);

   protected abstract ReadResult pollGenerationResult(PlayerState var1);

   protected abstract void enqueueResultPayloads(PlayerState var1, ReadResult var2);

   protected abstract void submitDiskRead(UUID var1, int var2, String var3, int var4, int var5, long var6);

   protected boolean compressAndEnqueueLoaded(
      PlayerState state, LoadedColumnData column, int requestId, long columnTimestamp, long submissionOrder, String dimension
   ) {
      if (column.serializedSections() != null && column.serializedSections().length != 0) {
         int estimatedBytes = column.serializedSections().length + 25;
         long packed = PositionUtil.packPosition(column.cx(), column.cz());
         this.timestampCache.put(dimension, packed, columnTimestamp, this.cycleNow);
         this.buildAndEnqueueColumnPayload(
            state, column.cx(), column.cz(), dimension, requestId, columnTimestamp, submissionOrder, column.serializedSections(), estimatedBytes
         );
         return true;
      } else {
         return false;
      }
   }

   protected abstract void buildAndEnqueueColumnPayload(
      PlayerState var1, int var2, int var3, String var4, int var5, long var6, long var8, byte[] var10, int var11
   );

   private void processingLoop() {
      while (true) {
         TickSnapshot snapshot;
         synchronized (this.snapshotLock) {
            while (this.pendingSnapshot == null) {
               try {
                  this.snapshotLock.wait(50L);
               } catch (InterruptedException e) {
                  return;
               }
            }

            snapshot = this.pendingSnapshot;
            this.pendingSnapshot = null;
         }

         if (snapshot.shutdown()) {
            return;
         }

         try {
            this.processCycle(snapshot);
            this.consecutiveErrors = 0;
         } catch (Exception e) {
            VSSLogger.error("Error in processing cycle", e);
            if (++this.consecutiveErrors >= 10) {
               VSSLogger.error("Processing thread hit " + this.consecutiveErrors + " consecutive errors, backing off");

               try {
                  Thread.sleep(1000L);
               } catch (InterruptedException ie) {
                  return;
               }
            }
         }
      }
   }

   private void processCycle(TickSnapshot snapshot) {
      this.cycleNow = VSSConstants.epochSeconds();
      this.ctx.diagnostics().resetTickCounters();
      this.releaseDroppedGenerationSlots();
      this.preparePlayers(snapshot);
      this.drainDiskResultsForAllPlayers(snapshot);
      this.drainGenerationResultsForAllPlayers(snapshot);
      this.processGenerationReady(snapshot);
      this.routeIncomingRequests(snapshot);
      if (++this.evictionCounter >= 1200) {
         this.evictionCounter = 0;
         int evicted = this.timestampCache.evictIfOversized();
         if (evicted > 0 && VSSLogger.isDebugEnabled()) {
            VSSLogger.debug("Evicted " + evicted + " oversized timestamp cache entries (" + this.timestampCache.size() + " remaining)");
         }
      }

      if (this.dataDir != null && ++this.saveCounter >= 6000) {
         this.saveCounter = 0;
         ColumnTimestampCache cacheSnapshot = this.timestampCache.snapshotForSave();
         SAVE_EXECUTOR.execute(() -> cacheSnapshot.save(this.dataDir));
      }
   }

   private void releaseDroppedGenerationSlots() {
      TickSnapshot.GenerationReadyData genReady;
      while ((genReady = this.droppedGenerationReady.poll()) != null) {
         PlayerState state = this.players.get(genReady.playerUuid());
         if (state != null) {
            state.removePendingByPosition(genReady.columnData().cx(), genReady.columnData().cz());
            state.getRateLimiters().generation().release();
         }
      }
   }

   private void preparePlayers(TickSnapshot snapshot) {
      OffThreadProcessor.TimestampInvalidation inv;
      while ((inv = this.timestampInvalidations.poll()) != null) {
         this.timestampCache.invalidate(inv.dimension(), inv.positions());
      }

      for (UUID removedUuid : snapshot.removedPlayers()) {
         this.cleanupDedupGroups(this.dedupTracker.removePlayer(removedUuid));
      }

      for (Entry<UUID, TickSnapshot.PlayerTickData> entry : snapshot.players().entrySet()) {
         PlayerState state = this.players.get(entry.getKey());
         if (state != null) {
            if (entry.getValue().dimensionChanged()) {
               state.clearProcessingState();
               this.cleanupDedupGroups(this.dedupTracker.removePlayer(entry.getKey()));
            }

            state.drainDirtyClearRequests();

            Integer cancelId;
            while ((cancelId = state.pollCancel()) != null) {
               PendingRequest pending = state.removePendingByRequestId(cancelId);
               if (pending != null) {
                  state.getRateLimiters().forRequest(pending.type(), this.diskReadingAvailable).release();
               }
            }
         }
      }
   }

   private void cleanupDedupGroups(List<DedupTracker.RemovedGroup> removedGroups) {
      for (DedupTracker.RemovedGroup rg : removedGroups) {
         int cx = PositionUtil.unpackX(rg.packed());
         int cz = PositionUtil.unpackZ(rg.packed());

         for (DedupTracker.Attachment attachment : rg.group().attached()) {
            PlayerState attachedState = this.players.get(attachment.playerUuid());
            if (attachedState != null) {
               attachedState.removePendingByPosition(cx, cz);
               attachedState.getRateLimiters().syncOnLoad().release();
            }
         }
      }
   }

   private void drainDiskResultsForAllPlayers(TickSnapshot snapshot) {
      for (Entry<UUID, TickSnapshot.PlayerTickData> entry : snapshot.players().entrySet()) {
         PlayerState state = this.players.get(entry.getKey());
         if (state != null) {
            UUID playerUuid = entry.getKey();

            ReadResult result;
            while ((result = this.pollDiskResult(state)) != null) {
               int cx = result.chunkX();
               int cz = result.chunkZ();
               PendingRequest pending = state.removePendingByPosition(cx, cz);
               int requestId = pending != null ? pending.requestId() : result.requestId();
               state.getRateLimiters().syncOnLoad().release();
               long packed = PositionUtil.packPosition(cx, cz);
               if (result.saturated()) {
                  this.ctx.sendActions().add(new SendAction.RateLimited(playerUuid, requestId));
                  if (VSSLogger.isDebugEnabled()) {
                     VSSLogger.debug("Rate-limited " + playerUuid + " (disk saturated): chunk [" + cx + ", " + cz + "]");
                  }
               } else if (result.notFound()) {
                  this.handleDiskNotFound(playerUuid, state, requestId, cx, cz, pending);
               } else {
                  state.markDiskReadDone(cx, cz);
                  if (result.sectionBytes() != null) {
                     this.enqueueResultPayloads(state, result);
                  } else {
                     this.ctx.sendActions().add(new SendAction.ColumnUpToDate(playerUuid, requestId));
                  }

                  this.timestampCache.put(entry.getValue().dimension(), packed, result.columnTimestamp(), this.cycleNow);
               }

               this.ctx.diagnostics().incrementDiskDrained();
               DedupTracker.Group group = this.dedupTracker.removeGroup(packed);
               if (group != null) {
                  this.dispatchDedupGroup(group, result, cx, cz);
               }
            }
         }
      }
   }

   private void dispatchDedupGroup(DedupTracker.Group group, ReadResult result, int cx, int cz) {
      byte[] sectionBytes = result.sectionBytes();

      for (DedupTracker.Attachment attachment : group.attached()) {
         PlayerState attachedState = this.players.get(attachment.playerUuid());
         if (attachedState != null) {
            PendingRequest attachedPending = attachedState.removePendingByPosition(cx, cz);
            attachedState.getRateLimiters().syncOnLoad().release();
            int attachedRequestId = attachedPending != null ? attachedPending.requestId() : attachment.requestId();
            if (result.saturated()) {
               this.ctx.sendActions().add(new SendAction.RateLimited(attachment.playerUuid(), attachedRequestId));
            } else if (result.notFound()) {
               this.handleDiskNotFound(attachment.playerUuid(), attachedState, attachedRequestId, cx, cz, attachedPending);
            } else if (sectionBytes != null) {
               attachedState.markDiskReadDone(cx, cz);
               this.buildAndEnqueueColumnPayload(
                  attachedState,
                  cx,
                  cz,
                  group.dimension(),
                  attachedRequestId,
                  result.columnTimestamp(),
                  attachment.submissionOrder(),
                  sectionBytes,
                  result.estimatedBytes()
               );
            } else {
               attachedState.markDiskReadDone(cx, cz);
               this.ctx.sendActions().add(new SendAction.ColumnUpToDate(attachment.playerUuid(), attachedRequestId));
            }

            this.ctx.diagnostics().incrementDiskDrained();
         }
      }
   }

   private void handleDiskNotFound(UUID playerUuid, PlayerState state, int requestId, int cx, int cz, PendingRequest pending) {
      if (pending == null || pending.type() != RequestType.GENERATION || !this.generationAvailable) {
         this.ctx.sendActions().add(new SendAction.ColumnNotGenerated(playerUuid, requestId));
      } else if (state.getRateLimiters().generation().tryAcquire()) {
         this.ctx.generationTicketRequests().add(new OffThreadProcessor.GenerationTicketRequest(playerUuid, requestId, cx, cz, this.ctx.sequence().next()));
         state.addPendingRequest(new PendingRequest(requestId, cx, cz, RequestType.GENERATION));
      } else {
         this.ctx.sendActions().add(new SendAction.ColumnNotGenerated(playerUuid, requestId));
      }
   }

   private void drainGenerationResultsForAllPlayers(TickSnapshot snapshot) {
      for (Entry<UUID, TickSnapshot.PlayerTickData> entry : snapshot.players().entrySet()) {
         PlayerState state = this.players.get(entry.getKey());
         if (state != null) {
            UUID playerUuid = entry.getKey();

            ReadResult result;
            while ((result = this.pollGenerationResult(state)) != null) {
               int cx = result.chunkX();
               int cz = result.chunkZ();
               PendingRequest pending = state.removePendingByPosition(cx, cz);
               int requestId = pending != null ? pending.requestId() : result.requestId();
               state.getRateLimiters().generation().release();
               if (result.notFound()) {
                  this.ctx.sendActions().add(new SendAction.ColumnNotGenerated(playerUuid, requestId));
               } else {
                  state.markDiskReadDone(cx, cz);
                  this.enqueueResultPayloads(state, result);
                  long packed = PositionUtil.packPosition(cx, cz);
                  this.timestampCache.put(entry.getValue().dimension(), packed, result.columnTimestamp(), this.cycleNow);
               }

               this.ctx.diagnostics().incrementGenDrained();
            }
         }
      }
   }

   private void processGenerationReady(TickSnapshot snapshot) {
      for (TickSnapshot.GenerationReadyData genReady : snapshot.generationReady()) {
         PlayerState state = this.players.get(genReady.playerUuid());
         if (state != null) {
            int cx = genReady.columnData().cx();
            int cz = genReady.columnData().cz();
            state.removePendingByPosition(cx, cz);
            state.markDiskReadDone(cx, cz);
            state.getRateLimiters().generation().release();
            TickSnapshot.PlayerTickData playerData = snapshot.players().get(genReady.playerUuid());
            if (playerData != null) {
               String dimension = playerData.dimension();
               boolean sent = this.compressAndEnqueueLoaded(
                  state, genReady.columnData(), genReady.requestId(), genReady.columnTimestamp(), genReady.submissionOrder(), dimension
               );
               if (!sent) {
                  this.sendActions.add(new SendAction.ColumnUpToDate(genReady.playerUuid(), genReady.requestId()));
               }

               this.ctx.diagnostics().incrementGenDrained();
            }
         }
      }
   }

   private void routeIncomingRequests(TickSnapshot snapshot) {
      this.requestRouter.routeAll(snapshot, this.players, this::submitDiskRead, this::compressAndEnqueueLoaded, this.cycleNow);
   }

   public ProcessingDiagnostics getDiagnostics() {
      return this.ctx.diagnostics();
   }

   public void shutdown() {
      this.postSnapshot(TickSnapshot.shutdownSentinel());

      try {
         this.processingThread.interrupt();
         this.processingThread.join(5000L);
         if (this.processingThread.isAlive()) {
            VSSLogger.warn("Processing thread did not terminate within 5000ms");
         }
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }

      if (this.dataDir != null) {
         this.timestampCache.save(this.dataDir);
      }
   }

   public record GenerationTicketRequest(UUID playerUuid, int requestId, int cx, int cz, long submissionOrder) {
   }

   private record TimestampInvalidation(String dimension, long[] positions) {
   }
}
