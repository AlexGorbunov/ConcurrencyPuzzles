import java.io.*;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by alexgorbunov on 10/4/14.
 */

public class FinancialServicesSimulation<T> {
    private static final int TRANSFER_FORMAT_ARGUMENTS = 3;
    private static final int DEFAULT_ACCOUNTS_NUMBER = 300;
    private static final int DEFAULT_TRANSFERS_NUMBER = 4_500_000;
    private static final int TRANSFER_AMOUNT_BOUND = 1_200;
    private static final int PRODUCERS_COUNT = 4;
    private static final int CONSUMERS_COUNT = 4;
    private static final String INPUT_FILE_NAME = "transfers.txt";

    private Object lock = new Object();
    private CircularBuffer<T> buffer;

    public FinancialServicesSimulation(final int queueSize) {
        buffer = new CircularBuffer<T>(queueSize);
    }

    public void transfer(final BankAccount fromAccount, final BankAccount toAccount, final int amount) {
        class TransferProcessing {
            public void transfer() {
                fromAccount.credit(amount);
                toAccount.debit(amount);
            }
        }

        int hashFrom = System.identityHashCode(fromAccount);
        int hashTo = System.identityHashCode(toAccount);

        final TransferProcessing tp = new TransferProcessing();

        try {
            if (hashFrom < hashTo) {
                synchronized (fromAccount) {
                    synchronized (toAccount) {
                        tp.transfer();
                    }
                }
            } else if (hashTo < hashFrom) {
                synchronized (toAccount) {
                    synchronized (fromAccount) {
                        tp.transfer();
                    }
                }
            } else {
                synchronized (lock) {
                    synchronized (toAccount) {
                        synchronized (fromAccount) {
                            tp.transfer();
                        }
                    }
                }
            }
        } catch (Exception exc) {
            System.err.println("Unrecognized financial operation exception");
        }

    }

    public void put(T element) throws InterruptedException {
        buffer.addElement(element);
    }

    public T take() throws InterruptedException {
        return buffer.removeElement();
    }

    public static void main(String[] args) throws InvalidPropertiesFormatException, InterruptedException {
        final String[] input = getInitialData();

        if (input.length != 2) {
            throw new RuntimeException("Incorrect data parsed, Expected value: 2, Current: " + input.length);
        }

        String[] rawAccounts = input[0].split(" ");
        final BankAccount[] bankAccounts = new BankAccount[ Integer.parseInt(rawAccounts[0]) ];
        int offset = 0;
        for (int i = 0; i < bankAccounts.length; i++) {
            bankAccounts[i] = new BankAccount(i, Integer.valueOf(rawAccounts[i + 1]) );
        }
        rawAccounts = null;

        int total = 0;
        for (BankAccount account : bankAccounts) {
            total += account.getBalance();
        }

        System.out.println("Initial total account balance: " + total);

        final String[] rawTransfers = input[1].split(" ");
        if (rawTransfers.length < 1)
            throw new InvalidPropertiesFormatException("Invalid transfers declaration");

        final int transfersCount = Integer.valueOf(rawTransfers[0]);
        if (transfersCount != (rawTransfers.length - 1) / TRANSFER_FORMAT_ARGUMENTS) {
            throw new IllegalArgumentException("Claimed transfers number is not equal to real transfers declaration");
        }

        final FinancialServicesSimulation<TransferTask> simulation = new FinancialServicesSimulation<>(5_000);
        final CountDownLatch latch = new CountDownLatch(1);

        class Producer extends Thread {
            private int offset = 0;
            public Producer(String name, int offset) {
                super(name + offset);
            }

            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (int i = offset + 1; i < rawTransfers.length; i += PRODUCERS_COUNT * TRANSFER_FORMAT_ARGUMENTS) {
                    try {
                        simulation.put( new TransferTask(
                                Integer.valueOf(rawTransfers[i]),
                                Integer.valueOf(rawTransfers[i + 1]),
                                Integer.valueOf(rawTransfers[i + 2]) ));
                    } catch (InterruptedException e) {
                        System.out.println("Thread " + Thread.currentThread().getName() + " interrupted abruptly.");
                    }
                }
            }
        }

        class Consumer extends Thread {
            private boolean valid = true;
            public Consumer(String name) {
                super(name);
            }
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (valid) {
                    try {
                        TransferTask task = (TransferTask) simulation.take();
                        simulation.transfer(bankAccounts[task.getFromAccountID()],
                                bankAccounts[task.getToAccountID()],
                                task.getAmount());
                    } catch (InterruptedException e) {
                        valid = false;
                        System.err.println("EXCEPTION HANDLED in Thread " + Thread.currentThread().getName());
                    }
                }
            }
        }
        Producer[] producers = new Producer[PRODUCERS_COUNT];
        for (int i = 0; i < producers.length; i++) {
            producers[i] = new Producer("Producer", i);
            producers[i].start();
        }
        Consumer[] consumers = new Consumer[CONSUMERS_COUNT];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new Consumer("Consumer" + i);
            consumers[i].start();
        }

        long start = System.nanoTime();
        latch.countDown();

        for (Producer producer : producers) {
            try {
                producer.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        while (simulation.buffer.getSize() != 0) {
            Thread.yield();
        }

        for (Consumer consumer : consumers) {
            if (consumer.isAlive())
                consumer.interrupt();
        }
        long finish = System.nanoTime();

        int finalSum = 0;
        for (BankAccount account : bankAccounts) {
            finalSum += account.getBalance();
        }

        Thread.sleep(30);
        System.out.println("Final total account balance: " + finalSum);
        System.out.println("TIME " + (double)(finish - start)/1_000_000_000 + "sec");
    }


    private static String[] getInitialData() {
        File file = new File(INPUT_FILE_NAME);
        if (!file.exists()) {
            return TransferDataGeneratorHelper.
                    generateInitialData(DEFAULT_ACCOUNTS_NUMBER, DEFAULT_TRANSFERS_NUMBER);
        }

        String[] result = new String[2];
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream( file.getPath() ), "utf-8"))) {
            String line = null;
            for (int i = 0; (line = reader.readLine()) != null; i++) {
                if (i > 1)
                    return TransferDataGeneratorHelper.
                            generateInitialData(DEFAULT_ACCOUNTS_NUMBER, DEFAULT_TRANSFERS_NUMBER);
                result[i] = line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private static class TransferDataGeneratorHelper {
        private static final int DEFAULT_BALANCE_BOUND = 2_500;
        public static String[] generateInitialData(final int accountsNumber, final int transferNumber) {
            List<Future<String>> futures = new ArrayList<>();
            ExecutorService threadPool = Executors.newFixedThreadPool(2);
            futures.add(threadPool.submit(new Callable<String>() {

                @Override
                public String call() throws Exception {
                    StringBuilder builder = new StringBuilder(1_000_000);
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    builder.append(accountsNumber);
                    for (int i = 0; i < accountsNumber; i++) {
                        builder.append(' ').append(random.nextInt(0, DEFAULT_BALANCE_BOUND));
                    }

                    return builder.toString();
                }
            }));
            futures.add(threadPool.submit(new Callable<String>() {

                @Override
                public String call() throws Exception {
                    StringBuilder builder = new StringBuilder(10_000_000);
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    builder.append(transferNumber);
                    for (int i = 0; i < transferNumber; i++) {
                        builder.append(' ').append( random.nextInt(0, accountsNumber) ).
                                append(' ').append( random.nextInt(0, accountsNumber) ).
                                append(' ').append( random.nextInt(0, TRANSFER_AMOUNT_BOUND) );
                    }

                    return builder.toString();
                }
            }));

            threadPool.shutdownNow();
            final String[] result = new String[futures.size()];
            for (int i = 0; i < futures.size(); i++){
                try {
                    result[i] = futures.get(i).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            return result;
        }
    }
}
