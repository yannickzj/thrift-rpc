import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.LoggerFactory;

/**
 * This class should be used by BE
 */
public class BcryptServiceHandler implements BcryptService.Iface {

    private static Logger log = LoggerFactory.getLogger(BcryptServiceHandler.class.getName());
    private static final int NUM_PROCESSORS = Runtime.getRuntime().availableProcessors();

    BcryptServiceHandler() {
    }

    /*
    ============================
	        inner class
	============================
	 */
    class AddrEntry {
        String host;
        int port;

        AddrEntry(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    class TransportClientConnection {
        TNonblockingTransport transport;
        BcryptService.AsyncClient client;
        TAsyncClientManager clientManager;

        TransportClientConnection(TNonblockingTransport transport, BcryptService.AsyncClient client, TAsyncClientManager clientManager) {
            this.transport = transport;
            this.client = client;
            this.clientManager = clientManager;
        }
    }

    public class AliveCallback implements AsyncMethodCallback<BcryptService.AsyncClient.isAlive_call> {
        CountDownLatch latch = new CountDownLatch(1);

        AddrEntry entry;
        String msg;
        boolean isAliveFlag = false;

        AliveCallback(String msg, AddrEntry entry) {
            this.msg = msg;
            this.entry = entry;
        }

        boolean getIsAliveFlag() {
            return isAliveFlag;
        }

        void startLatchWait() {
            try {
                this.latch.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onComplete(BcryptService.AsyncClient.isAlive_call c) {
            try {
                assert this.msg.equals(c.getResult());
                isAliveFlag = true;
            } catch (AssertionError e) {
                log.error("Unexpected reply of RPC isAlive");
            } catch (Exception e) {
                log.error("Unknown Exception");
                e.printStackTrace();
            } finally {
                this.latch.countDown();
            }
        }

        @Override
        public void onError(Exception e) {
            log.info("RPC isAlive callback onError");
            removeAddrDict(entry);
            this.latch.countDown();
        }
    }

    class DistributeTask<T> extends Thread {

        CountDownLatch latch;

        DistributeTask(CountDownLatch latch) {
            this.latch = latch;
        }

        List<T> calculationResult = null;

        List<T> getCalculationResult() {
            return calculationResult;
        }

        boolean hashPasswordResultIsSet() {
            return this.calculationResult != null;
        }

        void setCalculationResult(List<T> calculationResult) {
            if (!hashPasswordResultIsSet()) {
                this.calculationResult = calculationResult;
                //log.info("receive result from backend: " + calculationResult);
                this.latch.countDown();
            }
        }

        public void invokeRemoteFunction(AddrEntry entry)
                throws Exception {
            log.info("remote func invoked");
        }

        public void invokeLocalFunction() {
            log.info("local func invoked");
        }

        boolean startNewCalculation() {
            AddrEntry entry;
            while (!hashPasswordResultIsSet() && (entry = findAddrLocation()) != null) {
                try {
                    invokeRemoteFunction(entry);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    log.info("cannot invoke remote function");
                }
            }
            if (!hashPasswordResultIsSet()) {
                try {
                    // should not fail
                    invokeLocalFunction();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        public void run() {
            startNewCalculation();
        }

        class ExecutePasswordFunctionCallback<V>
                implements AsyncMethodCallback<V> {

            TransportClientConnection transportClientConnection;
            AddrEntry addrEntry;

            ExecutePasswordFunctionCallback(TransportClientConnection transportClientConnection, AddrEntry entry) {
                this.transportClientConnection = transportClientConnection;
                this.addrEntry = entry;
            }

            public void onComplete(V c) {
                try {
                    if (c instanceof BcryptService.AsyncClient.executeHashPassword_call) {
                        List<String> result = ((BcryptService.AsyncClient.executeHashPassword_call) c).getResult();
                        setCalculationResult(((List<T>) result));
                    } else if (c instanceof BcryptService.AsyncClient.executeCheckPassword_call) {
                        List<Boolean> result = ((BcryptService.AsyncClient.executeCheckPassword_call) c).getResult();
                        setCalculationResult(((List<T>) result));
                    }
                    this.transportClientConnection.transport.close();
                    transportClientConnection.clientManager.stop();
                } catch (TException e) {
                    e.printStackTrace();
                }
            }

            public void onError(Exception e) {
                e.printStackTrace();
                log.info(String.format("BE [%s at %s] failed", addrEntry.host, addrEntry.port));
                removeAddrDict(addrEntry);
                transportClientConnection.transport.close();
                transportClientConnection.clientManager.stop();
                startNewCalculation();
            }
        }
    }

    class hashPasswordDistributeTask extends DistributeTask<String> {
        List<String> password;
        short logRounds;

        hashPasswordDistributeTask(List<String> password, short logRounds, CountDownLatch latch) {
            super(latch);
            this.password = password;
            this.logRounds = logRounds;
        }

        @Override
        public void invokeRemoteFunction(AddrEntry entry)
                throws Exception {
            TransportClientConnection transportClientConnection = createSocketConnection(entry);
            BcryptService.AsyncClient client = transportClientConnection.client;
            ExecutePasswordFunctionCallback callback =
                    new ExecutePasswordFunctionCallback<BcryptService.AsyncClient.executeHashPassword_call>(transportClientConnection, entry);
            client.executeHashPassword(this.password, this.logRounds, callback);
        }

        @Override
        public void invokeLocalFunction() {
            List<String> result = executeHashPassword(password, logRounds);
            setCalculationResult(result);
        }
    }

    class checkPasswordDistributeTask extends DistributeTask<Boolean> {
        List<String> password, hash;

        checkPasswordDistributeTask(List<String> password, List<String> hash, CountDownLatch latch) {
            super(latch);
            this.password = password;
            this.hash = hash;
        }

        @Override
        public void invokeRemoteFunction(AddrEntry entry)
                throws Exception {
            TransportClientConnection transportClientConnection = createSocketConnection(entry);
            BcryptService.AsyncClient client = transportClientConnection.client;
            ExecutePasswordFunctionCallback callback =
                    new ExecutePasswordFunctionCallback<BcryptService.AsyncClient.executeCheckPassword_call>(transportClientConnection, entry);
            client.executeCheckPassword(password, hash, callback);
        }

        @Override
        public void invokeLocalFunction() {
            List<Boolean> result = executeCheckPassword(password, hash);
            setCalculationResult(result);
        }
    }

    /*
    ============================
          shared resources
    ============================
     */
    private static List<AddrEntry> addrDict = new ArrayList<>();
    private static long timestamp = System.currentTimeMillis();
    private static int index = -1;
    private static final Object addrDictLock = new Object();

    /*
	============================
	   synchronized functions
	============================
	 */
    private void addAddrDict(AddrEntry entry) {
        synchronized (addrDictLock) {
            if (!addrDict.contains(entry))
                addrDict.add(entry);
        }
    }

    private boolean removeAddrDict(AddrEntry entry) {
        synchronized (addrDictLock) {
            int removeIndex = addrDict.indexOf(entry);
            if (removeIndex >= 0) {
                if (index >= removeIndex) {
                    index--;
                }
                addrDict.remove(removeIndex);
                return true;
            } else {
                return false;
            }
        }
    }

    private int getAddrDictSize() {
        synchronized (addrDictLock) {
            return addrDict.size();
        }
    }

    private AddrEntry findAddrLocation() {
        synchronized (addrDictLock) {
            if (addrDict.isEmpty()) {
                return null;
            } else {
                index++;
                index = index % addrDict.size();
                return addrDict.get(index);
            }
        }
    }

    /*
    ============================
     non-synchronized functions
    ============================
     */
    static void updateTimestamp() {
        timestamp = System.currentTimeMillis();
    }

    static long getTimestamp() {
        return timestamp;
    }

    private boolean testRemoteIsAlive(TransportClientConnection transportClientConnection, AddrEntry entry) {
        String msg = "hello world";
        AliveCallback callback = new AliveCallback(msg, entry);
        BcryptService.AsyncClient client = transportClientConnection.client;
        try {
            client.isAlive(msg, callback);
        } catch (Exception e) {
            log.info(String.format("BE [%s at %s] failed", entry.host, entry.port));
            removeAddrDict(entry);
        }
        callback.startLatchWait();
        transportClientConnection.transport.close();
        transportClientConnection.clientManager.stop();
        return callback.getIsAliveFlag();
    }

    private TransportClientConnection createSocketConnection(AddrEntry entry) throws Exception {
        String host = entry.host;
        int port = entry.port;
        TNonblockingTransport transport = new TNonblockingSocket(host, port);
        TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
        TAsyncClientManager clientManager = new TAsyncClientManager();
        BcryptService.AsyncClient client = new BcryptService.AsyncClient(protocolFactory, clientManager, transport);

        return new TransportClientConnection(transport, client, clientManager);
    }

	/*
	============================
	 Client -> FE RPC functions
	============================
	 */

    public List<String> hashPassword(List<String> password, short logRounds)
            throws IllegalArgument {
        if (password.isEmpty()) {
            throw new IllegalArgument("password list is empty");
        }

        if (logRounds < 4 || logRounds > 30) {
            throw new IllegalArgument("logRound argument out of range (4, 30)");
        }

        int passwordListSize = password.size();
        int batchNumber = getAddrDictSize();
        int batchSize = (int) Math.ceil(1.0 * passwordListSize / batchNumber);
        batchNumber = (int) Math.ceil(1.0 * passwordListSize / batchSize);

        List<hashPasswordDistributeTask> taskList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(batchNumber);

        for (int batchIndex = 0; batchIndex < batchNumber; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(passwordListSize, (batchIndex + 1) * batchSize);
            List<String> passwordBatch = password.subList(startIndex, endIndex);

            hashPasswordDistributeTask task = new hashPasswordDistributeTask(passwordBatch, logRounds, latch);
            task.start();
            taskList.add(task);
        }

        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> hashResult = new ArrayList<>();
        for (int i = 0; i < batchNumber; i++) {
            hashPasswordDistributeTask task = taskList.get(i);
            hashResult.addAll(task.getCalculationResult());
            task.interrupt();
        }
        return hashResult;
    }

    public List<Boolean> checkPassword(List<String> password, List<String> hash)
            throws IllegalArgument {

        if (password.isEmpty()) {
            throw new IllegalArgument("password list is empty");
        }

        if (hash.isEmpty()) {
            throw new IllegalArgument("hash list is empty");
        }

        if (password.size() != hash.size()) {
            throw new IllegalArgument("password list length is different from hash list to compare");
        }

        int passwordListSize = password.size();
        int batchNumber = getAddrDictSize();
        int batchSize = (int) Math.ceil(1.0 * passwordListSize / batchNumber);
        batchNumber = (int) Math.ceil(1.0 * passwordListSize / batchSize);

        List<checkPasswordDistributeTask> taskList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(batchNumber);

        for (int batchIndex = 0; batchIndex < batchNumber; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(passwordListSize, (batchIndex + 1) * batchSize);
            List<String> passwordBatch = password.subList(startIndex, endIndex);
            List<String> hashBatch = hash.subList(startIndex, endIndex);

            checkPasswordDistributeTask task = new checkPasswordDistributeTask(passwordBatch, hashBatch, latch);
            task.start();
            taskList.add(task);
        }

        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Boolean> checkResult = new ArrayList<>();
        for (int i = 0; i < batchNumber; i++) {
            checkPasswordDistributeTask task = taskList.get(i);
            checkResult.addAll(task.getCalculationResult());
            task.interrupt();
        }
        return checkResult;
    }

    /*
    ===========================
       FE -> BE RPC functions
    ===========================
     */
    public List<String> executeHashPassword(List<String> password, short logRounds) {
        updateTimestamp();
        //log.info("password " + password);
        int passwordListSize = password.size();
        List<String> ret = new ArrayList<>(passwordListSize);

		/*
		multi thread BE client
		 */

        //int threadNumber = Runtime.getRuntime().availableProcessors();
        int threadSize = (int) Math.ceil(1.0 * passwordListSize / NUM_PROCESSORS);
        int threadNumber = (int) Math.ceil(1.0 * passwordListSize / threadSize);

        while (ret.size() < passwordListSize) ret.add("");

        CountDownLatch latch = new CountDownLatch(threadNumber);

        //long startTime = System.currentTimeMillis();
        class HashThread implements Runnable {
            int startIndex, endIndex;

            HashThread(int startIndex, int endIndex) {
                this.startIndex = startIndex;
                this.endIndex = endIndex;
            }

            public void run() {
                for (int index = startIndex; index < endIndex && index < passwordListSize; index++) {
                    String oneHash = BCrypt.hashpw(password.get(index), BCrypt.gensalt(logRounds));
                    ret.set(index, oneHash);
                }
                latch.countDown();
            }
        }


        for (int index = 0; index < threadNumber; index++) {
            int startIndex = index * threadSize;
            int endIndex = Math.min(startIndex + threadSize, passwordListSize);
            Thread thread = new Thread(new HashThread(startIndex, endIndex));
            thread.start();
        }
        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //log.info("multithread hashing elapsed time: " + (System.currentTimeMillis() - startTime));
        //log.info("finished hashing: " + ret);
        return ret;
    }

    public List<Boolean> executeCheckPassword(List<String> password, List<String> hash) {
        //log.info("password " + password);
        //log.info("hash " + hash);
        updateTimestamp();
        int passwordListSize = password.size();
        assert passwordListSize == hash.size();
        List<Boolean> ret = new ArrayList<>(passwordListSize);

		/*
		multi thread BE client
		 */

        while (ret.size() < passwordListSize) ret.add(false);

        //int threadNumber = Runtime.getRuntime().availableProcessors();
        int threadSize = (int) Math.ceil(1.0 * passwordListSize / NUM_PROCESSORS);
        int threadNumber = (int) Math.ceil(1.0 * passwordListSize / threadSize);

        CountDownLatch latch = new CountDownLatch(threadNumber);

        //long startTime = System.currentTimeMillis();
        class CheckThread implements Runnable {
            int startIndex, endIndex;

            CheckThread(int startIndex, int endIndex) {
                this.startIndex = startIndex;
                this.endIndex = endIndex;
            }

            public void run() {
                for (int index = startIndex; index < endIndex && index < passwordListSize; index++) {
                    boolean result;
                    try {
                        result = BCrypt.checkpw(password.get(index), hash.get(index));
                    } catch (Exception e) {
                        result = false;
                    }
                    ret.set(index, result);
                }

                latch.countDown();
            }
        }

        for (int index = 0; index < threadNumber; index++) {
            int startIndex = index * threadSize;
            int endIndex = Math.min(startIndex + threadSize, passwordListSize);
            Thread thread = new Thread(new CheckThread(startIndex, endIndex));
            thread.start();
        }

        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //log.info("multithread checking elapsed time: " + (System.currentTimeMillis() - startTime));

        //log.info("finished checking: " + ret);
        return ret;
    }

    /*
    ===========================
       BE -> FE RPC functions
    ===========================
     */
    public boolean registerBE(String host, int port) {
        try {
            AddrEntry entry = new AddrEntry(host, port);
            addAddrDict(entry);
            TransportClientConnection transportClientConnection = createSocketConnection(entry);
            log.info("created connection to " + host + " at " + port);
            return testRemoteIsAlive(transportClientConnection, entry);
        } catch (Exception e) {
            if (!(e instanceof IOException)) {
                log.error("Unknown Exception");
            }
            e.printStackTrace();
            return false;
        }
    }

    /*
    ===========================
       FE <-> BE RPC functions
    ===========================
     */
    public String isAlive(String msg) {   // just return true
        return msg;
    }

}
