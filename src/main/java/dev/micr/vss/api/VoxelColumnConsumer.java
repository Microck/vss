package dev.micr.vss.api;

import net.minecraft.class_1937;
import net.minecraft.class_5321;
import net.minecraft.class_638;

@FunctionalInterface
public interface VoxelColumnConsumer {
   void onVoxelColumnReceived(class_638 var1, class_5321<class_1937> var2, int var3, int var4, VoxelColumnData var5);
}
