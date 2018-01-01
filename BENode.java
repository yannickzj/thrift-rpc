import java.net.InetAddress;

import org.apache.log4j.BasicConfigurator;
import org.apache.thrift.transport.*;
import org.slf4j.Logger;

import org.apache.thrift.TException;
import org.apache.thrift.TProcessorFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.slf4j.LoggerFactory;


public class BENode {

    private static Logger log = LoggerFactory.getLogger(BENode.class.getName());
    private static TServer BEServer = null;
    private static final int pingTimeInterval = 1000;
    private static final int checkTimeInterval = 5000;

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private static class LaunchServer implements Runnable {

        private Thread t;
        private String threadName;
        private int portBE;

        LaunchServer(String threadName, int portBE) {
            this.threadName = threadName;
            this.portBE = portBE;
        }

        public void run() {
            // launch Thrift server
            try {
                BcryptService.Processor processor = new BcryptService.Processor(new BcryptServiceHandler());

                TNonblockingServerSocket socket = new TNonblockingServerSocket(portBE);
                THsHaServer.Args sargs = new THsHaServer.Args(socket);
                sargs.protocolFactory(new TBinaryProtocol.Factory());
                sargs.transportFactory(new TFramedTransport.Factory());
                sargs.processorFactory(new TProcessorFactory(processor));
                sargs.maxWorkerThreads(32);
                THsHaServer server = new THsHaServer(sargs);

				/*
                TServerSocket socket = new TServerSocket(portBE);
				TThreadPoolServer.Args sargs = new TThreadPoolServer.Args(socket);
				sargs.protocolFactory(new TBinaryProtocol.Factory());
				sargs.transportFactory(new TFramedTransport.Factory());
				sargs.processorFactory(new TProcessorFactory(processor));
				sargs.maxWorkerThreads(10);
				TThreadPoolServer server = new TThreadPoolServer(sargs);
				*/

                BEServer = server;
                log.info("Launching BE node on port " + portBE + " at host " + getHostName());
                server.serve();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void start() {
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }
    }

    private static class FEAliveKeeper implements Runnable {

        private Thread t;
        private String threadName;
        private String hostFE;
        private int portFE;
        private int portBE;
        private BcryptService.Client client;
        private final String msg = "helloworld";

        FEAliveKeeper(String threadName, String hostFE, int portFE, int portBE) {
            this.threadName = threadName;
            this.hostFE = hostFE;
            this.portFE = portFE;
            this.portBE = portBE;
        }

        public void run() {
            boolean registered = false;

            while (true) {
                if (!registered) {
                    registerBE();
                    registered = true;
                    BcryptServiceHandler.updateTimestamp();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    long diff = System.currentTimeMillis() - BcryptServiceHandler.getTimestamp();
                    if (diff > checkTimeInterval) {
                        try {
                            String res = client.isAlive(msg);
                            log.info(String.format("FE is alive [%s], idle time from last RPC [%.1fs]", res.equals(msg), diff / 1000.0));
                        } catch (TException e) {
                            log.info("FE fails");
                            registered = false;
                            continue;
                        }
                    }

                    try {
                        Thread.sleep(checkTimeInterval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void registerBE() {
            while (BEServer != null && !BEServer.isServing()) {
            }
            log.info("server is ready");

            TSocket sock = new TSocket(hostFE, portFE);
            TTransport transport = new TFramedTransport(sock);
            TProtocol protocol = new TBinaryProtocol(transport);
            this.client = new BcryptService.Client(protocol);

            while (!transport.isOpen()) {
                try {
                    transport.open();
                    if (client.registerBE(getHostName(), portBE)) {
                        log.info("BE node is successfully registered in FE");
                    } else {
                        log.info("BE node failed to be registered in FE");
                    }
                } catch (TTransportException e) {
                    log.info("waiting for FE");
                    try {
                        Thread.sleep(pingTimeInterval);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (TException e) {
                    log.info("RPC registerBE failed");
                }
            }

        }

        public void start() {
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }

    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java BENode FE_host FE_port BE_port");
            System.exit(-1);
        }

        // initialize log4j
        BasicConfigurator.configure();

        String hostFE = args[0];
        int portFE = Integer.parseInt(args[1]);
        int portBE = Integer.parseInt(args[2]);

        // setup server
        LaunchServer launchServerThread = new LaunchServer("server listening " + portBE, portBE);
        launchServerThread.start();

        // keep watching FE
        FEAliveKeeper aliveKeeper = new FEAliveKeeper("FE aliveKeeper", hostFE, portFE, portBE);
        aliveKeeper.start();
    }
}

