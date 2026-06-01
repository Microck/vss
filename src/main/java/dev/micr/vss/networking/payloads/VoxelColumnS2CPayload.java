package dev.micr.vss.networking.payloads;

import net.minecraft.class_1937;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_7924;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

public final class VoxelColumnS2CPayload implements class_8710 {
   private static final int MAX_SECTIONS_SIZE = 2097152;
   private static final int MAX_DIMENSION_STRING_LENGTH = 256;
   public static final class_9154<VoxelColumnS2CPayload> TYPE = new class_9154(class_2960.method_60654("vss:voxel_column"));
   public static final class_9139<class_2540, VoxelColumnS2CPayload> CODEC = class_9139.method_56437(VoxelColumnS2CPayload::write, VoxelColumnS2CPayload::read);
   private final int requestId;
   private final int chunkX;
   private final int chunkZ;
   private final class_5321<class_1937> dimension;
   private final long columnTimestamp;
   private final byte[] sectionBytes;

   public VoxelColumnS2CPayload(int requestId, int chunkX, int chunkZ, class_5321<class_1937> dimension, long columnTimestamp, byte[] sectionBytes) {
      this.requestId = requestId;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      this.dimension = dimension;
      this.columnTimestamp = columnTimestamp;
      this.sectionBytes = sectionBytes;
   }

   public int requestId() {
      return this.requestId;
   }

   public int chunkX() {
      return this.chunkX;
   }

   public int chunkZ() {
      return this.chunkZ;
   }

   public class_5321<class_1937> dimension() {
      return this.dimension;
   }

   public long columnTimestamp() {
      return this.columnTimestamp;
   }

   public byte[] decompressedSections() {
      return this.sectionBytes;
   }

   public int estimatedBytes() {
      return this.sectionBytes.length + 25;
   }

   private static int dimensionToOrdinal(class_5321<class_1937> dim) {
      if (dim == class_1937.field_25179) {
         return 0;
      } else if (dim == class_1937.field_25180) {
         return 1;
      } else {
         return dim == class_1937.field_25181 ? 2 : -1;
      }
   }

   private static void write(class_2540 buf, VoxelColumnS2CPayload payload) {
      buf.method_10804(payload.requestId);
      buf.method_53002(payload.chunkX);
      buf.method_53002(payload.chunkZ);
      int ordinal = dimensionToOrdinal(payload.dimension);
      buf.method_10804(ordinal);
      if (ordinal == -1) {
         buf.method_10814(payload.dimension.method_29177().toString());
      }

      buf.method_52974(payload.columnTimestamp);
      buf.method_10813(payload.sectionBytes);
   }

   private static VoxelColumnS2CPayload read(class_2540 buf) {
      int requestId = buf.method_10816();
      int cx = buf.readInt();
      int cz = buf.readInt();
      int ordinal = buf.method_10816();

      class_5321<class_1937> dim = switch (ordinal) {
         case 0 -> class_1937.field_25179;
         case 1 -> class_1937.field_25180;
         case 2 -> class_1937.field_25181;
         default -> class_5321.method_29179(class_7924.field_41223, class_2960.method_60654(buf.method_10800(256)));
      };
      long columnTimestamp = buf.readLong();
      byte[] sectionBytes = buf.method_10803(2097152);
      return new VoxelColumnS2CPayload(requestId, cx, cz, dim, columnTimestamp, sectionBytes);
   }

   public class_9154<? extends class_8710> method_56479() {
      return TYPE;
   }
}
