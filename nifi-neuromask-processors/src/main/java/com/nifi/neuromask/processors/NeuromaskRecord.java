package com.nifi.neuromask.processors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

class NeuromaskRecord implements Serializable {

    public static Map<Byte, String> RECORD_FIELDS = new HashMap<>() {{
        put((byte) 0x10, "Exhaled carbon dioxide content");
        put((byte) 0x11, "Exhaled oxygen content");
        put((byte) 0x12, "Body temperature");
        put((byte) 0x13, "Humidity of exhalation");
        put((byte) 0x14, "Index of volatile organic compounds (TVOC) in exhalation");
        put((byte) 0x15, "Atmosphere pressure");
        put((byte) 0x16, "Outside temperature");
        put((byte) 0x17, "Electrocardiogram readings");
        put((byte) 0x18, "IMU ax");
        put((byte) 0x19, "IMU ay");
        put((byte) 0x1A, "IMU az");
        put((byte) 0x1B, "IMU gx");
        put((byte) 0x1C, "IMU gy");
        put((byte) 0x1D, "IMU gz");
        put((byte) 0x1E, "IMU mx");
        put((byte) 0x1F, "IMU my");
        put((byte) 0x20, "IMU mz");
        put((byte) 0x21, "Photoplethysmogram red");
        put((byte) 0x22, "Photoplethysmogram IR");
        put((byte) 0x23, "Photoplethysmogram green");
        put((byte) 0x24, "Blood oxygen level (SpO2)");
        put((byte) 0x25, "Height");
        put((byte) 0x26, "Heart rate");
        put((byte) 0x27, "Steps");
        put((byte) 0x28, "Battery charge");
        put((byte) 0x29, "Exhaled Volatile Organic Compound (TVOC) Index (external sensor)");
        put((byte) 0x2A, "Carbon dioxide content (external sensor)");
        put((byte) 0x2B, "Outside humidity");
        put((byte) 0x00, "Reserved");
    }};
    private final int _time;
    private final int id;
    private final int size;

    public static final short RECORD_START_MARKER = (short) 0xF0AA;
    public static final short RECORD_END_MARKER = (short) 0xF1AA;

    private final Map<String, Double> data = new HashMap<>();

    NeuromaskRecord(int _time, int id, int size) {
        this._time = _time;
        this.id = id;
        this.size = size;
        for(String key: RECORD_FIELDS.values()){
            data.put(key, null);
        }
    }

    public void addParameter(String key, String value) {
        if (data.containsKey(key))
            this.data.put(key, Double.valueOf(value));
    }

    public int getTime() {
        return _time;
    }

    public int getId() {
        return id;
    }

    public int getSize() {
        return size;
    }

    public Map<String, Double> getData() {
        return new HashMap<>(data);
    }
}