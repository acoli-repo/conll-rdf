#!/bin/bash
# compile all missing and outdated java classes, and copy .properties files over.
# requires $HOME, $TARGET, and $CLASSPATH to be set, or maven to be installed.
# do not call directly, indirectly invoked via ./run.sh

mkdir -p "$TARGET";

JAVAS="";
# Find all java classes that need to be compiled
for java in $(find $HOME/src/main/java -name "*.java"); do
	class=$TARGET${java#$HOME/src/main/java}
	class=${class%.java}.class
	if [ ! -e $class ] || [ $java -nt $class ]; then
		JAVAS="$JAVAS $java"
	fi;
done;

# Copy properties files to target
for propertiesFile in $(find $HOME/src/main/resources -name '*.properties'); do
	cp -f -- "$propertiesFile" "$TARGET${propertiesFile#src/main/resources}" &> /dev/null;
done;

# Check if compiling is necessary
if [ -n "$JAVAS" ]; then
	# compile with maven whenever possible
	if ( mvn -version &> /dev/null ); then
		( cd $HOME && mvn compile &> /dev/null; );
	else
		# Change the paths to Windows-style paths if the script is running in cygwin
		if [ $OSTYPE = "cygwin" ]; then
			TARGET=$(cygpath -wa -- "$TARGET");
			CLASSPATH=$(cygpath -pwa -- "$CLASSPATH");
			JAVAS=$(for java in $JAVAS; do cygpath -wa -- "$java"; done);
		fi;
		# Compile only the outdated classes
		javac -d $TARGET -classpath $CLASSPATH $JAVAS;
	fi;
fi;

# debug
# echo 'ARGS:' $*
# echo 'HOME:' $HOME
# echo 'TARGET:' $TARGET
# echo 'CLASSPATH:' $CLASSPATH
# echo 'JAVAS:' $JAVAS;
