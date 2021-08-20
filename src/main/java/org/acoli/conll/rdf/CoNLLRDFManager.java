package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class CoNLLRDFManager {
	static Logger LOG = Logger.getLogger(CoNLLRDFManager.class);

	static Map<String,Supplier<? extends CoNLLRDFComponentFactory>> classFactoryMap;
	static {
		classFactoryMap = new HashMap<>();
		classFactoryMap.put(CoNLLStreamExtractor.class.getSimpleName(), () -> new CoNLLStreamExtractorFactory());
		classFactoryMap.put(CoNLLRDFUpdater.class.getSimpleName(), () -> new CoNLLRDFUpdaterFactory());
		classFactoryMap.put(CoNLLRDFFormatter.class.getSimpleName(), () -> new CoNLLRDFFormatterFactory());
			// ObjectNode conf = (ObjectNode) pipelineElement;
			// conf.set("output", config.get("output")); FIXME
		classFactoryMap.put(SimpleLineBreakSplitter.class.getSimpleName(), () -> new SimpleLineBreakSplitterFactory());
	}

	private ObjectNode config;
	private ArrayList<CoNLLRDFComponent> componentStack;

	PrintStream output;
	BufferedReader input;

	public static void main(String[] args) throws IOException {
		final CoNLLRDFManager manager;
		try {
			manager = new CoNLLRDFManagerFactory().buildFromCLI(args);
			manager.buildComponentStack();
		} catch (ParseException e) {
			LOG.error(e);
			System.exit(1);
			return;
		}

		manager.start();
	}

	public void parseConfig(String jsonString) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(Feature.ALLOW_COMMENTS, true);

		JsonNode node = objectMapper.readTree(jsonString);
		if (!node.getNodeType().equals(JsonNodeType.OBJECT)) {
			throw new IOException("File is no valid JSON config.");
		}
		config = (ObjectNode) node;

//		TODO: remove --- Car car = objectMapper.readValue(file, Car.class);
	}

	protected static BufferedReader parseConfAsInputStream(String confEntry) throws IOException {
		BufferedReader input;
		if (confEntry.equals("System.in")) {
			input = new BufferedReader(new InputStreamReader(System.in));
		} else if (new File(confEntry).canRead()) {
			if (confEntry.endsWith(".gz")) {
				input = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(confEntry))));
			} else {
				input = new BufferedReader(new FileReader(confEntry));
			}
		} else {
			throw new IOException("Could not read from " + confEntry);
		}
		return input;
	}

	protected static PrintStream parseConfAsOutputStream(String confEntry) throws IOException {
		PrintStream output;
		if (confEntry.equals("System.out")) {
			output = System.out;
		} else if (new File(confEntry).canWrite()) {
			output = new PrintStream(confEntry);
		} else if (new File(confEntry).createNewFile()) {
			output = new PrintStream(confEntry);
		} else {
			throw new IOException("Could not write to " + confEntry);
		}
		return output;
	}

	public void buildComponentStack() throws IOException, ParseException {
		//READ INPUT PARAMETER
		input = parseConfAsInputStream(config.get("input").asText());

		//READ OUTPUT PARAMETER
		output = parseConfAsOutputStream(config.get("output").asText());

		//READ PIPELINE PARAMETER
		if (!config.get("pipeline").isArray()) {
			throw new IOException("File is no valid JSON config.");
		}

		//BUILD COMPONENT STACK
		if (componentStack == null)
			componentStack = new ArrayList<CoNLLRDFComponent>();
		else
			componentStack.clear();

		// First inputStream is always main input
		BufferedReader nextInput = input;
		// Traverse pipeline array
		for (JsonNode pipelineElement:config.withArray("pipeline")) {
			if (!pipelineElement.getNodeType().equals(JsonNodeType.OBJECT)) {
				throw new IOException("File is no valid JSON config.");
			}

			// Create CoNLLRDFComponents (StreamExtractor, Updater, Formatter ...)
			String className = pipelineElement.get("class").asText();
			if (!classFactoryMap.containsKey(className)) {
				throw new ParseException( "Invalid JSON pipeline. Unknown class: " + className);
			}

			CoNLLRDFComponent component = classFactoryMap.get(className).get().buildFromJsonConf((ObjectNode) pipelineElement);
			componentStack.add(component);

			// Define Pipeline I/O
			// always use previously defined input... first main input, later piped input
			component.setInputStream(nextInput);
			if (componentStack.size() == config.withArray("pipeline").size()) {
				// last component, final output
				component.setOutputStream(output);
			} else {
				// intermediate pipeline to next component (using PipedOutputStream->PipedInputStream)
				PipedOutputStream compOutput = new PipedOutputStream();
				componentStack.get(componentStack.size()-1).setOutputStream(new PrintStream(compOutput));
				nextInput = new BufferedReader(new InputStreamReader(new PipedInputStream(compOutput)));
			}
		}
	}

	public void start() {
		for (CoNLLRDFComponent component:componentStack) {
			Thread t = new Thread(component);
	        t.start();
		}
	}
}
