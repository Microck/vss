package dev.micr.vss.common.processing;

public class PlayerBandwidthTracker {
   private static final int BURST_DIVISOR = 4;
   private long availableTokens = 0L;
   private long lastRefillNanos = System.nanoTime();
   private long totalSectionsSent = 0L;
   private long totalBytesSent = 0L;

   public boolean canSend(long allocationBytes) {
      if (allocationBytes <= 0L) {
         return false;
      }

      long now = System.nanoTime();
      long elapsedNanos = now - this.lastRefillNanos;
      if (elapsedNanos >= 1000000L) {
         this.lastRefillNanos = now;
         elapsedNanos = Math.min(elapsedNanos, 1000000000L);
         long refill = elapsedNanos * allocationBytes / 1000000000L;
         long burstCap = allocationBytes / 4L;
         this.availableTokens = Math.min(this.availableTokens + refill, burstCap);
      }

      return this.availableTokens > 0L;
   }

   public void recordSend(int bytes) {
      this.availableTokens = Math.max(0L, this.availableTokens - bytes);
      this.totalSectionsSent++;
      this.totalBytesSent += bytes;
   }

   public long getTotalSectionsSent() {
      return this.totalSectionsSent;
   }

   public long getTotalBytesSent() {
      return this.totalBytesSent;
   }
}
