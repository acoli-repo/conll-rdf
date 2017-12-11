#!/bin/bash
# determines the classpath, updates class files if necessary and runs the specified java class with the provided arguments
# we also add org.acoli.conll.rdf as package, so, call the class by local name, only, see examples

HOME=`echo $0 | sed -e s/'[^\/]*$'//`'/';
cd $HOME
HOME=`pwd -P`;
cd - >&/dev/null;

TGT=$HOME/bin

mkdir $TGT >&/dev/null;

CLASSPATH=$TGT":"`find $HOME/lib | perl -pe 's/\n/:/g;' | sed s/':$'//`;
if [ $OSTYPE = "cygwin" ]; then
	TGT=`cygpath -wa $HOME/bin`;
	CLASSPATH=$TGT;
	for lib in `find $HOME/lib`; do
		CLASSPATH=$CLASSPATH';'`cygpath -wa $lib`
	done;
fi;

JAVAS=$(
	cd $HOME;
	for java in `find  . | egrep 'java$'`; do
		class=`echo $java | sed -e s/'src\/'/'bin\/'/ -e s/'java$'/'class'/;`
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
		cd $HOME;
		./compile.sh;
	fi 2>&1;
then 
	java -Dfile.encoding=UTF8 -classpath $CLASSPATH org.acoli.conll.rdf.$*; 
fi;