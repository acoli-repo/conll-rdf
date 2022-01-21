package org.acoli.conll.rdf;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.node.*;

import org.apache.commons.cli.ParseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class CoNLLRDFManagerDeserializer extends StdDeserializer<CoNLLRDFManager> { 

	public CoNLLRDFManagerDeserializer() { 
		this(null); 
	} 

	public CoNLLRDFManagerDeserializer(Class<?> vc) { 
		super(vc); 
	}

	@Override
	public CoNLLRDFManager deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		JsonNode node = jp.getCodec().readTree(jp);
		if ( ! node.required("input").isTextual()) {
			throw new JsonParseException(jp, "Required property input is not of type String.");
		}
		if ( ! node.required("output").isTextual()) {
			throw new JsonParseException(jp, "Required property output is not of type String.");
		}
		if ( ! node.required("pipeline").isArray()) {
			throw new JsonParseException(jp, "Required property pipeline is not of type Array.");
		}
		// TODO jp.getCodec().treeToValue(n, valueType);
		
		String inputString = node.path("input").textValue();
		String outputString = node.path("output").textValue();
		ArrayNode pipelineNode = (ArrayNode) node.withArray("pipeline");
		// int id = (Integer) ((IntNode) node.get("id")).numberValue();
		// String CoNLLRDFManagerName = node.get("CoNLLRDFManagerName").asText();
		// int userId = (Integer) ((IntNode) node.get("createdBy")).numberValue();

		CoNLLRDFManager manager = new CoNLLRDFManager();
		manager.setInput(CoNLLRDFManager.parseConfAsInputStream(inputString));
		manager.setOutput(CoNLLRDFManager.parseConfAsOutputStream(outputString));
		try {
			manager.setComponentStack(CoNLLRDFManager.parsePipeline(pipelineNode));
			manager.buildComponentStack();
		} catch (ParseException e) {
			throw new JsonParseException(jp, "Pipeline property is not correct", e);
		}
		return manager;
	}
}