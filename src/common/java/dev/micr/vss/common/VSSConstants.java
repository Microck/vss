package dev.micr.vss.common;

public final class VSSConstants {
   public static final String MOD_ID = "vss";
   public static final int PROTOCOL_VERSION = 15;
   public static final String CHANNEL_HANDSHAKE = "vss:handshake_c2s";
   public static final String CHANNEL_CHUNK_REQUEST = "vss:batch_chunk_req";
   public static final String CHANNEL_SESSION_CONFIG = "vss:session_config";
   public static final String CHANNEL_DIRTY_COLUMNS = "vss:dirty_columns";
   public static final String CHANNEL_VOXEL_COLUMN = "vss:voxel_column";
   public static final String CHANNEL_BATCH_RESPONSE = "vss:batch_response";
   public static final String CHANNEL_CANCEL_REQUEST = "vss:cancel_request";
   public static final String CHANNEL_BANDWIDTH_UPDATE = "vss:bandwidth_update";
   public static final long NANOS_PER_SECOND = 1000000000L;
   public static final long NANOS_PER_MS = 1000000L;
   public static final int TICKS_PER_SECOND = 20;
   public static final int DISK_READ_TIMEOUT_SECONDS = 10;
   public static final int LOD_DISTANCE_BUFFER = 32;
   public static final int MAX_DIRTY_COLUMN_POSITIONS = 10240;
   public static final int ESTIMATED_COLUMN_OVERHEAD_BYTES = 25;
   public static final int MIN_LOD_DISTANCE = 1;
   public static final int MAX_LOD_DISTANCE = 512;
   public static final int MIN_BYTES_PER_SECOND = 1024;
   public static final int MAX_BYTES_PER_SECOND_PER_PLAYER = 104857600;
   public static final int MIN_DISK_READER_THREADS = 1;
   public static final int MAX_DISK_READER_THREADS = 64;
   public static final int MIN_SEND_QUEUE_SIZE = 1;
   public static final int MAX_SEND_QUEUE_SIZE = 100000;
   public static final long MAX_BYTES_PER_SECOND_GLOBAL_LIMIT = 1073741824L;
   public static final int MIN_CONCURRENT_GENERATIONS = 1;
   public static final int MAX_CONCURRENT_GENERATIONS = 256;
   public static final int MIN_GENERATION_TIMEOUT = 1;
   public static final int MAX_GENERATION_TIMEOUT = 600;
   public static final int MIN_DIRTY_BROADCAST_INTERVAL = 1;
   public static final int MAX_DIRTY_BROADCAST_INTERVAL = 300;
   public static final int MIN_RATE_LIMIT = 1;
   public static final int MAX_RATE_LIMIT = 1000;
   public static final int MIN_CONCURRENCY_LIMIT = 1;
   public static final int MAX_CONCURRENCY_LIMIT = 1000;
   public static final int MIN_TIMESTAMP_CACHE_SIZE_MB = 1;
   public static final int MAX_TIMESTAMP_CACHE_SIZE_MB = 256;
   public static final int MAX_BATCH_CHUNK_REQUESTS = 1024;
   public static final int MAX_BATCH_RESPONSES = 4096;
   public static final byte RESPONSE_RATE_LIMITED = 0;
   public static final byte RESPONSE_UP_TO_DATE = 1;
   public static final byte RESPONSE_NOT_GENERATED = 2;
   public static final int CAPABILITY_VOXEL_COLUMNS = 1;
   public static final int DIM_OVERWORLD = 0;
   public static final int DIM_THE_NETHER = 1;
   public static final int DIM_THE_END = 2;
   public static final int DIM_CUSTOM = -1;
   public static final String DIM_STR_OVERWORLD = "minecraft:overworld";
   public static final String DIM_STR_THE_NETHER = "minecraft:the_nether";
   public static final String DIM_STR_THE_END = "minecraft:the_end";

   private VSSConstants() {
   }

   public static long epochSeconds() {
      return System.currentTimeMillis() / 1000L;
   }

   public static int dimensionStringToOrdinal(String dimStr) {
      return switch (dimStr) {
         case "minecraft:overworld" -> 0;
         case "minecraft:the_nether" -> 1;
         case "minecraft:the_end" -> 2;
         default -> -1;
      };
   }
}
