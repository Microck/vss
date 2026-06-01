package dev.micr.vss.mixin;

import net.minecraft.class_3215;
import net.minecraft.class_3898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_3215.class)
public interface AccessorServerChunkCache {
   @Accessor("field_17254")
   class_3898 getChunkMap();
}
