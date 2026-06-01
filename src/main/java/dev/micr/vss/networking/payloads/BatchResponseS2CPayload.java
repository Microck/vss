package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record BatchResponseS2CPayload(byte[] responseTypes, int[] requestIds, int count) implements class_8710 {
   public static final class_9154<BatchResponseS2CPayload> TYPE = new class_9154(class_2960.method_60654("vss:batch_response"));
   public static final class_9139<class_2540, BatchResponseS2CPayload> CODEC = class_9139.method_56437((buf, payload) -> {
      buf.method_10804(payload.count);

      for (int i = 0; i < payload.count; i++) {
         buf.method_52997(payload.responseTypes[i]);
         buf.method_10804(payload.requestIds[i]);
      }
   }, buf -> {
      int count = buf.method_10816();
      if (count >= 0 && count <= 4096) {
         byte[] responseTypes = new byte[count];
         int[] requestIds = new int[count];

         for (int i = 0; i < count; i++) {
            responseTypes[i] = buf.readByte();
            requestIds[i] = buf.method_10816();
         }

         return new BatchResponseS2CPayload(responseTypes, requestIds, count);
      } else {
         throw new IllegalArgumentException("Batch response count out of range: " + count);
      }
   });

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
