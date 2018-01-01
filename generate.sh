#!/bin/sh

# configure Thrift path
THRIFT_CC=/opt/bin/thrift

echo --- Cleaning
rm -f *.jar
rm -f *.class
rm -fr gen-java

echo --- Compiling Thrift IDL
$THRIFT_CC --version
$THRIFT_CC --gen java rpc.thrift
