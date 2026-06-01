package dev.micr.vss.networking.server;

import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.config.VSSServerConfig;
import dev.micr.vss.networking.client.VSSClientNetworking;
import dev.micr.vss.networking.payloads.BandwidthUpdateC2SPayload;
import dev.micr.vss.networking.payloads.BatchChunkRequestC2SPayload;
import dev.micr.vss.networking.payloads.CancelRequestC2SPayload;
import dev.micr.vss.networking.payloads.HandshakeC2SPayload;
import dev.micr.vss.networking.payloads.SessionConfigS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

public class VSSServerNetworking {
   private static volatile RequestProcessingService requestService;

   public static RequestProcessingService getRequestService() {
      return requestService;
   }

   public static synchronized void startServiceForLan(MinecraftServer server) {
      if (requestService == null) {
         VSSLogger.info("Starting VSS LOD request processing service (LAN server)");
         requestService = new RequestProcessingService(server);
         VSSClientNetworking.triggerHostHandshake();
      }
   }

   public static void init() {
      ServerPlayNetworking.registerGlobalReceiver(
         HandshakeC2SPayload.TYPE,
         (payload, context) -> {
            class_3222 player = context.player();
            VSSLogger.info(
               "VSS handshake received from "
                  + player.method_5477().getString()
                  + " (protocol v"
                  + payload.protocolVersion()
                  + ", capabilities="
                  + payload.capabilities()
                  + ")"
            );
            VSSServerConfig config = VSSServerConfig.CONFIG;
            RequestProcessingService service = requestService;
            boolean effectiveEnabled = config.enabled && service != null;
            int serverCaps = 1;
            ServerPlayNetworking.send(
               player,
               new SessionConfigS2CPayload(
                  15,
                  effectiveEnabled,
                  config.lodDistanceChunks,
                  serverCaps,
                  config.syncOnLoadRateLimitPerPlayer,
                  config.syncOnLoadConcurrencyLimitPerPlayer,
                  config.generationRateLimitPerPlayer,
                  config.generationConcurrencyLimitPerPlayer,
                  config.enableChunkGeneration,
                  config.bytesPerSecondLimitPerPlayer
               )
            );
            if (payload.protocolVersion() != 15) {
               VSSLogger.warn(
                  "Player "
                     + player.method_5477().getString()
                     + " has incompatible VSS protocol version "
                     + payload.protocolVersion()
                     + " (server: 15), skipping LOD distribution"
               );
            } else {
               if (effectiveEnabled) {
                  service.registerPlayer(player, payload.capabilities());
                  VSSLogger.info(
                     "Player "
                        + player.method_5477().getString()
                        + " registered for VSS LOD request processing"
                        + (payload.capabilities() != 0 ? " (caps=" + payload.capabilities() + ")" : "")
                  );
               }
            }
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(BatchChunkRequestC2SPayload.TYPE, (payload, context) -> {
         RequestProcessingService service = requestService;
         if (service != null) {
            service.handleBatchRequest(context.player(), payload);
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(CancelRequestC2SPayload.TYPE, (payload, context) -> {
         RequestProcessingService service = requestService;
         if (service != null) {
            service.handleCancel(context.player(), payload);
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(BandwidthUpdateC2SPayload.TYPE, (payload, context) -> {
         RequestProcessingService service = requestService;
         if (service != null) {
            service.handleBandwidthUpdate(context.player(), payload);
         }
      });
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         if (!server.method_3816() && !Boolean.getBoolean("vss.test.integratedServer")) {
            VSSLogger.info("VSS LOD request processing deferred until LAN");
         } else {
            VSSLogger.info("Starting VSS LOD request processing service");
            requestService = new RequestProcessingService(server);
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         RequestProcessingService service = requestService;
         if (service != null) {
            VSSLogger.info("Stopping VSS LOD request processing service");
            service.shutdown();
            requestService = null;
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         RequestProcessingService service = requestService;
         if (service != null) {
            service.tick();
         }
      });
      VSSServerCommands.init();
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         RequestProcessingService service = requestService;
         if (service != null) {
            service.removePlayer(handler.method_32311().method_5667());
         }
      });
   }
}
