#!/bin/sh
rm galactica.jar
mvn clean
mvn package
cp target/galactica-MAINLINE-jar-with-dependencies.jar galactica.jar
