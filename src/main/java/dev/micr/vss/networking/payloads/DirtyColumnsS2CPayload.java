package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record DirtyColumnsS2CPayload(long[] dirtyPositions) implements class_8710 {
   public static final int MAX_POSITIONS = 10240;
   public static final class_9154<DirtyColumnsS2CPayload> TYPE = new class_9154(class_2960.method_60654("vss:dirty_columns"));
   public static final class_9139<class_2540, DirtyColumnsS2CPayload> CODEC = class_9139.method_56437((buf, payload) -> {
      buf.method_10804(payload.dirtyPositions.length);

      for (long pos : payload.dirtyPositions) {
         buf.method_52974(pos);
      }
   }, buf -> {
      int rawLen = Math.max(buf.method_10816(), 0);
      int len = Math.min(rawLen, 10240);
      long[] positions = new long[len];

      for (int i = 0; i < len; i++) {
         positions[i] = buf.readLong();
      }

      int excess = rawLen - len;
      if (excess > 0) {
         int toSkip = (int)Math.min(excess * 8L, buf.readableBytes());
         buf.method_52994(toSkip);
      }

      return new DirtyColumnsS2CPayload(positions);
   });

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
