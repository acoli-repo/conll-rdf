package org.acoli.conll.rdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

class CoNLLRDFCommandLine {
	private static final Logger LOG = LogManager.getLogger(CoNLLRDFCommandLine.class);
	private final Logger logger;
	private final String syntax;
	private final String header;
	private final String footer;
	private final Options options;
	private final HelpFormatter helpFormatter = new HelpFormatter();

	/**
	 * Command Line Handler, parses args[] and prints usage help
	 *
	 * @param syntax      e.g. CoNLLStreamExtractor baseURI [ FIELD ... ]
	 * @param description e.g. reads CoNLL from stdin, splits sentences, creates CoNLL RDF, applies SPARQL queries
	 * @param options     the command line options
	 * @param logger      the calling class' Log4j Logger
	 */
	CoNLLRDFCommandLine(String syntax, String description, Iterator<Option> options, Logger logger) {
		this.logger = logger;
		this.syntax = syntax;
		this.header = description;
		this.footer = null;
		this.options = new Options()
			.addOption("help", false, "print this help message and exit")
			.addOption("silent", false, "supress help message and logging of info messages");

		while (options.hasNext()) {
			this.options.addOption(options.next());
		}

		helpFormatter.setOptionComparator(null); // don't sort cli-options in help message
		helpFormatter.setSyntaxPrefix("synopsis: ");
	}
	CoNLLRDFCommandLine(String syntax, String description, Options options, Logger logger) {
		this(syntax, description, options.getOptions().iterator(), logger);
	}
	CoNLLRDFCommandLine(String syntax, String description, Option[] options, Logger logger) {
		this(syntax, description, Arrays.asList(options).iterator(), logger);
	}

	/**
	 *
	 * @param args
	 * @return the parsed Arguments as CommandLine
	 * @throws ParseException
	 */
	public CommandLine parseArgs(String[] args) throws ParseException {
		final CommandLine cmd;
		try {
			cmd = new DefaultParser().parse(this.options, args);
		} catch (ParseException e) {
			logUsageHelp();
			throw e;
		}

		LOG.debug("String[] args = " + Arrays.toString(args));
		LOG.debug("Option[] opts = " + Arrays.deepToString(cmd.getOptions()));
		LOG.debug("Unparsed args = " + cmd.getArgList().toString());

		// print help and exit
		if (cmd.hasOption("help")) {
			printUsageHelp();
			System.exit(0);
		}
		if (cmd.hasOption("silent")) {
			// Not part of the public API. See: https://logging.apache.org/log4j/2.x/faq.html#reconfig_level_from_code
			Configurator.setLevel(logger.getName(), Level.WARN);
		}

		// READ LOGLEVEL
		if (cmd.hasOption("loglevel")) {
			Configurator.setLevel(logger.getName(), Level.toLevel(cmd.getOptionValue("loglevel")));
		}
		return cmd;
	}

	/**
	 * Print usage help to stderr
	 */
	private void printUsageHelp() {
		helpFormatter.printHelp(syntax, header, options, footer);
	}
	/**
	 * Log usage help with LOG.info
	 */
	private void logUsageHelp() {
		final StringWriter info = new StringWriter();
		final PrintWriter pw = new PrintWriter(info);

		helpFormatter.printHelp(pw, 80, syntax, header, options, helpFormatter.getLeftPadding(),
				helpFormatter.getDescPadding(), footer);
		pw.flush();

		// add new line after logger's own prefix
		logger.info("\n" + info.toString());
	}

	/**
	 * Read an entire file to String, encoded as UTF-8. Intended to load small files
	 * like sparql queries into memory. Implementation of
	 * {@Code Files.readString(Path path)} available in Java 11
	 *
	 * @param path the path to the file
	 * @return a String containing the content read from the file
	 * @throws IOException              if the read fails
	 * @throws IllegalArgumentException if the file is reported as larger than 2GB
	 */
	public static String readString(Path path) throws IOException {
		// Files.readString(path, StandardCharsets.UTF_8); // requires Java 11
		if (path.toFile().length() > 2000000000) {
			throw new IllegalArgumentException(
					"The file " + path + " is too large. " + path.toFile().length() + " bytes");
		}
		byte[] buffer = new byte[(int) path.toFile().length()];
		buffer = Files.readAllBytes(path);
		return new String(buffer, StandardCharsets.UTF_8);
	}

	public static String readUrl(URL url) throws IOException {
		BufferedReader reader;
		if (url.toString().endsWith(".gz")) {
			reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(url.openStream()), StandardCharsets.UTF_8));
		} else {
			reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
		}
		StringBuilder out = new StringBuilder();
		for (String line = reader.readLine(); line != null; line = reader.readLine())
			out.append(line + "\n");
		return out.toString();
	}

	public static Query selectQueryFromLiteral(String arg) {
		final Query query = QueryFactory.create(arg);
		if (query.isSelectType()) {
			return query;
		} else {
			throw new IllegalArgumentException("The provided query is not a select query: " + arg);
		}
	}
	public static UpdateRequest updateRequestFromLiteral(String arg) {
		return UpdateFactory.create(arg);
	}

	public static String readSparqlFile(String arg) throws IOException {
		Path path = Paths.get(arg);
		File file = path.toFile();

		if (file.isFile() && file.exists()) { // can be read from a file
			LOG.debug("Found File " + arg + " with length " + file.length());
		}
		if ( ! arg.endsWith(".sparql")) {
			throw new IllegalArgumentException("File " + arg + " has an unexpected extension. expected '.sparql'");
		}
		return readString(path);
	}

	public static Query readSparqlSelect(String arg) throws IOException {
		// arg = arg.strip();
		try {
			if (new File(arg).exists()) {
				return selectQueryFromLiteral(readString(Paths.get(arg)));
			}
		} catch (InvalidPathException e) {
			LOG.debug(e);
		}
		try {
			return selectQueryFromLiteral(readUri(new URI(arg)));
		} catch (URISyntaxException e) {
			LOG.debug(e);
		}
		// read as literal
		try {
			return selectQueryFromLiteral(arg);
		} catch (QueryException e) {
			LOG.debug(e);
		}
		throw new IllegalArgumentException("arg could not be read as sparql");
	}
	public static Model readModel(URI uri) throws IOException {
		Model m = ModelFactory.createDefaultModel();
		return m.read(readUri(uri));
	}

	/**
	 * Tries to read from a specific URI.
	 * Tries to read content directly or from GZIP
	 * Validates content against UTF-8.
	 * @param uri
	 * 		the URI to be read
	 * @return
	 * 		the text content
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public static String readUri(URI uri) throws MalformedURLException, IOException {
		if (uri.isAbsolute()){
			return readUrl(uri.toURL());
		} else {
			return readString(Paths.get(uri));
		}
	}

	public static Writer newFileWriter(File outputDir, String updateName, String sentence_id, int update_id, int iteration, int step, String fileExtension) throws FileNotFoundException {
		if (updateName != null && !updateName.isEmpty()) {
			updateName = updateName.replace(".sparql", "");
		} else {
			updateName = UUID.randomUUID().toString();
		}
		File file = new File(outputDir,
			String.format(
				"%s__U%03d_I%04d_S%03d__%s%s",
				sentence_id, update_id, iteration, step, updateName, fileExtension)
			);
		return new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
	}

	public static Pair<String, String> parseUpdate(String arg) throws IOException, ParseException {
		final String updateRaw = arg.replaceFirst("\\{[0-9u*]+\\}$", "");
		String freq = arg.replaceFirst(".*\\{([0-9u*]+)\\}$", "$1");
		if (arg.equals(freq)) {
			freq = "1";
		} else if (freq.equals("u")) {
			freq = "*";
	}
		return new ImmutablePair<>(updateRaw, freq);
	}

	public static File parseDir(String arg) throws IOException {
		// arg = arg.toLowerCase(); // FIXME why?
		File dir = new File(arg);
		if (dir.exists() || dir.mkdirs()) {
			if (! dir.isDirectory()) {
				dir = null;
				throw new IOException("Error: Given directory is not valid: " + arg);
			}
		} else {
			dir = null;
			throw new IOException("Error: Failed to create given directory: " + arg);
		}
		return dir;
	}

	/**
	 * Legacy parsing of Sparql (Select) Queries that may be provided verbatim
	 * @return a String containing the Query
	 * @throws IOException if the argument isn't a query, or valid file, or url
	 */
	public static String parseSelectOptionLegacy(String sparqlArg) throws IOException {
		// TODO Unit testing for this static Method
		// TODO Reduce Code Duplication with select Option between StreamExtractor and Formatter
		// FIXME Refactor and use helper methods (readFile etc)
		String sparql = "";

		Reader sparqlreader = new StringReader(sparqlArg);
		File file = new File(sparqlArg);
		URL url = null;
		try {
			url = new URL(sparqlArg);
		} catch (MalformedURLException e) {
		}

		if (file.exists()) { // can be read from a file
			sparqlreader = new FileReader(file);
		} else if (url != null) {
			try {
				sparqlreader = new InputStreamReader(url.openStream());
			} catch (Exception e) {
			}
		}

		BufferedReader in = new BufferedReader(sparqlreader);
		for (String line = in.readLine(); line != null; line = in.readLine()) {
			sparql = sparql + line + "\n";
		}
		return sparql;
	}
}
