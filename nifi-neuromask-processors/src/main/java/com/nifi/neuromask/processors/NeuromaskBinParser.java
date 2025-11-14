package com.nifi.neuromask.processors;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.nifi.logging.ComponentLog;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class NeuromaskBinParser {

    private final ComponentLog logger;
    private final int floatPrecision;
    private final int bufferSize;

    public static final int DEFAULT_FLOAT_PRECISION = 6;
    private static final int DEFAULT_READING_BUFFER_SIZE = 1024;

    private NeuromaskBinParser(ComponentLog logger, int floatPrecision, int bufferSize) {
        this.logger = logger;
        this.floatPrecision = floatPrecision;
        this.bufferSize = bufferSize;
    }

    public static class Builder {
        private ComponentLog logger;
        private int floatPrecision = DEFAULT_FLOAT_PRECISION;
        private int bufferSize = DEFAULT_READING_BUFFER_SIZE;

        public Builder withLogger(ComponentLog logger) {
            this.logger = logger;
            return this;
        }

        public Builder withFloatPrecision(int precision) {
            this.floatPrecision = precision;
            return this;
        }

        public Builder withBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public NeuromaskBinParser build() {
            if (logger == null) {
                throw new IllegalStateException("Logger must be provided using withLogger() method");
            }
            return new NeuromaskBinParser(logger, floatPrecision, bufferSize);
        }
    }

    public int getFloatPrecision() {
        return floatPrecision;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public ComponentLog getLogger() {
        return logger;
    }

    public List<String> parseStream(InputStream stream) throws  IOException{
        List<String> output = new ArrayList<>();
        try(BufferedInputStream fin = new BufferedInputStream(stream, this.bufferSize)) {
            if (fin.available() >= 2) {
                List<byte[]> entries = new ArrayList<>();
                int x_old = fin.read();
                int x = fin.read();
                byte[] buf = {(byte)x_old, (byte)x};
                while (x != -1) {
                    while (x != -1 && bytesToShort(buf) != NeuromaskRecord.RECORD_START_MARKER) {
                        buf[0] = buf[1];
                        x = fin.read();
                        buf[1] = (byte)(x & 0xFF);
                    }
                    if (x >= 0) {
                        ByteArrayOutputStream tmpEntry = new ByteArrayOutputStream();
                        while (x != -1 && bytesToShort(buf) != NeuromaskRecord.RECORD_END_MARKER) {
                            tmpEntry.write(buf[0]);
                            buf[0] = buf[1];
                            x = fin.read();
                            buf[1] = (byte) (x & 0xFF);
                        }
                        if (x >= 0) {
                            tmpEntry.write(buf);
                            entries.add(tmpEntry.toByteArray());
                        }
                    }
                }
                logger.debug("Found {} entries", entries.size());
                ObjectMapper mapper = new ObjectMapper();
                mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
                for(byte[] entry : entries) {
                    int entrySize = ((entry[3] & 0xFF) << 8) | (entry[2] & 0xFF);
                    // 6 = len(size_field) + len(START_BYTES) + len(END_BYTES)
                    if (entrySize == entry.length - 6) {
                        int entryId = ((entry[6] & 0xFF) << 16) | (entry[5] & 0xFF) << 8 | (entry[4] & 0xFF);
                        int entryTimestamp = ((entry[10] & 0xFF) << 24) | ((entry[9] & 0xFF) << 16) |
                                (entry[8] & 0xFF) << 8 | (entry[7] & 0xFF);
                        logger.debug("size = {}, id = {}, time = {}", entrySize, entryId, entryTimestamp);
                        logger.debug("entry actual size: {}", entry.length);
                        StringBuilder sb = new StringBuilder();
                        for (byte b : entry) {
                            sb.append(String.format("%02X ", b & 0xFF));
                        }
                        String hexString = sb.toString().trim();
                        logger.debug("entry bytes: {}", hexString);
                        NeuromaskRecord rec = new NeuromaskRecord(entryTimestamp, entryId, entrySize);
                        for (int i = 11; i <= entry.length - 5 - 2; i += 5) {
                            if (NeuromaskRecord.RECORD_FIELDS.containsKey(entry[i])) {
                                int asInt = (entry[i + 1] & 0xFF) | ((entry[i + 2] & 0xFF) << 8) |
                                        ((entry[i + 3] & 0xFF) << 16) | ((entry[i + 4] & 0xFF) << 24);
                                float asFloat = Float.intBitsToFloat(asInt);
                                String formattedFloat = formatFloat(asFloat);
                                logger.debug("{}: {}", NeuromaskRecord.RECORD_FIELDS.get(entry[i]), formattedFloat);
                                rec.addParameter(NeuromaskRecord.RECORD_FIELDS.get(entry[i]), formattedFloat);
                            } else {
                                logger.warn(String.format("Key %02X not found in neuromask record fields", entry[i]));
                            }
                        }
                        try {
                            output.add(mapper.writeValueAsString(rec));
                        } catch (JsonProcessingException e) {
                            logger.error("JSON processing error {}", e.getMessage());
                        }
                    }
                    else  {
                        logger.warn("Packet with incorrect size value");
                    }
                }
            }
        }
        return output;
    }

    public short bytesToShort(byte[] bytes) {
        if (bytes == null || bytes.length != 2) {
            throw new IllegalArgumentException("Array must contain exactly 2 bytes");
        }
        return (short) ((bytes[0] << 8) | (bytes[1] & 0xFF));
    }

    public String formatFloat(float value) {
        DecimalFormat df = new DecimalFormat("0." + "#".repeat(floatPrecision));
        return df.format(value);
    }

}