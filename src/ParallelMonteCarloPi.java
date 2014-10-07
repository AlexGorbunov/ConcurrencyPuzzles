import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Oleksandr_Gorbunov on 9/23/2014.
 */
public class ParallelMonteCarloPi {
    public static final int ITERATIONS_COUNT = 1_000_000_000;
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1)
            throw new IllegalArgumentException("" +
                    "Incorect parameters count. Expected count is 1 that is assumed as threads count," +
                    "but current is " + args.length);

        final int threadsCount = Integer.parseInt(args[0]);
        final AtomicInteger caughtValues = new AtomicInteger(0);

        //------------------------------------------------
        final int threshold = ITERATIONS_COUNT/ threadsCount;
        //final CountDownLatch latch = new CountDownLatch(1);

        /*final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Random rand = new Random();
                int localCaught = 0;

                double x = 0., y = 0.;
                for (int i = 0; i < threshold; i++) {
                    x = rand.nextDouble();
                    y = rand.nextDouble();
                    double length = Math.sqrt( x * x + y * y );
                    if (length <= 1.d) {
                        localCaught++;
                    }
                }

                caughtValues.addAndGet(localCaught);
            }
        };*/
        ThreadGroup group = new ThreadGroup("WorkerThreadGroup");
        Thread[] workers = new Thread[threadsCount];
        long startTime = System.nanoTime();
        for (int k = 0; k < threadsCount; k++) {
            workers[k] = new Thread(group, new Runnable() {
                @Override
                public void run() {
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    int localCaught = 0;

                    double x = 0., y = 0.;
                    for (int i = 0; i < threshold; i++) {
                        x = rand.nextDouble();
                        y = rand.nextDouble();
                        double length = Math.sqrt( x * x + y * y );
                        if (length <= 1.d) {
                            localCaught++;
                        }
                    }

                    caughtValues.addAndGet(localCaught);
                }
            });
            workers[k].start();
        }

        for (Thread thread : workers)
            thread.join();
        long endTime = System.nanoTime();

        double result = ((double)caughtValues.get() / ITERATIONS_COUNT) * 4;

        System.out.println("PI is " + result);
        System.out.println("THREADS " + threadsCount);
        System.out.println("ITERATIONS " + ITERATIONS_COUNT);
        System.out.println("TIME " + (double)(endTime - startTime)/1_000_000_000 + "sec");
    }
}
