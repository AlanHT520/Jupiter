package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import net.alan.jupiter.tcp.model.TcpMessage;

public interface TcpHandler {

    String type();

    TcpMessage handle(String requestId, JsonNode payload);
}