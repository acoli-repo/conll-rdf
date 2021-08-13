package org.acoli.conll.rdf;

import static org.acoli.conll.rdf.CoNLLRDFCommandLine.readString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class CoNLLRDFManagerFactory {
	static Logger LOG = Logger.getLogger(CoNLLRDFManagerFactory.class);

	CoNLLRDFManager buildFromCLI(String[] args) throws IOException, ParseException {
		final CoNLLRDFManager manager = new CoNLLRDFManager();
		final CommandLine cmd = new CoNLLRDFCommandLine("CoNLLRDFManager -c JSON",
				"Build a conll-rdf pipeline from a json configuration",
				new Options().addRequiredOption("c", "config", true, "Specify JSON config file"), LOG).parseArgs(args);

		if (cmd.hasOption("c")) {
			try {
				manager.parseConfig(readString(Paths.get(cmd.getOptionValue("c"))));
			} catch (IOException e) {
				throw new IOException(
						"Error when reading config file " + new File(cmd.getOptionValue("c")).getAbsolutePath(), e);
			}
		} else {
			throw new ParseException("No config file specified.");
		}
		return manager;
	}
}
