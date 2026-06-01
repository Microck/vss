package dev.micr.vss.common.processing;

public class SequenceCounter {
   private long value = 0L;

   public long next() {
      return this.value++;
   }
}
