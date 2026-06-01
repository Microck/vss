package dev.micr.vss.networking.server;

import dev.micr.vss.common.processing.LoadedColumnData;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import net.minecraft.class_1944;
import net.minecraft.class_2540;
import net.minecraft.class_2804;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3218;
import net.minecraft.class_3562;
import net.minecraft.class_3568;
import net.minecraft.class_4076;

public final class SectionSerializer {
   private SectionSerializer() {
   }

   public static LoadedColumnData serializeColumn(class_3218 level, class_2818 chunk, int cx, int cz) {
      int minSectionY = level.method_32891();
      class_2826[] sections = chunk.method_12006();
      class_3568 lightEngine = level.method_22336();
      class_3562 blockLightListener = lightEngine.method_15562(class_1944.field_9282);
      ArrayList<SectionSerializer.SectionInfo> includedSections = new ArrayList<>(sections.length);

      for (int i = 0; i < sections.length; i++) {
         class_2826 section = sections[i];
         if (section != null) {
            int sectionY = minSectionY + i;
            class_4076 sectionPos = class_4076.method_18676(cx, sectionY, cz);
            class_2804 blLayer = blockLightListener.method_15544(sectionPos);
            boolean hasBlockLight = blLayer != null && hasNonZeroData(blLayer);
            if (!section.method_38292() || hasBlockLight) {
               includedSections.add(new SectionSerializer.SectionInfo(i, sectionY, sectionPos, blLayer, hasBlockLight));
            }
         }
      }

      if (includedSections.isEmpty()) {
         return new LoadedColumnData(cx, cz, null, 0);
      }

      class_2540 buf = new class_2540(Unpooled.buffer(sections.length * 1024));

      try {
         buf.method_10804(includedSections.size());
         class_3562 skyLightListener = lightEngine.method_15562(class_1944.field_9284);

         for (SectionSerializer.SectionInfo info : includedSections) {
            class_2826 section = sections[info.index];
            buf.method_52997(info.sectionY);
            section.method_12257(buf);
            buf.method_52964(info.hasBlockLight);
            if (info.hasBlockLight) {
               buf.method_52983(info.blLayer.method_12137());
            }

            class_2804 slLayer = skyLightListener.method_15544(info.sectionPos);
            boolean hasSkyLight = slLayer != null && hasNonZeroData(slLayer);
            buf.method_52964(hasSkyLight);
            if (hasSkyLight) {
               buf.method_52983(slLayer.method_12137());
            }
         }

         byte[] serialized = new byte[buf.readableBytes()];
         buf.method_52979(serialized);
         return new LoadedColumnData(cx, cz, serialized, serialized.length);
      } finally {
         buf.release();
      }
   }

   private static boolean hasNonZeroData(class_2804 layer) {
      for (byte b : layer.method_12137()) {
         if (b != 0) {
            return true;
         }
      }

      return false;
   }

   private record SectionInfo(int index, int sectionY, class_4076 sectionPos, class_2804 blLayer, boolean hasBlockLight) {
   }
}
