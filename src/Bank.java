import javax.naming.InsufficientResourcesException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Alex on 25.09.2014.
 */
class Account {
    private final long id;
    private double balance;

    public Account(long id, double balance) {
        this.id = id;
        this.balance = balance;
    }

    public Account(long id) {
        this(id, 0.d);
    }

    public void debit(double money) {
        balance += money;
    }

    public void credit(double money) {
        balance -= money;
    }

    public double getBalance() {
        return balance;
    }

    public long getID() {
        return id;
    }
}

class InsufficientFundsException extends Exception {
    public InsufficientFundsException(final String message) {
        super(message);
    }
}

public class Bank {
    private static final Object lock = new Object();
    private static final int ACCOUNTS_COUNT = 250;
    private static final int THREADS_COUNT = 2150;

    private String name;
    private final long licence;

    public Bank(String name, long licence) {
        this.name = name;
        this.licence = licence;
    }

    public void transfer(final Account fromAccount, final Account toAccount, final double amount) {
        class TransferProcessing {
            public void transfer() throws InsufficientFundsException {
                if (fromAccount.getBalance() < amount)
                    System.err.println("Insufficient funds on account number: " + fromAccount.getID() +
                            " with balance of " + String.format("%.5f", fromAccount.getBalance()) +
                            " to withdraw " + String.format("%.5f", amount));
                else {
                    fromAccount.credit(amount);
                    toAccount.debit(amount);
                }
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

    public static void main(String[] args) throws InterruptedException {
        final Random randomizer = new Random();

        final Bank bank = new Bank("Native", Math.abs(randomizer.nextLong()));
        final Account[] accounts = new Account[ACCOUNTS_COUNT];

        double totalAmount = 0.d;

        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, randomizer.nextDouble() * 1200.d);
            totalAmount += accounts[i].getBalance();
        }

        final CountDownLatch latch = new CountDownLatch(1);

        class Worker extends Thread {
            @Override
            public void run() {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                bank.transfer(accounts[random.nextInt(0, ACCOUNTS_COUNT)],
                        accounts[random.nextInt(0, ACCOUNTS_COUNT)],
                        random.nextDouble(0, 320.d));
            }
        }

        Thread[] threads = new Thread[THREADS_COUNT];
        for (Thread thread : threads) {
            thread = new Worker();
            thread.start();
        }

        latch.countDown();

        for (Thread thread : threads) {
            if (thread != null && thread.isAlive()) thread.join();
        }

        double finalAmount = 0.d;
        for (int i = 0; i < accounts.length; i++) {
            finalAmount += accounts[i].getBalance();
        }
        System.out.println("Initial bank balance was: " + String.format("%.6f", totalAmount));
        System.out.println("Final bank balance is:    " + String.format("%.6f", finalAmount));
    }

}
