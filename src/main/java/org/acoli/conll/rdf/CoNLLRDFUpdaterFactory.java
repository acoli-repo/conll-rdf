package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.parseUpdate;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
}
