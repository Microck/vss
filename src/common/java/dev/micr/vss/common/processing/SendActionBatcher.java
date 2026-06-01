package dev.micr.vss.common.processing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

public final class SendActionBatcher {
   private final Map<UUID, SendActionBatcher.PlayerBatch> batches = new HashMap<>();
   private static final int INITIAL_CAPACITY = 64;

   public void add(UUID playerUuid, byte responseType, int requestId) {
      SendActionBatcher.PlayerBatch batch = this.batches.computeIfAbsent(playerUuid, k -> new SendActionBatcher.PlayerBatch());
      batch.add(responseType, requestId);
   }

   public boolean isEmpty() {
      return this.batches.isEmpty();
   }

   public void forEach(SendActionBatcher.BatchConsumer consumer) {
      for (Entry<UUID, SendActionBatcher.PlayerBatch> entry : this.batches.entrySet()) {
         SendActionBatcher.PlayerBatch batch = entry.getValue();
         if (batch.count > 0) {
            consumer.accept(entry.getKey(), batch.types, batch.requestIds, batch.count);
         }
      }
   }

   public void clear() {
      this.batches.clear();
   }

   @FunctionalInterface
   public interface BatchConsumer {
      void accept(UUID var1, byte[] var2, int[] var3, int var4);
   }

   private static final class PlayerBatch {
      byte[] types = new byte[64];
      int[] requestIds = new int[64];
      int count;

      void add(byte responseType, int requestId) {
         if (this.count >= this.types.length) {
            this.types = Arrays.copyOf(this.types, this.types.length * 2);
            this.requestIds = Arrays.copyOf(this.requestIds, this.requestIds.length * 2);
         }

         this.types[this.count] = responseType;
         this.requestIds[this.count] = requestId;
         this.count++;
      }
   }
}
