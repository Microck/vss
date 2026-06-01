package dev.micr.vss.common.tracking;

import dev.micr.vss.common.PositionUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;

public class DirtyColumnTracker {
   private final Map<String, LongOpenHashSet> dirtyColumns = new HashMap<>();

   public synchronized void markDirty(String dimension, int cx, int cz) {
      long packed = PositionUtil.packPosition(cx, cz);
      this.dirtyColumns.computeIfAbsent(dimension, k -> new LongOpenHashSet()).add(packed);
   }

   public synchronized long[] drainDirty(String dimension) {
      LongOpenHashSet set = this.dirtyColumns.get(dimension);
      if (set != null && !set.isEmpty()) {
         long[] result = set.toLongArray();
         set.clear();
         return result;
      } else {
         return null;
      }
   }
}
