package dev.micr.vss.networking.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.micr.vss.common.DiagnosticsFormatter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.class_124;
import net.minecraft.class_2561;

public class VSSClientCommands {
   public static void init() {
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("vss")
                     .then(
                        ClientCommandManager.literal("clearcache")
                           .executes(
                              context -> {
                                 LodRequestManager manager = VSSClientNetworking.getRequestManager();
                                 if (manager != null) {
                                    manager.flushCache();
                                    ((FabricClientCommandSource)context.getSource())
                                       .sendFeedback(class_2561.method_43470("VSS column cache cleared for current server. Chunks will be re-requested."));
                                 } else {
                                    ColumnCacheStore.clearAll();
                                    ((FabricClientCommandSource)context.getSource())
                                       .sendFeedback(class_2561.method_43470("VSS column cache cleared for all servers."));
                                 }

                                 return 1;
                              }
                           )
                     ))
                  .then(ClientCommandManager.literal("diag").executes(context -> {
                     showDiagnostics((FabricClientCommandSource)context.getSource());
                     return 1;
                  }))
            )
         );
   }

   private static void showDiagnostics(FabricClientCommandSource source) {
      LodRequestManager manager = VSSClientNetworking.getRequestManager();
      if (manager != null && VSSClientNetworking.isServerEnabled()) {
         source.sendFeedback(class_2561.method_43470("=== VSS Client Diagnostics ===").method_27692(class_124.field_1065));
         int serverDist = VSSClientNetworking.getServerLodDistance();
         int effectiveDist = manager.getEffectiveLodDistanceChunks();
         source.sendFeedback(
            class_2561.method_43470(String.format("Connection: server_lod_dist=%d, effective_dist=%d", serverDist, effectiveDist))
               .method_27692(class_124.field_1080)
         );
         long received = VSSClientNetworking.getColumnsReceived();
         long bytes = VSSClientNetworking.getBytesReceived();
         long dropped = VSSClientNetworking.getColumnsDropped();
         long startMs = VSSClientNetworking.getConnectionStartMs();
         long uptimeSec = startMs > 0L ? (System.currentTimeMillis() - startMs) / 1000L : 0L;
         source.sendFeedback(
            class_2561.method_43470(
                  String.format(
                     "Throughput: received=%d (%s), dropped=%d, recv_rate=%s/s, req_rate=%s/s, uptime=%s",
                     received,
                     DiagnosticsFormatter.formatBytes(bytes),
                     dropped,
                     DiagnosticsFormatter.formatRate(manager.getReceiveRate()),
                     DiagnosticsFormatter.formatRate(manager.getRequestRate()),
                     DiagnosticsFormatter.formatUptime(uptimeSec)
                  )
               )
               .method_27692(class_124.field_1080)
         );
         int queued = VSSClientNetworking.getQueuedColumnCount();
         source.sendFeedback(class_2561.method_43470(String.format("Queue: queued=%d/%d", queued, 8000)).method_27692(class_124.field_1080));
         int receivedCols = manager.getReceivedColumnCount();
         int empty = manager.getEmptyColumnCount();
         int dirty = manager.getDirtyColumnCount();
         source.sendFeedback(
            class_2561.method_43470(String.format("Columns: received=%d, empty=%d, dirty=%d", receivedCols, empty, dirty)).method_27692(class_124.field_1080)
         );
         source.sendFeedback(
            class_2561.method_43470(
                  String.format(
                     "Responses: columns=%d, up_to_date=%d, not_generated=%d, rate_limited=%d",
                     manager.getTotalColumnsReceived(),
                     manager.getTotalUpToDate(),
                     manager.getTotalNotGenerated(),
                     manager.getTotalRateLimited()
                  )
               )
               .method_27692(class_124.field_1080)
         );
         source.sendFeedback(
            class_2561.method_43470(
                  String.format("Requests: send_cycles=%d, total_requested=%d", manager.getTotalSendCycles(), manager.getTotalPositionsRequested())
               )
               .method_27692(class_124.field_1080)
         );
         int confirmedRing = manager.getConfirmedRing();
         int scanRing = manager.getScanRing();
         int maxRing = manager.getEffectiveLodDistanceChunks();
         source.sendFeedback(
            class_2561.method_43470(
                  String.format("Scan: confirmed=%d, scanning=%d/%d, missing_vanilla=%d", confirmedRing, scanRing, maxRing, manager.getMissingVanillaChunks())
               )
               .method_27692(class_124.field_1080)
         );
         int budget = manager.getLastBudget();
         int syncQueued = manager.getLastSyncQueued();
         int genQueued = manager.getLastGenQueued();
         source.sendFeedback(
            class_2561.method_43470(
                  String.format(
                     "Budget: used=%d/%d (sync=%d, gen=%d), queue=%d", syncQueued + genQueued, budget, syncQueued, genQueued, manager.getQueueRemaining()
                  )
               )
               .method_27692(class_124.field_1080)
         );
      } else {
         source.sendFeedback(class_2561.method_43470("VSS is not active on this server").method_27692(class_124.field_1061));
      }
   }
}
