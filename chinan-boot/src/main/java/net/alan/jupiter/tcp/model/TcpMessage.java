package net.alan.jupiter.tcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TcpMessage {

    private String type;
    private String requestId;
    private Integer code;
    private String message;
    private JsonNode payload;

    public boolean isRequest() {
        return code == null;
    }

    public static TcpMessage response(String requestId, int code, String message, JsonNode payload) {
        return TcpMessage.builder()
                .requestId(requestId)
                .code(code)
                .message(message)
                .payload(payload)
                .build();
    }
}