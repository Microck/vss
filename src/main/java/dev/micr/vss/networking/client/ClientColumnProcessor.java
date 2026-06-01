package dev.micr.vss.networking.client;

import dev.micr.vss.api.VSSApi;
import dev.micr.vss.api.VoxelColumnData;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.config.VSSClientConfig;
import dev.micr.vss.networking.payloads.VoxelColumnS2CPayload;
import io.netty.buffer.Unpooled;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.class_11897;
import net.minecraft.class_2540;
import net.minecraft.class_2804;
import net.minecraft.class_2826;
import net.minecraft.class_310;
import net.minecraft.class_638;

class ClientColumnProcessor {
   static final int MAX_QUEUED_COLUMNS = 8000;
   private static final long DROP_WARN_INTERVAL_MS = 5000L;
   private static final int MAX_SECTIONS_PER_COLUMN = 64;
   private final ConcurrentLinkedQueue<VoxelColumnS2CPayload> columnQueue = new ConcurrentLinkedQueue<>();
   private final AtomicInteger queueSize = new AtomicInteger();
   private final AtomicLong columnsDropped = new AtomicLong();
   private volatile long lastDropWarnMs = 0L;
   private volatile ExecutorService executor = createExecutor();
   private final AtomicBoolean processing = new AtomicBoolean();
   private volatile boolean shuttingDown;

   private static ExecutorService createExecutor() {
      return Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "VSS-ColumnProcessor");
         t.setDaemon(true);
         return t;
      });
   }

   void offer(VoxelColumnS2CPayload payload) {
      if (!this.shuttingDown) {
         if (this.queueSize.get() < 8000) {
            this.columnQueue.add(payload);
            this.queueSize.incrementAndGet();
         } else {
            long dropped = this.columnsDropped.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - this.lastDropWarnMs > 5000L) {
               this.lastDropWarnMs = now;
               VSSLogger.warn("Column processing queue full (8000), " + dropped + " columns dropped total");
            }
         }
      }
   }

   void scheduleProcessing(boolean serverEnabled) {
      if (!this.shuttingDown) {
         if (serverEnabled && VSSClientConfig.CONFIG.receiveServerLods && VSSApi.hasVoxelConsumers()) {
            class_310 mc = class_310.method_1551();
            class_638 level = mc.field_1687;
            if (level == null) {
               this.columnQueue.clear();
               this.queueSize.set(0);
            } else if (!this.columnQueue.isEmpty()) {
               if (VSSClientConfig.CONFIG.offThreadSectionProcessing) {
                  if (this.processing.compareAndSet(false, true)) {
                     class_638 capturedLevel = level;

                     try {
                        this.executor.execute(() -> {
                           try {
                              this.drainColumnQueue(capturedLevel);
                           } finally {
                              this.processing.set(false);
                           }
                        });
                     } catch (Exception e) {
                        this.processing.set(false);
                     }
                  }
               } else {
                  this.drainColumnQueue(level);
               }
            }
         } else {
            this.columnQueue.clear();
            this.queueSize.set(0);
         }
      }
   }

   private void drainColumnQueue(class_638 level) {
      class_11897 factory = class_11897.method_74159(level.method_30349());

      VoxelColumnS2CPayload payload;
      while (!Thread.currentThread().isInterrupted() && (payload = this.columnQueue.poll()) != null) {
         this.queueSize.decrementAndGet();
         if (level.method_27983().equals(payload.dimension())) {
            byte[] decompressed = payload.decompressedSections();
            if (decompressed != null && decompressed.length != 0) {
               try {
                  class_2540 buf = new class_2540(Unpooled.wrappedBuffer(decompressed));

                  try {
                     int sectionCount = Math.max(0, Math.min(buf.method_10816(), 64));
                     VoxelColumnData.SectionData[] sectionDatas = new VoxelColumnData.SectionData[sectionCount];

                     for (int i = 0; i < sectionCount; i++) {
                        int sectionY = buf.readByte();
                        class_2826 section = new class_2826(factory);
                        section.method_12258(buf);
                        class_2804 blockLight = null;
                        if (buf.readBoolean()) {
                           byte[] lightBytes = new byte[2048];
                           buf.method_52979(lightBytes);
                           blockLight = new class_2804(lightBytes);
                        }

                        class_2804 skyLight = null;
                        if (buf.readBoolean()) {
                           byte[] lightBytes = new byte[2048];
                           buf.method_52979(lightBytes);
                           skyLight = new class_2804(lightBytes);
                        }

                        sectionDatas[i] = new VoxelColumnData.SectionData(sectionY, section, blockLight, skyLight);
                     }

                     VoxelColumnData columnData = new VoxelColumnData(sectionDatas, payload.columnTimestamp());
                     VSSApi.dispatchColumn(level, payload.dimension(), payload.chunkX(), payload.chunkZ(), columnData);
                  } finally {
                     buf.release();
                  }
               } catch (Exception e) {
                  VSSLogger.error("Failed to process voxel column at " + payload.chunkX() + "," + payload.chunkZ(), e);
               }
            }
         }
      }
   }

   void shutdown() {
      this.shuttingDown = true;
      ExecutorService old = this.executor;
      old.shutdownNow();
      this.columnQueue.clear();
      this.queueSize.set(0);

      try {
         old.awaitTermination(2L, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }

      this.processing.set(false);
      this.executor = createExecutor();
      this.shuttingDown = false;
   }

   int getQueuedCount() {
      return this.queueSize.get();
   }

   long getColumnsDropped() {
      return this.columnsDropped.get();
   }

   void resetStats() {
      this.columnsDropped.set(0L);
      this.lastDropWarnMs = 0L;
   }
}
