package dev.micr.vss.common.processing;

public record PendingRequest(int requestId, int cx, int cz, RequestType type) {
}
