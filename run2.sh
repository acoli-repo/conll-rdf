#!/bin/bash

className="$1"
argsString="${*:2}"
args=(--batch-mode --quiet --errors)

mvn compile "${args[@]}"

mvn exec:java -Dexec.mainClass="org.acoli.conll.rdf.$1" "${args[@]}" -Dexec.args="$argsString"
