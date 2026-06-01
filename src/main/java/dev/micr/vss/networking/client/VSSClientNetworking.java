package dev.micr.vss.networking.client;

import dev.micr.vss.api.VSSApi;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.config.VSSClientConfig;
import dev.micr.vss.networking.payloads.BatchResponseS2CPayload;
import dev.micr.vss.networking.payloads.DirtyColumnsS2CPayload;
import dev.micr.vss.networking.payloads.HandshakeC2SPayload;
import dev.micr.vss.networking.payloads.SessionConfigS2CPayload;
import dev.micr.vss.networking.payloads.VoxelColumnS2CPayload;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.class_1132;
import net.minecraft.class_310;
import net.minecraft.class_5218;
import net.minecraft.class_642;

public class VSSClientNetworking {
   private static volatile boolean serverEnabled = false;
   private static volatile int serverLodDistance = 0;
   private static final AtomicLong columnsReceived = new AtomicLong();
   private static final AtomicLong bytesReceived = new AtomicLong();
   private static volatile long connectionStartMs = 0L;
   private static volatile LodRequestManager requestManager;
   private static final ClientColumnProcessor columnProcessor = new ClientColumnProcessor();

   public static boolean isServerEnabled() {
      return serverEnabled;
   }

   public static int getServerLodDistance() {
      return serverLodDistance;
   }

   public static long getColumnsReceived() {
      return columnsReceived.get();
   }

   public static long getBytesReceived() {
      return bytesReceived.get();
   }

   public static long getColumnsDropped() {
      return columnProcessor.getColumnsDropped();
   }

   public static long getConnectionStartMs() {
      return connectionStartMs;
   }

   public static LodRequestManager getRequestManager() {
      return requestManager;
   }

   public static int getQueuedColumnCount() {
      return columnProcessor.getQueuedCount();
   }

   public static void triggerHostHandshake() {
      class_310.method_1551().execute(() -> {
         if (VSSClientConfig.CONFIG.receiveServerLods) {
            if (requestManager == null) {
               try {
                  int clientCaps = VSSApi.hasVoxelConsumers() ? 1 : 0;
                  ClientPlayNetworking.send(new HandshakeC2SPayload(15, clientCaps));
               } catch (Exception e) {
                  VSSLogger.debug("LAN host handshake send failed: " + e.getMessage());
               }
            }
         }
      });
   }

   public static void init() {
      registerPacketHandlers();
      registerConnectionLifecycle();
      registerTickHandler();
   }

   private static void registerPacketHandlers() {
      ClientPlayNetworking.registerGlobalReceiver(
         SessionConfigS2CPayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> {
                  VSSLogger.info(
                     "Server session config received (protocol v"
                        + payload.protocolVersion()
                        + ", LOD distance: "
                        + payload.lodDistanceChunks()
                        + " chunks, enabled: "
                        + payload.enabled()
                        + ", syncRate: "
                        + payload.syncOnLoadRateLimitPerPlayer()
                        + ")"
                  );
                  if (payload.protocolVersion() != 15) {
                     VSSLogger.warn("Server has incompatible VSS protocol version " + payload.protocolVersion() + " (client: 15), LOD distribution disabled");
                     serverEnabled = false;
                  } else {
                     serverEnabled = payload.enabled();
                     serverLodDistance = payload.lodDistanceChunks();
                     if (payload.enabled()) {
                        connectionStartMs = System.currentTimeMillis();
                        LodRequestManager manager = new LodRequestManager();
                        class_310 mc = class_310.method_1551();
                        class_642 serverData = mc.method_1558();
                        class_1132 spServer = mc.method_1576();
                        String serverAddr;
                        if (serverData != null && serverData.field_3761 != null) {
                           serverAddr = serverData.field_3761;
                        } else if (spServer != null) {
                           Path worldDir = spServer.method_27050(class_5218.field_24188).getFileName();
                           serverAddr = "local:" + (worldDir != null ? worldDir : "world");
                        } else {
                           serverAddr = "unknown";
                        }

                        manager.onSessionConfig(payload, serverAddr);
                        requestManager = manager;
                     }
                  }
               }
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(BatchResponseS2CPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         LodRequestManager manager = requestManager;
         if (manager != null) {
            for (int i = 0; i < payload.count(); i++) {
               int requestId = payload.requestIds()[i];
               byte type = payload.responseTypes()[i];
               switch (type) {
                  case 0:
                     manager.onRateLimited(requestId);
                     break;
                  case 1:
                     manager.onColumnUpToDate(requestId);
                     break;
                  case 2:
                     manager.onColumnNotGenerated(requestId);
                     break;
                  default:
                     VSSLogger.warn("Unknown batch response type: " + type);
               }
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(DirtyColumnsS2CPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         LodRequestManager manager = requestManager;
         if (manager != null) {
            manager.onDirtyColumns(payload.dirtyPositions());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(VoxelColumnS2CPayload.TYPE, (payload, context) -> {
         columnsReceived.incrementAndGet();
         bytesReceived.addAndGet(payload.estimatedBytes());
         context.client().execute(() -> {
            LodRequestManager manager = requestManager;
            if (manager != null) {
               manager.onColumnReceived(payload.requestId(), payload.columnTimestamp());
            }

            columnProcessor.offer(payload);
         });
      });
   }

   private static void registerConnectionLifecycle() {
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> {
         serverEnabled = false;
         serverLodDistance = 0;
         requestManager = null;
         if (VSSClientConfig.CONFIG.receiveServerLods) {
            if (!class_310.method_1551().method_1496() || Boolean.getBoolean("vss.test.integratedServer")) {
               try {
                  int clientCaps = VSSApi.hasVoxelConsumers() ? 1 : 0;
                  ClientPlayNetworking.send(new HandshakeC2SPayload(15, clientCaps));
               } catch (Exception e) {
                  VSSLogger.debug("Handshake send failed (server likely doesn't have VSS): " + e.getMessage());
               }
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         LodRequestManager manager = requestManager;
         if (manager != null) {
            manager.disconnect();
            manager.saveCache();
         }

         columnProcessor.shutdown();
         columnProcessor.resetStats();
         serverEnabled = false;
         serverLodDistance = 0;
         columnsReceived.set(0L);
         bytesReceived.set(0L);
         connectionStartMs = 0L;
         requestManager = null;
      });
   }

   private static void registerTickHandler() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         LodRequestManager manager = requestManager;
         if (manager != null && serverEnabled) {
            manager.tick();
         }

         columnProcessor.scheduleProcessing(serverEnabled);
      });
   }
}
