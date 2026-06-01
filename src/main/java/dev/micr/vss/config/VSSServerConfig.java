package dev.micr.vss.config;

public class VSSServerConfig extends JsonConfig {
   private static final String FILE_NAME = "vss-server-config.json";
   public static final VSSServerConfig CONFIG = load(VSSServerConfig.class, "vss-server-config.json");
   public boolean enabled = true;
   public int lodDistanceChunks = 256;
   public int bytesPerSecondLimitPerPlayer = 20971520;
   public int diskReaderThreads = 5;
   public int sendQueueLimitPerPlayer = 4000;
   public int bytesPerSecondLimitGlobal = 104857600;
   public boolean enableChunkGeneration = true;
   public int generationConcurrencyLimitGlobal = 32;
   public int generationTimeoutSeconds = 60;
   public int dirtyBroadcastIntervalSeconds = 10;
   public int syncOnLoadRateLimitPerPlayer = 800;
   public int syncOnLoadConcurrencyLimitPerPlayer = 200;
   public int generationRateLimitPerPlayer = 80;
   public int generationConcurrencyLimitPerPlayer = 16;
   public int perDimensionTimestampCacheSizeMB = 32;

   @Override
   protected String getFileName() {
      return "vss-server-config.json";
   }

   @Override
   protected void validate() {
      this.lodDistanceChunks = Math.clamp(this.lodDistanceChunks, 1, 512);
      this.bytesPerSecondLimitPerPlayer = Math.clamp(this.bytesPerSecondLimitPerPlayer, 1024, 104857600);
      this.diskReaderThreads = Math.clamp(this.diskReaderThreads, 1, 64);
      this.sendQueueLimitPerPlayer = Math.clamp(this.sendQueueLimitPerPlayer, 1, 100000);
      this.bytesPerSecondLimitGlobal = (int)Math.clamp(this.bytesPerSecondLimitGlobal, 1024L, 1073741824L);
      this.generationConcurrencyLimitGlobal = Math.clamp(this.generationConcurrencyLimitGlobal, 1, 256);
      this.generationTimeoutSeconds = Math.clamp(this.generationTimeoutSeconds, 1, 600);
      this.dirtyBroadcastIntervalSeconds = Math.clamp(this.dirtyBroadcastIntervalSeconds, 1, 300);
      this.syncOnLoadRateLimitPerPlayer = Math.clamp(this.syncOnLoadRateLimitPerPlayer, 1, 1000);
      this.syncOnLoadConcurrencyLimitPerPlayer = Math.clamp(this.syncOnLoadConcurrencyLimitPerPlayer, 1, 1000);
      this.generationRateLimitPerPlayer = Math.clamp(this.generationRateLimitPerPlayer, 1, 1000);
      this.generationConcurrencyLimitPerPlayer = Math.clamp(this.generationConcurrencyLimitPerPlayer, 1, 1000);
      this.perDimensionTimestampCacheSizeMB = Math.clamp(this.perDimensionTimestampCacheSizeMB, 1, 256);
   }
}
