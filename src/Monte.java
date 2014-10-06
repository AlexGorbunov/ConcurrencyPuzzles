import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Oleksandr_Gorbunov on 9/23/2014.
 */
public class Monte {
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1)
            throw new IllegalArgumentException("Incorect parameters count. Expected count is 1 that is assumed as threads count," +
                    "but current is " + args.length);
        final int threadsCount = Integer.parseInt(args[0]);
        final int iterationsCount = 1_000_000_000;

        final AtomicInteger caughtValues = new AtomicInteger(0);

        final CountDownLatch latch = new CountDownLatch(1);
        class MonteCarloThread extends Thread {
            private int localCaught = 0;
            private int offset;

            public MonteCarloThread(int offset) {
                this.offset = offset;
            }

            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                double x = 0., y = 0.;
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                for (int i = offset; i < iterationsCount; i += threadsCount) {
                    x = rand.nextDouble();
                    y = rand.nextDouble();
                    double length = Math.sqrt( x * x + y * y );
                    if (length <= 1.d) {
                        localCaught++;
                    }
                }
                caughtValues.addAndGet(localCaught);
            }
        }

        Thread[] realThreads = new Thread[threadsCount];
        for (int k = 0; k < threadsCount; k++) {
            realThreads[k] = new MonteCarloThread(k);
            realThreads[k].start();
        }
        long startTime = System.nanoTime();
        latch.countDown();
        for (Thread thread : realThreads)
            thread.join();
        long endTime = System.nanoTime();
        double result = ((double)caughtValues.get() / iterationsCount);

        System.out.println("PI is " + (result * 4));
        System.out.println("THREADS " + threadsCount);
        System.out.println("ITERATIONS " + iterationsCount);
        System.out.println("TIME " + (double)(endTime - startTime)/1_000_000_000 + "sec");
    }
}
