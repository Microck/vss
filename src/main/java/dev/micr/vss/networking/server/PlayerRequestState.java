package dev.micr.vss.networking.server;

import dev.micr.vss.common.PositionUtil;
import dev.micr.vss.common.processing.AbstractPlayerRequestState;
import dev.micr.vss.common.processing.IncomingRequest;
import net.minecraft.class_1937;
import net.minecraft.class_3222;
import net.minecraft.class_5321;
import net.minecraft.class_8710;

public class PlayerRequestState extends AbstractPlayerRequestState<PlayerRequestState.QueuedPayload> {
   private volatile class_3222 player;
   private class_5321<class_1937> lastDimension;

   public PlayerRequestState(class_3222 player, int syncRate, int syncConcurrency, int genRate, int genConcurrency) {
      super(player.method_5667(), syncRate, syncConcurrency, genRate, genConcurrency);
      this.player = player;
      this.lastDimension = player.method_51469().method_27983();
   }

   public void addRequest(int requestId, long packedPosition, long clientTimestamp) {
      int cx = PositionUtil.unpackX(packedPosition);
      int cz = PositionUtil.unpackZ(packedPosition);
      this.enqueueIncomingRequest(new IncomingRequest(requestId, cx, cz, clientTimestamp));
   }

   public void onDimensionChange() {
      this.onDimensionChangeBase();
   }

   public void updatePlayer(class_3222 newPlayer) {
      this.player = newPlayer;
   }

   public class_3222 getPlayer() {
      return this.player;
   }

   public class_5321<class_1937> getLastDimension() {
      return this.lastDimension;
   }

   public boolean checkDimensionChange() {
      class_5321<class_1937> currentDim = this.player.method_51469().method_27983();
      if (!currentDim.equals(this.lastDimension)) {
         this.lastDimension = currentDim;
         return true;
      } else {
         return false;
      }
   }

   public record QueuedPayload(class_8710 payload, int requestId, int estimatedBytes, long submissionOrder)
      implements Comparable<PlayerRequestState.QueuedPayload> {
      public int compareTo(PlayerRequestState.QueuedPayload other) {
         return Long.compare(this.submissionOrder, other.submissionOrder);
      }
   }
}
