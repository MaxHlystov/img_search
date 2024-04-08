#!/bin/bash
javac -d ./class ./src/ru/fmtk/khlystov/image_detect/Main.java
java -Dfile.encoding=UTF-8 -classpath ./class/ ru.fmtk.khlystov.image_detect.Main $1
