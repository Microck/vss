package dev.micr.vss.config;

public class VSSClientConfig extends JsonConfig {
   private static final String FILE_NAME = "vss-client-config.json";
   public static VSSClientConfig CONFIG = load(VSSClientConfig.class, "vss-client-config.json");
   public boolean receiveServerLods = true;
   public int lodDistanceChunks = 0;
   public boolean offThreadSectionProcessing = true;

   @Override
   protected String getFileName() {
      return "vss-client-config.json";
   }

   @Override
   protected void validate() {
      this.lodDistanceChunks = Math.clamp(this.lodDistanceChunks, 0, 512);
   }
}
