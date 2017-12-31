# Scalable distributed system using Apache Thrift

## Introduction

This project aims to build a scalable distributed system for computing the bcrypt key derivation function, which is a popular technique for securing passwords in web applications.

## Architecture

The system comprises a front end (FE) layer and a back end (BE) layer. The FE layer accepts connections from clients and forward requests to the BE layer in a manner that balances load. The BE layer is distributed horizontally for scalability.

<p align="center"><img src="/README/architecture.png" width="600"></p>

Clients only talk to the FE node, and the FE node talks to the clients and BE nodes.

## Functionality

The system supports two fundamental computations on passwords:

+ **Hash password**: hash a given password using a given number of bcrypt rounds. The output is represented as a string encoding the number of rounds, the cryptographic salt, and the hash.

+ **Check password**: check that a given password matches a previously computed hash.

*jBcrypt-0.4* is used for cryptographic computations. Passwords are processed in batches. The RPC accepts lists as input and returns lists as output. The i'th element of the output list corresponds to the i'th element of the input list.


## Exception handling

The FE node throws an IllegalArgument exception at the client in the following cases:

+ Any of the list arguments passed to hashPassword or checkPassword is empty;

+ The password and hash arguments of checkPassword are lists of unequal length;

+ The logRounds argument of hashPassword is out of range with respect to the range of values supported by jBcrypt.
  
The FE node does not throw an exception in the following cases:

+ Empty password passed to hashPassword or checkPassword;

+ Malformed hash passed to checkPassword (checkPassword returns false if the hash is malformed)
 

## Fault tolerance

The system tolerates BE node failures. For example, the client does not observe any exceptions if the number of BE nodes is reduced from 2 to 1, or even from 1 to 0. The FE node performs all the computations locally if no BE nodes are available.

The system also tolerates FE node failures. If the FE fails and is restarted then the client observes exceptions only temporarily while the FE node is down.

The FE node will be restarted eventually after each failure. BE nodes may be restarted or they may fail permanently.


## Prerequisites

+ Apache Thrift 0.9.3

+ OpenJDK 1.8.0

## How to run the system

