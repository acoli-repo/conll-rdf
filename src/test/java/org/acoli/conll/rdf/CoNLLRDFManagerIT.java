package org.acoli.conll.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CoNLLRDFManagerIT {
	static Logger LOG = LogManager.getLogger(CoNLLRDFManagerIT.class);

	ObjectMapper objectMapper;

	@Test
	@Disabled("Not Useful")
	public void serialize() throws IOException, ParseException {
		objectMapper = new ObjectMapper();
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		CoNLLRDFManager manager = new CoNLLRDFManagerFactory().parseJsonConf(
				"{\"input\":\"System.in\",\"output\":\"System.out\",\"pipeline\":[{\"class\":\"CoNLLRDFStreamExtractor\"}]}");
		String managerAsString = objectMapper.writeValueAsString(manager);
		LOG.info(managerAsString);
	}

	@Test
	@Disabled("Not Implemented")
	public void deserialize() throws IOException {
		objectMapper = new ObjectMapper();
		String json = "{\"input\":\"System.in\",\"output\":\"System.out\",\"pipeline\":[]}";
		objectMapper.readValue(json, CoNLLRDFManager.class);
	}

	// TODO: Change and rename these tests
	@Test
	void testAPipeline() throws IOException, ParseException {
		String given = CoNLLRDFCommandLine.readString(Paths.get("examples/analyze-ud.json"));
		CoNLLRDFManager manager = new CoNLLRDFManagerFactory().parseJsonConf(given);
		CoNLLStreamExtractor streamExtractor = (CoNLLStreamExtractor) manager.getComponentStack().get(0);
		CoNLLRDFUpdater updater = (CoNLLRDFUpdater) manager.getComponentStack().get(1);
		CoNLLRDFFormatter formatter = (CoNLLRDFFormatter) manager.getComponentStack().get(2);

		assertTrue(manager.getInput().ready());
		assertNotNull(manager.getInput());
		assertEquals(3, manager.getComponentStack().size());
		assertEquals("https://github.com/UniversalDependencies/UD_English#", streamExtractor.getBaseURI());
		assertNotNull(manager.getInput());
		assertNotNull(manager.getComponentStack().get(0).getInputStream());
		assertNotNull(manager.getComponentStack().get(1).getInputStream());
		assertNotNull(manager.getComponentStack().get(2).getInputStream());
		assertEquals(manager.getInput(), streamExtractor.getInputStream());
		assertNotEquals(updater.getInputStream(), formatter.getInputStream());
		// manager.start();
	}

}
