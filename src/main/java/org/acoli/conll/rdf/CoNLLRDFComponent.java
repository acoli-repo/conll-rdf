package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

public abstract class CoNLLRDFComponent implements Runnable {
	static final List<Integer> CHECKINTERVAL = Arrays.asList(3, 10, 25, 50, 100, 200, 500);
	// maximal update iterations allowed until the update loop is canceled and an error msg is thrown
	// (to prevent faulty update scripts running in an endless loop)
	static final int MAXITERATE = 999;

	private BufferedReader inputStream = new BufferedReader(new InputStreamReader(System.in));
	private PrintStream outputStream = System.out;

	protected abstract void processSentenceStream() throws IOException;

	public final BufferedReader getInputStream() {
		return inputStream;
	}
	public final void setInputStream(BufferedReader inputStream) {
		this.inputStream = inputStream;
	}
	public final PrintStream getOutputStream() {
		return outputStream;
	}
	public final void setOutputStream(PrintStream outputStream) {
		this.outputStream = outputStream;
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
	// TODO is this method used anywhere?
	public final void start() {
		run();
	}
}
