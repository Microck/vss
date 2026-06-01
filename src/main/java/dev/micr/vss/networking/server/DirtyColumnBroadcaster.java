package dev.micr.vss.networking.server;

import dev.micr.vss.common.PositionUtil;
import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.common.tracking.DirtyColumnTracker;
import dev.micr.vss.config.VSSServerConfig;
import dev.micr.vss.networking.payloads.DirtyColumnsS2CPayload;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

class DirtyColumnBroadcaster {
   private final MinecraftServer server;
   private final Map<UUID, PlayerRequestState> players;
   private final FabricOffThreadProcessor offThreadProcessor;
   private final DirtyColumnTracker dirtyTracker;
   private int counter = 0;
   private long[] positionFilterBuffer = null;

   DirtyColumnBroadcaster(
      MinecraftServer server, Map<UUID, PlayerRequestState> players, FabricOffThreadProcessor offThreadProcessor, DirtyColumnTracker dirtyTracker
   ) {
      this.server = server;
      this.players = players;
      this.offThreadProcessor = offThreadProcessor;
      this.dirtyTracker = dirtyTracker;
   }

   void tick(VSSServerConfig config) {
      int intervalTicks = config.dirtyBroadcastIntervalSeconds * 20;
      if (++this.counter >= intervalTicks) {
         this.counter = 0;
         Set<UUID> failedPlayers = null;

         for (class_3218 level : this.server.method_3738()) {
            String dimensionStr = level.method_27983().method_29177().toString();
            long[] dirty = this.dirtyTracker.drainDirty(dimensionStr);
            if (dirty != null && dirty.length != 0) {
               this.offThreadProcessor.invalidateTimestamps(dimensionStr, dirty);
               int bufLen = Math.min(dirty.length, 10240);
               if (this.positionFilterBuffer == null || this.positionFilterBuffer.length < bufLen) {
                  this.positionFilterBuffer = new long[bufLen];
               }

               for (PlayerRequestState state : this.players.values()) {
                  if (state.hasCompletedHandshake()) {
                     class_3222 player = state.getPlayer();
                     if ((failedPlayers == null || !failedPlayers.contains(player.method_5667()))
                        && state.getLastDimension().equals(level.method_27983())
                        && !player.method_31481()) {
                        int playerCx = player.method_31477() >> 4;
                        int playerCz = player.method_31479() >> 4;
                        int lodDist = config.lodDistanceChunks;
                        int count = 0;

                        for (long packed : dirty) {
                           if (!PositionUtil.isOutOfRange(packed, playerCx, playerCz, lodDist)) {
                              this.positionFilterBuffer[count++] = packed;
                              if (count >= 10240) {
                                 break;
                              }
                           }
                        }

                        if (count > 0) {
                           long[] result = new long[count];
                           System.arraycopy(this.positionFilterBuffer, 0, result, 0, count);
                           state.clearDiskReadDoneForPositions(result);

                           try {
                              ServerPlayNetworking.send(player, new DirtyColumnsS2CPayload(result));
                           } catch (Exception e) {
                              VSSLogger.error("Failed to send dirty columns to " + player.method_5477().getString(), e);
                              if (failedPlayers == null) {
                                 failedPlayers = new HashSet<>();
                              }

                              failedPlayers.add(player.method_5667());
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
