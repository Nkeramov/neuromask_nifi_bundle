package com.nifi.neuromask.processors;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


@Tags({"neuromask", "bin", "parser"})
@CapabilityDescription("Parser for neuromask bin packets")
public class NeuromaskBinParser extends AbstractProcessor {
	private static final int DEFAULT_FLOAT_PRECISION = 3;
    public static final PropertyDescriptor FLOAT_PRECISION_PROPERTY = new PropertyDescriptor
            .Builder().name("Float precision")
            .displayName("Float precision")
            .description("Number of decimal places for float values")
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.defaultValue(String.valueOf(DEFAULT_FLOAT_PRECISION))
			.addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
			.required(false)
			.build();
	static final Relationship REL_SUCCESS = new Relationship.Builder()
			.description("All FlowFiles that was putted to BloomFilter are routed to this relationship")
			.name("success")
			.build();
	static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure")
			.description("When a flowFile fails it is routed here.")
			.build();

    private List<PropertyDescriptor> properties;
    private Set<Relationship> relationships;
	private Integer floatPrecision;
	private static final int DEFAULT_BUFFER_SIZE = 1024;

    @Override
    protected void init(final ProcessorInitializationContext context) {
		getLogger().debug("Init mask bin data parser processor");
		this.properties = List.of(FLOAT_PRECISION_PROPERTY);
		this.relationships = Set.of(REL_SUCCESS, REL_FAILURE);
    }

	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}

	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}


    @OnScheduled
    public void onScheduled(final ProcessContext context) {
		this.floatPrecision = context.getProperty(FLOAT_PRECISION_PROPERTY).asInteger();
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
		FlowFile flowFile = session.get();
		if ( flowFile == null ) {
			return;
		}
        Map<Byte, String> entryFields = new HashMap<>() {{
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
		List<String> outputJsons = new ArrayList<>();

		session.read(flowFile, new InputStreamCallback() {
			@Override
			public void process(InputStream in) {
				int bufSize = DEFAULT_BUFFER_SIZE;
				try(BufferedInputStream fin = new BufferedInputStream(in, bufSize))
				{
					getLogger().info("New file received");
					getLogger().info(String.format("File size: %d bytes", fin.available()));
					byte[] c = {0, 0, 0, 0};
					int m = 0;
					for(int i = 0; i < 3 && m == 0; i++) {
						int x = fin.read();
						if (x < 0)
							m++;
						c[i] = (byte)(x & 0xff);
					}
					if (m == 0) {
						List<byte[]> entries = new ArrayList<>();
						ByteArrayOutputStream tmpEntry = new ByteArrayOutputStream();
						byte f = 0;
						int x = fin.read();
						while (x != -1) {
							c[3] = (byte)(x & 0xff);
							if (c[0] == (byte)0xF1 && c[1] == (byte)0xAA && c[2] == (byte)0xF0 && c[3] == (byte)0xAA) {
								tmpEntry.write(c[0]);
								tmpEntry.write(c[1]);
								entries.add(tmpEntry.toByteArray());
								tmpEntry.reset();
								f = 1;
							}
							else {
								if (f == 0)
									tmpEntry.write(c[0]);
								else
									f = 0;
							}
							for(int i = 0; i < 4 - 1; i++) {
								c[i] = c[i + 1];
							}
							x = fin.read();
						}
						entries.add(tmpEntry.toByteArray());
						getLogger().info(String.format("Found %d entries", entries.size()));
						for(byte[] entry : entries) {
							int j = 0;
							for(byte b:entry) {
								if ((j < 11) || j > 11 && ((j - 11) % 5 != 0))
									b = reverseByte(b);
								j++;
							}
							int entrySize = ((entry[3] & 0xff) << 8) | (entry[2] & 0xff);
							int entryId = ((entry[6] & 0xff) << 16) | (entry[5] & 0xff) << 8 | (entry[4] & 0xff);
							int entryTimestamp = ((entry[10] & 0xff) << 24) | ((entry[9] & 0xff) << 16) | (entry[8] & 0xff) << 8 | (entry[7] & 0xff);
							getLogger().info(String.format("size = %d, id = %d, time = %d", entrySize, entryId, entryTimestamp));
							ObjectMapper mapper = new ObjectMapper();
							ObjectNode rootNode = mapper.createObjectNode();
							rootNode.put("size", entrySize);
							rootNode.put("uuid", entryId);
							rootNode.put("_time", entryTimestamp);
							for(int i = 11; i <= entry.length - 5 - 2; i += 5) {
								if (entryFields.containsKey(entry[i])) {
									int asInt = (entry[i+1] & 0xFF) | ((entry[i+2] & 0xFF) << 8) | ((entry[i+3] & 0xFF) << 16) | ((entry[i+4] & 0xFF) << 24);
									float asFloat = roundToNDecimalPlaces(Float.intBitsToFloat(asInt), floatPrecision);
									getLogger().info(String.format("%s: %s", entryFields.get(entry[i]), String.format("%." + DEFAULT_FLOAT_PRECISION + "f", asFloat)));
									rootNode.put(entryFields.get(entry[i]), asFloat);
								}
								else
								{
									getLogger().error(String.format("%02X key not found", entry[i]));
								}
							}
							try {
								outputJsons.add(mapper.writer().writeValueAsString(rootNode));
							}
							catch (JsonProcessingException e) {
								getLogger().error("JSON processing error ", e.getMessage());

							}
						}
					}
				}
				catch(Exception e){
					getLogger().error("BIN-file reading error", e.getMessage());
					throw new ProcessException(e);
				}
			}
		});
		
		
		flowFile = session.write(flowFile, new OutputStreamCallback() {
			@Override
			public void process(final OutputStream out) throws IOException {
				if (outputJsons.size() > 0){
					for(String str: outputJsons) {
						out.write(str.getBytes());
						out.write(System.lineSeparator().getBytes());
					}
				}
			}
		});
		session.transfer(flowFile, REL_SUCCESS);
    }

	private static byte reverseByte(byte b) {
		return (byte)(((b << 4) & 0xF0) | ((b >> 4) & 0x0F));
	}

	private static float roundToNDecimalPlaces(float value, int places) {
		if (places < 0) throw new IllegalArgumentException();
		BigDecimal bd = new BigDecimal(Double.toString(value));
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.floatValue();
	}
}
