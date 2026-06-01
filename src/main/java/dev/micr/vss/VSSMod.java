package dev.micr.vss;

import dev.micr.vss.benchmark.BenchmarkHook;
import dev.micr.vss.networking.VSSNetworking;
import dev.micr.vss.networking.server.VSSServerNetworking;
import net.fabricmc.api.ModInitializer;

public class VSSMod implements ModInitializer {
   public void onInitialize() {
      VSSNetworking.registerPayloads();
      VSSServerNetworking.init();
      BenchmarkHook.initServer();
   }
}
