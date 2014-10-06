import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Alex on 26.09.2014.
 */
public class SinSeriesSimulation {
    private static final int ITERATIONS_COUNT = 100_000_000;
    private static int threadsCount = 1;

    public static void main(String[] args) {
        if (args.length != 1)
            throw new IllegalArgumentException("Incorect parameters count. Expected count is 1 that is assumed as threads count," +
                    "but current is " + args.length);
        threadsCount = Integer.parseInt(args[0]);

        /*List<Future<Double>> futures = new ArrayList<>();
        ExecutorService service = Executors.newFixedThreadPool(threadsCount);

        long startTime = System.nanoTime();
        for (int i = 0; i < threadsCount; i++) {
            final int item = i;
            futures.add(
                    service.submit(
                            new Callable<Double>() {
                                @Override
                                public Double call() throws Exception {
                                    double sum = 0.d;

                                    for (int k = -1 * ITERATIONS_COUNT + item; k < ITERATIONS_COUNT; k += threadsCount ) {
                                        sum += Math.sin(k);
                                    }

                                    return sum;
                                }
                            } ));
        }

        service.shutdown();

        double totalSinSum = 0.d;
        for (Future<Double> future : futures) {
            try {
                totalSinSum += future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        long endTime = System.nanoTime();

        while(!service.isTerminated()) {}*/


        //--------------------------------------------------------
        Thread[] threads = new Thread[threadsCount];

        final AtomicReference<Double> atomicSumRef = new AtomicReference<>(0.d);
        final CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < threadsCount; i++) {
            final int item = i;

            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    double sum = 0.d;

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    for (int k = -1 * ITERATIONS_COUNT + item; k < ITERATIONS_COUNT + 1; k += threadsCount ) {
                        sum += Math.sin(k);
                    }

                    synchronized (atomicSumRef) {
                        atomicSumRef.set(atomicSumRef.get() + sum);
                    }
                }
            });
            threads[i].start();
        }

        long startTime = System.nanoTime();
        latch.countDown();

        for (Thread t : threads)
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        long endTime = System.nanoTime();

        double totalSinSum = atomicSumRef.get();

        System.out.println("Total sum of sinus series from: " + -ITERATIONS_COUNT + " to " + ITERATIONS_COUNT + " is: " + totalSinSum);
        System.out.println("THREADS " + threadsCount);
        System.out.println("ITERATIONS " + ITERATIONS_COUNT);
        System.out.println("TIME " + (double)(endTime - startTime)/1_000_000_000 + "sec");
    }

}
