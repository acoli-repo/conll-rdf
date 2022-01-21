package org.acoli.conll.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class CoNLLStreamExtractorFactoryTest {
	static Logger LOG = LogManager.getLogger(CoNLLStreamExtractorFactoryTest.class);

	// throw ParseException if no arguments are provided
    @Test
    void noOption() throws IOException, ParseException {
        assertThrows(ParseException.class, () -> {
            new CoNLLRDFManagerFactory().buildFromCLI(new String[] {});
        });
    }

	// column label (with dash) in cli-args
	@Test
	void CoNLLColumnLabelWithDash() throws ParseException, IOException {
		CoNLLStreamExtractor extractor = new CoNLLStreamExtractorFactory().buildFromCLI(
				new String[] { "url", "WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS" });

		assertEquals("url", extractor.getBaseURI());
		assertEquals(new LinkedList<String>(Arrays.asList("WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS")),
				extractor.getColumns());
	}

	// column label with dash in first line
	@Test
	void CoNLLUPlusStyleColumnLabelWithDash() throws ParseException, IOException {
		CoNLLStreamExtractor extractor = new CoNLLStreamExtractorFactory().buildFromCLI(new String[] { "url" });
		extractor.setInputStream(
				new BufferedReader(new StringReader("# global.columns = WORD POS PARSE NER COREF PRED PRED-ARGS \n\n")));
		extractor.findColumnsFromComment();
		assertEquals("url", extractor.getBaseURI());
		assertEquals(new LinkedList<String>(Arrays.asList("WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS")),
				extractor.getColumns());
		extractor.getInputStream().close();
	}

	// deprecated update
	@Test
	void optionUpdate() throws ParseException, IOException {
		CoNLLStreamExtractor extractor = new CoNLLStreamExtractorFactory().buildFromCLI(new String [] {
			"url", "WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS", "-u", "example/sparql/remove-ID.sparql"});
		extractor.setInputStream(
				new BufferedReader(new StringReader("\n\n")));
		List<Pair<String, String>> actualUpdates = extractor.getUpdates();
		assertEquals(1, actualUpdates.size());
		assertEquals("example/sparql/remove-ID.sparql\n", actualUpdates.get(0).getLeft());
		assertEquals("1", actualUpdates.get(0).getRight());
		extractor.processSentenceStream();

		// TODO test if the file is loaded properly
	}

	@Test
	void optionUpdateWithIterations() throws ParseException, IOException {
		CoNLLStreamExtractor extractor = new CoNLLStreamExtractorFactory().buildFromCLI(new String [] {
			"url", "WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS", "-u", "example/sparql/remove-ID.sparql{2}"});
		extractor.setInputStream(
				new BufferedReader(new StringReader("\n\n")));
		List<Pair<String, String>> actualUpdates = extractor.getUpdates();
		assertEquals(1, actualUpdates.size());
		assertEquals("example/sparql/remove-ID.sparql\n", actualUpdates.get(0).getLeft());
		assertEquals("2", actualUpdates.get(0).getRight());
	}

	// select
	// TODO Add test cases for URL and literal (after refactor)
	@Test
	void optionSelect() throws ParseException, IOException {
		CoNLLStreamExtractor extractor = new CoNLLStreamExtractorFactory().buildFromCLI(new String [] {"url", "-s", "src/test/resources/select-test.sparql"});
		// TODO Use Resource
		// File expectedFile = this.getClass().getResource("select-conllu.sparql").getFile();
		String expected = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object .}\n";

		assertEquals(expected, extractor.getSelect());
	}
}
