package dev.micr.vss.common.processing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class DedupTracker {
   private final Long2ObjectOpenHashMap<DedupTracker.Group> pending = new Long2ObjectOpenHashMap();

   boolean tryAttachOrCreate(long packed, String dimension, UUID primaryPlayer, int requestId, long submissionOrder) {
      DedupTracker.Group existing = (DedupTracker.Group)this.pending.get(packed);
      if (existing != null) {
         existing.attached().add(new DedupTracker.Attachment(primaryPlayer, requestId, submissionOrder));
         return true;
      } else {
         this.pending.put(packed, new DedupTracker.Group(primaryPlayer, dimension, new ArrayList<>(2)));
         return false;
      }
   }

   DedupTracker.Group removeGroup(long packed) {
      return (DedupTracker.Group)this.pending.remove(packed);
   }

   List<DedupTracker.RemovedGroup> removePlayer(UUID playerUuid) {
      List<DedupTracker.RemovedGroup> removed = null;
      ObjectIterator<Entry<DedupTracker.Group>> iter = this.pending.long2ObjectEntrySet().iterator();

      while (iter.hasNext()) {
         Entry<DedupTracker.Group> entry = (Entry<DedupTracker.Group>)iter.next();
         DedupTracker.Group group = (DedupTracker.Group)entry.getValue();
         if (group.primaryPlayer().equals(playerUuid)) {
            if (removed == null) {
               removed = new ArrayList<>();
            }

            removed.add(new DedupTracker.RemovedGroup(entry.getLongKey(), group));
            iter.remove();
         } else {
            group.attached().removeIf(a -> a.playerUuid().equals(playerUuid));
         }
      }

      return removed != null ? removed : List.of();
   }

   record Attachment(UUID playerUuid, int requestId, long submissionOrder) {
   }

   record Group(UUID primaryPlayer, String dimension, ArrayList<DedupTracker.Attachment> attached) {
   }

   record RemovedGroup(long packed, DedupTracker.Group group) {
   }
}
