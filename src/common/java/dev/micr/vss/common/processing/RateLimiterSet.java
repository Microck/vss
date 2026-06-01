package dev.micr.vss.common.processing;

public class RateLimiterSet {
   private final ConcurrencyLimiter syncOnLoadLimiter;
   private final ConcurrencyLimiter generationLimiter;
   private final int syncRateLimit;
   private final int genRateLimit;

   public RateLimiterSet(int syncRateLimit, int syncConcurrency, int genRateLimit, int genConcurrency) {
      this.syncOnLoadLimiter = new ConcurrencyLimiter(syncConcurrency);
      this.generationLimiter = new ConcurrencyLimiter(genConcurrency);
      this.syncRateLimit = syncRateLimit;
      this.genRateLimit = genRateLimit;
   }

   public ConcurrencyLimiter syncOnLoad() {
      return this.syncOnLoadLimiter;
   }

   public ConcurrencyLimiter generation() {
      return this.generationLimiter;
   }

   public ConcurrencyLimiter forRequest(RequestType type, boolean diskReadingAvailable) {
      return type != RequestType.SYNC && !diskReadingAvailable ? this.generationLimiter : this.syncOnLoadLimiter;
   }

   public int syncRateLimit() {
      return this.syncRateLimit;
   }

   public int genRateLimit() {
      return this.genRateLimit;
   }
}
