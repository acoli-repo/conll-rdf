package org.acoli.conll.rdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.acoli.fintan.core.FintanInputStream;
import org.acoli.fintan.core.FintanOutputStream;
import org.acoli.fintan.core.FintanStreamComponent;
import org.acoli.fintan.core.FintanStreamHandler;
import org.acoli.fintan.load.RDFStreamLoader;
import org.acoli.fintan.write.RDFStreamWriter;
import org.apache.jena.rdf.model.Model;

public class CoNLLRDFTestUtil {
    public static void connectStreamComponent(FintanStreamComponent<InputStream, FintanOutputStream<Model>> component, OutputStream outputStream) throws IOException {
		final FintanStreamHandler<Model> stream = new FintanStreamHandler<Model>();
		final RDFStreamWriter streamWriter = new RDFStreamWriter();

        component.setOutputStream(stream);
        streamWriter.setInputStream(stream);
        streamWriter.setOutputStream(outputStream);

		new Thread(streamWriter).start();
    }
    public static void connectStreamComponent(FintanStreamComponent<FintanInputStream<Model>, OutputStream> component, InputStream inputStream) throws IOException {
        final FintanStreamHandler<Model> inStream = new FintanStreamHandler<Model>();
		final RDFStreamLoader streamLoader = new RDFStreamLoader();

        streamLoader.setInputStream(inputStream);
        streamLoader.setOutputStream(inStream);
        component.setInputStream(inStream);

		new Thread(streamLoader).start();
    }
    public static void connectStreamComponent(FintanStreamComponent<FintanInputStream<Model>, FintanOutputStream<Model>> component, InputStream inputStream, OutputStream outputStream) throws IOException {
		final FintanStreamHandler<Model> inStream = new FintanStreamHandler<Model>();
		final FintanStreamHandler<Model> outStream = new FintanStreamHandler<Model>();
		final RDFStreamLoader streamLoader = new RDFStreamLoader();
		final RDFStreamWriter streamWriter = new RDFStreamWriter();

        streamLoader.setInputStream(inputStream);
        streamLoader.setOutputStream(inStream);
        component.setInputStream(inStream);
        component.setOutputStream(outStream);
        streamWriter.setInputStream(outStream);
        streamWriter.setOutputStream(outputStream);

		new Thread(streamLoader).start();
		new Thread(streamWriter).start();
    }
    
}
