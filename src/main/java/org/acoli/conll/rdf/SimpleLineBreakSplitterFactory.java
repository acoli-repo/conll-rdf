package org.acoli.conll.rdf;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class SimpleLineBreakSplitterFactory extends CoNLLRDFComponentFactory {
	@Override
	public SimpleLineBreakSplitter buildFromCLI(String[] args) {
		return new SimpleLineBreakSplitter();
	}

	@Override
	public SimpleLineBreakSplitter buildFromJsonConf(ObjectNode config) {
		return new SimpleLineBreakSplitter();
	}

}
