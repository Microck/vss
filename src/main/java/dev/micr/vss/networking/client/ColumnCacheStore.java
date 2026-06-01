package dev.micr.vss.networking.client;

import dev.micr.vss.common.VSSLogger;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1937;
import net.minecraft.class_5321;

public class ColumnCacheStore {
   private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");
   private static final int FORMAT_VERSION = 3;
   private static final int MAX_CACHE_ENTRIES = 2000000;
   private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("vss").resolve("cache");
   private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "VSS-CacheIO");
      t.setDaemon(true);
      return t;
   });

   public static Long2LongOpenHashMap load(String serverAddress, class_5321<class_1937> dimension) {
      Long2LongOpenHashMap map = new Long2LongOpenHashMap();
      map.defaultReturnValue(-1L);
      Path file = getCacheFile(serverAddress, dimension);
      if (!Files.exists(file)) {
         return map;
      }

      try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
         int version = in.readInt();
         if (version != 3 && version != 2 && version != 1) {
            VSSLogger.warn("Column cache " + file + " has unsupported version " + version + ", discarding");
            return map;
         }

         int count = in.readInt();
         if (count < 0 || count > 2000000) {
            VSSLogger.warn("Column cache " + file + " has invalid entry count " + count + ", discarding");
            return map;
         }

         map.ensureCapacity(count);

         for (int i = 0; i < count; i++) {
            long pos = in.readLong();
            long value = in.readLong();
            if (version == 2) {
               map.put(pos, value >> 8);
            } else {
               map.put(pos, value);
            }
         }

         String migration = version < 3 ? " (migrated from v" + version + ")" : "";
         VSSLogger.info("Loaded " + count + " cached column entries for " + dimensionKey(dimension) + migration);
      } catch (IOException e) {
         VSSLogger.warn("Failed to load column cache from " + file, e);
      }

      return map;
   }

   public static CompletableFuture<Long2LongOpenHashMap> loadAsync(String serverAddress, class_5321<class_1937> dimension) {
      return CompletableFuture.supplyAsync(() -> load(serverAddress, dimension), IO_EXECUTOR);
   }

   public static void saveAsync(String serverAddress, class_5321<class_1937> dimension, Long2LongOpenHashMap columns) {
      if (!columns.isEmpty()) {
         Long2LongOpenHashMap copy = new Long2LongOpenHashMap(columns);
         copy.defaultReturnValue(-1L);
         IO_EXECUTOR.execute(() -> save(serverAddress, dimension, copy));
      }
   }

   public static void save(String serverAddress, class_5321<class_1937> dimension, Long2LongOpenHashMap columns) {
      if (!columns.isEmpty()) {
         Path file = getCacheFile(serverAddress, dimension);
         Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");

         try {
            Files.createDirectories(file.getParent());

            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmpFile))) {
               out.writeInt(3);
               out.writeInt(columns.size());
               ObjectIterator e2 = columns.long2LongEntrySet().iterator();

               while (e2.hasNext()) {
                  Entry entry = (Entry)e2.next();
                  out.writeLong(entry.getLongKey());
                  out.writeLong(entry.getLongValue());
               }
            }

            Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            VSSLogger.info("Saved " + columns.size() + " cached column entries for " + dimensionKey(dimension));
         } catch (IOException e) {
            VSSLogger.warn("Failed to save column cache to " + file, e);

            try {
               Files.deleteIfExists(tmpFile);
            } catch (IOException e2) {
               VSSLogger.warn("Failed to clean up temporary cache file " + tmpFile, e2);
            }
         }
      }
   }

   public static void clearForServer(String serverAddress) {
      Path dir = getServerDir(serverAddress);
      if (Files.exists(dir)) {
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
               Files.deleteIfExists(file);
            }

            Files.deleteIfExists(dir);
            VSSLogger.info("Cleared column cache for server " + serverAddress);
         } catch (IOException e) {
            VSSLogger.warn("Failed to clear column cache for " + serverAddress, e);
         }
      }
   }

   public static void clearAll() {
      if (Files.exists(CACHE_DIR)) {
         try (DirectoryStream<Path> servers = Files.newDirectoryStream(CACHE_DIR)) {
            for (Path serverDir : servers) {
               if (Files.isDirectory(serverDir)) {
                  try (DirectoryStream<Path> files = Files.newDirectoryStream(serverDir)) {
                     for (Path file : files) {
                        Files.deleteIfExists(file);
                     }
                  }

                  Files.deleteIfExists(serverDir);
               }
            }

            VSSLogger.info("Cleared all column caches");
         } catch (IOException e) {
            VSSLogger.warn("Failed to clear all column caches", e);
         }
      }
   }

   private static Path getServerDir(String serverAddress) {
      return CACHE_DIR.resolve(sanitizeForFilePath(serverAddress));
   }

   private static Path getCacheFile(String serverAddress, class_5321<class_1937> dimension) {
      return getServerDir(serverAddress).resolve(dimensionKey(dimension) + ".bin");
   }

   private static String dimensionKey(class_5321<class_1937> dimension) {
      return sanitizeForFilePath(dimension.method_29177().toString());
   }

   static String sanitizeForFilePath(String name) {
      return SANITIZE_PATTERN.matcher(name).replaceAll("_");
   }
}
