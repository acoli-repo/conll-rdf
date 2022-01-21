package org.acoli.conll.rdf;

import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleLineBreakSplitter extends CoNLLRDFComponent {
	static final Logger LOG = LogManager.getLogger(SimpleLineBreakSplitter.class);

	@Override
	protected void processSentenceStream() throws IOException {
		String line;
		int empty = 0;
		while((line = getInputStream().readLine())!=null) {
			if (line.trim().isEmpty()) {
				empty++;
			} else {
				if (empty > 0) {
					getOutputStream().print("\n#newsegment\n");
					empty = 0;
				}
				getOutputStream().print(line+"\n");
			}
		}
		getOutputStream().close();
	}

	public void configureFromCommandLine(String[] args) throws IOException, ParseException {
		// Nothing to do
	}

	public static void main(String[] args) throws IOException {
		SimpleLineBreakSplitter splitter = new SimpleLineBreakSplitter();

		try {
			splitter.configureFromCommandLine(args);
		} catch (ParseException e) {
			LOG.error(e);
			System.exit(1);
		}

		long start = System.currentTimeMillis();
		// READ SENTENCES from System.in
		splitter.processSentenceStream();
		LOG.info((System.currentTimeMillis() - start) / 1000 + " seconds");
	}
}
