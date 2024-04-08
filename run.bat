@ECHO OFF
JAVA_HOME%\bin\javac -d ./class .\src\ru\fmtk\khlystov\image_detect\Main.java
JAVA_HOME%\bin\java -Dfile.encoding=UTF-8 -classpath .\class ru.fmtk.khlystov.image_detect.Main $1