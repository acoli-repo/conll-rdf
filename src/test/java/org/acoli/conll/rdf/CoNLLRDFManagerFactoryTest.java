package org.acoli.conll.rdf;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

public class CoNLLRDFManagerFactoryTest {
    // throw ParseException if no arguments are provided
    @Test
    void noOption() throws IOException, ParseException {
        assertThrows(ParseException.class, () -> {
            new CoNLLRDFManagerFactory().buildFromCLI(new String[] {});
        });
    }

    @Test
    void noConfigFile() throws IOException, ParseException {
        assertThrows(ParseException.class, () -> {
            new CoNLLRDFManagerFactory().buildFromCLI(new String[] {"-c"});
        });
    }
}
