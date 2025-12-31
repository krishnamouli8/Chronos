package com.krishnamouli.chronos.network.resp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Redis RESP (REdis Serialization Protocol) encoder.
 * Encodes responses back to clients.
 */
public class RESPEncoder extends MessageToByteEncoder<Object> {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg == null) {
            encodeNull(out);
        } else if (msg instanceof String) {
            encodeSimpleString(out, (String) msg);
        } else if (msg instanceof byte[]) {
            encodeBulkString(out, (byte[]) msg);
        } else if (msg instanceof Long || msg instanceof Integer) {
            encodeInteger(out, ((Number) msg).longValue());
        } else if (msg instanceof List) {
            encodeArray(out, (List<?>) msg);
        } else if (msg instanceof RESPError) {
            encodeError(out, ((RESPError) msg).getMessage());
        } else {
            throw new IllegalArgumentException("Unsupported type: " + msg.getClass());
        }
    }

    private void encodeSimpleString(ByteBuf out, String str) {
        out.writeByte('+');
        out.writeBytes(str.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeError(ByteBuf out, String error) {
        out.writeByte('-');
        out.writeBytes(error.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeInteger(ByteBuf out, long value) {
        out.writeByte(':');
        out.writeBytes(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeBulkString(ByteBuf out, byte[] data) {
        out.writeByte('$');
        out.writeBytes(String.valueOf(data.length).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
        out.writeBytes(data);
        out.writeBytes(CRLF);
    }

    private void encodeNull(ByteBuf out) {
        out.writeByte('$');
        out.writeBytes("-1".getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void encodeArray(ByteBuf out, List<?> array) throws Exception {
        out.writeByte('*');
        out.writeBytes(String.valueOf(array.size()).getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);

        for (Object item : array) {
            encode(null, item, out);
        }
    }

    public static class RESPError {
        private final String message;

        public RESPError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
