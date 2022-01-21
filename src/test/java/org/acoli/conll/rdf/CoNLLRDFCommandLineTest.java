package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CoNLLRDFCommandLineTest {
	private static final Logger LOG = LogManager.getLogger(CoNLLRDFCommandLineTest.class);

	@Test
	void silentOption() throws ParseException {
		Logger logger = LogManager.getLogger("silentOption");
		CoNLLRDFCommandLine conllCli = new CoNLLRDFCommandLine("syntax", "description", new Option[] {}, logger);

		conllCli.parseArgs(new String[] { "-silent" });

		assertEquals(Level.WARN, logger.getLevel());
	}

	@Test
	@Disabled("Unimplemented test")
	void helpOption() throws ParseException {
		// TODO implement test
	}

	@Test
	public void parseUpdateArg() throws IOException, ParseException {
		assertEquals(new ImmutablePair<String, String>("aaa", "1"), parseUpdate("aaa"));
		assertEquals(new ImmutablePair<String, String>("aaa", "1"), parseUpdate("aaa{1}"));
		assertEquals(new ImmutablePair<String, String>("aaa", "*"), parseUpdate("aaa{*}"));
		assertEquals(new ImmutablePair<String, String>("aaa", "*"), parseUpdate("aaa{u}"));
	}

	@Test
	public void readSparqlFileArg() throws IOException {
		selectQueryFromLiteral(readSparqlFile("examples/sparql/select-sentence-strings.sparql"));
		readSparqlSelect("examples/sparql/select-sentence-strings.sparql");
		selectQueryFromLiteral(readUrl(new URL(
				"https://raw.githubusercontent.com/acoli-repo/conll-rdf/master/examples/sparql/select-sentence-strings.sparql")));
		readSparqlSelect(
				"https://raw.githubusercontent.com/acoli-repo/conll-rdf/master/examples/sparql/select-sentence-strings.sparql");
	}

	@Test
	public void preserveOrder() {
		Options optionsA = new Options().addOption("help", false, "print this help message and exit")
				.addOption("silent", false, "supress help message and logging of info messages");
		Options optionsB = new Options()
				.addOption(Option.builder("rdf").hasArgs().optionalArg(true)
						.desc("write formatted CoNLL-RDF to stdout (sorted by list of CoNLL COLS, if provided)")
						.build())
				.addOption(Option.builder("conll").hasArgs().optionalArg(true)
						.desc("write formatted CoNLL to stdout (only specified COLS)").build())
				.addOption("debug", false, "write formatted, color-highlighted full turtle to stderr")
				.addOption("grammar", false, "write CoNLL data structures to stdout")
				.addOption("semantics", false, "write semantic graph to stdout").addOption("sparqltsv", true,
						"write TSV generated from SPARQL statement to stdout.\nif with -grammar, then skip type assignments");
		Iterator<Option> it = optionsB.getOptions().iterator();
		while (it.hasNext()) {
			optionsA.addOption(it.next());
		}
		LOG.debug(optionsA.toString());
	}

	@Test
	public void multiOptions() throws ParseException {
		Option model = Option.builder("model").hasArg().hasArgs().build();
		final Options options = new Options().addOption(model).addOption("dummy", false, "description");
		final CoNLLRDFCommandLine conllCli = new CoNLLRDFCommandLine("syntax", "description", options, LOG);
		CommandLine cmd = conllCli.parseArgs(new String[] { "-model", "a", "b", "-model", "c", "-dummy" });

		List<String[]> thatOption = new ArrayList<String[]>();
		for (Option opt : cmd.getOptions()) {
			LOG.debug("opt: " + opt.getOpt());
			LOG.debug("equals: " + opt.equals(model));
			if (opt.getOpt().equals("model")) {
				LOG.debug(Arrays.asList(opt.getValues()).toString());
				thatOption.add(opt.getValues());
			}
		}
		LOG.debug(thatOption.size());
		LOG.debug(Arrays.asList(thatOption).toString());
	}
}
