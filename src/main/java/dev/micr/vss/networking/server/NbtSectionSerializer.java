package dev.micr.vss.networking.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_11897;
import net.minecraft.class_1923;
import net.minecraft.class_1959;
import net.minecraft.class_1972;
import net.minecraft.class_2359;
import net.minecraft.class_2378;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2509;
import net.minecraft.class_2520;
import net.minecraft.class_2540;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2826;
import net.minecraft.class_2841;
import net.minecraft.class_3898;
import net.minecraft.class_5455;
import net.minecraft.class_6563;
import net.minecraft.class_6880;
import net.minecraft.class_7522;
import net.minecraft.class_7924;
import net.minecraft.class_6880.class_6883;

final class NbtSectionSerializer {
   private static final byte[] EMPTY = new byte[0];

   private NbtSectionSerializer() {
   }

   static byte[] readAndSerializeSections(class_3898 chunkMap, class_5455 registryAccess, int cx, int cz) throws Exception {
      CompletableFuture<Optional<class_2487>> future = chunkMap.method_23696(new class_1923(cx, cz));
      Optional<class_2487> optionalTag = future.get(10L, TimeUnit.SECONDS);
      if (optionalTag.isEmpty()) {
         return null;
      }

      class_2487 chunkNbt = optionalTag.get();
      String statusStr = chunkNbt.method_68564("Status", null);
      if (statusStr != null && class_2806.method_12168(statusStr) == class_2806.field_12803) {
         class_11897 factory = class_11897.method_74159(registryAccess);
         Codec<class_2841<class_2680>> blockStateCodec = factory.comp_4787();
         Codec<class_7522<class_6880<class_1959>>> biomeCodec = factory.comp_4790();
         class_2378<class_1959> biomeRegistry = registryAccess.method_30530(class_7924.field_41236);
         class_6883<class_1959> defaultBiome = biomeRegistry.method_46747(class_1972.field_9451);
         class_2359<class_6880<class_1959>> biomeHolderMap = biomeRegistry.method_40295();
         Optional<class_2499> sectionsTag = chunkNbt.method_10554("sections");
         if (sectionsTag.isEmpty()) {
            return null;
         }

         class_2499 sectionsList = sectionsTag.orElseThrow();

         record ParsedSection(int sectionY, class_2826 section, byte[] blockLight, byte[] skyLight) {
         }

         ArrayList<ParsedSection> parsed = new ArrayList<>(sectionsList.size());

         for (class_2520 sectionElement : sectionsList) {
            class_2487 sectionTag = (class_2487)sectionElement;
            int sectionY = sectionTag.method_68083("Y", Integer.MIN_VALUE);
            if (sectionY != Integer.MIN_VALUE) {
               byte[] blockLightData = sectionTag.method_10547("BlockLight").orElse(EMPTY);
               class_2826 result = parseSection(sectionTag, sectionY, blockStateCodec, biomeCodec, defaultBiome, biomeHolderMap, blockLightData);
               if (result != null) {
                  byte[] skyLightData = sectionTag.method_10547("SkyLight").orElse(EMPTY);
                  parsed.add(new ParsedSection(sectionY, result, blockLightData, skyLightData));
               }
            }
         }

         if (parsed.isEmpty()) {
            return new byte[0];
         }

         class_2540 buf = new class_2540(Unpooled.buffer(parsed.size() * 1024));

         try {
            buf.method_10804(parsed.size());

            for (ParsedSection p : parsed) {
               buf.method_52997(p.sectionY);
               p.section.method_12257(buf);
               boolean hasBlockLight = p.blockLight.length == 2048;
               buf.method_52964(hasBlockLight);
               if (hasBlockLight) {
                  buf.method_52983(p.blockLight);
               }

               boolean hasSkyLight = p.skyLight.length == 2048;
               buf.method_52964(hasSkyLight);
               if (hasSkyLight) {
                  buf.method_52983(p.skyLight);
               }
            }

            byte[] result = new byte[buf.readableBytes()];
            buf.method_52979(result);
            return result;
         } finally {
            buf.release();
         }
      } else {
         return null;
      }
   }

   private static class_2826 parseSection(
      class_2487 sectionTag,
      int sectionY,
      Codec<class_2841<class_2680>> blockStateCodec,
      Codec<class_7522<class_6880<class_1959>>> biomeCodec,
      class_6880<class_1959> defaultBiome,
      class_2359<class_6880<class_1959>> biomeHolderMap,
      byte[] blockLightData
   ) {
      Optional<class_2487> blockStatesOpt = sectionTag.method_10562("block_states");
      if (blockStatesOpt.isEmpty()) {
         return null;
      }

      DataResult<class_2841<class_2680>> blockStatesResult = blockStateCodec.parse(class_2509.field_11560, (class_2520)blockStatesOpt.get());
      class_2841<class_2680> blockStates = (class_2841<class_2680>)blockStatesResult.result().orElse(null);
      if (blockStates == null) {
         return null;
      }

      Optional<class_2487> optBiomes = sectionTag.method_10562("biomes");
      class_7522<class_6880<class_1959>> biomes;
      if (optBiomes.isPresent()) {
         DataResult<class_7522<class_6880<class_1959>>> biomesResult = biomeCodec.parse(class_2509.field_11560, (class_2520)optBiomes.get());
         biomes = (class_7522<class_6880<class_1959>>)biomesResult.result().orElse(null);
      } else {
         biomes = null;
      }

      class_2826 section;
      if (biomes instanceof class_2841<class_6880<class_1959>> biomeContainer) {
         section = new class_2826(blockStates, biomeContainer);
      } else {
         class_2841<class_6880<class_1959>> defaultBiomeContainer = new class_2841(defaultBiome, class_6563.method_74165(biomeHolderMap));
         section = new class_2826(blockStates, defaultBiomeContainer);
      }

      if (section.method_38292()) {
         if (blockLightData.length != 2048) {
            return null;
         }

         boolean hasLight = false;

         for (byte b : blockLightData) {
            if (b != 0) {
               hasLight = true;
               break;
            }
         }

         if (!hasLight) {
            return null;
         }
      }

      return section;
   }
}
