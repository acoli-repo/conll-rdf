package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.acoli.conll.rdf.CoNLLRDFFormatter.Mode;
import org.acoli.conll.rdf.CoNLLRDFFormatter.Module;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.log4j.Logger;

public class CoNLLRDFManager {
	static Logger LOG = Logger.getLogger(CoNLLRDFManager.class);

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
			CoNLLRDFComponent component;
			if (pipelineElement.get("class").asText().equals(CoNLLStreamExtractor.class.getSimpleName())) {
				component = buildStreamExtractor((ObjectNode) pipelineElement);
			} else if (pipelineElement.get("class").asText().equals(CoNLLRDFUpdater.class.getSimpleName())) {
				component = buildUpdater((ObjectNode) pipelineElement);
			} else if (pipelineElement.get("class").asText().equals(CoNLLRDFFormatter.class.getSimpleName())) {
				component = buildFormatter((ObjectNode) pipelineElement);
			} else if (pipelineElement.get("class").asText().equals(SimpleLineBreakSplitter.class.getSimpleName())) {
				component = buildSimpleLineBreakSplitter((ObjectNode) pipelineElement);
			} else {
				throw new IOException("File is no valid JSON config.");
			}
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


	private CoNLLRDFComponent buildStreamExtractor(ObjectNode conf) throws IOException {
		return new CoNLLStreamExtractorFactory().buildFromJsonConf(conf);
	}

	private CoNLLRDFComponent buildUpdater(ObjectNode conf) throws IOException, ParseException {
		return new CoNLLRDFUpdaterFactory().buildFromJsonConf(conf);
	}

	private CoNLLRDFComponent buildFormatter(ObjectNode conf) throws IOException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatter();
		ObjectMapper mapper = new ObjectMapper();
		if (conf.path("output").isTextual()) {
			PrintStream output = parseConfAsOutputStream(conf.get("output").asText());
			formatter.setOutputStream(output);
		}
		for (JsonNode modConf : conf.withArray("modules")) {
			Mode mode;
			JsonNode columnsArray = null;
			String select = "";
			PrintStream outputStream = null;
			String modeString = modConf.get("mode").asText();
			switch (modeString) {
				case "RDF":
				case "CONLLRDF":
					mode = Mode.CONLLRDF;
					columnsArray = modConf.withArray("columns");
					break;
				case "CONLL":
					mode = Mode.CONLL;
					columnsArray = modConf.withArray("columns");
					break;
				case "DEBUG":
					mode = Mode.DEBUG;
					outputStream = System.err;
					break;
				case "SPARQLTSV":
					// TODO case "QUERY":
					mode = Mode.QUERY;
					select = readString(Paths.get(modConf.get("select").asText()));
					// TODO Attach context to IOExceptions thrown by readString
					break;
				case "GRAMMAR":
					mode = Mode.GRAMMAR;
					break;
				case "SEMANTICS":
					mode = Mode.SEMANTICS;
					break;
				case "GRAMMAR+SEMANTICS":
					mode = Mode.GRAMMAR_SEMANTICS;
					break;

				default:
					throw new IllegalArgumentException("Unknown mode: " + modeString);
			}
			Module module = formatter.addModule(mode);

			// select is either "" or a selectQuery as String
			module.setSelect(select);
			// convert JSON array to Java List
			if (columnsArray != null) {
				List<String> columnList = mapper.convertValue(columnsArray, new TypeReference<List<String>>() {});
				module.setCols(columnList);
			}
			// Set outputStream, if config has a property "output"
			if (modConf.path("output").isTextual()) {
				outputStream = parseConfAsOutputStream(modConf.get("output").asText());
			}
			// outputStream can be null or System.err
			module.setOutputStream(outputStream);
		}
		if (formatter.getModules().size() == 0) {
			formatter.addModule(Mode.CONLLRDF);
		}
		return formatter;
	}

	private CoNLLRDFComponent buildSimpleLineBreakSplitter(ObjectNode conf) {
		return new SimpleLineBreakSplitter();
	}

	public void start() {
		for (CoNLLRDFComponent component:componentStack) {
			Thread t = new Thread(component);
	        t.start();
		}
	}
}
