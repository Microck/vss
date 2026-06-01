package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record SessionConfigS2CPayload(
   int protocolVersion,
   boolean enabled,
   int lodDistanceChunks,
   int serverCapabilities,
   int syncOnLoadRateLimitPerPlayer,
   int syncOnLoadConcurrencyLimitPerPlayer,
   int generationRateLimitPerPlayer,
   int generationConcurrencyLimitPerPlayer,
   boolean generationEnabled,
   long playerBandwidthLimit
) implements class_8710 {
   public static final class_9154<SessionConfigS2CPayload> TYPE = new class_9154(class_2960.method_60654("vss:session_config"));
   public static final class_9139<class_2540, SessionConfigS2CPayload> CODEC = class_9139.method_56437((buf, payload) -> {
      buf.method_10804(payload.protocolVersion);
      buf.method_52964(payload.enabled);
      buf.method_10804(payload.lodDistanceChunks);
      buf.method_10804(payload.serverCapabilities);
      buf.method_10804(payload.syncOnLoadRateLimitPerPlayer);
      buf.method_10804(payload.syncOnLoadConcurrencyLimitPerPlayer);
      buf.method_10804(payload.generationRateLimitPerPlayer);
      buf.method_10804(payload.generationConcurrencyLimitPerPlayer);
      buf.method_52964(payload.generationEnabled);
      buf.method_10791(payload.playerBandwidthLimit);
   }, buf -> {
      int version = buf.method_10816();
      boolean enabled = buf.readBoolean();
      int lodDist = buf.method_10816();
      int serverCaps = buf.method_10816();
      int syncRate = buf.method_10816();
      int syncConc = buf.method_10816();
      int genRate = buf.method_10816();
      int genConc = buf.method_10816();
      boolean genEnabled = buf.readBoolean();
      long bwLimit = buf.method_10792();
      return new SessionConfigS2CPayload(version, enabled, lodDist, serverCaps, syncRate, syncConc, genRate, genConc, genEnabled, bwLimit);
   });

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
