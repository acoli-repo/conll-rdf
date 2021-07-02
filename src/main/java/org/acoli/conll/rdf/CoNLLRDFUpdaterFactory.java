package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.parseUpdate;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.log4j.Logger;

public class CoNLLRDFUpdaterFactory {
	static Logger LOG = Logger.getLogger(CoNLLRDFUpdaterFactory.class);

	public CoNLLRDFUpdater buildFromCLI(String[] args) throws IOException, ParseException {
		CoNLLRDFUpdater updater = new CoNLLRDFUpdater();
		final CommandLine cmd = new CoNLLRDFCommandLine(
				"CoNLLRDFUpdater [-loglevel LEVEL] [-threads T] [-lookahead N] [-lookback N] [-custom [-model URI [GRAPH]]* [-graphsout DIR [SENT_ID ...]] [-triplesout DIR [SENT_ID ...]] -updates [UPDATE ...]]",
				"read TTL from stdin => update CoNLL-RDF", new Option[] {
						// Define cli options in the correct order for the help-message
						Option.builder("loglevel").hasArg().desc("set log level to LEVEL").argName("level").build(),
						Option.builder("threads").hasArg()
								.desc("use T threads max\ndefault: half of available logical processor cores")
								.type(Number.class).build(),
						Option.builder("lookahead").hasArg().desc("cache N further sentences in lookahead graph")
								.type(Number.class).build(),
						Option.builder("lookback").hasArg().desc("cache N preceeding sentences in lookback graph")
								.type(Number.class).build(),
						new Option("prefixDeduplication", false, "Remove duplicates of TTL-Prefixes"),
						Option.builder("custom").hasArg(false).desc("use custom update scripts")
								./* required(). */build(),
						Option.builder("model").hasArgs().desc("to load additional Models into local graph").build(),
						Option.builder("graphsout").hasArgs().desc(
								"output directory for the .dot graph files\nfollowed by the IDs of the sentences to be visualized\ndefault: first sentence only")
								.build(),
						Option.builder("triplesout").hasArgs()
								.desc("same as graphsout but write N-TRIPLES for text debug instead.").build(),
						Option.builder("updates").hasArgs()
								.desc("followed by SPARQL scripts paired with {iterations/u}").build() },
				CoNLLRDFUpdater.LOG).parseArgs(args);

		if (cmd.hasOption("threads")) {
			updater.setThreads(((Number) cmd.getParsedOptionValue("threads")).intValue());
		}
		if (cmd.hasOption("lookahead")) {
			updater.activateLookahead(((Number) cmd.getParsedOptionValue("lookahead")).intValue());
		}
		if (cmd.hasOption("lookback")) {
			updater.activateLookback(((Number) cmd.getParsedOptionValue("lookback")).intValue());
		}
		if (cmd.hasOption("prefixDeduplication")) {
			updater.activatePrefixDeduplication();
		}
		// READ GRAPHSOUT PARAMETERS
		if (cmd.hasOption("graphsout")) {
			String[] graphsoutArgs = cmd.getOptionValues("graphsout");
			String outputDir = graphsoutArgs[0];
			List<String> outputSentences = Arrays.asList(Arrays.copyOfRange(graphsoutArgs, 1, graphsoutArgs.length));
			updater.activateGraphsOut(outputDir, outputSentences);
		}
		// READ TRIPLESOUT PARAMETERS
		if (cmd.hasOption("triplesout")) {
			String[] triplesoutArgs = cmd.getOptionValues("triplesout");
			String outputDir = triplesoutArgs[0];
			List<String> outputSentences = Arrays.asList(Arrays.copyOfRange(triplesoutArgs, 1, triplesoutArgs.length));
			updater.activateTriplesOut(outputDir, outputSentences);
		}

		if (cmd.hasOption("model")) {
			for (Option opt : cmd.getOptions()) {
				if (opt.getOpt().equals("model")) { // opt.equals(model)
					String[] model = opt.getValues();
					try {
						if (model.length == 1) {
							updater.loadGraph(new URI(model[0]), new URI(model[0]));
						} else if (model.length == 2) {
							updater.loadGraph(new URI(model[0]), new URI(model[1]));
						} else {
							throw new ParseException("Error while loading model: Please provide one or two URIs");
						}
					} catch (URISyntaxException e) {
						throw new ParseException("Error while loading model: Could not parse given arguments as URI");
					}
				}
			}
		}

		if (cmd.hasOption("updates")) {
			List<Triple<String, String, String>> updates = new ArrayList<>();
			for (String arg : Arrays.asList(cmd.getOptionValues("updates"))) {
				Pair<String, String> parsed = parseUpdate(arg);
				// should be <#UPDATEFILENAMEORSTRING, #UPDATESTRING, #UPDATEITER>
				updates.add(new ImmutableTriple<String, String, String>(parsed.getKey(), parsed.getKey(),
						parsed.getValue()));
			}
			updater.parseUpdates(updates);
		}
		return updater;
	}

	public CoNLLRDFUpdater buildFromJsonConf(ObjectNode conf) throws IOException, ParseException {
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
				updater.activatePrefixDeduplication();
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
}
