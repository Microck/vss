package dev.micr.vss.common.processing;

public class ProcessingDiagnostics {
   private volatile int procTickDiskQueued;
   private volatile int procTickDiskDrained;
   private volatile int procTickGenDrained;
   private volatile int procTickInMemory;
   private volatile int procTickSkippedDuplicate;
   private volatile int procTickUpToDate;
   private volatile int procTickSyncRateLimited;
   private volatile int procTickGenRateLimited;
   private volatile int procTickQueueFull;
   private volatile int procTickQueued;
   private volatile long totalQueueFull;
   private volatile long totalQueued;
   private volatile long totalInMemory;
   private volatile long totalUpToDate;
   private volatile long totalGenDrained;
   private volatile long totalSyncRateLimited;
   private volatile long totalGenRateLimited;

   public void resetTickCounters() {
      this.procTickDiskQueued = 0;
      this.procTickDiskDrained = 0;
      this.procTickGenDrained = 0;
      this.procTickInMemory = 0;
      this.procTickSkippedDuplicate = 0;
      this.procTickUpToDate = 0;
      this.procTickSyncRateLimited = 0;
      this.procTickGenRateLimited = 0;
      this.procTickQueueFull = 0;
      this.procTickQueued = 0;
   }

   public void incrementDiskQueued() {
      this.procTickDiskQueued++;
   }

   public void incrementDiskDrained() {
      this.procTickDiskDrained++;
   }

   public void incrementSkippedDuplicate() {
      this.procTickSkippedDuplicate++;
   }

   public void incrementGenDrained() {
      this.procTickGenDrained++;
      this.totalGenDrained++;
   }

   public void incrementInMemory() {
      this.procTickInMemory++;
      this.totalInMemory++;
   }

   public void incrementUpToDate() {
      this.procTickUpToDate++;
      this.totalUpToDate++;
   }

   public void incrementSyncRateLimited() {
      this.procTickSyncRateLimited++;
      this.totalSyncRateLimited++;
   }

   public void incrementGenRateLimited() {
      this.procTickGenRateLimited++;
      this.totalGenRateLimited++;
   }

   public void incrementQueueFull() {
      this.procTickQueueFull++;
      this.totalQueueFull++;
   }

   public void incrementQueued() {
      this.procTickQueued++;
      this.totalQueued++;
   }

   public void incrementRateLimited(RequestType type) {
      if (type == RequestType.SYNC) {
         this.incrementSyncRateLimited();
      } else {
         this.incrementGenRateLimited();
      }
   }

   public int getLastDiskQueued() {
      return this.procTickDiskQueued;
   }

   public int getLastDiskDrained() {
      return this.procTickDiskDrained;
   }

   public int getLastGenDrained() {
      return this.procTickGenDrained;
   }

   public int getLastInMemory() {
      return this.procTickInMemory;
   }

   public int getLastSkippedDuplicate() {
      return this.procTickSkippedDuplicate;
   }

   public int getLastUpToDate() {
      return this.procTickUpToDate;
   }

   public long getTotalInMemory() {
      return this.totalInMemory;
   }

   public long getTotalUpToDate() {
      return this.totalUpToDate;
   }

   public long getTotalGenDrained() {
      return this.totalGenDrained;
   }

   public long getTotalSyncRateLimited() {
      return this.totalSyncRateLimited;
   }

   public long getTotalGenRateLimited() {
      return this.totalGenRateLimited;
   }

   public long getTotalQueueFull() {
      return this.totalQueueFull;
   }

   public long getTotalQueued() {
      return this.totalQueued;
   }
}
