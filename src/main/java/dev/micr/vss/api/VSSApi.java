package dev.micr.vss.api;

import dev.micr.vss.common.VSSLogger;
import dev.micr.vss.networking.client.VSSClientNetworking;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_1937;
import net.minecraft.class_5321;
import net.minecraft.class_638;

public final class VSSApi {
   private static final List<VoxelColumnConsumer> columnConsumers = new CopyOnWriteArrayList<>();

   private VSSApi() {
   }

   public static void registerColumnConsumer(VoxelColumnConsumer consumer) {
      columnConsumers.add(consumer);
      VSSLogger.info("Registered voxel column consumer: " + consumer.getClass().getName());
   }

   public static void removeColumnConsumer(VoxelColumnConsumer consumer) {
      columnConsumers.remove(consumer);
   }

   public static boolean hasVoxelConsumers() {
      return !columnConsumers.isEmpty();
   }

   public static boolean isServerEnabled() {
      return VSSClientNetworking.isServerEnabled();
   }

   public static int getServerLodDistance() {
      return VSSClientNetworking.getServerLodDistance();
   }

   public static void dispatchColumn(class_638 level, class_5321<class_1937> dimension, int chunkX, int chunkZ, VoxelColumnData columnData) {
      for (VoxelColumnConsumer consumer : columnConsumers) {
         try {
            consumer.onVoxelColumnReceived(level, dimension, chunkX, chunkZ, columnData);
         } catch (Exception e) {
            VSSLogger.error("Voxel column consumer threw exception", e);
         }
      }
   }
}
