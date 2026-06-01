package dev.micr.vss.networking.client;

import dev.micr.vss.common.PositionUtil;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.function.IntConsumer;

class InFlightTracker {
   private final Long2LongOpenHashMap pendingRequests = new Long2LongOpenHashMap();
   private int nextRequestId;
   private final Int2LongOpenHashMap requestIdToPosition;
   private final Long2IntOpenHashMap positionToRequestId;
   private final LongOpenHashSet generationPositions;

   InFlightTracker() {
      this.pendingRequests.defaultReturnValue(0L);
      this.nextRequestId = 0;
      this.requestIdToPosition = new Int2LongOpenHashMap();
      this.requestIdToPosition.defaultReturnValue(Long.MIN_VALUE);
      this.positionToRequestId = new Long2IntOpenHashMap();
      this.positionToRequestId.defaultReturnValue(-1);
      this.generationPositions = new LongOpenHashSet();
   }

   int send(long position) {
      int requestId = this.nextRequestId++;
      this.requestIdToPosition.put(requestId, position);
      this.positionToRequestId.put(position, requestId);
      return requestId;
   }

   void markPending(long position, long sendTimeNanos, boolean isGeneration) {
      this.pendingRequests.put(position, sendTimeNanos);
      if (isGeneration) {
         this.generationPositions.add(position);
      }
   }

   InFlightTracker.RemovedRequest removeByRequestId(int requestId) {
      long pos = this.removeAllByRequestId(requestId);
      return pos != Long.MIN_VALUE ? new InFlightTracker.RemovedRequest(pos) : null;
   }

   private long removeAllByRequestId(int requestId) {
      long pos = this.requestIdToPosition.remove(requestId);
      if (pos == Long.MIN_VALUE) {
         return Long.MIN_VALUE;
      }

      this.positionToRequestId.remove(pos);
      this.pendingRequests.remove(pos);
      this.generationPositions.remove(pos);
      return pos;
   }

   boolean isInFlight(long position) {
      return this.pendingRequests.containsKey(position);
   }

   int size() {
      return this.pendingRequests.size();
   }

   int generationCount() {
      return this.generationPositions.size();
   }

   private int removeFromSecondaryMaps(long pos) {
      int requestId = this.positionToRequestId.remove(pos);
      if (requestId != -1) {
         this.requestIdToPosition.remove(requestId);
      }

      return requestId;
   }

   void timeoutSweep(long thresholdNanos) {
      long now = System.nanoTime();
      ObjectIterator<Entry> iter = this.pendingRequests.long2LongEntrySet().iterator();

      while (iter.hasNext()) {
         Entry entry = (Entry)iter.next();
         if (now - entry.getLongValue() > thresholdNanos) {
            long pos = entry.getLongKey();
            this.removeFromSecondaryMaps(pos);
            this.generationPositions.remove(pos);
            iter.remove();
         }
      }
   }

   void pruneOutOfRange(int playerCx, int playerCz, int pruneDistance, IntConsumer cancelCallback) {
      ObjectIterator<Entry> iter = this.pendingRequests.long2LongEntrySet().iterator();

      while (iter.hasNext()) {
         Entry entry = (Entry)iter.next();
         long pos = entry.getLongKey();
         if (PositionUtil.isOutOfRange(pos, playerCx, playerCz, pruneDistance)) {
            int requestId = this.removeFromSecondaryMaps(pos);
            this.generationPositions.remove(pos);
            if (requestId != -1) {
               cancelCallback.accept(requestId);
            }

            iter.remove();
         }
      }
   }

   void forEachRequestId(IntConsumer callback) {
      ObjectIterator var2 = this.positionToRequestId.long2IntEntrySet().iterator();

      while (var2.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var2.next();
         callback.accept(entry.getIntValue());
      }
   }

   void clear() {
      this.nextRequestId = 0;
      this.pendingRequests.clear();
      this.requestIdToPosition.clear();
      this.positionToRequestId.clear();
      this.generationPositions.clear();
   }

   record RemovedRequest(long position) {
   }
}
