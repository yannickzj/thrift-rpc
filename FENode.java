import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;

import org.apache.thrift.TProcessorFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.slf4j.LoggerFactory;

public class FENode {

    static Logger log = LoggerFactory.getLogger(FENode.class.getName());

    public static void main(String [] args) throws Exception {
		if (args.length != 1) {
	    	System.err.println("Usage: java FENode FE_port");
	    	System.exit(-1);
		}

		BasicConfigurator.configure();

		int portFE = Integer.parseInt(args[0]);
		log.info("Launching FE node on port " + portFE);

		// launch Thrift server
		BcryptServiceHandler handler = new BcryptServiceHandler();
		BcryptService.Processor processor = new BcryptService.Processor(handler);
		TNonblockingServerSocket socket = new TNonblockingServerSocket(portFE);
		THsHaServer.Args sargs = new THsHaServer.Args(socket);
		sargs.protocolFactory(new TBinaryProtocol.Factory());
		sargs.transportFactory(new TFramedTransport.Factory());
		sargs.processorFactory(new TProcessorFactory(processor));
		sargs.maxWorkerThreads(32);
		TServer server = new THsHaServer(sargs);
		server.serve();
    }

}
