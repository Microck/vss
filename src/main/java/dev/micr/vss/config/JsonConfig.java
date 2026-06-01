package dev.micr.vss.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.micr.vss.common.VSSLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public abstract class JsonConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   protected abstract String getFileName();

   protected void validate() {
   }

   public void save() {
      try {
         Path path = this.resolvePath();
         Files.createDirectories(path.getParent());
         Files.writeString(path, GSON.toJson(this));
      } catch (Exception e) {
         VSSLogger.error("Failed to save config " + this.getFileName(), e);
      }
   }

   private Path resolvePath() {
      return FabricLoader.getInstance().getConfigDir().resolve(this.getFileName());
   }

   protected static <T extends JsonConfig> T load(Class<T> type, String fileName) {
      Path path = FabricLoader.getInstance().getConfigDir().resolve(fileName);
      boolean fileExists = Files.isRegularFile(path);
      if (fileExists) {
         try {
            String json = Files.readString(path);
            T config = (T)GSON.fromJson(json, type);
            if (config != null) {
               config.validate();
               config.save();
               return config;
            }

            VSSLogger.warn("Config " + fileName + " was empty or invalid, using defaults");
         } catch (Exception e) {
            VSSLogger.error("Failed to read config " + fileName + ", using defaults", e);
         }
      }

      try {
         T config = (T)type.getDeclaredConstructor().newInstance();
         if (!fileExists) {
            config.save();
         }

         return config;
      } catch (ReflectiveOperationException e) {
         throw new RuntimeException("Cannot instantiate config " + type.getName(), e);
      }
   }
}
