#!/bin/bash
#
# Compile conll-rdf with maven, and install it locally, then
# package the Conll-rdf project as a stand-alone jar-with-dependencies
# Please read the notice at the start of run.sh if you're not sure what you're seeing here.

# store the path of the conll-rdf directory
conll_dir="$(dirname -- "$(realpath -- "$0")")"

# Package conll-rdf as a jar-with dependencies, which can then be run as follows:
# java -cp target/conll-rdf-1.0-SNAPSHOT-jar-with-dependencies.jar org.acoli.conll.rdf.CoNLLStreamExtractor uri id word
# Make sure JAVA_HOME is set to the correct version while compiling, if encountering errors like class not found
mvn clean install package --batch-mode --quiet --file="${conll_dir}" -DskipTests
