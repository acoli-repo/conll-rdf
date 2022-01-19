package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoNLLRDFManagerFactory {
	static Logger LOG = LogManager.getLogger(CoNLLRDFManagerFactory.class);

	CoNLLRDFManager buildFromCLI(String[] args) throws IOException, ParseException {
		final CoNLLRDFManager manager;
		final CommandLine cmd = new CoNLLRDFCommandLine("CoNLLRDFManager -c JSON",
				"Build a conll-rdf pipeline from a json configuration",
				new Options().addRequiredOption("c", "config", true, "Specify JSON config file"), LOG).parseArgs(args);

		if (cmd.hasOption("c")) {
			try {
				// manager.parseConfig(readString(Paths.get(cmd.getOptionValue("c"))));
				String jsonString = readString(Paths.get(cmd.getOptionValue("c")));
				manager = parseJsonConf(jsonString);
			} catch (IOException e) {
				throw new IOException(
						"Error when reading config file " + new File(cmd.getOptionValue("c")).getAbsolutePath(), e);
			}
		} else {
			throw new ParseException("No config file specified.");
		}
		return manager;
	}

	CoNLLRDFManager parseJsonConf(String json) throws IOException, ParseException {

		SimpleModule module = new SimpleModule();
		module.addDeserializer(CoNLLRDFManager.class, new CoNLLRDFManagerDeserializer());

		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(Feature.ALLOW_COMMENTS);

		mapper.registerModules(module);
		mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);

		return mapper.readValue(normalizeConfig(json), CoNLLRDFManager.class);
	}

	static String normalizeConfig(String jsonString) throws IOException, ParseException {
		// Copy output to the last component in the pipeline (relevant if it is formatter)
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.enable(Feature.ALLOW_COMMENTS);

		JsonNode node = objectMapper.readTree(jsonString);
		
		JsonNode classArray = node.withArray("pipeline");
		JsonNode lastComponent = classArray.get(classArray.size() - 1);

		if (node.path("output").isTextual() && ! lastComponent.has("output")) {
			((ObjectNode) lastComponent).put("output", node.get("output").textValue());
		}

		return node.toString();
	}
}
