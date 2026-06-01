package dev.micr.vss.compat;

import java.util.OptionalInt;
import net.fabricmc.loader.api.FabricLoader;

public final class ModCompat {
   private static boolean voxyLoaded;

   public static void init() {
      if (FabricLoader.getInstance().isModLoaded("voxy")) {
         voxyLoaded = VoxyCompat.init();
      }
   }

   public static OptionalInt getVoxyViewDistanceChunks() {
      return !voxyLoaded ? OptionalInt.empty() : VoxyCompat.getViewDistanceChunks();
   }
}
