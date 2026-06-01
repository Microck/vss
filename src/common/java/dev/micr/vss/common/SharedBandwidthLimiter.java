package dev.micr.vss.common;

public class SharedBandwidthLimiter {
   private final long maxBytesPerSecond;
   private long availableTokens;
   private long lastRefillNanos;
   private long totalBytesSent;

   public SharedBandwidthLimiter(long maxBytesPerSecond) {
      this.maxBytesPerSecond = maxBytesPerSecond;
      this.availableTokens = maxBytesPerSecond;
      this.lastRefillNanos = System.nanoTime();
   }

   private void refill() {
      long now = System.nanoTime();
      long elapsedNanos = now - this.lastRefillNanos;
      if (elapsedNanos >= 1000000L) {
         this.lastRefillNanos = now;
         elapsedNanos = Math.min(elapsedNanos, 1000000000L);
         long refill = elapsedNanos * this.maxBytesPerSecond / 1000000000L;
         this.availableTokens = Math.min(this.availableTokens + refill, this.maxBytesPerSecond);
      }
   }

   public long getPerPlayerAllocation(int activePlayerCount) {
      this.refill();
      return this.availableTokens > 0L && activePlayerCount > 0 ? this.availableTokens / activePlayerCount : 0L;
   }

   public void recordSend(int bytes) {
      this.availableTokens = Math.max(0L, this.availableTokens - bytes);
      this.totalBytesSent += bytes;
   }

   public long getTotalBytesSent() {
      return this.totalBytesSent;
   }
}
