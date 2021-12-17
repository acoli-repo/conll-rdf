#!/bin/bash
#
# compile conll-rdf with maven.
# Please read the notice at the start of run.sh if you're not sure what you're seeing here.

# Bash Strict Mode
set -euo pipefail
IFS=$'\n\t'

mvn clean compile -am --batch-mode --quiet -e
