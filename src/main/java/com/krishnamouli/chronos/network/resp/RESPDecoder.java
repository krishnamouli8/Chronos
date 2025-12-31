package com.krishnamouli.chronos.network.resp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis RESP (REdis Serialization Protocol) decoder.
 * Supports RESP2 protocol.
 */
public class RESPDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        in.markReaderIndex();
        Object decoded = decodeMessage(in);

        if (decoded == null) {
            in.resetReaderIndex();
            return;
        }

        out.add(decoded);
    }

    private Object decodeMessage(ByteBuf in) {
        if (!in.isReadable()) {
            return null;
        }

        byte firstByte = in.readByte();

        switch (firstByte) {
            case '+': // Simple String
                return decodeSimpleString(in);
            case '-': // Error
                return decodeError(in);
            case ':': // Integer
                return decodeInteger(in);
            case '$': // Bulk String
                return decodeBulkString(in);
            case '*': // Array
                return decodeArray(in);
            default:
                throw new IllegalArgumentException("Unknown RESP type: " + (char) firstByte);
        }
    }

    private String decodeSimpleString(ByteBuf in) {
        String line = readLine(in);
        return line != null ? line : null;
    }

    private String decodeError(ByteBuf in) {
        String line = readLine(in);
        return line != null ? "ERROR:" + line : null;
    }

    private Long decodeInteger(ByteBuf in) {
        String line = readLine(in);
        return line != null ? Long.parseLong(line) : null;
    }

    private byte[] decodeBulkString(ByteBuf in) {
        String lengthStr = readLine(in);
        if (lengthStr == null) {
            return null;
        }

        int length = Integer.parseInt(lengthStr);
        if (length == -1) {
            return null; // Null bulk string
        }

        if (in.readableBytes() < length + 2) { // +2 for \r\n
            return null;
        }

        byte[] data = new byte[length];
        in.readBytes(data);
        in.skipBytes(2); // Skip \r\n

        return data;
    }

    private List<Object> decodeArray(ByteBuf in) {
        String countStr = readLine(in);
        if (countStr == null) {
            return null;
        }

        int count = Integer.parseInt(countStr);
        if (count == -1) {
            return null; // Null array
        }

        List<Object> array = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            in.markReaderIndex();
            Object element = decodeMessage(in);
            if (element == null) {
                in.resetReaderIndex();
                return null;
            }
            array.add(element);
        }

        return array;
    }

    private String readLine(ByteBuf in) {
        int lineLength = indexOf(in, (byte) '\n');
        if (lineLength == -1) {
            return null;
        }

        byte[] lineBytes = new byte[lineLength];
        in.readBytes(lineBytes);
        in.skipBytes(1); // Skip \n

        // Remove \r if present
        if (lineLength > 0 && lineBytes[lineLength - 1] == '\r') {
            return new String(lineBytes, 0, lineLength - 1, StandardCharsets.UTF_8);
        }

        return new String(lineBytes, StandardCharsets.UTF_8);
    }

    private int indexOf(ByteBuf buffer, byte value) {
        int index = buffer.readerIndex();
        int end = buffer.writerIndex();

        for (; index < end; index++) {
            if (buffer.getByte(index) == value) {
                return index - buffer.readerIndex();
            }
        }

        return -1;
    }
}
