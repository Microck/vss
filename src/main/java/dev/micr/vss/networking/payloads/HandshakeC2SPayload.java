package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record HandshakeC2SPayload(int protocolVersion, int capabilities) implements class_8710 {
   public static final class_9154<HandshakeC2SPayload> TYPE = new class_9154(class_2960.method_60654("vss:handshake_c2s"));
   public static final class_9139<class_2540, HandshakeC2SPayload> CODEC = class_9139.method_56437((buf, payload) -> {
      buf.method_10804(payload.protocolVersion);
      buf.method_10804(payload.capabilities);
   }, buf -> {
      int version = buf.method_10816();
      int caps = buf.method_10816();
      return new HandshakeC2SPayload(version, caps);
   });

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
