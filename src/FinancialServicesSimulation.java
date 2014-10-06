import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.InvalidPropertiesFormatException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Created by alexgorbunov on 10/4/14.
 */
class BankAccount {
    private final long id;
    private int balance;

    public BankAccount(long id, int balance) {
        this.id = id;
        this.balance = balance;
    }

    public BankAccount(long id) {
        this(id, 0);
    }

    public void debit(double money) {
        balance += money;
    }

    public void credit(double money) {
        balance -= money;
    }

    public int getBalance() {
        return balance;
    }

    public long getID() {
        return id;
    }

    @Override
    public String toString() {
        return "Account ID: " + getID() + ", balance: " + getBalance();
    }
}

public class FinancialServicesSimulation<T> {
    private static final int TRANSFER_FORMAT_ARGUMENTS = 3;
    private Object lock = new Object();
    private CircularBuffer<T> buffer;

    public FinancialServicesSimulation(final int queueSize) {
        buffer = new CircularBuffer<T>(queueSize);
    }

    public void transfer(final BankAccount fromAccount, final BankAccount toAccount, final int amount) {
        class TransferProcessing {
            public void transfer() throws InsufficientFundsException {
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
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream("src/transfers.txt"), "utf-8")) ) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("somthing wrong occurred!");
        }

        System.out.println(builder);

        String[] lines = generateInitialData(140, 13240);

        //lines = builder.toString().split("\n");

        if (lines.length != 2) {
            throw new RuntimeException("Incorrect data parsed");
        }

        String[] accountsInfo = lines[0].split(" ");
        final BankAccount[] bankAccounts = new BankAccount[ Integer.parseInt(accountsInfo[0]) ];
        for (int i = 0; i < accountsInfo.length - 1; i++) {
            bankAccounts[i] = new BankAccount(i, Integer.valueOf(accountsInfo[i + 1]) );
        }

        int total = 0;
        for (BankAccount account : bankAccounts) {
            //System.out.println(account);
            total += account.getBalance();
        }

        System.out.println("Total sum: " + total);

        String[] transfers = lines[1].split(" ");
        if (transfers.length < 1)
            throw new InvalidPropertiesFormatException("Invalid transfers declaration");

        final int transfersCount = Integer.valueOf(transfers[0]);
        if (transfersCount != (transfers.length - 1) / 3) {
            throw new IllegalArgumentException("Claimed transfers number is not equal to real transfers declaration");
        }


        class TransferTask {
            private final int fromAccountID;
            private final int toAccountID;
            private final int amount;

            public int getFromAccountID() {
                return fromAccountID;
            }

            public int getToAccountID() {
                return toAccountID;
            }

            public int getAmount() {
                return amount;
            }

            TransferTask(int fromAccountID, int toAccountID, int amount) {
                this.fromAccountID = fromAccountID;
                this.toAccountID = toAccountID;
                this.amount = amount;
            }

            @Override
            public String toString() {
                return "from: " + getFromAccountID() + ", to: " + getToAccountID() + ", amount: " + getAmount();
            }
        }

        final FinancialServicesSimulation simulation = new FinancialServicesSimulation<TransferTask>(50);
        final CountDownLatch latch = new CountDownLatch(1);

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
//        Producer producer = new Producer("MainProducer");
//        producer.start();
        Consumer[] consumers = new Consumer[3];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new Consumer("Consumer" + i);
            consumers[i].start();
        }

        latch.countDown();

        int incNumber = 0;
        long start = System.nanoTime();
        for (int i = 0; i < transfersCount; i++) {
            incNumber = i * TRANSFER_FORMAT_ARGUMENTS;
            TransferTask task = new TransferTask(
                    Integer.valueOf(transfers[incNumber + 1]),
                    Integer.valueOf(transfers[incNumber + 2]),
                    Integer.valueOf(transfers[incNumber + 3])
            );
            simulation.put(task);


//            simulation.transfer(
//                    bankAccounts[Integer.valueOf(transfers[incNumber + 1])],
//                    bankAccounts[Integer.valueOf(transfers[incNumber + 2])],
//                    Integer.valueOf(transfers[incNumber + 3])
//            );
        }

        while(simulation.buffer.getSize() != 0) {
            Thread.sleep(20);
        }
        long finish = System.nanoTime();

        for (Consumer consumer : consumers) {
            if (consumer.isAlive())
                consumer.interrupt();
        }

        int finalSum = 0;
        for (BankAccount account : bankAccounts) {
            //System.out.println(account);
            finalSum += account.getBalance();
        }

        System.out.println("Final sum: " + finalSum);
        System.out.println("TIME " + (double)(finish - start)/1_000_000_000 + "sec");
    }


    private static String[] generateInitialData(int accountsNumber, int transfersNumber) {
        Random random = new Random();

        StringBuilder builder = new StringBuilder();
        builder.append(accountsNumber);
        for (int i = 0; i < accountsNumber; i++) {
            builder.append(' ').append(Math.abs(random.nextInt(1500)));
        }

        builder.append('\n');
        builder.append(transfersNumber);

        for (int i = 0; i < transfersNumber; i++) {
            builder.append(' ').append(random.nextInt(accountsNumber)).
                append(' ').append(random.nextInt(accountsNumber)).
                append(' ').append(Math.abs(random.nextInt(1000)));
        }

        System.out.println(builder);

        return builder.toString().split("\n");
    }
}
