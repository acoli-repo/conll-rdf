#!/bin/bash
# compile all missing and outdated java classes, and copy .properties files over.
# requires $HOME, $TARGET, and $CLASSPATH to be set, or maven to be installed.
# do not call directly, indirectly invoked via ./run.sh

mkdir -p "$TARGET";

JAVAS="";
for java in $(find $HOME/src/main/java -name "*.java"); do
	class=$TARGET${java#$HOME/src/main/java}
	class=${class%.java}.class
	if [ ! -e $class ] || [ $java -nt $class ]; then
		JAVAS="$JAVAS $java"
	fi;
done;

for propertiesFile in $(find $HOME/src/main/resources -name '*.properties'); do
	cp -f -- "$propertiesFile" "$TARGET${propertiesFile#src/main/resources}" &> /dev/null;
done;

if [ -n "$JAVAS" ]; then
	if [ $OSTYPE = "cygwin" ]; then
		TARGET=$(cygpath -wa -- "$TARGET");
		CLASSPATH=$(cygpath -pwa -- "$CLASSPATH");
		JAVAS=$(for java in $JAVAS; do cygpath -wa -- "$java"; done);
	fi;
	javac -d $TARGET -classpath $CLASSPATH $JAVAS;
fi;

# debug
# echo 'ARGS:' $*
# echo 'HOME:' $HOME
# echo 'TARGET:' $TARGET
# echo 'CLASSPATH:' $CLASSPATH
# echo 'JAVAS:' $JAVAS;
