package dev.micr.vss.common.processing;

import dev.micr.vss.common.DiagnosticsFormatter;

public class TickDiagnostics {
   private int lastTickSectionsSent;
   private int lastTickDiskQueued;
   private int lastTickDiskDrained;
   private int lastTickGenDrained;
   private int lastTickInMemorySerialized;
   private int lastTickBytesFlushed;
   private int lastTickQueuePeak;
   private int lastTickSkippedDuplicate;
   private int lastTickUpToDate;
   private int curTickSectionsSent;
   private int curTickBytesFlushed;
   private int curTickQueuePeak;
   private static final int WINDOW_TICKS = 100;
   private final int[] byteRing = new int[100];
   private final long[] nanosRing = new long[100];
   private long windowByteSum;
   private int ringPos;
   private int ringCount;

   public void reset(ProcessingDiagnostics diag) {
      this.windowByteSum = this.windowByteSum - this.byteRing[this.ringPos];
      this.byteRing[this.ringPos] = this.curTickBytesFlushed;
      this.nanosRing[this.ringPos] = System.nanoTime();
      this.windowByteSum = this.windowByteSum + this.curTickBytesFlushed;
      this.ringPos = (this.ringPos + 1) % 100;
      if (this.ringCount < 100) {
         this.ringCount++;
      }

      this.lastTickSectionsSent = this.curTickSectionsSent;
      this.lastTickDiskQueued = diag.getLastDiskQueued();
      this.lastTickDiskDrained = diag.getLastDiskDrained();
      this.lastTickGenDrained = diag.getLastGenDrained();
      this.lastTickInMemorySerialized = diag.getLastInMemory();
      this.lastTickBytesFlushed = this.curTickBytesFlushed;
      this.lastTickQueuePeak = this.curTickQueuePeak;
      this.lastTickSkippedDuplicate = diag.getLastSkippedDuplicate();
      this.lastTickUpToDate = diag.getLastUpToDate();
      this.curTickSectionsSent = 0;
      this.curTickBytesFlushed = 0;
      this.curTickQueuePeak = 0;
   }

   public long getWindowBytesPerSecond() {
      if (this.ringCount < 2) {
         return 0L;
      }

      int newestIdx = (this.ringPos - 1 + 100) % 100;
      int oldestIdx = this.ringCount < 100 ? 0 : this.ringPos;
      long elapsedNanos = this.nanosRing[newestIdx] - this.nanosRing[oldestIdx];
      return elapsedNanos <= 0L ? 0L : this.windowByteSum * 1000000000L / elapsedNanos;
   }

   public void recordSectionSent(int estimatedBytes) {
      this.curTickSectionsSent++;
      this.curTickBytesFlushed += estimatedBytes;
   }

   public void updateQueuePeak(int queueSize) {
      this.curTickQueuePeak = Math.max(this.curTickQueuePeak, queueSize);
   }

   public String format(int maxSendQueueSize) {
      return String.format(
         "sent=%d, disk=%d/%d, utd=%d, gen=%d, in_mem=%d, skipped=%d, bytes=%s, qpeak=%d/%d",
         this.lastTickSectionsSent,
         this.lastTickDiskDrained,
         this.lastTickDiskQueued,
         this.lastTickUpToDate,
         this.lastTickGenDrained,
         this.lastTickInMemorySerialized,
         this.lastTickSkippedDuplicate,
         DiagnosticsFormatter.formatBytes(this.lastTickBytesFlushed),
         this.lastTickQueuePeak,
         maxSendQueueSize
      );
   }

   public String formatSummary(long bwRate, long maxBytesPerSecondGlobal) {
      return String.format(
         "sent=%d/tick, disk=%d/%d, utd=%d, bw=%s/%s",
         this.lastTickSectionsSent,
         this.lastTickDiskDrained,
         this.lastTickDiskQueued,
         this.lastTickUpToDate,
         DiagnosticsFormatter.formatBytes(bwRate),
         DiagnosticsFormatter.formatBytes(maxBytesPerSecondGlobal)
      );
   }
}
