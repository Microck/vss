package dev.micr.vss.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.processing.DiskReaderDiagnostics;
import dev.micr.vss.common.processing.ProcessingDiagnostics;
import dev.micr.vss.networking.client.LodRequestManager;
import dev.micr.vss.networking.client.VSSClientNetworking;
import dev.micr.vss.networking.server.ChunkDiskReader;
import dev.micr.vss.networking.server.ChunkGenerationService;
import dev.micr.vss.networking.server.PlayerRequestState;
import dev.micr.vss.networking.server.RequestProcessingService;
import dev.micr.vss.networking.server.VSSServerNetworking;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BenchmarkMetricsExporter {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final double BYTES_PER_MB = 1048576.0;

   private BenchmarkMetricsExporter() {
   }

   public static void exportServer(Path outputFile, long durationSeconds) {
      RequestProcessingService service = VSSServerNetworking.getRequestService();
      if (service == null) {
         VSSLogger.warn("[Benchmark] No RequestProcessingService available, skipping server export");
      } else {
         LinkedHashMap<String, Object> result = new LinkedHashMap<>();
         result.put("timestamp", Instant.now().toString());
         result.put("duration_seconds", durationSeconds);
         long totalSent = 0L;
         long totalBytes = 0L;

         for (PlayerRequestState state : service.getPlayers().values()) {
            totalSent += state.getTotalSectionsSent();
            totalBytes += state.getTotalBytesSent();
         }

         long uptime = service.getUptimeSeconds();
         LinkedHashMap<String, Object> throughput = new LinkedHashMap<>();
         throughput.put("total_sections_sent", totalSent);
         throughput.put("total_bytes_sent", totalBytes);
         throughput.put("sections_per_second", uptime > 0L ? (double)totalSent / uptime : 0.0);
         throughput.put("bytes_per_second", uptime > 0L ? (double)totalBytes / uptime : 0.0);
         result.put("throughput", throughput);
         ProcessingDiagnostics diag = service.getOffThreadProcessor().getDiagnostics();
         LinkedHashMap<String, Object> sources = new LinkedHashMap<>();
         sources.put("in_memory", diag.getTotalInMemory());
         sources.put("up_to_date", diag.getTotalUpToDate());
         sources.put("generation", diag.getTotalGenDrained());
         ChunkDiskReader diskReader = service.getDiskReader();
         sources.put("disk_read", diskReader != null ? diskReader.getDiag().getSuccessfulReadCount() : 0L);
         result.put("sources", sources);
         LinkedHashMap<String, Object> diskReaderMap = new LinkedHashMap<>();
         if (diskReader != null) {
            DiskReaderDiagnostics dd = diskReader.getDiag();
            diskReaderMap.put("submitted", dd.getSubmittedCount());
            diskReaderMap.put("completed", dd.getCompletedCount());
            diskReaderMap.put("empty", dd.getEmptyCount());
            diskReaderMap.put("errors", dd.getErrorCount());
            long completed = dd.getCompletedCount();
            double avgMs = completed > 0L ? (double)dd.getTotalReadTimeNanos() / completed / 1000000.0 : 0.0;
            diskReaderMap.put("avg_read_time_ms", avgMs);
            diskReaderMap.put("saturation_events", dd.getSaturationCount());
         }

         result.put("disk_reader", diskReaderMap);
         LinkedHashMap<String, Object> genMap = new LinkedHashMap<>();
         ChunkGenerationService genService = service.getGenerationService();
         if (genService != null) {
            genMap.put("submitted", genService.getTotalSubmitted());
            genMap.put("completed", genService.getTotalCompleted());
            genMap.put("timeouts", genService.getTotalTimeouts());
         }

         result.put("generation", genMap);
         LinkedHashMap<String, Object> rateLimiting = new LinkedHashMap<>();
         rateLimiting.put("sync_rate_limited", diag.getTotalSyncRateLimited());
         rateLimiting.put("gen_rate_limited", diag.getTotalGenRateLimited());
         rateLimiting.put("queue_full", diag.getTotalQueueFull());
         rateLimiting.put("queued", diag.getTotalQueued());
         result.put("rate_limiting", rateLimiting);
         LinkedHashMap<String, Object> bandwidth = new LinkedHashMap<>();
         bandwidth.put("total_bytes_sent", service.getBandwidthLimiter().getTotalBytesSent());
         result.put("bandwidth", bandwidth);
         LinkedHashMap<String, Object> jvm = new LinkedHashMap<>();
         MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
         MemoryUsage heap = memBean.getHeapMemoryUsage();
         jvm.put("heap_used_mb", heap.getUsed() / 1048576.0);
         jvm.put("heap_max_mb", heap.getMax() / 1048576.0);
         long gcCount = 0L;
         long gcTime = 0L;

         for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c >= 0L) {
               gcCount += c;
            }

            if (t >= 0L) {
               gcTime += t;
            }
         }

         jvm.put("gc_count", gcCount);
         jvm.put("gc_time_ms", gcTime);
         result.put("jvm", jvm);
         writeJson(outputFile, result);
         VSSLogger.info("[Benchmark] Server metrics written to " + outputFile);
      }
   }

   public static Map<String, Object> buildClientMetrics() {
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      result.put("timestamp", Instant.now().toString());
      result.put("columns_received", VSSClientNetworking.getColumnsReceived());
      result.put("bytes_received", VSSClientNetworking.getBytesReceived());
      LodRequestManager manager = VSSClientNetworking.getRequestManager();
      if (manager != null) {
         result.put("total_up_to_date", manager.getTotalUpToDate());
         result.put("total_not_generated", manager.getTotalNotGenerated());
         result.put("total_rate_limited", manager.getTotalRateLimited());
         result.put("send_cycles", manager.getTotalSendCycles());
         result.put("positions_requested", manager.getTotalPositionsRequested());
      }

      return result;
   }

   public static void exportClient(Path outputFile) {
      writeClientSnapshot(outputFile, buildClientMetrics());
   }

   public static void writeClientSnapshot(Path outputFile, Map<String, Object> snapshot) {
      writeJson(outputFile, snapshot);
      VSSLogger.info("[Benchmark] Client metrics written to " + outputFile);
   }

   private static void writeJson(Path outputFile, Map<String, Object> data) {
      try {
         Path parent = outputFile.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }

         Files.writeString(outputFile, GSON.toJson(data));
      } catch (IOException e) {
         VSSLogger.error("[Benchmark] Failed to write metrics to " + outputFile, e);
      }
   }
}
