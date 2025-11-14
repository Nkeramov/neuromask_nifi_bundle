package com.nifi.neuromask.processors;

import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


@Tags({"neuromask", "bin", "parser"})
@CapabilityDescription("Parser for neuromask bin packets")
@WritesAttributes({
		@WritesAttribute(attribute = "mime.type", description = "application/json"),
		@WritesAttribute(attribute = NeuromaskBinParserProcessor.RECORD_COUNT, description = "contains the number of records retrieved from the file")
})
public class NeuromaskBinParserProcessor extends AbstractProcessor {

	public final static String RECORD_COUNT = "record.count";

    static final PropertyDescriptor FLOAT_PRECISION_PROPERTY = new PropertyDescriptor
            .Builder().name("Float precision")
            .displayName("Float precision")
            .description("Number of decimal places for float values")
			.expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
			.defaultValue(String.valueOf(NeuromaskBinParser.DEFAULT_FLOAT_PRECISION))
			.addValidator(StandardValidators.NON_NEGATIVE_INTEGER_VALIDATOR)
			.required(false)
			.build();

	static final PropertyDescriptor INCLUDE_ZERO_RECORD_FLOWFILES = new PropertyDescriptor.Builder()
			.name("include-zero-record-flowfiles")
			.displayName("Include Zero Record FlowFiles")
			.description("When converting an incoming FlowFile, if the conversion results in no data, "
					+ "this property specifies whether or not a FlowFile will be sent to the corresponding relationship")
			.expressionLanguageSupported(ExpressionLanguageScope.NONE)
			.allowableValues("true", "false")
			.defaultValue("true")
			.required(true)
			.build();

	static final Relationship REL_SUCCESS = new Relationship.Builder()
			.description("All FlowFiles that was successfully converted to JSON-format are routed to this relationship")
			.name("success")
			.build();
	static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure")
			.description("When a flowFile fails it is routed here.")
			.build();

	private final static List<PropertyDescriptor> propertyDescriptors;

	private final static Set<Relationship> relationships;

    static  {
		propertyDescriptors = List.of(FLOAT_PRECISION_PROPERTY, INCLUDE_ZERO_RECORD_FLOWFILES);

		relationships = Set.of(REL_SUCCESS, REL_FAILURE);
    }

	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return propertyDescriptors;
	}

	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}

    @Override
	public final void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
		FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}
		final boolean includeZeroRecordFlowFiles = context.getProperty(INCLUDE_ZERO_RECORD_FLOWFILES).isSet()? context.getProperty(INCLUDE_ZERO_RECORD_FLOWFILES).asBoolean():true;
		final int floatPrecision = context.getProperty(FLOAT_PRECISION_PROPERTY).evaluateAttributeExpressions(flowFile).asInteger();
		final AtomicInteger recordCount = new AtomicInteger(0);
		final NeuromaskBinParser parser = new NeuromaskBinParser.Builder()
				.withLogger(getLogger())
				.withFloatPrecision(floatPrecision)
				.build();
		try {
			FlowFile resultFlowFile = session.write(flowFile, (in, out) -> {
				try (BufferedInputStream bufferedIn = new BufferedInputStream(in, parser.getBufferSize());
					 BufferedWriter writer = new BufferedWriter(
							 new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
					List<String> parsedRecords = parser.parseStream(bufferedIn);
					recordCount.set(parsedRecords.size());
					for (String record : parsedRecords) {
						writer.write(record);
						writer.newLine();
					}
				} catch (IOException e) {
					throw new ProcessException("Failed to process stream", e);
				}
			});
			resultFlowFile = session.putAttribute(resultFlowFile, RECORD_COUNT, String.valueOf(recordCount.get()));
			resultFlowFile = session.putAttribute(resultFlowFile, CoreAttributes.MIME_TYPE.key(), "application/json");
			if (recordCount.get() > 0 || includeZeroRecordFlowFiles) {
				session.transfer(resultFlowFile, REL_SUCCESS);
				getLogger().info("Successfully converted {} records for {}", recordCount.get(), resultFlowFile);
			} else {
				session.remove(resultFlowFile);
				getLogger().info("No records converted, flowfile removed");
			}
		} catch (ProcessException e) {
			getLogger().error("Failed to process {}; will route to failure", flowFile, e);
			session.transfer(flowFile, REL_FAILURE);
		}
    }
}
