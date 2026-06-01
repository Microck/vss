package dev.micr.vss.benchmark;

import dev.micr.vss.api.VSSApi;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.networking.client.VSSClientNetworking;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;

public final class BenchmarkHook {
   private static final boolean ENABLED = Boolean.getBoolean("vss.benchmark");
   private static final int DURATION_SECONDS = Integer.getInteger("vss.benchmark.duration", 60);
   private static volatile Map<String, Object> latestClientSnapshot;

   private BenchmarkHook() {
   }

   public static void initServer() {
      if (ENABLED) {
         VSSLogger.info("[Benchmark] Server hook active, duration=" + DURATION_SECONDS + "s");
         int targetTicks = DURATION_SECONDS * 20;
         int[] tickCount = new int[]{0};
         ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
            VSSLogger.info("[Benchmark] Server started, counting " + targetTicks + " ticks");
            tickCount[0] = 0;
         });
         ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            tickCount[0]++;
            if (tickCount[0] == targetTicks) {
               VSSLogger.info("[Benchmark] Duration reached (" + DURATION_SECONDS + "s), exporting metrics");
               Path outputFile = Path.of("benchmark-results", "server.json");
               BenchmarkMetricsExporter.exportServer(outputFile, DURATION_SECONDS);
               VSSLogger.info("[Benchmark] Halting server");
               server.method_3747(false);
            }
         });
      }
   }

   public static void initClient() {
      if (ENABLED) {
         VSSLogger.info("[Benchmark] Client hook active");
         VSSApi.registerColumnConsumer((level, dimension, chunkX, chunkZ, columnData) -> {});
         int[] clientTick = new int[]{0};
         ClientTickEvents.END_CLIENT_TICK.register((net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick)client -> {
            clientTick[0]++;
            if (clientTick[0] % 20 == 0 && VSSClientNetworking.isServerEnabled()) {
               latestClientSnapshot = BenchmarkMetricsExporter.buildClientMetrics();
            }
         });
         ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
            VSSLogger.info("[Benchmark] Client disconnected, exporting metrics");
            Path outputFile = Path.of("benchmark-results", "client.json");
            Map<String, Object> snapshot = latestClientSnapshot;
            if (snapshot != null) {
               BenchmarkMetricsExporter.writeClientSnapshot(outputFile, snapshot);
            } else {
               BenchmarkMetricsExporter.exportClient(outputFile);
            }

            VSSLogger.info("[Benchmark] Exiting client");
            Runtime.getRuntime().halt(0);
         });
      }
   }
}
