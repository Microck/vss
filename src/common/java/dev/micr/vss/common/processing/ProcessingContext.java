package dev.micr.vss.common.processing;

import java.util.concurrent.ConcurrentLinkedQueue;

record ProcessingContext(
   ConcurrentLinkedQueue<SendAction> sendActions,
   ConcurrentLinkedQueue<OffThreadProcessor.GenerationTicketRequest> generationTicketRequests,
   ProcessingDiagnostics diagnostics,
   SequenceCounter sequence
) {
}
