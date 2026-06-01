package dev.micr.vss.common.processing;

import dev.micr.vss.common.VSSLogger;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractChunkDiskReader<R extends ReadResultAccess> {
   private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
   private static final int QUEUE_CAPACITY_PER_THREAD = 32;
   protected final ExecutorService executor;
   private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<R>> playerResults = new ConcurrentHashMap<>();
   private final AtomicBoolean isShutdown = new AtomicBoolean(false);
   protected final DiskReaderDiagnostics diag = new DiskReaderDiagnostics();

   protected AbstractChunkDiskReader(int threadCount) {
      int queueCapacity = threadCount * 32;
      this.executor = new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), r -> {
         Thread thread = new Thread(r, "VSS Disk Reader #" + THREAD_COUNTER.incrementAndGet());
         thread.setDaemon(true);
         thread.setPriority(1);
         return thread;
      });
   }

   protected boolean isShutdown() {
      return this.isShutdown.get();
   }

   public void registerPlayer(UUID playerUuid) {
      this.playerResults.computeIfAbsent(playerUuid, k -> new ConcurrentLinkedQueue<>());
   }

   protected void addResult(UUID playerUuid, R result) {
      ConcurrentLinkedQueue<R> queue = this.playerResults.get(playerUuid);
      if (queue != null) {
         queue.add(result);
      }
   }

   public ConcurrentLinkedQueue<R> getPlayerQueue(UUID playerUuid) {
      return this.playerResults.get(playerUuid);
   }

   public void removePlayerResults(UUID playerUuid) {
      this.playerResults.remove(playerUuid);
   }

   public String getDiagnostics() {
      int pending = 0;

      for (ConcurrentLinkedQueue<R> queue : this.playerResults.values()) {
         pending += queue.size();
      }

      return this.diag.formatDiagnostics(pending);
   }

   public DiskReaderDiagnostics getDiag() {
      return this.diag;
   }

   public void shutdown() {
      this.isShutdown.set(true);
      this.executor.shutdownNow();

      try {
         if (!this.executor.awaitTermination(5L, TimeUnit.SECONDS)) {
            VSSLogger.warn("Disk reader threads did not terminate within 5 seconds");
         }
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }

      this.playerResults.clear();
   }
}
