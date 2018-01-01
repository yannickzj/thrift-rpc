#!/bin/sh

# configure Java and Thrift paths
JAVA=/usr/lib/jvm/java-1.8.0/bin/java
JAVA_CC=/usr/lib/jvm/java-1.8.0/bin/javac
THRIFT_CC=/opt/bin/thrift

echo --- Cleaning
rm -f *.jar
rm -f *.class
rm -fr gen-java
rm -f tmp/*.log
mkdir tmp

echo --- Compiling Thrift IDL
$THRIFT_CC --version
$THRIFT_CC --gen java rpc.thrift

echo --- Compiling Java
$JAVA_CC -version
$JAVA_CC gen-java/*.java -cp .:"lib/*"
$JAVA_CC *.java -cp .:gen-java/:"lib/*"

echo --- Done, now run your code.
# $JAVA -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" FENode 10123
# $JAVA -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" BENode localhost 10123 10124
# $JAVA -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" Client localhost 10123 hello
# /usr/lib/jvm/java-1.8.0/bin/java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" YJClient ecelinux5 10123 hello 20 1 10 20
# /usr/lib/jvm/java-1.8.0/bin/java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" FENode 10123
# taskset -c 0-1 /usr/lib/jvm/java-1.8.0/bin/java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" BENode ecelinux5 10123 10124
# taskset -c 2-3 /usr/lib/jvm/java-1.8.0/bin/java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" BENode ecelinux5 10123 10125

# $JAVA -cp .:gen-java/:"lib/*" FENode 10123
# $JAVA -cp .:gen-java/:"lib/*" BENode localhost 10123 10124
# $JAVA -cp .:gen-java/:"lib/*" Client localhost 10123 hello
