package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record BandwidthUpdateC2SPayload(long desiredRate) implements class_8710 {
   public static final class_9154<BandwidthUpdateC2SPayload> TYPE = new class_9154(class_2960.method_60654("vss:bandwidth_update"));
   public static final class_9139<class_2540, BandwidthUpdateC2SPayload> CODEC = class_9139.method_56437(
      (buf, payload) -> buf.method_10791(payload.desiredRate), buf -> new BandwidthUpdateC2SPayload(buf.method_10792())
   );

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
