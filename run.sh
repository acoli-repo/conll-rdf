#!/bin/bash
# determines the classpath, updates class files if necessary and runs the specified java class with the provided arguments
# we also add org.acoli.conll.rdf as package, so, call the class by local name, only, see examples

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
CLASSPATH=$HOME/bin":"`find $HOME/lib | perl -pe 's/\n/:/g;' | sed s/':$'//`;
if [ $OSTYPE = "cygwin" ]; then
	CLASSPATH=`echo $CLASSPATH | sed s/':'/';'/g;`;
fi;
JAVAS=$(
	for java in `find  $HOME | sed s/'^\.\/'// | egrep 'java$'`; do
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
	java -Dfile.encoding=UTF8 -classpath $CLASSPATH org.acoli.conll.rdf.$*; 
fi;