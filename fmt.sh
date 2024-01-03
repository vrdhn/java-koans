#!/bin/sh


if [ ! -f ../google-java-format-1.19.1-all-deps.jar ] ;
then
	wget https://github.com/google/google-java-format/releases/download/v1.19.1/google-java-format-1.19.1-all-deps.jar
        mv google-java-format-1.19.1-all-deps.jar ..
fi
java -jar ../google-java-format-1.19.1-all-deps.jar -i -a *.java */*.java
