package dev.micr.vss.mixin;

import dev.micr.vss.networking.server.RequestProcessingService;
import dev.micr.vss.networking.server.VSSServerNetworking;
import net.minecraft.class_2791;
import net.minecraft.class_3218;
import net.minecraft.class_3898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_3898.class)
public class ChunkMapSaveHook {
   @Unique
   private String vss$cachedDimension;

   @Inject(method = "method_17228", at = @At("RETURN"))
   private void vss$onChunkSaved(class_2791 chunk, CallbackInfoReturnable<Boolean> cir) {
      if ((Boolean)cir.getReturnValue()) {
         RequestProcessingService service = VSSServerNetworking.getRequestService();
         if (service != null) {
            if (this.vss$cachedDimension == null) {
               class_3218 level = ((AccessorChunkMap)this).getLevel();
               this.vss$cachedDimension = level.method_27983().method_29177().toString();
            }

            service.getDirtyTracker().markDirty(this.vss$cachedDimension, chunk.method_12004().field_9181, chunk.method_12004().field_9180);
         }
      }
   }
}
