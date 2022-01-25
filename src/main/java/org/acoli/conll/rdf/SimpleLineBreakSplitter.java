package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleLineBreakSplitter extends CoNLLRDFComponent {
	static final Logger LOG = LogManager.getLogger(SimpleLineBreakSplitter.class);

	@Override
	protected void processSentenceStream() throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(getInputStream()));
		PrintStream out = new PrintStream(getOutputStream());
		String line;
		int empty = 0;
		while((line = in.readLine())!=null) {
			if (line.trim().isEmpty()) {
				empty++;
			} else {
				if (empty > 0) {
					out.print("\n#newsegment\n");
					empty = 0;
				}
				out.print(line+"\n");
			}
		}
		getOutputStream().close();
	}


	public static void main(String[] args) throws IOException {
		final SimpleLineBreakSplitter splitter;
		try {
			splitter = new SimpleLineBreakSplitterFactory().buildFromCLI(args);
			splitter.setInputStream(System.in);
			splitter.setOutputStream(System.out);
		} catch (Exception e){
			LOG.error(e);
			System.exit(1);
			return;
		}
		long start = System.currentTimeMillis();
		// READ SENTENCES from System.in
		splitter.processSentenceStream();
		LOG.info((System.currentTimeMillis() - start) / 1000 + " seconds");
	}
}
