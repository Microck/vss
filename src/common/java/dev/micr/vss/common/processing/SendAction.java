package dev.micr.vss.common.processing;

import java.util.UUID;

public sealed interface SendAction permits SendAction.RateLimited, SendAction.ColumnUpToDate, SendAction.ColumnNotGenerated {
   UUID playerUuid();

   int requestId();

   default byte responseType() {
      return switch (this) {
         case SendAction.RateLimited a -> 0;
         case SendAction.ColumnUpToDate a -> 1;
         case SendAction.ColumnNotGenerated a -> 2;
         default -> throw new MatchException(null, null);
      };
   }

   record ColumnNotGenerated(UUID playerUuid, int requestId) implements SendAction {
   }

   record ColumnUpToDate(UUID playerUuid, int requestId) implements SendAction {
   }

   record RateLimited(UUID playerUuid, int requestId) implements SendAction {
   }
}
