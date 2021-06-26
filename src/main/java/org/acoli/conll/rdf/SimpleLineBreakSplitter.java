package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SimpleLineBreakSplitter extends CoNLLRDFComponent {

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

	public static void main(String[] args) throws IOException {
		System.err.println("synopsis: SimpleLineBreakSplitter");
		SimpleLineBreakSplitter splitter = new SimpleLineBreakSplitter();

		long start = System.currentTimeMillis();

		splitter.setInputStream(new BufferedReader(new InputStreamReader(System.in)));
		splitter.setOutputStream(System.out);

		//READ SENTENCES from System.in
		splitter.processSentenceStream();
		System.err.println(((System.currentTimeMillis()-start)/1000 + " seconds"));
	}
}
