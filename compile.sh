#!/bin/bash
#
# compile conll-rdf with maven.
# Please read the notice at the start of run.sh if you're not sure what you're seeing here.

# Bash Strict Mode
# set -euo pipefail
# IFS=$'\n\t'

conll_dir="$(dirname -- "$(realpath -- "$0")")"

mvn clean install --also-make --batch-mode --quiet  "--file=${conll_dir}/parent" -e -DskipTests=true
