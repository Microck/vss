package dev.micr.vss.mixin;

import net.minecraft.class_3218;
import net.minecraft.class_3898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_3898.class)
public interface AccessorChunkMap {
   @Accessor("field_17214")
   class_3218 getLevel();
}
