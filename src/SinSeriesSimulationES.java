import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by alexgorbunov on 10/9/14.
 */
public class SinSeriesSimulationES {
    private static final int ITERATIONS_COUNT = 100_000_000;
    private static int threadsCount = 1;

    public static void main(String[] args) {
        if (args.length != 1)
            throw new IllegalArgumentException("Incorect parameters count. Expected count is 1 that is assumed as threads count," +
                    "but current is " + args.length);
        threadsCount = Integer.parseInt(args[0]);

        //--------------------------------------------------------
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadsCount, threadsCount + 4,
                250, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(20_000),
                new ThreadPoolExecutor.AbortPolicy());

        double totalSum = 0.d;
        List<Future<Double>> futures = new ArrayList<>(threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            final int item = i;

            futures.add(
                    executor.submit(
                            new Callable<Double>() {
                                @Override
                                public Double call() throws Exception {
                                    double sum = 0.d;

                                    for (int k = -1 * ITERATIONS_COUNT + item; k < ITERATIONS_COUNT + 1; k += threadsCount) {
                                        sum += Math.sin(k);
                                    }

                                    return sum;
                                }
                            }
                    )
            );

        }

        long startTime = System.nanoTime();

        executor.shutdownNow();
        for (Future<Double> future : futures) {
            try {
                totalSum += future.get().doubleValue();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        long endTime = System.nanoTime();

        System.out.println("Total sum of sinus series from: " + -ITERATIONS_COUNT + " to " +
                ITERATIONS_COUNT + " is: " + String.format("%.6f", totalSum));
        System.out.println("THREADS " + threadsCount);
        System.out.println("ITERATIONS " + ITERATIONS_COUNT);
        System.out.println("TIME " + (double)(endTime - startTime)/1_000_000_000 + "sec");
    }
}
