package net.alan.jupiter.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.tcp.model.TcpMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Slf4j
public class TcpMessageCodec {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_FRAME_SIZE = 10 * 1024 * 1024;

    public static TcpMessage readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 4 || length > MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length: " + length);
        }
        byte[] body = new byte[length - 4];
        in.readFully(body);
        String json = new String(body, StandardCharsets.UTF_8);
        return mapper.readValue(json, TcpMessage.class);
    }

    public static void writeFrame(DataOutputStream out, TcpMessage msg) throws IOException {
        byte[] body = mapper.writeValueAsBytes(msg);
        int totalLength = 4 + body.length;
        out.writeInt(totalLength);
        out.write(body);
        out.flush();
    }
}