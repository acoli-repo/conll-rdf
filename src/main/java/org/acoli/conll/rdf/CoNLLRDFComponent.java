package org.acoli.conll.rdf;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import de.unifrankfurt.informatik.acoli.fintan.core.StreamTransformerGenericIO;

public abstract class CoNLLRDFComponent extends StreamTransformerGenericIO {
	static final List<Integer> CHECKINTERVAL = Arrays.asList(3, 10, 25, 50, 100, 200, 500);
	static final String DEFAULTUPDATENAME = "DIRECTUPDATE";
	// maximal update iterations allowed until the update loop is canceled and an error msg is thrown
	// (to prevent faulty update scripts running in an endless loop)
	static final int MAXITERATE = 999;

	protected abstract void processSentenceStream() throws IOException;

	

	@Override
	public final void run() {
		try {
			processSentenceStream();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	// TODO is this method used anywhere? Yes: in Fintan
	public final void start() {
		run();
	}

	// TODO Address Code duplication of main methods
	// TODO Unify Logging of process durations
}
