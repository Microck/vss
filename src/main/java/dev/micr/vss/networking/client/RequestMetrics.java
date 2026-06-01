package dev.micr.vss.networking.client;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

class RequestMetrics {
   private static final double EWMA_SMOOTHING_FACTOR = 0.3;
   private int receivedCount = 0;
   private int emptyCount = 0;
   private long totalSendCycles = 0L;
   private long totalPositionsRequested = 0L;
   private long totalColumnsReceived = 0L;
   private long totalUpToDate = 0L;
   private long totalNotGenerated = 0L;
   private long totalRateLimited = 0L;
   private long lastRateUpdateMs = 0L;
   private int columnsReceivedInWindow = 0;
   private int positionsRequestedInWindow = 0;
   private double receiveRate = 0.0;
   private double requestRate = 0.0;

   void adjustCounters(long oldTimestamp, long newTimestamp) {
      if (oldTimestamp >= 0L) {
         if (oldTimestamp > 0L) {
            this.receivedCount--;
         } else {
            this.emptyCount--;
         }
      }

      if (newTimestamp > 0L) {
         this.receivedCount++;
      } else if (newTimestamp == 0L) {
         this.emptyCount++;
      }
   }

   void recordSendCycle(int positionCount) {
      this.totalSendCycles++;
      this.totalPositionsRequested += positionCount;
      this.positionsRequestedInWindow += positionCount;
   }

   void recordColumnReceived() {
      this.totalColumnsReceived++;
      this.columnsReceivedInWindow++;
   }

   void recordUpToDate() {
      this.totalUpToDate++;
   }

   void recordNotGenerated() {
      this.totalNotGenerated++;
   }

   void recordRateLimited() {
      this.totalRateLimited++;
   }

   void updateRollingRates() {
      long now = System.currentTimeMillis();
      long elapsed = now - this.lastRateUpdateMs;
      if (elapsed >= 1000L) {
         double seconds = elapsed / 1000.0;
         double instantReceiveRate = this.columnsReceivedInWindow / seconds;
         double instantRequestRate = this.positionsRequestedInWindow / seconds;
         this.receiveRate = this.receiveRate * 0.7 + instantReceiveRate * 0.3;
         this.requestRate = this.requestRate * 0.7 + instantRequestRate * 0.3;
         this.columnsReceivedInWindow = 0;
         this.positionsRequestedInWindow = 0;
         this.lastRateUpdateMs = now;
      }
   }

   void reset() {
      this.receivedCount = 0;
      this.emptyCount = 0;
      this.lastRateUpdateMs = 0L;
      this.columnsReceivedInWindow = 0;
      this.positionsRequestedInWindow = 0;
      this.receiveRate = 0.0;
      this.requestRate = 0.0;
   }

   void bulkRecount(Long2LongOpenHashMap timestamps) {
      this.receivedCount = 0;
      this.emptyCount = 0;
      LongIterator var2 = timestamps.values().iterator();

      while (var2.hasNext()) {
         long ts = (Long)var2.next();
         if (ts > 0L) {
            this.receivedCount++;
         } else if (ts == 0L) {
            this.emptyCount++;
         }
      }
   }

   void onTimestampRemoved(long timestamp) {
      if (timestamp > 0L) {
         this.receivedCount--;
      } else if (timestamp == 0L) {
         this.emptyCount--;
      }
   }

   int getReceivedCount() {
      return this.receivedCount;
   }

   int getEmptyCount() {
      return this.emptyCount;
   }

   long getTotalSendCycles() {
      return this.totalSendCycles;
   }

   long getTotalPositionsRequested() {
      return this.totalPositionsRequested;
   }

   long getTotalColumnsReceived() {
      return this.totalColumnsReceived;
   }

   long getTotalUpToDate() {
      return this.totalUpToDate;
   }

   long getTotalNotGenerated() {
      return this.totalNotGenerated;
   }

   long getTotalRateLimited() {
      return this.totalRateLimited;
   }

   double getReceiveRate() {
      return this.receiveRate;
   }

   double getRequestRate() {
      return this.requestRate;
   }
}
