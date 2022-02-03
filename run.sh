#!/bin/bash
#
# Conll-rdf wrapper script using a jar-with-dependencies instead of the default maven-exec.
# Please read the notice at the start of run-via-maven.sh if you're not sure what you're seeing here.

# store the path of the conll-rdf/target directory
conll_dir="$(dirname -- "$(realpath -- "$0")")"
target_dir="${conll_dir}/target"
package_jar="${target_dir}/conll-rdf-1.0-SNAPSHOT-jar-with-dependencies.jar"

# if the number of arguments is equal to 0, no class name is provided
if [ $# -eq 0 ]; then
	echo "Please give the name of the conll-rdf class you want to run"
	exit 1
fi

# Check for presence of packaged jar
if [ ! -e "${package_jar}" ]; then
    echo "Please make sure to run package.sh first, and verify the jar-with-dependencies is present in the target folder"
    exit 1
fi

class_name=$1
shift 1

java -cp "${package_jar}" "org.acoli.conll.rdf.${class_name}" "$@"
