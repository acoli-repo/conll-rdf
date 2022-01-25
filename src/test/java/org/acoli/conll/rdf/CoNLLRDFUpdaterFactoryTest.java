package org.acoli.conll.rdf;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CoNLLRDFUpdaterFactoryTest {
	// throw ParseException if no arguments are provided
	@Test
	void noOption() throws IOException, ParseException {
		assertThrows(ParseException.class, () -> {
			new CoNLLRDFManagerFactory().buildFromCLI(new String[] {});
		});
	}

	// loglevel
	@Test
	void setLoglevel() throws IOException, ParseException {
		new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-loglevel", "TRACE" });
		assertEquals(Level.TRACE, CoNLLRDFUpdater.LOG.getLevel());
		new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-loglevel", "DEBUG" });
		assertEquals(Level.DEBUG, CoNLLRDFUpdater.LOG.getLevel());
	}

	@Disabled("log4j Level.toLevel method defaults to DEBUG. No Exception is thrown.")
	@Test
	void invalidLoglevel() throws IOException, ParseException {
		assertThrows(ParseException.class, () -> {
			new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-loglevel", "FOO" });
		});
	}

	// threads
	@Test
	void setThreads() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-threads", "9" });
		assertEquals(9, updater.getThreads());
	}

	// lookahead
	@Test
	void setLookahead() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-lookahead", "4" });
		assertEquals(4, updater.getLookahead());
	}

	// lookback
	@Test
	void setLookback() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-lookback", "7" });
		assertEquals(7, updater.getLookback());
	}

	// prefixDeduplication
	@Test
	void setPrefixDeduplication() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-prefixDeduplication" });
		assertEquals(true, updater.getPrefixDeduplication());
	}

	@Test
	void unsetPrefixDeduplication() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] {});
		assertEquals(false, updater.getPrefixDeduplication());
	}

	// custom
	@Test
	void setCustom() throws IOException, ParseException {
		new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-custom" });
	}

	// model
	@Test
	void setModel() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-model", "http://purl.org/olia/penn.owl" });
		assertTrue(updater.hasGraph("http://purl.org/olia/penn.owl"));
	}

	@Test
	void setModelWithName() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-model", "http://purl.org/olia/penn.owl", "http://localhost" });
		assertTrue(updater.hasGraph("http://localhost"));
	}

	// TODO test with model from local File
	/* @Test
	void setModelFromFile() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.getUpdater(new String[] { "-model", "http://purl.org/olia/penn.owl", "http://localhost" });
		assertTrue(updater.hasGraph("http://localhost"));
	}*/

	// graphsout
	// TODO use junit5's @TempDir for the tests producing files
	@Test
	void setGraphsout() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-graphsout", "graphsdir", "sentence_id1" });
		assertEquals(new File("graphsdir"), updater.getGraphOutputDir());
		assertArrayEquals(new String[] { "sentence_id1" }, updater.getGraphOutputSentences());
	}

	@Test
	void setManyGraphsout() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-graphsout", "graphsdir", "sentence_id1", "sentence_id2" });
		assertArrayEquals(new String[] { "sentence_id1", "sentence_id2" }, updater.getGraphOutputSentences());
	}

	@Test
	void setGraphsoutWithoutID() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-graphsout", "graphsdir" });
		assertArrayEquals(new String[] {}, updater.getGraphOutputSentences());
		assertNotNull(updater.getGraphOutputDir());
		// Stream<String> inputStream = Stream.of("");
		String rdfSentence = "@prefix : <https://github.com/acoli-repo/conll-rdf#> ."
				+ "\n@prefix nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> ."
				+ "\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ." + "\n:s1_0 a nif:Sentence ."
				+ "\n:s2_0 a nif:Sentence .";
		updater.setInputStream(IOUtils.toInputStream(rdfSentence, "UTF-8"));
		updater.processSentenceStream();
		assertArrayEquals(new String[] { "s1_0" }, updater.getGraphOutputSentences());
	}

	// triplesout
	@Test
	void setTriplesout() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-triplesout", "triplesdir", "sentence_id1" });
		assertEquals(new File("triplesdir"), updater.getTriplesOutputDir());
		assertArrayEquals(new String[] { "sentence_id1" }, updater.getTriplesOutputSentences());
	}

	@Test
	void setManyTriplesout() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-triplesout", "triplesdir", "sentence_id1", "sentence_id2" });
		assertArrayEquals(new String[] { "sentence_id1", "sentence_id2" }, updater.getTriplesOutputSentences());
	}

	@Test
	void setTriplesoutWithoutID() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-triplesout", "triplesdir" });
		assertArrayEquals(new String[] {}, updater.getTriplesOutputSentences());
		assertNotNull(updater.getTriplesOutputDir());
		// Stream<String> inputStream = Stream.of("");
		String rdfSentence = "@prefix : <https://github.com/acoli-repo/conll-rdf#> ."
				+ "\n@prefix nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> ."
				+ "\n@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ." + "\n:s1_0 a nif:Sentence ."
				+ "\n:s2_0 a nif:Sentence .";
		updater.setInputStream(IOUtils.toInputStream(rdfSentence, "UTF-8"));
		updater.processSentenceStream();
		assertArrayEquals(new String[] { "s1_0" }, updater.getTriplesOutputSentences());
	}

	// updates
	@Test
	void setUpdate() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory()
				.buildFromCLI(new String[] { "-updates", "examples/sparql/remove-IGNORE.sparql" });
		assertArrayEquals(new String[] { "examples/sparql/remove-IGNORE.sparql" }, updater.getUpdateNames());
		assertArrayEquals(new String[] { "1" }, updater.getUpdateMaxIterations());
	}

	@Test
	void setUpdates() throws IOException, ParseException {
		final CoNLLRDFUpdater updater = new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-updates",
				"examples/sparql/remove-IGNORE.sparql{u}", "examples/sparql/remove-ID.sparql" });
		assertArrayEquals(new String[] { "examples/sparql/remove-IGNORE.sparql", "examples/sparql/remove-ID.sparql" },
				updater.getUpdateNames());
		assertArrayEquals(new String[] { "*", "1" }, updater.getUpdateMaxIterations());
	}

	// graphsout
	@Test
	void invalidGraphsout() throws IOException, ParseException {
		assertThrows(ParseException.class, () -> {
			new CoNLLRDFUpdaterFactory().buildFromCLI(new String[] { "-graphsout" });
		});
	}
}
