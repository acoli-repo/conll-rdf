package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.PrintStream;

public abstract class CoNLLRDFComponent implements Runnable {

	private BufferedReader inputStream;
	private PrintStream outputStream;
	
	


	public BufferedReader getInputStream() {
		return inputStream;
	}

	public void setInputStream(BufferedReader inputStream) {
		this.inputStream = inputStream;
	}

	public PrintStream getOutputStream() {
		return outputStream;
	}

	public void setOutputStream(PrintStream outputStream) {
		this.outputStream = outputStream;
	
	}

	public abstract void start();
	
	
}
