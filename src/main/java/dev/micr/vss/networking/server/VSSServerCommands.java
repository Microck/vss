package dev.micr.vss.networking.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.micr.vss.common.DiagnosticsFormatter;
import dev.micr.vss.common.SharedBandwidthLimiter;
import dev.micr.vss.common.processing.ProcessingDiagnostics;
import dev.micr.vss.config.VSSServerConfig;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.class_2168;
import net.minecraft.class_2170;
import net.minecraft.class_2561;
import net.minecraft.class_3222;

class VSSServerCommands {
   public static void init() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("vsslod")
                        .requires(source -> source.method_9259(2)))
                     .then(class_2170.method_9247("stats").executes(ctx -> showStats((class_2168)ctx.getSource()))))
                  .then(class_2170.method_9247("diag").executes(ctx -> showDiagnostics((class_2168)ctx.getSource())))
            )
         );
   }

   private static int showStats(class_2168 source) {
      RequestProcessingService service = VSSServerNetworking.getRequestService();
      if (service == null) {
         source.method_9213(class_2561.method_43470("VSS LOD request processing is not active"));
         return 0;
      }

      Map<UUID, PlayerRequestState> players = service.getPlayers();
      if (players.isEmpty()) {
         source.method_9226(() -> class_2561.method_43470("No players connected with VSS"), false);
         return 1;
      }

      source.method_9226(() -> class_2561.method_43470("=== VSS LOD Request Stats ==="), false);

      for (Entry<UUID, PlayerRequestState> entry : players.entrySet()) {
         PlayerRequestState state = entry.getValue();
         class_3222 player = state.getPlayer();
         String line = String.format(
            "%s: handshake=%s, sent=%d sections (%s), pending_sync=%d, pending_gen=%d, send_queue=%d, requests=%d",
            player.method_5477().getString(),
            state.hasCompletedHandshake() ? "yes" : "no",
            state.getTotalSectionsSent(),
            DiagnosticsFormatter.formatBytes(state.getTotalBytesSent()),
            state.getPendingSyncCount(),
            state.getPendingGenerationCount(),
            state.getSendQueueSize(),
            state.getTotalRequestsReceived()
         );
         source.method_9226(() -> class_2561.method_43470(line), false);
      }

      return 1;
   }

   private static int showDiagnostics(class_2168 source) {
      RequestProcessingService service = VSSServerNetworking.getRequestService();
      if (service == null) {
         source.method_9213(class_2561.method_43470("VSS LOD request processing is not active"));
         return 0;
      }

      VSSServerConfig config = VSSServerConfig.CONFIG;
      long uptimeSec = service.getUptimeSeconds();
      ProcessingDiagnostics diag = service.getOffThreadProcessor().getDiagnostics();
      ChunkDiskReader diskReader = service.getDiskReader();
      long diskCompleted = diskReader != null ? diskReader.getDiag().getSuccessfulReadCount() : 0L;
      ChunkGenerationService genService = service.getGenerationService();
      SharedBandwidthLimiter bwLimiter = service.getBandwidthLimiter();
      long totalSent = 0L;
      long totalBytes = 0L;
      ArrayList<DiagnosticsFormatter.PlayerDiag> players = new ArrayList<>();

      for (Entry<UUID, PlayerRequestState> entry : service.getPlayers().entrySet()) {
         PlayerRequestState state = entry.getValue();
         totalSent += state.getTotalSectionsSent();
         totalBytes += state.getTotalBytesSent();
         players.add(
            new DiagnosticsFormatter.PlayerDiag(
               state.getPlayer().method_5477().getString(),
               state.getSendQueueSize(),
               config.sendQueueLimitPerPlayer,
               state.getPendingSyncCount(),
               state.getPendingGenerationCount(),
               state.getTotalSectionsSent(),
               state.getTotalBytesSent()
            )
         );
      }

      DiagnosticsFormatter.DiagData data = new DiagnosticsFormatter.DiagData(
         config.enabled,
         config.lodDistanceChunks,
         config.bytesPerSecondLimitPerPlayer,
         config.bytesPerSecondLimitGlobal,
         uptimeSec,
         totalSent,
         totalBytes,
         diag.getTotalInMemory(),
         diag.getTotalUpToDate(),
         diag.getTotalGenDrained(),
         diskCompleted,
         service.getTickDiagnostics(),
         diskReader != null ? diskReader.getDiagnostics() : "N/A",
         genService != null ? genService.getDiagnostics() : null,
         genService != null,
         bwLimiter.getTotalBytesSent(),
         service.getWindowBandwidthRate(),
         players
      );

      for (String line : DiagnosticsFormatter.formatDiagnostics(data)) {
         source.method_9226(() -> class_2561.method_43470(line), false);
      }

      return 1;
   }
}
