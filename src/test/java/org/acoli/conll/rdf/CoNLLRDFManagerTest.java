package org.acoli.conll.rdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.acoli.conll.rdf.CoNLLRDFFormatter.Mode;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CoNLLRDFManagerTest {
	CoNLLRDFManager manager;

	@BeforeEach
	void init() {
		manager = new CoNLLRDFManager();
	}

	@Test
	void whenNoInput_thenThrowException() throws JsonMappingException, JsonProcessingException {
		// TODO should be different Exception type?
		String given = "{\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLStreamExtractor\",\"baseURI\":\"URI\",\"columns\":[\"COL1\",\"COL2\"]}]}";
		assertThrows(IllegalArgumentException.class, () -> {
			manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
			manager.buildComponentStack();
			assertFalse(manager.getOutput() == null);
		});
	}

	@Test
	void whenInputWrongType_thenThrowException() throws JsonMappingException, JsonProcessingException {
		String given = "{\"input\":{},\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLStreamExtractor\",\"baseURI\":\"URI\",\"columns\":[\"COL1\",\"COL2\"]}]}";
		assertThrows(JsonParseException.class, () -> {
			manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
			manager.buildComponentStack();
			assertFalse(manager.getOutput() == null);
		});
	}

	@Test
	void whenNoDefaultOut_thenThrowException() {
		// TODO should be different Exception type?
		String given = "{\"input\":\"System.in\",\"pipeline\":[{\"class\":\"CoNLLStreamExtractor\",\"baseURI\":\"URI\",\"columns\":[\"COL1\",\"COL2\"]}]}";
		assertThrows(IllegalArgumentException.class, () -> {
			manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
			manager.buildComponentStack();
		});
	}

	@Test
	void givenDefaultOut_whenNoFormatterOut_thenUseDefaultOut() throws IOException, ParseException {
		String given = "{\"input\":\"System.in\",\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLRDFFormatter\",\"modules\":[{\"mode\":\"RDF\",\"columns\":[\"COL1\",\"COL2\"]}]}]}";
		// String actual;
		// String expected = "{\"input\":\"System.in\",\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLRDFFormatter\",\"modules\":[{\"mode\":\"RDF\",\"columns\":[\"COL1\",\"COL2\"],\"output\":\"System.out\"}]}]}";
		// actual = CoNLLRDFManagerFactory.normalizeConfig(given);
		manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
		assertNotNull(((CoNLLRDFFormatter) manager.getComponentStack().get(0)).getModules().get(0).getOutputStream());
		// assertJson(actual).where().path("pipeline", ANY, "output").isIgnored().isEqualTo(expected);
		//assertJson(actual).at("pipeline/1/")
	}

	@Test
	void givenDefaultOut_whenNoFormatterModule_thenUseRDF() throws IOException, ParseException {
		String given = "{\"input\":\"System.in\",\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLRDFFormatter\"}]}";
		Mode actual;
		Mode expected = Mode.CONLLRDF;

		manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
		actual = ((CoNLLRDFFormatter) manager.getComponentStack().get(0)).getModules().get(0).getMode();
		assertJson(actual).isEqualTo(expected);
		// fail("Unimplemented");
	}

	@Test
	@Disabled("Unimplemented Check")
	void whenFormatterNotLast_thenThrowException() {
		String given = "{\"input\":\"System.in\",\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLRDFFormatter\",\"modules\":[{\"mode\":\"DEBUG\"}]},{\"class\":\"CoNLLStreamExtractor\",\"baseURI\":\"URI\",\"columns\":[\"COL1\",\"COL2\"]}]}";
		assertThrows(ParseException.class, () -> {
			manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
			manager.buildComponentStack();
		});
	}

}
