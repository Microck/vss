package dev.micr.vss.common.processing;

import java.util.ArrayDeque;
import java.util.UUID;

public interface PlayerStateAccess {
   void drainDirtyClearRequests();

   void clearProcessingState();

   boolean hasDiskReadDone(int var1, int var2);

   void markDiskReadDone(int var1, int var2);

   int getSendQueueSize();

   int getPendingSyncCount();

   int getPendingGenerationCount();

   boolean supportsVoxelColumns();

   UUID getPlayerUUID();

   RateLimiterSet getRateLimiters();

   ArrayDeque<AbstractPlayerRequestState.QueuedRequest> getWaitingQueue();

   int getWaitingQueueSize();

   IncomingRequest pollIncomingRequest();

   void addPendingRequest(PendingRequest var1);

   PendingRequest removePendingByPosition(int var1, int var2);

   PendingRequest removePendingByRequestId(int var1);

   boolean hasPendingRequest(int var1, int var2);

   Integer pollCancel();
}
