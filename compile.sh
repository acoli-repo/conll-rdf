#!/bin/bash
# determines the classpath, updates class files if necessary and runs the specified java class with the provided arguments
CLASSPATH=".:"`find lib | perl -pe 's/\n/:/g;' | sed s/':$'//`;
if [ $OSTYPE = "cygwin" ]; then
	CLASSPATH=`echo $CLASSPATH | sed s/':'/';'/g;`;
fi;
JAVAS=$(
	for java in `find  | sed s/'^\.\/'// | egrep 'java$'`; do
		class=`echo $java | sed s/'java$'/'class'/;`
		if [ ! -e $class ]; then
			echo $java;
		else if [ $java -nt $class ]; then
			echo $java;
			fi;
		fi;
	done;
	)
if
	if echo $JAVAS | grep java >/dev/null; then
		javac -classpath $CLASSPATH $JAVAS;
	fi 2>&1;
then 
	javac -g -classpath $CLASSPATH $*; 
fi;