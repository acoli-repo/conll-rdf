package org.acoli.conll.rdf;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.cli.ParseException;

public abstract class CoNLLRDFComponentFactory {
	public abstract CoNLLRDFComponent buildFromCLI(String[] args) throws IOException, ParseException;

	public abstract CoNLLRDFComponent buildFromJsonConf(ObjectNode conf) throws IOException, IllegalArgumentException, ParseException;
}
