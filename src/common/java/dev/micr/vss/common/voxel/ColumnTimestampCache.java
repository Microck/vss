package dev.micr.vss.common.voxel;

import dev.micr.vss.common.VSSConstants;
import dev.micr.vss.common.VSSLogger;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ColumnTimestampCache {
   private static final int FORMAT_VERSION = 1;
   private static final String FILE_NAME = "vss-timestamps.bin";
   private static final int BYTES_PER_ENTRY = 16;
   private final Map<String, ColumnTimestampCache.DimensionCache> caches = new HashMap<>();
   private final int maxEntriesPerDimension;

   public ColumnTimestampCache(int maxEntriesPerDimension) {
      this.maxEntriesPerDimension = maxEntriesPerDimension;
   }

   public static int mbToEntries(int mb) {
      return mb * 65536;
   }

   public void put(String dimension, long packed, long timestamp, long now) {
      ColumnTimestampCache.DimensionCache cache = this.caches.computeIfAbsent(dimension, k -> new ColumnTimestampCache.DimensionCache());
      cache.timestamps.put(packed, timestamp);
      cache.insertionTimes.put(packed, now);
   }

   public long get(String dimension, long packed) {
      ColumnTimestampCache.DimensionCache cache = this.caches.get(dimension);
      return cache == null ? 0L : cache.timestamps.getOrDefault(packed, 0L);
   }

   public void invalidate(String dimension, long[] positions) {
      ColumnTimestampCache.DimensionCache cache = this.caches.get(dimension);
      if (cache != null) {
         for (long pos : positions) {
            cache.timestamps.remove(pos);
            cache.insertionTimes.remove(pos);
         }
      }
   }

   public int evictIfOversized() {
      int evicted = 0;

      for (ColumnTimestampCache.DimensionCache cache : this.caches.values()) {
         int excess = cache.timestamps.size() - this.maxEntriesPerDimension;
         if (excess > 0) {
            evicted += this.evictOldest(cache, excess);
         }
      }

      return evicted;
   }

   private int evictOldest(ColumnTimestampCache.DimensionCache cache, int count) {
      long[] keys = new long[count];
      long[] times = new long[count];
      int found = 0;
      ObjectIterator i = cache.insertionTimes.long2LongEntrySet().iterator();

      while (i.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2LongMap.Entry e = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)i.next();
         long t = e.getLongValue();
         if (found < count) {
            keys[found] = e.getLongKey();
            times[found] = t;
            found++;
         } else {
            int maxIdx = 0;

            for (int ix = 1; ix < count; ix++) {
               if (times[ix] > times[maxIdx]) {
                  maxIdx = ix;
               }
            }

            if (t < times[maxIdx]) {
               keys[maxIdx] = e.getLongKey();
               times[maxIdx] = t;
            }
         }
      }

      for (int ix = 0; ix < found; ix++) {
         cache.timestamps.remove(keys[ix]);
         cache.insertionTimes.remove(keys[ix]);
      }

      return found;
   }

   public int size() {
      int total = 0;

      for (ColumnTimestampCache.DimensionCache cache : this.caches.values()) {
         total += cache.timestamps.size();
      }

      return total;
   }

   public void save(Path dataDir) {
      if (!this.caches.isEmpty()) {
         Path file = dataDir.resolve("vss-timestamps.bin");
         Path tmpFile = file.resolveSibling("vss-timestamps.bin.tmp");

         try {
            Files.createDirectories(dataDir);
            DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmpFile));

            try {
               out.writeInt(1);
               out.writeInt(this.caches.size());

               for (Entry<String, ColumnTimestampCache.DimensionCache> entry : this.caches.entrySet()) {
                  out.writeUTF(entry.getKey());
                  ColumnTimestampCache.DimensionCache dimCache = entry.getValue();
                  out.writeInt(dimCache.timestamps.size());
                  ObjectIterator var8 = dimCache.timestamps.long2LongEntrySet().iterator();

                  while (var8.hasNext()) {
                     it.unimi.dsi.fastutil.longs.Long2LongMap.Entry e = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)var8.next();
                     out.writeLong(e.getLongKey());
                     out.writeLong(e.getLongValue());
                  }
               }
            } catch (Throwable var12) {
               try {
                  out.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }

               throw var12;
            }

            out.close();
            Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (VSSLogger.isDebugEnabled()) {
               VSSLogger.debug("Saved " + this.size() + " timestamp cache entries to " + file);
            }
         } catch (IOException e) {
            VSSLogger.warn("Failed to save timestamp cache to " + file, e);

            try {
               Files.deleteIfExists(tmpFile);
            } catch (IOException e2) {
               VSSLogger.warn("Failed to clean up temporary timestamp cache file " + tmpFile, e2);
            }
         }
      }
   }

   public void load(Path dataDir) {
      Path file = dataDir.resolve("vss-timestamps.bin");
      if (Files.exists(file)) {
         long now = VSSConstants.epochSeconds();
         int totalLoaded = 0;

         try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int version = in.readInt();
            if (version == 1) {
               int dimCount = in.readInt();

               for (int d = 0; d < dimCount; d++) {
                  String dimension = in.readUTF();
                  int entryCount = in.readInt();
                  if (entryCount >= 0 && entryCount <= this.maxEntriesPerDimension) {
                     ColumnTimestampCache.DimensionCache cache = this.caches.computeIfAbsent(dimension, k -> new ColumnTimestampCache.DimensionCache());
                     cache.timestamps.ensureCapacity(entryCount);
                     cache.insertionTimes.ensureCapacity(entryCount);

                     for (int i = 0; i < entryCount; i++) {
                        long packed = in.readLong();
                        long timestamp = in.readLong();
                        cache.timestamps.put(packed, timestamp);
                        cache.insertionTimes.put(packed, now);
                     }

                     totalLoaded += entryCount;
                  } else {
                     VSSLogger.warn("Timestamp cache dimension " + dimension + " has invalid count " + entryCount + ", skipping");
                     in.skipBytes(entryCount * 16);
                  }
               }

               VSSLogger.info("Loaded " + totalLoaded + " timestamp cache entries from " + file);
            } else {
               VSSLogger.warn("Timestamp cache " + file + " has unsupported version " + version + ", discarding");
            }
         } catch (IOException e) {
            VSSLogger.warn("Failed to load timestamp cache from " + file, e);
         }
      }
   }

   public ColumnTimestampCache snapshotForSave() {
      ColumnTimestampCache snapshot = new ColumnTimestampCache(this.maxEntriesPerDimension);

      for (Entry<String, ColumnTimestampCache.DimensionCache> entry : this.caches.entrySet()) {
         ColumnTimestampCache.DimensionCache dimCache = entry.getValue();
         ColumnTimestampCache.DimensionCache copy = new ColumnTimestampCache.DimensionCache(
            new Long2LongOpenHashMap(dimCache.timestamps), new Long2LongOpenHashMap()
         );
         snapshot.caches.put(entry.getKey(), copy);
      }

      return snapshot;
   }

   private record DimensionCache(Long2LongOpenHashMap timestamps, Long2LongOpenHashMap insertionTimes) {
      DimensionCache() {
         this(new Long2LongOpenHashMap(), new Long2LongOpenHashMap());
      }
   }
}
