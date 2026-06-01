package dev.micr.vss.common;

public final class PositionUtil {
   private PositionUtil() {
   }

   public static long packPosition(int cx, int cz) {
      return (long)cx << 32 | cz & 4294967295L;
   }

   public static int unpackX(long packed) {
      return (int)(packed >> 32);
   }

   public static int unpackZ(long packed) {
      return (int)packed;
   }

   public static int chebyshevDistance(int x1, int z1, int x2, int z2) {
      return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
   }

   public static boolean isOutOfRange(long packed, int playerCx, int playerCz, int distance) {
      return chebyshevDistance(unpackX(packed), unpackZ(packed), playerCx, playerCz) > distance;
   }
}
