package dev.micr.vss.common.processing;

import dev.micr.vss.common.PositionUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPlayerRequestState<Q extends Comparable<Q>> implements PlayerStateAccess {
   private final UUID playerUuid;
   private volatile boolean hasHandshake = false;
   private volatile int capabilities = 0;
   private final ConcurrentLinkedQueue<IncomingRequest> incomingRequests = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<Integer> incomingCancels = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<long[]> pendingDirtyClear = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<Q> readyPayloads = new ConcurrentLinkedQueue<>();
   private final Long2ObjectOpenHashMap<PendingRequest> pendingByPosition = new Long2ObjectOpenHashMap();
   private final Int2ObjectOpenHashMap<PendingRequest> pendingByRequestId = new Int2ObjectOpenHashMap();
   private final PriorityQueue<Q> sendQueue = new PriorityQueue<>();
   private final LongOpenHashSet diskReadDone = new LongOpenHashSet();
   private final PlayerBandwidthTracker bandwidth = new PlayerBandwidthTracker();
   private final RateLimiterSet rateLimiters;
   private final ArrayDeque<AbstractPlayerRequestState.QueuedRequest> waitingQueue = new ArrayDeque<>();
   private final AtomicLong totalRequestsReceived = new AtomicLong();
   private volatile long desiredBandwidth = Long.MAX_VALUE;
   private volatile int sendQueueSizeSnapshot = 0;
   private volatile int pendingSyncCount = 0;
   private volatile int pendingGenerationCount = 0;

   protected AbstractPlayerRequestState(UUID playerUuid, int syncRate, int syncConcurrency, int genRate, int genConcurrency) {
      this.playerUuid = playerUuid;
      this.rateLimiters = new RateLimiterSet(syncRate, syncConcurrency, genRate, genConcurrency);
   }

   public void setCapabilities(int capabilities) {
      this.capabilities = capabilities;
   }

   public int getCapabilities() {
      return this.capabilities;
   }

   @Override
   public boolean supportsVoxelColumns() {
      return (this.capabilities & 1) != 0;
   }

   public void markHandshakeComplete() {
      this.hasHandshake = true;
   }

   public boolean hasCompletedHandshake() {
      return this.hasHandshake;
   }

   protected void enqueueIncomingRequest(IncomingRequest request) {
      this.incomingRequests.add(request);
      this.totalRequestsReceived.incrementAndGet();
   }

   public void addCancel(int requestId) {
      this.incomingCancels.add(requestId);
   }

   public void drainReadyPayloads() {
      Q qp;
      while ((qp = this.readyPayloads.poll()) != null) {
         this.sendQueue.add(qp);
      }

      this.sendQueueSizeSnapshot = this.sendQueue.size();
   }

   public boolean canSend(long allocationBytes) {
      return this.bandwidth.canSend(allocationBytes);
   }

   public void recordSend(int bytes) {
      this.bandwidth.recordSend(bytes);
   }

   @Override
   public void drainDirtyClearRequests() {
      long[] dirtyPositions;
      while ((dirtyPositions = this.pendingDirtyClear.poll()) != null) {
         for (long pos : dirtyPositions) {
            this.getDiskReadDonePositions().remove(pos);
         }
      }
   }

   public void clearDiskReadDoneForPositions(long[] positions) {
      this.pendingDirtyClear.add(positions);
   }

   protected void onDimensionChangeBase() {
      this.incomingRequests.clear();
      this.incomingCancels.clear();
      this.readyPayloads.clear();
      this.sendQueue.clear();
      this.pendingDirtyClear.clear();
   }

   @Override
   public void clearProcessingState() {
      this.pendingByPosition.clear();
      this.pendingByRequestId.clear();
      this.diskReadDone.clear();
      this.waitingQueue.clear();
      this.pendingSyncCount = 0;
      this.pendingGenerationCount = 0;
   }

   @Override
   public IncomingRequest pollIncomingRequest() {
      return this.incomingRequests.poll();
   }

   @Override
   public void addPendingRequest(PendingRequest pending) {
      long packed = PositionUtil.packPosition(pending.cx(), pending.cz());
      PendingRequest replaced = (PendingRequest)this.pendingByPosition.put(packed, pending);
      if (replaced != null) {
         this.pendingByRequestId.remove(replaced.requestId());
         this.decrementPendingCounter(replaced.type());
      }

      this.pendingByRequestId.put(pending.requestId(), pending);
      this.incrementPendingCounter(pending.type());
   }

   @Override
   public PendingRequest removePendingByPosition(int cx, int cz) {
      long packed = PositionUtil.packPosition(cx, cz);
      PendingRequest pending = (PendingRequest)this.pendingByPosition.remove(packed);
      if (pending != null) {
         this.pendingByRequestId.remove(pending.requestId());
         this.decrementPendingCounter(pending.type());
      }

      return pending;
   }

   @Override
   public PendingRequest removePendingByRequestId(int requestId) {
      PendingRequest pending = (PendingRequest)this.pendingByRequestId.remove(requestId);
      if (pending != null) {
         long packed = PositionUtil.packPosition(pending.cx(), pending.cz());
         this.pendingByPosition.remove(packed);
         this.decrementPendingCounter(pending.type());
      }

      return pending;
   }

   @Override
   public boolean hasPendingRequest(int cx, int cz) {
      return this.pendingByPosition.containsKey(PositionUtil.packPosition(cx, cz));
   }

   private void incrementPendingCounter(RequestType type) {
      if (type == RequestType.SYNC) {
         this.pendingSyncCount++;
      } else {
         this.pendingGenerationCount++;
      }
   }

   private void decrementPendingCounter(RequestType type) {
      if (type == RequestType.SYNC) {
         this.pendingSyncCount--;
      } else {
         this.pendingGenerationCount--;
      }
   }

   @Override
   public Integer pollCancel() {
      return this.incomingCancels.poll();
   }

   @Override
   public boolean hasDiskReadDone(int cx, int cz) {
      return this.diskReadDone.contains(PositionUtil.packPosition(cx, cz));
   }

   @Override
   public void markDiskReadDone(int cx, int cz) {
      this.diskReadDone.add(PositionUtil.packPosition(cx, cz));
   }

   public Iterable<IncomingRequest> getIncomingRequests() {
      return this.incomingRequests;
   }

   public void addReadyPayload(Q payload) {
      this.readyPayloads.add(payload);
   }

   protected LongOpenHashSet getDiskReadDonePositions() {
      return this.diskReadDone;
   }

   @Override
   public RateLimiterSet getRateLimiters() {
      return this.rateLimiters;
   }

   @Override
   public ArrayDeque<AbstractPlayerRequestState.QueuedRequest> getWaitingQueue() {
      return this.waitingQueue;
   }

   @Override
   public int getWaitingQueueSize() {
      return this.waitingQueue.size();
   }

   @Override
   public UUID getPlayerUUID() {
      return this.playerUuid;
   }

   public PriorityQueue<Q> getSendQueue() {
      return this.sendQueue;
   }

   @Override
   public int getSendQueueSize() {
      return this.sendQueueSizeSnapshot;
   }

   @Override
   public int getPendingSyncCount() {
      return this.pendingSyncCount;
   }

   @Override
   public int getPendingGenerationCount() {
      return this.pendingGenerationCount;
   }

   public long getTotalSectionsSent() {
      return this.bandwidth.getTotalSectionsSent();
   }

   public long getTotalBytesSent() {
      return this.bandwidth.getTotalBytesSent();
   }

   public long getTotalRequestsReceived() {
      return this.totalRequestsReceived.get();
   }

   public void setDesiredBandwidth(long desiredRate) {
      this.desiredBandwidth = desiredRate;
   }

   public long getDesiredBandwidth() {
      return this.desiredBandwidth;
   }

   public record QueuedRequest(IncomingRequest request, RequestType type, String dimension) {
   }
}
