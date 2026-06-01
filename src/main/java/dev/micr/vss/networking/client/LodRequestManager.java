package dev.micr.vss.networking.client;

import dev.micr.vss.common.VSSConstants;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.networking.payloads.BatchChunkRequestC2SPayload;
import dev.micr.vss.networking.payloads.CancelRequestC2SPayload;
import dev.micr.vss.networking.payloads.SessionConfigS2CPayload;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1937;
import net.minecraft.class_310;
import net.minecraft.class_5321;
import net.minecraft.class_631;
import net.minecraft.class_638;
import net.minecraft.class_746;

public class LodRequestManager {
   private static final int BACKPRESSURE_NUMERATOR = 3;
   private static final int BACKPRESSURE_DENOMINATOR = 4;
   private static final int MIN_SEND_PER_TICK = 16;
   private static final long TIMEOUT_NANOS = 10000000000L;
   private SessionConfigS2CPayload sessionConfig;
   private String serverAddress;
   private int lastChunkX;
   private int lastChunkZ;
   private class_5321<class_1937> lastDimension;
   private final Long2LongOpenHashMap columnTimestamps = new Long2LongOpenHashMap();
   private final InFlightTracker tracker;
   private final RequestQueue queue;
   private boolean cacheLoaded;
   private volatile CompletableFuture<Long2LongOpenHashMap> pendingCacheLoad;
   private final LongOpenHashSet dirtyColumns;
   private final LongOpenHashSet rateLimitRetryPositions;
   private final LongOpenHashSet validatedThisSession;
   private final RequestMetrics metrics;
   private int maxGenConcurrency;
   private int maxSyncConcurrency;
   private boolean skipNextScan;
   private int maxSendPerTick;
   private int[] sendRequestIdBuffer;
   private long[] sendPositionBuffer;
   private long[] sendTimestampBuffer;
   private final SpiralScanner scanner;

   public LodRequestManager() {
      this.columnTimestamps.defaultReturnValue(-1L);
      this.tracker = new InFlightTracker();
      this.queue = new RequestQueue();
      this.cacheLoaded = false;
      this.pendingCacheLoad = null;
      this.dirtyColumns = new LongOpenHashSet();
      this.rateLimitRetryPositions = new LongOpenHashSet();
      this.validatedThisSession = new LongOpenHashSet();
      this.metrics = new RequestMetrics();
      this.maxSendPerTick = 16;
      this.sendRequestIdBuffer = new int[16];
      this.sendPositionBuffer = new long[16];
      this.sendTimestampBuffer = new long[16];
      this.scanner = new SpiralScanner();
   }

   private void putTimestamp(long packed, long timestamp) {
      long old = this.columnTimestamps.put(packed, timestamp);
      this.metrics.adjustCounters(old, timestamp);
   }

   private void clearTimestamps() {
      this.columnTimestamps.clear();
      this.metrics.reset();
   }

   private void loadTimestamps(Long2LongOpenHashMap loaded) {
      this.columnTimestamps.putAll(loaded);
      this.metrics.bulkRecount(this.columnTimestamps);
   }

   public void onSessionConfig(SessionConfigS2CPayload config, String serverAddress) {
      this.sessionConfig = config;
      this.serverAddress = serverAddress;
      this.resetRequestState();
      this.lastDimension = null;
      this.cacheLoaded = false;
      this.scanner.reset();
      this.maxGenConcurrency = config.generationConcurrencyLimitPerPlayer();
      this.maxSyncConcurrency = config.syncOnLoadConcurrencyLimitPerPlayer();
      this.skipNextScan = false;
   }

   private static int countMissingVanillaChunks(class_638 level, int playerCx, int playerCz, int radius) {
      class_631 chunkSource = level.method_2935();
      int missing = 0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (!chunkSource.method_12123(playerCx + dx, playerCz + dz)) {
               missing++;
            }
         }
      }

      return missing;
   }

   public void tick() {
      if (this.sessionConfig != null && this.sessionConfig.enabled()) {
         class_310 mc = class_310.method_1551();
         class_746 player = mc.field_1724;
         if (player != null && !player.method_29504()) {
            class_638 level = mc.field_1687;
            if (level != null) {
               int playerCx = player.method_31477() >> 4;
               int playerCz = player.method_31479() >> 4;
               class_5321<class_1937> currentDim = level.method_27983();
               if (this.lastDimension != null && !currentDim.equals(this.lastDimension)) {
                  this.onDimensionChange(currentDim);
               } else if (!this.cacheLoaded) {
                  this.cacheLoaded = true;
                  this.startAsyncCacheLoad(currentDim);
               }

               this.lastDimension = currentDim;
               if (playerCx != this.lastChunkX || playerCz != this.lastChunkZ) {
                  int pruneDistance = this.scanner.getPruneDistance(this.sessionConfig);
                  this.scanner.pruneOutOfRangeTimestamps(this.columnTimestamps, this.metrics, playerCx, playerCz, pruneDistance);
                  this.scanner.pruneOutOfRangePositions(this.dirtyColumns, playerCx, playerCz, pruneDistance);
                  this.scanner.pruneOutOfRangePositions(this.rateLimitRetryPositions, playerCx, playerCz, pruneDistance);
                  this.scanner.pruneOutOfRangePositions(this.validatedThisSession, playerCx, playerCz, pruneDistance);
                  this.pruneAndCancelOutOfRangePending(playerCx, playerCz, pruneDistance);
                  this.lastChunkX = playerCx;
                  this.lastChunkZ = playerCz;
                  this.scanner.resetScanCounter();
               }

               this.metrics.updateRollingRates();
               int columnQueueSize = VSSClientNetworking.getQueuedColumnCount();
               int columnQueueCapacity = 8000;
               if (columnQueueSize < columnQueueCapacity * 3 / 4) {
                  if (this.pendingCacheLoad != null) {
                     if (!this.pendingCacheLoad.isDone()) {
                        return;
                     }

                     try {
                        Long2LongOpenHashMap loaded = this.pendingCacheLoad.getNow(null);
                        if (loaded != null && this.lastDimension != null) {
                           this.loadTimestamps(loaded);
                        }
                     } catch (Exception var15) {
                     }

                     this.pendingCacheLoad = null;
                  }

                  if (this.scanner.advanceScanTick()) {
                     if (this.skipNextScan) {
                        this.skipNextScan = false;
                     } else {
                        int viewDistance = mc.field_1690.method_38521();
                        int missingVanilla = countMissingVanillaChunks(level, playerCx, playerCz, viewDistance);
                        this.scanner.updateMissingVanillaChunks(missingVanilla);
                        int budget = SpiralScanner.baseBudget(this.sessionConfig);
                        int haltThreshold = columnQueueCapacity * 3 / 4;
                        if (columnQueueSize > 0) {
                           budget = Math.max(1, Math.round(budget * Math.max(0.0F, 1.0F - (float)columnQueueSize / haltThreshold)));
                        }

                        if (missingVanilla > 0) {
                           int exclusionArea = (2 * viewDistance + 1) * (2 * viewDistance + 1);
                           float vanillaScale = Math.max(0.0F, 1.0F - (float)missingVanilla / exclusionArea);
                           if (vanillaScale <= 0.0F) {
                              budget = 0;
                           } else {
                              budget = Math.max(1, Math.round(budget * vanillaScale));
                           }
                        }

                        if (budget > 0) {
                           SpiralScanner.ScanResult scanResult = this.scanner
                              .scan(
                                 playerCx,
                                 playerCz,
                                 viewDistance,
                                 this.columnTimestamps,
                                 this.dirtyColumns,
                                 this.rateLimitRetryPositions,
                                 this.validatedThisSession,
                                 this.tracker::isInFlight,
                                 this.sessionConfig,
                                 budget
                              );
                           if (scanResult.count() > 0) {
                              this.queue.populate(scanResult);
                              this.updateSendPerTick(scanResult.count());
                           }
                        }
                     }

                     this.tracker.timeoutSweep(10000000000L);
                  }

                  if (this.queue.hasNext()) {
                     int sent = this.drainQueue(this.maxSendPerTick);
                     if (sent > 0) {
                        this.sendRequests(this.sendPositionBuffer, this.sendTimestampBuffer, sent);
                     }
                  }
               }
            }
         }
      }
   }

   private void updateSendPerTick(int lastScanQueued) {
      int perTick = Math.min(1024, Math.max(16, (lastScanQueued + 20 - 1) / 20));
      if (perTick != this.maxSendPerTick) {
         this.maxSendPerTick = perTick;
         if (perTick > this.sendPositionBuffer.length) {
            this.sendRequestIdBuffer = new int[perTick];
            this.sendPositionBuffer = new long[perTick];
            this.sendTimestampBuffer = new long[perTick];
         }
      }
   }

   private int drainQueue(int maxToSend) {
      long now = System.nanoTime();
      long[] positionBuffer = this.sendPositionBuffer;
      long[] timestampBuffer = this.sendTimestampBuffer;
      int count = 0;

      while (count < maxToSend && this.queue.hasNext()) {
         long pos = this.queue.peekPosition();
         long ts = this.queue.peekTimestamp();
         if (this.tracker.isInFlight(pos)) {
            this.queue.skip();
         } else {
            long stored = this.columnTimestamps.get(pos);
            if (stored > 0L && !this.dirtyColumns.contains(pos) && !this.rateLimitRetryPositions.contains(pos) && this.validatedThisSession.contains(pos)) {
               this.queue.skip();
            } else {
               boolean isGen = ts == 0L;
               if (isGen && this.tracker.generationCount() >= this.maxGenConcurrency) {
                  this.queue.skip();
               } else if (!isGen && this.tracker.size() - this.tracker.generationCount() >= this.maxSyncConcurrency) {
                  this.queue.skip();
               } else {
                  this.queue.skip();
                  positionBuffer[count] = pos;
                  timestampBuffer[count] = ts;
                  this.tracker.markPending(pos, now, isGen);
                  this.rateLimitRetryPositions.remove(pos);
                  this.dirtyColumns.remove(pos);
                  count++;
               }
            }
         }
      }

      return count;
   }

   private void sendRequests(long[] positionBuffer, long[] timestampBuffer, int count) {
      int[] requestIds = this.sendRequestIdBuffer;

      for (int i = 0; i < count; i++) {
         requestIds[i] = this.tracker.send(positionBuffer[i]);
      }

      try {
         ClientPlayNetworking.send(new BatchChunkRequestC2SPayload(requestIds, positionBuffer, timestampBuffer, count));
      } catch (Exception e) {
         VSSLogger.error("Failed to send batch chunk request", e);

         for (int i = 0; i < count; i++) {
            this.tracker.removeByRequestId(requestIds[i]);
         }
      }

      this.metrics.recordSendCycle(count);
   }

   public void onColumnReceived(int requestId, long columnTimestamp) {
      InFlightTracker.RemovedRequest completion = this.tracker.removeByRequestId(requestId);
      if (completion != null) {
         this.dirtyColumns.remove(completion.position());
         this.putTimestamp(completion.position(), columnTimestamp);
         this.validatedThisSession.add(completion.position());
      }

      this.metrics.recordColumnReceived();
   }

   public void onDirtyColumns(long[] dirtyPositions) {
      boolean added = false;

      for (long packed : dirtyPositions) {
         long stored = this.columnTimestamps.get(packed);
         if (stored > 0L) {
            this.dirtyColumns.add(packed);
            added = true;
         }
      }

      if (added) {
         this.scanner.resetScanCounter();
      }
   }

   public void onColumnNotGenerated(int requestId) {
      InFlightTracker.RemovedRequest removal = this.tracker.removeByRequestId(requestId);
      if (removal != null) {
         this.putTimestamp(removal.position(), 0L);
      }

      this.metrics.recordNotGenerated();
   }

   public void onColumnUpToDate(int requestId) {
      InFlightTracker.RemovedRequest completion = this.tracker.removeByRequestId(requestId);
      if (completion != null) {
         this.validatedThisSession.add(completion.position());
         if (this.columnTimestamps.get(completion.position()) == -1L) {
            this.putTimestamp(completion.position(), VSSConstants.epochSeconds());
         }
      }

      this.metrics.recordUpToDate();
   }

   public void onRateLimited(int requestId) {
      InFlightTracker.RemovedRequest removal = this.tracker.removeByRequestId(requestId);
      if (removal != null) {
         this.rateLimitRetryPositions.add(removal.position());
      }

      this.metrics.recordRateLimited();
      this.skipNextScan = true;
   }

   private void onDimensionChange(class_5321<class_1937> newDimension) {
      this.saveCache();
      this.cancelAllPending();
      this.resetRequestState();
      this.queue.clear();
      this.scanner.resetScanCounter();
      this.cacheLoaded = true;
      this.startAsyncCacheLoad(newDimension);
   }

   private void resetRequestState() {
      this.clearTimestamps();
      this.dirtyColumns.clear();
      this.rateLimitRetryPositions.clear();
      this.validatedThisSession.clear();
      this.tracker.clear();
      this.skipNextScan = false;
   }

   private void startAsyncCacheLoad(class_5321<class_1937> dimension) {
      this.pendingCacheLoad = ColumnCacheStore.loadAsync(this.serverAddress, dimension);
   }

   private void sendCancelPacket(int reqId) {
      try {
         ClientPlayNetworking.send(new CancelRequestC2SPayload(reqId));
      } catch (Exception var3) {
      }
   }

   private void pruneAndCancelOutOfRangePending(int playerCx, int playerCz, int pruneDistance) {
      this.tracker.pruneOutOfRange(playerCx, playerCz, pruneDistance, this::sendCancelPacket);
   }

   private void cancelAllPending() {
      this.tracker.forEachRequestId(this::sendCancelPacket);
   }

   public void disconnect() {
      this.tracker.clear();
   }

   public void saveCache() {
      if (this.serverAddress != null && this.lastDimension != null && !this.columnTimestamps.isEmpty()) {
         ColumnCacheStore.saveAsync(this.serverAddress, this.lastDimension, this.columnTimestamps);
      }
   }

   public void flushCache() {
      if (this.serverAddress != null) {
         ColumnCacheStore.clearForServer(this.serverAddress);
      }

      this.cancelAllPending();
      this.resetRequestState();
      this.queue.clear();
      this.scanner.reset();
   }

   public int getReceivedColumnCount() {
      return this.metrics.getReceivedCount();
   }

   public int getEmptyColumnCount() {
      return this.metrics.getEmptyCount();
   }

   public int getEffectiveLodDistanceChunks() {
      return this.sessionConfig != null ? this.scanner.getEffectiveLodDistance(this.sessionConfig) : 0;
   }

   public long getTotalSendCycles() {
      return this.metrics.getTotalSendCycles();
   }

   public long getTotalPositionsRequested() {
      return this.metrics.getTotalPositionsRequested();
   }

   public int getDirtyColumnCount() {
      return this.dirtyColumns.size();
   }

   public int getConfirmedRing() {
      return this.scanner.getConfirmedRing();
   }

   public int getScanRing() {
      return this.scanner.getScanRing();
   }

   public int getMissingVanillaChunks() {
      return this.scanner.getMissingVanillaChunks();
   }

   public long getTotalColumnsReceived() {
      return this.metrics.getTotalColumnsReceived();
   }

   public long getTotalUpToDate() {
      return this.metrics.getTotalUpToDate();
   }

   public long getTotalNotGenerated() {
      return this.metrics.getTotalNotGenerated();
   }

   public long getTotalRateLimited() {
      return this.metrics.getTotalRateLimited();
   }

   public double getReceiveRate() {
      return this.metrics.getReceiveRate();
   }

   public double getRequestRate() {
      return this.metrics.getRequestRate();
   }

   public int getPendingCount() {
      return this.tracker.size();
   }

   public int getQueueRemaining() {
      return this.queue.remaining();
   }

   public int getLastBudget() {
      return this.scanner.getLastBudget();
   }

   public int getLastSyncQueued() {
      return this.scanner.getLastSyncQueued();
   }

   public int getLastGenQueued() {
      return this.scanner.getLastGenQueued();
   }
}
