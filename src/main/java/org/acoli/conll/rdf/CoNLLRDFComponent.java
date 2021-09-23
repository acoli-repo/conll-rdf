package org.acoli.conll.rdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.acoli.fintan.core.StreamTransformerGenericIO;

public abstract class CoNLLRDFComponent extends StreamTransformerGenericIO {
	static final List<Integer> CHECKINTERVAL = Arrays.asList(3, 10, 25, 50, 100, 200, 500);
	static final String DEFAULTUPDATENAME = "DIRECTUPDATE";
	// maximal update iterations allowed until the update loop is canceled and an error msg is thrown
	// (to prevent faulty update scripts running in an endless loop)
	static final int MAXITERATE = 999;

	protected abstract void processSentenceStream() throws IOException;

	@Override
	public void setInputStream(InputStream inputStream, String name) throws IOException {
		if (name == null || FINTAN_DEFAULT_STREAM_NAME.equals(name)) {
			setInputStream(inputStream);
		} else {
			throw new IOException("Only default InputStream is supported for "+CoNLLRDFComponent.class.getName());
		}
	}
	
	@Override
	public void setOutputStream(OutputStream outputStream, String name) throws IOException {
		if (name == null || FINTAN_DEFAULT_STREAM_NAME.equals(name)) {
			setOutputStream(outputStream);
		} else {
			throw new IOException("Only default OutputStream is supported for "+CoNLLRDFComponent.class.getName());
		}
	}

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
