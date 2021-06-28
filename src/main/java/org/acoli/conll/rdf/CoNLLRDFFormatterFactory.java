package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readString;
import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readUrl;
import static org.acoli.conll.rdf.CoNLLRDFManager.parseConfAsOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.acoli.conll.rdf.CoNLLRDFFormatter.Mode;
import org.acoli.conll.rdf.CoNLLRDFFormatter.Module;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class CoNLLRDFFormatterFactory {
	static Logger LOG = Logger.getLogger(CoNLLRDFFormatterFactory.class);

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
			module = new Module();
			module.setMode(Mode.CONLL);
			module.setOutputStream(formatter.getOutputStream());
			String[] optionValues = cmd.getOptionValues("conll");
			if (optionValues != null) {
				module.setCols(Arrays.asList(optionValues));
			}
			formatter.addModule(module);
		}
		if (cmd.hasOption("rdf")) {
			module = new Module();
			module.setMode(Mode.CONLLRDF);
			module.setOutputStream(formatter.getOutputStream());
			String[] optionValues = cmd.getOptionValues("rdf");
			if (optionValues != null) {
				module.setCols(Arrays.asList(optionValues));
			}
			formatter.addModule(module);
		}
		if (cmd.hasOption("debug")) {
			module = new Module();
			module.setMode(Mode.DEBUG);
			module.setOutputStream(System.err);
			formatter.addModule(module);
		}

		if (cmd.hasOption("sparqltsv")) {
			LOG.warn("Option -sparqltsv has been deprecated in favor of -query");
			module = new Module();
			module.setMode(Mode.QUERY);
			module.setOutputStream(formatter.getOutputStream());
			module.setSelect(parseSparqlTSVOptionValues(cmd.getOptionValues("sparqltsv")));
			formatter.addModule(module);
		}
		if (cmd.hasOption("query")) {
			module = new Module();
			module.setMode(Mode.QUERY);
			module.setOutputStream(formatter.getOutputStream());
			module.setSelect(parseSparqlTSVOptionValues(cmd.getOptionValues("query")));
			formatter.addModule(module);
		}
		if (cmd.hasOption("query") && cmd.hasOption("sparqltsv")) {
			throw new ParseException("Tried to combine deprecated -sparqltsv and -query");
		}

		if (cmd.hasOption("grammar") && !cmd.hasOption("semantics")) {
			module = new Module();
			module.setMode(Mode.GRAMMAR);
			module.setOutputStream(formatter.getOutputStream());
			formatter.addModule(module);
		}
		if (cmd.hasOption("semantics") && !cmd.hasOption("grammar")) {
			module = new Module();
			module.setMode(Mode.SEMANTICS);
			module.setOutputStream(formatter.getOutputStream());
			formatter.addModule(module);
		}
		if (cmd.hasOption("semantics") && cmd.hasOption("grammar")) {
			module = new Module();
			module.setMode(Mode.GRAMMAR_SEMANTICS);
			module.setOutputStream(formatter.getOutputStream());
			formatter.addModule(module);
		}

		// if no modules were added, provide the default option
		if (formatter.getModules().isEmpty()) {
			LOG.info("No Option selected. Defaulting to Mode CoNLL-RDF");
			module = new Module();
			module.setMode(Mode.CONLLRDF);
			module.setOutputStream(formatter.getOutputStream());
			formatter.addModule(module);
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

	public CoNLLRDFFormatter buildFromJsonConfig(ObjectNode conf) throws IOException {
		CoNLLRDFFormatter f = new CoNLLRDFFormatter();
		PrintStream output = parseConfAsOutputStream(conf.get("output").asText());

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
				m.setMode(Mode.QUERY);
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
}
