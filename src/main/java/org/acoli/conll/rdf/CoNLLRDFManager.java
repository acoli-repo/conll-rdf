package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoNLLRDFManager {
	static Logger LOG = LogManager.getLogger(CoNLLRDFManager.class);

	static Map<String,Supplier<? extends CoNLLRDFComponentFactory>> classFactoryMap;
	static {
		classFactoryMap = new HashMap<>();
		classFactoryMap.put(CoNLLStreamExtractor.class.getSimpleName(), () -> new CoNLLStreamExtractorFactory());
		classFactoryMap.put(CoNLLRDFUpdater.class.getSimpleName(), () -> new CoNLLRDFUpdaterFactory());
		classFactoryMap.put(CoNLLRDFFormatter.class.getSimpleName(), () -> new CoNLLRDFFormatterFactory());
			// conf.set("output", config.get("output")); FIXME
		classFactoryMap.put(SimpleLineBreakSplitter.class.getSimpleName(), () -> new SimpleLineBreakSplitterFactory());
	}

	private InputStream input;
	private OutputStream output;
	private JsonNode[] pipeline;
	private JsonNode config;
	private ArrayList<CoNLLRDFComponent> componentStack = new ArrayList<CoNLLRDFComponent>();

	public InputStream getInput() {
		return input;
	}

	public void setInput(InputStream input) {
		this.input = input;
	}

	public OutputStream getOutput() {
		return output;
	}

	public void setOutput(OutputStream output) {
		this.output = output;
	}

	public JsonNode[] getPipeline() {
		return pipeline;
	}

	public void setPipeline(JsonNode[] pipeline) {
		this.pipeline = pipeline;
	}

	public JsonNode getConfig() {
		return config;
	}

	public void setConfig(JsonNode config) {
		this.config = config;
	}

	ArrayList<CoNLLRDFComponent> getComponentStack() {
		return componentStack;
	}

	void setComponentStack(ArrayList<CoNLLRDFComponent> componentStack) {
		this.componentStack = componentStack;
	}	

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

	protected static InputStream parseConfAsInputStream(String confEntry) throws IOException {
		InputStream input;
		if (confEntry == null) {
			throw new IllegalArgumentException();
		} else if (confEntry.equals("System.in")) {
			input = System.in;
		} else if (new File(confEntry).canRead()) {
			if (confEntry.endsWith(".gz")) {
				input = new GZIPInputStream(new FileInputStream(confEntry));
			} else {
				input = new FileInputStream(confEntry);
			}
		} else {
			throw new IOException("Could not read from " + confEntry);
		}
		return input;
	}

	protected static PrintStream parseConfAsOutputStream(String confEntry) throws IOException {
		PrintStream output;
		if (confEntry == null) {
			throw new IllegalArgumentException();
		} else if (confEntry.equals("System.out")) {
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
		//READ PIPELINE PARAMETER
		/*
		JsonNode pipelineNode = config.get("pipeline");
		if (!pipelineNode.isArray()) {
			throw new IOException("File is no valid JSON config.");
		}
		componentStack.clear();

		JsonNode[] pipelineArray = new JsonNode[] {config.withArray("pipeline")};

		componentStack = parsePipeline(pipelineArray);
		*/
		linkComponents(componentStack, input, output);
	}

	static ArrayList<CoNLLRDFComponent> parsePipeline(Iterable<JsonNode> pipelineArray) throws IOException, ParseException {
		ArrayList<CoNLLRDFComponent> componentArray = new ArrayList<>();

		for (JsonNode pipelineElement:pipelineArray) {
			if (!pipelineElement.getNodeType().equals(JsonNodeType.OBJECT)) {
				throw new IllegalArgumentException("Elements of \"pipeline\" have to be obejct-type");
			}

			// Create CoNLLRDFComponents (StreamExtractor, Updater, Formatter ...)
			String className = pipelineElement.required("class").asText();
			if (!classFactoryMap.containsKey(className)) {
				throw new IllegalArgumentException( "Unknown class: " + className);
			}

			CoNLLRDFComponent component = classFactoryMap.get(className).get().buildFromJsonConf((ObjectNode) pipelineElement);
			componentArray.add(component);
		}
		return componentArray;
	}

	/**
	 * Link all components using Piped Streams, and set Pipeline I/O.
	 * @param componentArray The List of components to be linked.
	 * @param input Link this to the first component
	 * @param output Link last component to this.
	 */
	static void linkComponents(List<CoNLLRDFComponent> componentArray, InputStream input, OutputStream output) throws IOException {
		CoNLLRDFComponent prevComponent = null;
		for (CoNLLRDFComponent component : componentArray) {
			if (prevComponent == null) {
				// link input to first component
				component.setInputStream(input);
			} else {
				// prepare piped Streams
				PipedOutputStream pipedOutput = new PipedOutputStream();
				PipedInputStream pipedInput = new PipedInputStream(pipedOutput);
				// link previous component to this one
				prevComponent.setOutputStream(new PrintStream(pipedOutput));
				component.setInputStream(pipedInput);
			}
			prevComponent = component;
		}
		// link last component to output
		prevComponent.setOutputStream(output);
	}

	public void start() {
		for (CoNLLRDFComponent component:componentStack) {
			Thread t = new Thread(component);
	        t.start();
		}
	}
}
