package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record CancelRequestC2SPayload(int requestId) implements class_8710 {
   public static final class_9154<CancelRequestC2SPayload> TYPE = new class_9154(class_2960.method_60654("vss:cancel_request"));
   public static final class_9139<class_2540, CancelRequestC2SPayload> CODEC = class_9139.method_56437(
      (buf, payload) -> buf.method_10804(payload.requestId), buf -> new CancelRequestC2SPayload(buf.method_10816())
   );

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
