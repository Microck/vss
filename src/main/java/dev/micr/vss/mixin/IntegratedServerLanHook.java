package dev.micr.vss.mixin;

import dev.micr.vss.networking.server.VSSServerNetworking;
import net.minecraft.class_1132;
import net.minecraft.class_1934;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1132.class)
public class IntegratedServerLanHook {
   @Inject(method = "method_3763", at = @At("RETURN"))
   private void vss$onLanPublished(class_1934 gameType, boolean allowCheats, int port, CallbackInfoReturnable<Boolean> cir) {
      if ((Boolean)cir.getReturnValue()) {
         VSSServerNetworking.startServiceForLan((class_1132)(Object)this);
      }
   }
}
