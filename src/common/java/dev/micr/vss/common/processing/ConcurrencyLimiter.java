package dev.micr.vss.common.processing;

public class ConcurrencyLimiter {
   private final int maxConcurrency;
   private int currentConcurrency;

   public ConcurrencyLimiter(int maxConcurrency) {
      this.maxConcurrency = maxConcurrency;
   }

   public boolean tryAcquire() {
      if (this.currentConcurrency >= this.maxConcurrency) {
         return false;
      }

      this.currentConcurrency++;
      return true;
   }

   public void release() {
      if (this.currentConcurrency > 0) {
         this.currentConcurrency--;
      }
   }

   public int getCurrentConcurrency() {
      return this.currentConcurrency;
   }

   public int getMaxConcurrency() {
      return this.maxConcurrency;
   }
}
