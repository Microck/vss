package dev.micr.vss.common;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosticsFormatter {
   private DiagnosticsFormatter() {
   }

   public static List<String> formatDiagnostics(DiagnosticsFormatter.DiagData d) {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("=== VSS LOD Diagnostics ===");
      lines.add(
         String.format(
            "Config: enabled=%s, lodDist=%d, bw/player=%s/s, bw/global=%s/s", d.enabled, d.lodDist, formatBytes(d.bwPerPlayer), formatBytes(d.bwGlobal)
         )
      );
      double secRate = d.uptimeSec > 0L ? (double)d.totalSent / d.uptimeSec : 0.0;
      double byteRate = d.uptimeSec > 0L ? (double)d.totalBytes / d.uptimeSec : 0.0;
      lines.add(
         String.format(
            "Throughput: sent=%d (%s), rate=%s sections/s (%s/s), uptime=%s",
            d.totalSent,
            formatBytes(d.totalBytes),
            formatRate(secRate),
            formatBytes((long)byteRate),
            formatUptime(d.uptimeSec)
         )
      );
      lines.add(String.format("Sources (total): in_mem=%d, disk=%d, up_to_date=%d, gen=%d", d.cumInMem, Math.max(0L, d.diskCompleted), d.cumUtd, d.cumGen));
      lines.add("Sources (tick): " + d.tickDiagnostics);
      lines.add("DiskReader: " + d.diskReaderDiagnostics);
      if (d.generationEnabled) {
         lines.add("Generation: " + d.generationDiagnostics);
      } else {
         lines.add("Generation: disabled");
      }

      lines.add(String.format("Bandwidth: %s/s / %s/s global (%s total)", formatBytes(d.bwWindowRate), formatBytes(d.bwGlobal), formatBytes(d.bwTotal)));

      for (DiagnosticsFormatter.PlayerDiag p : d.players) {
         double pRate = d.uptimeSec > 0L ? (double)p.sent / d.uptimeSec : 0.0;
         lines.add(
            String.format(
               "  %s: sq=%d/%d, psync=%d, pgen=%d, sent=%d (%s), rate=%s/s",
               p.name,
               p.sendQueue,
               p.maxSendQueue,
               p.pendingSync,
               p.pendingGen,
               p.sent,
               formatBytes(p.bytes),
               formatRate(pRate)
            )
         );
      }

      return lines;
   }

   public static String formatRate(double rate) {
      return rate >= 1000.0 ? String.format("%.1fK", rate / 1000.0) : String.format("%.0f", rate);
   }

   public static String formatUptime(long seconds) {
      if (seconds < 60L) {
         return seconds + "s";
      } else {
         return seconds < 3600L ? String.format("%dm %ds", seconds / 60L, seconds % 60L) : String.format("%dh %dm", seconds / 3600L, seconds % 3600L / 60L);
      }
   }

   public static String formatBytes(long bytes) {
      if (bytes < 1024L) {
         return bytes + " B";
      } else if (bytes < 1048576L) {
         return String.format("%.1f KB", bytes / 1024.0);
      } else {
         return bytes < 1073741824L ? String.format("%.1f MB", bytes / 1048576.0) : String.format("%.2f GB", bytes / 1.0737418E9F);
      }
   }

   public record DiagData(
      boolean enabled,
      int lodDist,
      long bwPerPlayer,
      long bwGlobal,
      long uptimeSec,
      long totalSent,
      long totalBytes,
      long cumInMem,
      long cumUtd,
      long cumGen,
      long diskCompleted,
      String tickDiagnostics,
      String diskReaderDiagnostics,
      String generationDiagnostics,
      boolean generationEnabled,
      long bwTotal,
      long bwWindowRate,
      List<DiagnosticsFormatter.PlayerDiag> players
   ) {
   }

   public record PlayerDiag(String name, int sendQueue, int maxSendQueue, int pendingSync, int pendingGen, long sent, long bytes) {
   }
}
