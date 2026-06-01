package dev.micr.vss.common.processing;

public interface ReadResultAccess {
   int chunkX();

   int chunkZ();

   int requestId();

   long columnTimestamp();

   boolean notFound();

   long submissionOrder();

   default boolean saturated() {
      return false;
   }

   default byte[] sectionBytes() {
      return null;
   }

   default int estimatedBytes() {
      return 0;
   }
}
