package org.acoli.conll.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.acoli.conll.rdf.CoNLLRDFFormatter.Mode;

public class CoNLLRDFFormatterFactoryTest {
	// rdf COLS
	@Test
	void rdfOptionNoColumns() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-rdf" });
		assertEquals(Mode.CONLLRDF, formatter.getModules().get(0).getMode());
	}

	@Test
	void rdfOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-rdf", "WORD", "POS" });
		assertEquals(Mode.CONLLRDF, formatter.getModules().get(0).getMode());
		assertEquals(new LinkedList<String>(Arrays.asList("WORD", "POS")), formatter.getModules().get(0).getCols());
	}

	// conll COLS
	@Test
	void conllOptionNoColumns() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-conll" });
		assertEquals(Mode.CONLL, formatter.getModules().get(0).getMode());
	}

	@Test
	void conllOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-conll", "WORD", "POS" });
		assertEquals(Mode.CONLL, formatter.getModules().get(0).getMode());
		assertEquals(new LinkedList<String>(Arrays.asList("WORD", "POS")), formatter.getModules().get(0).getCols());
	}

	// debug
	@Test
	void debugOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-debug" });
		assertEquals(Mode.DEBUG, formatter.getModules().get(0).getMode());
	}

	// grammar
	@Test
	void grammarOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-grammar" });
		assertEquals(Mode.GRAMMAR, formatter.getModules().get(0).getMode());
	}

	// semantics
	@Test
	void semanticsOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-semantics" });
		assertEquals(Mode.SEMANTICS, formatter.getModules().get(0).getMode());
	}

	// query (sparqltsv) SPARQL
	// TODO test with url
	@Test
	void queryOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-query", "Some Query here" });
		assertEquals(Mode.QUERY, formatter.getModules().get(0).getMode());
		assertEquals("Some Query here", formatter.getModules().get(0).getSelect());
	}

	@Test
	void queryOptionFile() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-query", "src/test/resources/select-test.sparql" });
		assertEquals(Mode.QUERY, formatter.getModules().get(0).getMode());
		assertEquals("SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object .}", formatter.getModules().get(0).getSelect());
	}

	// -sparqltsv is deprecated
	@Test
	void sparqltsvOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-sparqltsv", "Some Query Here" });
		assertEquals(Mode.QUERY, formatter.getModules().get(0).getMode());
		assertEquals("Some Query Here", formatter.getModules().get(0).getSelect().trim());
	}
	// grammar + query

	@Test
	void GrammarSemanticsOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] { "-grammar", "-semantics"});
		assertEquals(Mode.GRAMMAR_SEMANTICS, formatter.getModules().get(0).getMode());
	}

	// if no parameters are supplied, -conllrdf is inferred
	@Test
	void noOption() throws IOException, ParseException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory().buildFromCLI(new String[] {});
		assertEquals(Mode.CONLLRDF, formatter.getModules().get(0).getMode());
	}

	// column label with dash in cli args
	@Test
	void conllOptionWithDashInQuery() throws ParseException, IOException {
		CoNLLRDFFormatter formatter = new CoNLLRDFFormatterFactory()
				.buildFromCLI(new String[] { "-conll", "WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS" });

		assertEquals(new LinkedList<String>(Arrays.asList("WORD", "POS", "PARSE", "NER", "COREF", "PRED", "PRED-ARGS")),
				formatter.getModules().get(0).getCols());
	}
}
