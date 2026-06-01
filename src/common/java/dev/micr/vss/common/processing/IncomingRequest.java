package dev.micr.vss.common.processing;

public record IncomingRequest(int requestId, int cx, int cz, long clientTimestamp) {
}
