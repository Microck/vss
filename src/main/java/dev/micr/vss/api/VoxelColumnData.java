package dev.micr.vss.api;

import net.minecraft.class_2804;
import net.minecraft.class_2826;

public record VoxelColumnData(VoxelColumnData.SectionData[] sections, long columnTimestamp) {
   public record SectionData(int sectionY, class_2826 section, class_2804 blockLight, class_2804 skyLight) {
   }
}
