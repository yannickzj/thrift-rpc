import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TTransportFactory;

public class Client {
	static Logger log = Logger.getLogger(BENode.class.getName());

    public static void main(String [] args) {
		if (args.length != 5) {
	    	System.err.println("Usage: java Client FE_host FE_port password");
	    	System.exit(-1);
		}

		BasicConfigurator.configure();

		try {
	    	TSocket sock = new TSocket(args[0], Integer.parseInt(args[1]));
	    	TTransport transport = new TFramedTransport(sock);
	    	TProtocol protocol = new TBinaryProtocol(transport);
	    	BcryptService.Client client = new BcryptService.Client(protocol);
	    	transport.open();
	    	log.info("connect to FE success");

	    	List<String> password = new ArrayList<>();
	    	for(int i = 0; i < Integer.parseInt(args[3]); i ++)
	    		password.add(args[2]);

	    	//log.info("pw: " + password);
			long start = System.currentTimeMillis();
	    	List<String> hash = client.hashPassword(password, (short)Integer.parseInt(args[4]));
			//System.out.println("elapsed time: " + (System.currentTimeMillis() - start));
			//System.out.println("---");
			client.checkPassword(password, hash);
			//System.out.println("Password: " + password.get(0));
	    	//System.out.println("Hash: " + hash.get(0));
			//System.out.println("Positive check: " + client.checkPassword(password, hash));
			//System.out.println("---");

			/*
			hash.set(0, "$2a$14$reBHJvwbb0UWqJHLyPTVF.6Ld5sFRirZx/bXMeMmeurJledKYdZmG");
	    	System.out.println("Negative check: " + client.checkPassword(password, hash));
			System.out.println("---");

			hash.set(0, "too short");
	    	System.out.println("Exception check: " + client.checkPassword(password, hash));
			System.out.println("---");
			*/

			transport.close();
		} catch (TException e) {
	    	e.printStackTrace();
		}
    }
}
