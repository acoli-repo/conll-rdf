#!/bin/bash
#
# compile & run, using maven, the specified java class (of the org.acoli.conll.rdf package) with the provided arguments.
# This is intended to be the most failproof way to use conll-rdf
# If you need better performance (e.g. faster startup), you should probably use java on a jar-with-dependencies instead.

# To the unfortunate HiWi or post-graduate tasked with modifying this file after I'm gone: Good luck!
# Please read up on Quotes in bash before modifying this file. See https://mywiki.wooledge.org/Quotes for example.
# Also http://mywiki.wooledge.org/WordSplitting while you're at it.

# Configure Bash Strict Mode
# * set -e to exit on error
# * set -u to fail on references to unbound variables
# * set -o pipefail to carry the non-zero exit code of a pipeline
# * IFS=$'\n\t' (InternalFieldSeperator) to not split arrays on spaces. Notice the $'...' syntax
# Uncomment the following two lines to enable (disabled because running this like `. run.sh`
# would apply these settings to your console and terminate bash on error)
# set -euo pipefail
# IFS=$'\n\t'

# store the path of the conll-rdf directory
# $0 is a special variable containing the location of the script
conll_dir="$(dirname -- "$(realpath -- "$0")")"
# use like target_dir="${conll_dir}/target/classes"

# join(char seperator, arg1 ... argn) concatenates an array with the seperating character
function join {
  local IFS="$1"
  shift
  printf '%s\n' "$*"
}

# if the number of arguments is equal to 0, no class name is provided
if [ $# -eq 0 ]; then
	echo "Please give the name of the conll-rdf class you want to run"
	exit 1
fi

# first cli argument
class_name="$1"
# "consume" 1 argument
shift
# $@ is a special variable containing the arguments passed to the shell script.
# ${variable@Q} is "Parameter Transformation" to quote the content of the array for re-use
# as the variable is $@ is this case, ${}
# $@ is the variable of the arguments. For a wrapper to java you'd use ("$@")
# For an explanation to the use of quotes, see the reference at the top of this file.
args_array=("${@@Q}")
# join the quoted args into a single string
args=$(join ' ' "${args_array[@]}")

mvn_args=("--batch-mode" "--quiet" "--file=${conll_dir}" "-Dfile.encoding=UTF8")
# Uncomment the following line for a full stack-trace
# mvn_args=("--batch-mode" "--quiet" "-e")
mvn_compile_goal=("compile")
mvn_exec_goal=("exec:java" "-Dexec.mainClass=org.acoli.conll.rdf.$class_name" "-Dexec.args=${args}")

all_args=("${mvn_args[@]}" "${mvn_compile_goal[@]}" "${mvn_exec_goal[@]}")

mvn "${all_args[@]}" |
	# the following is a hack to allow CoNLLRDFUpdater to process output of CoNLLBrackets2RDF
	# currently, CoNLLRDFUpdater supportes the historical Turtle 1.0 prefix only
	# TODO: fix this in CoNLLRDFUpdater, this is a hack, only
	sed -e s/'^[\t ]*PREFIX \(.*\)$'/'@prefix \1 .'/g

# The following commands were pretty useful in debugging the code above
# echo ${#mvn_exec_goal[@]} # echo the size of the array
# declare -p mvn_exec_goal # "describe" the array
