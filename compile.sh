#!/bin/bash
# determines the classpath, updates class files if necessary and runs the specified java class with the provided arguments
# must be run from $HOME: do not call directly, indirectly evoked via ./run.sh

HOME=`echo $0 | sed -e s/'[^\/]*$'//`'.';
cd $HOME
HOME=`pwd -P`;
cd - >&/dev/null;

mkdir $HOME/bin >& /dev/null;

TGT=$HOME/bin;

CLASSPATH=$HOME/bin":"`find $HOME/lib | perl -pe 's/\n/:/g;' | sed s/':$'//`;
if [ $OSTYPE = "cygwin" ]; then
	TGT=`cygpath -wa $HOME/bin`;
	CLASSPATH=$TGT;
	for lib in `find $HOME/lib`; do
		CLASSPATH=$CLASSPATH';'`cygpath -wa $lib`
	done;
fi;

JAVAS=$(
	for java in `find . | sed s/'^\.\/'// | egrep 'java$'`; do
		class=`echo $java | sed s/'java$'/'class'/;`
		if [ ! -e $class ]; then
			echo $java;
		else if [ $java -nt $class ]; then
			echo $java;
			fi;
		fi;
	done;
	)
for propertiesFile in `find ./src | sed s/'^\.\/'// | egrep 'properties$'`; do
	cp -f $propertiesFile ${propertiesFile/'src'/'bin'} >& /dev/null;
done;
if
	if echo $JAVAS | grep java >/dev/null; then
		javac -d $TGT -classpath $CLASSPATH $JAVAS;
	fi 2>&1;
then
	echo >& /dev/null;
else
	javac -d $TGT -g -classpath $CLASSPATH $*; 
fi;
