#!/bin/bash
# run the specified java class (of the org.acoli.conll.rdf package) with the provided arguments
# after calling compile.sh to ensure it's up to date.

# store the path of the conll-rdf directory in HOME
HOME=$( dirname -- "$(realpath -- "$0")");
TARGET="$HOME/target/classes";
CLASSPATH="$TARGET:$HOME/lib/*";

if (source $HOME/compile.sh &> /dev/null;); then
	if [ $OSTYPE = "cygwin" ]; then
		TARGET=$(cygpath -wa -- "$TARGET");
		CLASSPATH=$(cygpath -pwa -- "$CLASSPATH");
	fi;
	#could also add -Dlog4j.configuration=file:'src/main/resources/log4j.properties' for another log4j config
	java -Dfile.encoding=UTF8 -classpath $CLASSPATH org.acoli.conll.rdf.$*
else
	echo "error: compile unsuccessful"
fi;
