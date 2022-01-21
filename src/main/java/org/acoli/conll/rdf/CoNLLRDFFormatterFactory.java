package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readString;
import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readUrl;
import static org.acoli.conll.rdf.CoNLLRDFManager.parseConfAsOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.acoli.conll.rdf.CoNLLRDFFormatter.Mode;
import org.acoli.conll.rdf.CoNLLRDFFormatter.Module;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoNLLRDFFormatterFactory extends CoNLLRDFComponentFactory {
	static Logger LOG = LogManager.getLogger(CoNLLRDFFormatterFactory.class);

	@Override
	public CoNLLRDFFormatter buildFromCLI(String[] args) throws IOException, ParseException {
		final CoNLLRDFFormatter formatter = new CoNLLRDFFormatter();
		final CoNLLRDFCommandLine conllCli = new CoNLLRDFCommandLine(
				"CoNLLRDFFormatter [-rdf [COLS]] [-conll COLS] [-debug] [-grammar] [-semantics] [-query SPARQL]",
				"read TTL from stdin => format CoNLL-RDF or extract and highlight CoNLL (namespace conll:) and semantic (namespace terms:) subgraphs\ndefaults to -rdf if no options are selected",
				new Option[] {
						// Define cli options in the correct order for the help-message
						Option.builder("rdf").hasArgs().optionalArg(true)
								.desc("write formatted CoNLL-RDF to stdout (sorted by list of CoNLL COLS, if provided)")
								.build(),
						Option.builder("conll").hasArgs().optionalArg(true)
								.desc("write formatted CoNLL to stdout (only specified COLS)").build(),
						new Option("debug", false, "write formatted, color-highlighted full turtle to stderr"),
						new Option("grammar", false, "write CoNLL data structures to stdout"),
						new Option("semantics", false,
								"write semantic graph to stdout.\nif combined with -grammar, skip type assignments"),
						new Option("query", true, "write TSV generated from SPARQL statement to stdout"),
						new Option("sparqltsv", true, "deprecated: use -query instead") },
				LOG);
		// TODO which args are optional?
		final CommandLine cmd = conllCli.parseArgs(args);

		Module module;

		if (cmd.hasOption("conll")) {
			module = formatter.addModule(Mode.CONLL);
			String[] optionValues = cmd.getOptionValues("conll");
			if (optionValues != null) {
				module.setCols(Arrays.asList(optionValues));
			}
		}
		if (cmd.hasOption("rdf")) {
			module = formatter.addModule(Mode.CONLLRDF);
			String[] optionValues = cmd.getOptionValues("rdf");
			if (optionValues != null) {
				module.setCols(Arrays.asList(optionValues));
			}
		}
		if (cmd.hasOption("debug")) {
			module = formatter.addModule(Mode.DEBUG);
			module.setOutputStream(System.err);
		}

		if (cmd.hasOption("sparqltsv")) {
			LOG.warn("Option -sparqltsv has been deprecated in favor of -query");
			module = formatter.addModule(Mode.QUERY);
			module.setSelect(parseSparqlTSVOptionValues(cmd.getOptionValues("sparqltsv")));
		}
		if (cmd.hasOption("query")) {
			module = formatter.addModule(Mode.QUERY);
			module.setSelect(parseSparqlTSVOptionValues(cmd.getOptionValues("query")));
		}
		if (cmd.hasOption("query") && cmd.hasOption("sparqltsv")) {
			throw new ParseException("Tried to combine deprecated -sparqltsv and -query");
		}

		if (cmd.hasOption("grammar") && !cmd.hasOption("semantics")) {
			module = formatter.addModule(Mode.GRAMMAR);
		}
		if (cmd.hasOption("semantics") && !cmd.hasOption("grammar")) {
			module = formatter.addModule(Mode.SEMANTICS);
		}
		if (cmd.hasOption("semantics") && cmd.hasOption("grammar")) {
			module = formatter.addModule(Mode.GRAMMAR_SEMANTICS);
		}

		// if no modules were added, provide the default option
		if (formatter.getModules().isEmpty()) {
			LOG.info("No Option selected. Defaulting to Mode CoNLL-RDF");
			module = formatter.addModule(Mode.CONLLRDF);
		}

		return formatter;
	}

	static String parseSparqlTSVOptionValues(String[] optionValues) throws IOException, ParseException {
		// FIXME Legacy Code
		final String optionValue;

		if (optionValues.length == 1) {
			optionValue = optionValues[0];
		} else if (optionValues.length == 0) {
			// TODO this code should not be reachable
			throw new ParseException("Option-Value for -sparqltsv is an empty string.");
		} else {
			// because queries may be parsed by the shell (Cygwin)
			optionValue = String.join(" ", optionValues);
		}

		LOG.debug("Parsing Option-Value for -sparqltsv: " + optionValue);

		if (new File(optionValue).exists()) {
			LOG.debug("Attempting to read query from file");
			return readString(Paths.get(optionValue));
		}

		try {
			URL url = new URL(optionValue);
			LOG.debug("Attempting to read query from URL");
			return readUrl(url);
		} catch (MalformedURLException e) {
			LOG.debug(e);
		}

		// TODO consider verifying the output
		LOG.debug("Returning unchanged Option Value as Query");
		return optionValue;
	}

	static String parseQueryOptionValues(String[] optionValues) throws IOException, ParseException {
		final String optionValue;
		LOG.debug("Parsing Option-Value for -query");
		// TODO only URL and File

		if (optionValues.length == 1) {
			optionValue = optionValues[0];
		} else if (optionValues.length == 0) {
			// TODO this code should not be reachable
			optionValue = "";
			return optionValue;
		} else {
			LOG.error("Parsing multiple queries in one operation is not supported at the moment.");
			throw new ParseException("Expected a single file-path or URL as argument for query. Got "
					+ optionValues.length + ":\n" + String.join(" ", optionValues));
		}

		if (new File(optionValue).exists()) {
			LOG.debug("Attempting to read query from file");
			return readString(Paths.get(optionValue));
		}

		try {
			URL url = new URL(optionValue);
			LOG.debug("Attempting to read query from URL");
			return readUrl(url);
		} catch (MalformedURLException e) {
			LOG.debug(e);
		}

		throw new ParseException("Failed to parse Option-Value as file-path or URL: " + optionValue);
	}

	@Override
	public CoNLLRDFFormatter buildFromJsonConf(ObjectNode conf) throws IOException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatter();

		if (conf.path("output").isTextual()) {
			PrintStream output = parseConfAsOutputStream(conf.get("output").asText());
			formatter.setOutputStream(output);
		}
		for (JsonNode modConf : conf.withArray("modules")) {
			addModule(formatter, modConf);
		}
		if (formatter.getModules().size() == 0) {
			formatter.addModule(Mode.CONLLRDF);
		}
		return formatter;
	}

	private Module addModule(CoNLLRDFFormatter formatter, JsonNode modConf)
			throws IOException {
		ObjectMapper mapper = new ObjectMapper();

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
				LOG.warn("Mode SPARQLTSV is deprecated, please use QUERY instead.");
			case "QUERY":
				mode = Mode.QUERY;
				// TODO check URI
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
		return module;
	}
}
