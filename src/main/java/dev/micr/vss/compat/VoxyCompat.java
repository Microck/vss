package dev.micr.vss.compat;

import dev.micr.vss.api.VSSApi;
import dev.micr.vss.api.VoxelColumnData;
import dev.micr.vss.common.VSSLogger;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.OptionalInt;
import net.minecraft.class_1937;
import net.minecraft.class_2804;
import net.minecraft.class_2826;
import net.minecraft.class_638;

class VoxyCompat {
   private static MethodHandle worldIdentifierOf;
   private static MethodHandle rawIngest;
   private static volatile MethodHandle getVoxyConfig;
   private static volatile MethodHandle getSectionRenderDist;

   static boolean init() {
      try {
         Lookup lookup = MethodHandles.lookup();
         Class<?> worldIdClass = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
         worldIdentifierOf = lookup.findStatic(worldIdClass, "of", MethodType.methodType(worldIdClass, class_1937.class))
            .asType(MethodType.methodType(Object.class, class_1937.class));
         Class<?> ingestClass = Class.forName("me.cortex.voxy.common.world.service.VoxelIngestService");
         rawIngest = lookup.findStatic(
            ingestClass,
            "rawIngest",
            MethodType.methodType(boolean.class, worldIdClass, class_2826.class, int.class, int.class, int.class, class_2804.class, class_2804.class)
         );
         VSSApi.registerColumnConsumer(
            (level, dimension, chunkX, chunkZ, columnData) -> {
               try {
                  Object worldId = (Object)worldIdentifierOf.invoke((class_638)level);
                  if (worldId == null) {
                     return;
                  }

                  for (VoxelColumnData.SectionData s : columnData.sections()) {
                     rawIngest.invoke(
                        (Object)worldId,
                        (class_2826)s.section(),
                        (int)chunkX,
                        (int)s.sectionY(),
                        (int)chunkZ,
                        (class_2804)s.blockLight(),
                        (class_2804)s.skyLight()
                     );
                  }
               } catch (Throwable e) {
                  if (e instanceof Error && !(e instanceof LinkageError) && !(e instanceof AssertionError)) {
                     throw (Error)e;
                  }

                  VSSLogger.error("Voxy raw ingest failed", e);
               }
            }
         );
         VSSLogger.info("Voxy detected - registered raw ingest bridge");
         return true;
      } catch (ClassNotFoundException e) {
         VSSLogger.warn("Voxy compat: class not found - " + e.getMessage());
         return false;
      } catch (NoSuchMethodException e) {
         VSSLogger.warn("Voxy compat: method not found - " + e.getMessage());
         return false;
      } catch (Throwable e) {
         VSSLogger.error("Failed to initialize Voxy compat", e);
         return false;
      }
   }

   private static void initConfigHandles() throws Throwable {
      if (getVoxyConfig == null) {
         Lookup lookup = MethodHandles.lookup();
         Class<?> voxyConfigClass = Class.forName("me.cortex.voxy.client.config.VoxyConfig");
         Field configField = voxyConfigClass.getField("CONFIG");
         getSectionRenderDist = lookup.findGetter(voxyConfigClass, "sectionRenderDistance", float.class)
            .asType(MethodType.methodType(float.class, Object.class));
         getVoxyConfig = lookup.unreflectGetter(configField).asType(MethodType.methodType(Object.class));
      }
   }

   static OptionalInt getViewDistanceChunks() {
      try {
         initConfigHandles();
         Object config = (Object)getVoxyConfig.invokeExact();
         float sectionDist = (float)getSectionRenderDist.invokeExact((Object)config);
         return OptionalInt.of(Math.round(sectionDist * 32.0F));
      } catch (Throwable e) {
         return OptionalInt.empty();
      }
   }
}
