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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.acoli.conll.rdf.CoNLLRDFFormatter.Mode;
import org.acoli.conll.rdf.CoNLLRDFFormatter.Module;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

public class CoNLLRDFManager {
	private ObjectNode config;
	private ArrayList<CoNLLRDFComponent> componentStack;

	PrintStream output;
	BufferedReader input;

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addRequiredOption("c", "config", true, "Specify JSON config file");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		CoNLLRDFManager man = new CoNLLRDFManager();

		if(cmd.hasOption("c")) {
			try {
				man.readConfig(cmd.getOptionValue("c"));
			} catch (IOException e) {
				throw new Exception("Error when reading config file "+new File(cmd.getOptionValue("c")).getAbsolutePath(), e);
			}
		}
		else {
		    throw new ParseException("No config file specified.");
		}

		man.buildComponentStack();
		man.start();
	}


	public void readConfig(String path) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(Feature.ALLOW_COMMENTS, true);

		File file = new File(path);
		if (!file.canRead()) {
			throw new IOException("File cannot be read.");
		}
		JsonNode node = objectMapper.readTree(file);
		if (!node.getNodeType().equals(JsonNodeType.OBJECT)) {
			throw new IOException("File is no valid JSON config.");
		}
		config = (ObjectNode) node;

//		TODO: remove --- Car car = objectMapper.readValue(file, Car.class);
	}

	private BufferedReader parseConfAsInputStream(String confEntry) throws IOException {
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

	private PrintStream parseConfAsOutputStream(String confEntry) throws IOException {
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

	public void buildComponentStack() throws IOException {
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
		CoNLLStreamExtractor ex = new CoNLLStreamExtractor();
		ex.setBaseURI(conf.get("baseURI").asText());
		ex.getColumns().clear();
		//TODO: DONE------TEST
		for (JsonNode col:conf.withArray("columns")) {
			ex.getColumns().add(col.asText());
		}

		return ex;
	}

	private CoNLLRDFComponent buildUpdater(ObjectNode conf) throws IOException {

		// READ THREAD PARAMETERS
		int threads = 0;
		if (conf.get("threads") != null)
			threads = conf.get("threads").asInt(0);
		CoNLLRDFUpdater updater = new CoNLLRDFUpdater("","",threads);

		// READ GRAPHSOUT PARAMETERS
		if (conf.get("graphsoutDIR") != null) {
			String graphOutputDir = conf.get("graphsoutDIR").asText("");
			if (!graphOutputDir.equals("")) {
				List<String> graphOutputSentences = new ArrayList<String>();
				for (JsonNode snt:conf.withArray("graphsoutSNT")) {
					graphOutputSentences.add(snt.asText());
				}
				updater.activateGraphsOut(graphOutputDir, graphOutputSentences);
			}
		}

		// READ TRIPLESOUT PARAMETERS
		if (conf.get("triplesoutDIR") != null) {
			String triplesOutputDir = conf.get("triplesoutDIR").asText("");
			if (!triplesOutputDir.equals("")) {
				List<String> triplesOutputSentences = new ArrayList<String>();
				for (JsonNode snt:conf.withArray("triplesoutSNT")) {
					triplesOutputSentences.add(snt.asText());
				}
				updater.activateTriplesOut(triplesOutputDir, triplesOutputSentences);
			}
		}

		// READ LOOKAHEAD PARAMETERS
		if (conf.get("lookahead") != null) {
			int lookahead_snts = conf.get("lookahead").asInt(0);
			if (lookahead_snts > 0)
				updater.activateLookahead(lookahead_snts);
		}

		// READ LOOKBACK PARAMETERS
		if (conf.get("lookback") != null) {
			int lookback_snts = conf.get("lookback").asInt(0);
			if (lookback_snts > 0)
				updater.activateLookback(lookback_snts);
		}

		// READ PREFIX DEDUPLICATION
		if (conf.get("prefixDeduplication") != null) {
			Boolean prefixDeduplication = conf.get("prefixDeduplication").asBoolean();
			if (prefixDeduplication)
				updater.activateRemovePrefixDuplicates();
		}

		// READ ALL UPDATES
		// should be <#UPDATEFILENAMEORSTRING, #UPDATESTRING, #UPDATEITER>
		List<Triple<String, String, String>> updates = new ArrayList<Triple<String, String, String>>();
		for (JsonNode update:conf.withArray("updates")) {
			String freq = update.get("iter").asText("1");
			if (freq.equals("u"))
				freq = "*";
			try {
				Integer.parseInt(freq);
			} catch (NumberFormatException e) {
				if (!"*".equals(freq))
					throw e;
			}
			String path = update.get("path").asText();
			updates.add(new ImmutableTriple<String, String, String>(path, path, freq));
		}
		updater.parseUpdates(updates);

		// READ ALL MODELS
		for (JsonNode model:conf.withArray("models")) {
			List<String> models = new ArrayList<String>();
			String uri = model.get("source").asText();
			if (!uri.equals("")) models.add(uri);
			uri = model.get("graph").asText();
			if (!uri.equals("")) models.add(uri);
			if (models.size()==1) {
				try {
					updater.loadGraph(new URI(models.get(0)), new URI(models.get(0)));
				} catch (URISyntaxException e) {
					throw new IOException(e);
				}
			} else if (models.size()==2){
				try {
					updater.loadGraph(new URI(models.get(0)), new URI(models.get(1)));
				} catch (URISyntaxException e) {
					throw new IOException(e);
				}
			} else if (models.size()>2){
				throw new IOException("Error while loading model: Please specify model source URI and graph destination.");
			}
			models.removeAll(models);
		}

		return updater;
	}

	private CoNLLRDFComponent buildFormatter(ObjectNode conf) throws IOException {
		CoNLLRDFFormatter f = new CoNLLRDFFormatter();

		if (conf.withArray("modules").size() <= 0) {
			Module m = new Module();
			m.setMode(Mode.CONLLRDF);
			m.setOutputStream(output);
			m.getCols().clear();
			f.getModules().add(m);
		}
		for (JsonNode modConf:conf.withArray("modules")) {
			Module m = new Module();
			try {
				m.setOutputStream(parseConfAsOutputStream(modConf.get("output").asText()));
			} catch (Exception e) {
				m.setOutputStream(output);
			}
			if (modConf.get("mode").asText().equals("RDF") || modConf.get("mode").asText().equals("CONLLRDF")) {
				m.setMode(Mode.CONLLRDF);
				m.getCols().clear();
				for (JsonNode col:modConf.withArray("columns")) {
					m.getCols().add(col.asText());
				}
				f.getModules().add(m);
			}
			if (modConf.get("mode").asText().equals("CONLL")) {
				m.setMode(Mode.CONLL);
				m.getCols().clear();
				for (JsonNode col:modConf.withArray("columns")) {
					m.getCols().add(col.asText());
				}
				f.getModules().add(m);
			}
			if (modConf.get("mode").asText().equals("DEBUG")) {
				m.setMode(Mode.DEBUG);
				m.setOutputStream(System.err);
				f.getModules().add(m);
			}
			if (modConf.get("mode").asText().equals("SPARQLTSV")) {
				m.setMode(Mode.SPARQLTSV);
				if (new File(modConf.get("select").asText()).canRead()) {
					BufferedReader in = new BufferedReader(new FileReader(modConf.get("select").asText()));
					String select="";
					for(String line = in.readLine(); line!=null; line=in.readLine())
						select=select+line+"\n";
					m.setSelect(select);
					in.close();
					f.getModules().add(m);
				} else {
					throw new IOException("Could not read from " + modConf.get("select").asText());
				}
			}
			if (modConf.get("mode").asText().equals("GRAMMAR")) {
				m.setMode(Mode.GRAMMAR);
				f.getModules().add(m);
			}
			if (modConf.get("mode").asText().equals("SEMANTICS")) {
				m.setMode(Mode.SEMANTICS);
				f.getModules().add(m);
			}
			if (modConf.get("mode").asText().equals("GRAMMAR+SEMANTICS")) {
				m.setMode(Mode.GRAMMAR_SEMANTICS);
				f.getModules().add(m);
			}
		}
		return f;
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
