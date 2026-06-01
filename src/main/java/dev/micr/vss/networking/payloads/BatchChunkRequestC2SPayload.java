package dev.micr.vss.networking.payloads;

import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public record BatchChunkRequestC2SPayload(int[] requestIds, long[] packedPositions, long[] clientTimestamps, int count) implements class_8710 {
   public static final class_9154<BatchChunkRequestC2SPayload> TYPE = new class_9154(class_2960.method_60654("vss:batch_chunk_req"));
   public static final class_9139<class_2540, BatchChunkRequestC2SPayload> CODEC = class_9139.method_56437((buf, payload) -> {
      buf.method_10804(payload.count);

      for (int i = 0; i < payload.count; i++) {
         buf.method_10804(payload.requestIds[i]);
         buf.method_52974(payload.packedPositions[i]);
         buf.method_52974(payload.clientTimestamps[i]);
      }
   }, buf -> {
      int count = buf.method_10816();
      if (count >= 0 && count <= 1024) {
         int[] requestIds = new int[count];
         long[] packedPositions = new long[count];
         long[] clientTimestamps = new long[count];

         for (int i = 0; i < count; i++) {
            requestIds[i] = buf.method_10816();
            packedPositions[i] = buf.readLong();
            clientTimestamps[i] = buf.readLong();
         }

         return new BatchChunkRequestC2SPayload(requestIds, packedPositions, clientTimestamps, count);
      } else {
         throw new IllegalArgumentException("Batch chunk request count out of range: " + count);
      }
   });

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
