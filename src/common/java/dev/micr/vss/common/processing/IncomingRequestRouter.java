package dev.micr.vss.common.processing;

import dev.micr.vss.common.PositionUtil;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.voxel.ColumnTimestampCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

class IncomingRequestRouter<PS extends PlayerStateAccess> {
   private final ColumnTimestampCache timestampCache;
   private final DedupTracker dedupTracker;
   private final boolean diskReadingAvailable;
   private final boolean generationAvailable;
   private final ProcessingContext ctx;

   IncomingRequestRouter(
      ColumnTimestampCache timestampCache, DedupTracker dedupTracker, boolean diskReadingAvailable, boolean generationAvailable, ProcessingContext ctx
   ) {
      this.timestampCache = timestampCache;
      this.dedupTracker = dedupTracker;
      this.diskReadingAvailable = diskReadingAvailable;
      this.generationAvailable = generationAvailable;
      this.ctx = ctx;
   }

   void routeAll(
      TickSnapshot snapshot,
      Map<UUID, PS> players,
      IncomingRequestRouter.DiskReadSubmitter diskReadSubmitter,
      IncomingRequestRouter.LoadedColumnSerializer<PS> loadedSerializer,
      long cycleNow
   ) {
      for (Entry<UUID, TickSnapshot.PlayerTickData> entry : snapshot.players().entrySet()) {
         if (!entry.getValue().dimensionChanged()) {
            PS state = players.get(entry.getKey());
            if (state != null && state.supportsVoxelColumns()) {
               this.processIncomingRequests(state, entry.getKey(), entry.getValue(), snapshot, diskReadSubmitter, loadedSerializer, cycleNow);
            }
         }
      }
   }

   private void processIncomingRequests(
      PS state,
      UUID playerUuid,
      TickSnapshot.PlayerTickData playerData,
      TickSnapshot snapshot,
      IncomingRequestRouter.DiskReadSubmitter diskReadSubmitter,
      IncomingRequestRouter.LoadedColumnSerializer<PS> loadedSerializer,
      long cycleNow
   ) {
      String dimension = playerData.dimension();
      Long2ObjectMap<LoadedColumnData> loadedProbes = snapshot.loadedChunkProbes().getOrDefault(playerUuid, Long2ObjectMaps.emptyMap());
      this.drainWaitingQueue(state, playerUuid, dimension, loadedProbes, snapshot, diskReadSubmitter, loadedSerializer, cycleNow);

      IncomingRequest req;
      while ((req = state.pollIncomingRequest()) != null) {
         if (!this.resolvedAsDuplicate(state, playerUuid, req)) {
            if (this.sendQueueFull(state, snapshot)) {
               break;
            }

            long packed = PositionUtil.packPosition(req.cx(), req.cz());
            if (!this.resolvedFromTimestamp(state, playerUuid, req, packed, dimension)
               && !this.resolvedFromLoadedProbe(state, playerUuid, req, packed, loadedProbes, dimension, loadedSerializer, cycleNow)) {
               RequestType type = req.clientTimestamp() == 0L ? RequestType.GENERATION : RequestType.SYNC;
               ConcurrencyLimiter limiter = this.tryConcurrencyOrEnqueue(state, playerUuid, req, type, dimension);
               if (limiter != null) {
                  this.submitToDiskOrGeneration(state, playerUuid, req, packed, dimension, type, limiter, diskReadSubmitter);
               }
            }
         }
      }
   }

   private void drainWaitingQueue(
      PS state,
      UUID playerUuid,
      String dimension,
      Long2ObjectMap<LoadedColumnData> loadedProbes,
      TickSnapshot snapshot,
      IncomingRequestRouter.DiskReadSubmitter diskReadSubmitter,
      IncomingRequestRouter.LoadedColumnSerializer<PS> loadedSerializer,
      long cycleNow
   ) {
      ArrayDeque<AbstractPlayerRequestState.QueuedRequest> queue = state.getWaitingQueue();

      while (!queue.isEmpty()) {
         AbstractPlayerRequestState.QueuedRequest queued = queue.peek();
         IncomingRequest req = queued.request();
         long packed = PositionUtil.packPosition(req.cx(), req.cz());
         if (state.hasDiskReadDone(req.cx(), req.cz())) {
            queue.poll();
            this.ctx.sendActions().add(new SendAction.ColumnUpToDate(playerUuid, req.requestId()));
         } else if (this.resolvedFromTimestamp(state, playerUuid, req, packed, dimension)) {
            queue.poll();
         } else if (this.resolvedFromLoadedProbe(state, playerUuid, req, packed, loadedProbes, dimension, loadedSerializer, cycleNow)) {
            queue.poll();
         } else {
            ConcurrencyLimiter limiter = state.getRateLimiters().forRequest(queued.type(), this.diskReadingAvailable);
            if (!limiter.tryAcquire()) {
               break;
            }

            queue.poll();
            this.submitToDiskOrGeneration(state, playerUuid, req, packed, dimension, queued.type(), limiter, diskReadSubmitter);
         }
      }
   }

   private boolean resolvedAsDuplicate(PS state, UUID playerUuid, IncomingRequest req) {
      if (state.hasDiskReadDone(req.cx(), req.cz())) {
         this.ctx.sendActions().add(new SendAction.ColumnUpToDate(playerUuid, req.requestId()));
         return true;
      } else if (state.hasPendingRequest(req.cx(), req.cz())) {
         this.ctx.diagnostics().incrementSkippedDuplicate();
         return true;
      } else {
         return false;
      }
   }

   private boolean sendQueueFull(PS state, TickSnapshot snapshot) {
      if (snapshot.maxSendQueueSize() > 0 && state.getSendQueueSize() >= snapshot.maxSendQueueSize()) {
         this.ctx.diagnostics().incrementQueueFull();
         return true;
      } else {
         return false;
      }
   }

   private ConcurrencyLimiter tryConcurrencyOrEnqueue(PS state, UUID playerUuid, IncomingRequest req, RequestType type, String dimension) {
      RateLimiterSet limiters = state.getRateLimiters();
      ConcurrencyLimiter limiter = limiters.forRequest(type, this.diskReadingAvailable);
      if (limiter.tryAcquire()) {
         return limiter;
      }

      int queueCap = limiters.syncRateLimit() + limiters.genRateLimit();
      if (state.getWaitingQueueSize() < queueCap) {
         state.getWaitingQueue().add(new AbstractPlayerRequestState.QueuedRequest(req, type, dimension));
         this.ctx.diagnostics().incrementQueued();
      } else {
         this.ctx.sendActions().add(new SendAction.RateLimited(playerUuid, req.requestId()));
         this.ctx.diagnostics().incrementRateLimited(type);
         if (VSSLogger.isDebugEnabled()) {
            VSSLogger.debug(
               "Rate-limited " + playerUuid + " (" + type + "): queue full at " + queueCap + " for chunk [" + req.cx() + ", " + req.cz() + "] in " + dimension
            );
         }
      }

      return null;
   }

   private boolean resolvedFromLoadedProbe(
      PS state,
      UUID playerUuid,
      IncomingRequest req,
      long packed,
      Long2ObjectMap<LoadedColumnData> probes,
      String dimension,
      IncomingRequestRouter.LoadedColumnSerializer<PS> loadedSerializer,
      long cycleNow
   ) {
      LoadedColumnData probe = (LoadedColumnData)probes.get(packed);
      if (probe == null) {
         return false;
      }

      long ts = cycleNow;
      boolean sent = loadedSerializer.serializeAndEnqueue(state, probe, req.requestId(), ts, this.ctx.sequence().next(), dimension);
      if (!sent) {
         this.ctx.sendActions().add(new SendAction.ColumnUpToDate(playerUuid, req.requestId()));
      }

      state.markDiskReadDone(req.cx(), req.cz());
      this.ctx.diagnostics().incrementInMemory();
      return true;
   }

   private boolean resolvedFromTimestamp(PS state, UUID playerUuid, IncomingRequest req, long packed, String dimension) {
      if (req.clientTimestamp() <= 0L) {
         return false;
      } else {
         long cachedTs = this.timestampCache.get(dimension, packed);
         if (cachedTs > 0L && cachedTs <= req.clientTimestamp()) {
            state.markDiskReadDone(req.cx(), req.cz());
            this.ctx.sendActions().add(new SendAction.ColumnUpToDate(playerUuid, req.requestId()));
            this.ctx.diagnostics().incrementUpToDate();
            return true;
         } else {
            return false;
         }
      }
   }

   private void submitToDiskOrGeneration(
      PS state,
      UUID playerUuid,
      IncomingRequest req,
      long packed,
      String dimension,
      RequestType type,
      ConcurrencyLimiter limiter,
      IncomingRequestRouter.DiskReadSubmitter diskReadSubmitter
   ) {
      long order = this.ctx.sequence().next();
      if (type == RequestType.SYNC || this.diskReadingAvailable) {
         state.addPendingRequest(new PendingRequest(req.requestId(), req.cx(), req.cz(), type));
         boolean attached = this.dedupTracker.tryAttachOrCreate(packed, dimension, playerUuid, req.requestId(), order);
         if (!attached) {
            diskReadSubmitter.submit(playerUuid, req.requestId(), dimension, req.cx(), req.cz(), order);
         }

         this.ctx.diagnostics().incrementDiskQueued();
      } else if (type == RequestType.GENERATION && this.generationAvailable) {
         state.addPendingRequest(new PendingRequest(req.requestId(), req.cx(), req.cz(), type));
         this.ctx.generationTicketRequests().add(new OffThreadProcessor.GenerationTicketRequest(playerUuid, req.requestId(), req.cx(), req.cz(), order));
      } else {
         this.ctx.sendActions().add(new SendAction.ColumnNotGenerated(playerUuid, req.requestId()));
         limiter.release();
      }
   }

   @FunctionalInterface
   interface DiskReadSubmitter {
      void submit(UUID var1, int var2, String var3, int var4, int var5, long var6);
   }

   @FunctionalInterface
   interface LoadedColumnSerializer<PS> {
      boolean serializeAndEnqueue(PS var1, LoadedColumnData var2, int var3, long var4, long var6, String var8);
   }
}
