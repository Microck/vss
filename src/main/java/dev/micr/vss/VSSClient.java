package dev.micr.vss;

import dev.micr.vss.benchmark.BenchmarkHook;
import dev.micr.vss.compat.ModCompat;
import dev.micr.vss.networking.client.VSSClientCommands;
import dev.micr.vss.networking.client.VSSClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class VSSClient implements ClientModInitializer {
   public void onInitializeClient() {
      VSSClientNetworking.init();
      VSSClientCommands.init();
      ModCompat.init();
      BenchmarkHook.initClient();
   }
}
