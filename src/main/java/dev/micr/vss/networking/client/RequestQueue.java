package dev.micr.vss.networking.client;

class RequestQueue {
   private static final long[] EMPTY = new long[0];
   private long[] positions = EMPTY;
   private long[] timestamps = EMPTY;
   private int size = 0;
   private int readIndex = 0;

   void populate(SpiralScanner.ScanResult result) {
      int n = result.count();
      if (this.positions.length < n) {
         this.positions = new long[n];
         this.timestamps = new long[n];
      }

      System.arraycopy(result.positions(), 0, this.positions, 0, n);
      System.arraycopy(result.timestamps(), 0, this.timestamps, 0, n);
      this.size = n;
      this.readIndex = 0;
   }

   boolean hasNext() {
      return this.readIndex < this.size;
   }

   long peekPosition() {
      return this.positions[this.readIndex];
   }

   long peekTimestamp() {
      return this.timestamps[this.readIndex];
   }

   void skip() {
      if (this.readIndex < this.size) {
         this.readIndex++;
      }
   }

   int remaining() {
      return Math.max(0, this.size - this.readIndex);
   }

   void clear() {
      this.positions = EMPTY;
      this.timestamps = EMPTY;
      this.size = 0;
      this.readIndex = 0;
   }
}
