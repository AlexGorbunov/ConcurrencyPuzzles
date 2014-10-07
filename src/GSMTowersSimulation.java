import com.sun.org.apache.xpath.internal.SourceTree;

import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by alexgorbunov on 10/6/14.
 */
class GSMTower {
    private final int x;
    private final int y;
    private int actionRadius;

    public GSMTower(int x, int y, int radius) {
        this.x = x;
        this.y = y;
        this.actionRadius = radius;
    }

    public int getActionRadius() {
        return actionRadius;
    }

    public int getXCoordinate() {
        return x;
    }

    public int getYCoordinate() {
        return y;
    }
}

class GSMCall {
    private final int x;
    private final int y;

    public GSMCall(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getXCoordinate() {
        return x;
    }

    public int getYCoordinate() {
        return y;
    }
}

public class GSMTowersSimulation<T> {
    private static final int TOWER_DESCRIPTION_ARGUMENTS_NUMBER = 3;
    private static final int TOWER_CALL_ARGUMENTS_NUMBER = 2;
    private static final int GSM_CALL_COORDINATES_BOUND = 10_000;
    private static final int GSM_CALL_RADIUS_BOUND = 2_000;
    private static final int PRODUCERS_COUNT = 4;
    private Object lock = new Object();
    private CircularBuffer<T> buffer;

    public GSMTowersSimulation(final int queueSize) {
        this.buffer = new CircularBuffer<T>(queueSize);
    }

    public void put(T element) throws InterruptedException {
        buffer.addElement(element);
    }

    public T take() throws InterruptedException {
        return buffer.removeElement();
    }

    public static void main(String[] args) throws InvalidPropertiesFormatException {
        final String[] input = generateInitialData(200, 400_000);

        if (input.length != 2) {
            throw new RuntimeException("Incorrect data parsed");
        }

        final String[] rawGsmTowers = input[0].split(" ");
        System.out.println("rawGsmTowers length: " + rawGsmTowers.length);
        final GSMTower[] gsmTowers = new GSMTower[ Integer.parseInt(rawGsmTowers[0]) ];
        int offset = 0;
        for (int i = 0; i < gsmTowers.length; i++) {
            offset = i * TOWER_DESCRIPTION_ARGUMENTS_NUMBER;
            gsmTowers[i] = new GSMTower(
                    Integer.valueOf(rawGsmTowers[offset + 1]),
                    Integer.valueOf(rawGsmTowers[offset + 2]),
                    Integer.valueOf(rawGsmTowers[offset + 3])
            );
        }

        final String[] rawGsmCalls = input[1].split(" ");
        if (rawGsmCalls.length < 1)
            throw new InvalidPropertiesFormatException("Invalid transfers declaration");

        final int gsmCallsCount = Integer.valueOf(rawGsmCalls[0]);
        if (gsmCallsCount != (rawGsmCalls.length - 1) / TOWER_CALL_ARGUMENTS_NUMBER) {
            throw new IllegalArgumentException("Claimed transfers number is not equal to real transfers declaration");
        }

        final GSMTowersSimulation simulation = new GSMTowersSimulation<GSMCall>(8_000);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReferenceArray<Integer> atomicResults = new AtomicReferenceArray<Integer>(gsmTowers.length);
        for (int j = 0; j < atomicResults.length(); j++)
            atomicResults.set(j, 0);

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

                for (int i = offset + 1; i < rawGsmCalls.length; i += PRODUCERS_COUNT * TOWER_CALL_ARGUMENTS_NUMBER) {

                    try {
                        simulation.put(new GSMCall(
                                Integer.valueOf(rawGsmCalls[i]),
                                Integer.valueOf(rawGsmCalls[i + 1]))
                        );
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
                        GSMCall gsmCall = (GSMCall) simulation.take();

                        for (int k = 0; k < gsmTowers.length; k++) {
                            GSMTower tower = gsmTowers[k];
                            int resultX = gsmCall.getXCoordinate() - tower.getXCoordinate();
                            int resultY = gsmCall.getYCoordinate() - tower.getYCoordinate();
                            double distance = Math.sqrt(resultX * resultX + resultY * resultY);

                            if (distance < tower.getActionRadius())
                                atomicResults.set(k, atomicResults.get(k).intValue() + 1);
                        }

                    } catch (InterruptedException e) {
                        valid = false;
                        System.err.println("EXCEPTION HANDLED in Thread " + Thread.currentThread().getName());
                    }
                }
            }
        }

        Producer[] producers = new Producer[PRODUCERS_COUNT];
        Consumer[] consumers = new Consumer[PRODUCERS_COUNT];
        for (int i = 0; i < consumers.length; i++) {
            producers[i] = new Producer("Producer", i);
            producers[i].start();
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
            try {
                Thread.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        long finish = System.nanoTime();

        for (Consumer consumer : consumers) {
            if (consumer.isAlive())
                consumer.interrupt();
        }

        for (int i = 0; i < atomicResults.length(); i++) {
            int count = atomicResults.get(i).intValue();
            System.out.println("Tower " + i + " is eligible to handle " + count + " calls.");
        }

        System.out.println("TIME " + (double)(finish - start)/1_000_000_000 + "sec");
    }

    private static String[] generateInitialData(final int towerNumber, final int gsmCalls) {
        List<Future<String>> futures = new ArrayList<>();
        ExecutorService threadPool = Executors.newFixedThreadPool(2);
        futures.add(threadPool.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                StringBuilder builder = new StringBuilder(1_000_000);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                builder.append(towerNumber);
                for (int i = 0; i < towerNumber; i++) {
                    builder.append(' ').append(random.nextInt(-GSM_CALL_COORDINATES_BOUND, GSM_CALL_COORDINATES_BOUND)).
                            append(' ').append(random.nextInt(-GSM_CALL_COORDINATES_BOUND, GSM_CALL_COORDINATES_BOUND)).
                            append(' ').append(random.nextInt(0, GSM_CALL_RADIUS_BOUND));
                }

                return builder.toString();
            }
        }));
        futures.add(threadPool.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                StringBuilder builder = new StringBuilder(10_000_000);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                builder.append(gsmCalls);
                for (int i = 0; i < gsmCalls; i++) {
                    builder.append(' ').append( random.nextInt(-GSM_CALL_COORDINATES_BOUND, GSM_CALL_COORDINATES_BOUND) ).
                            append(' ').append( random.nextInt(-GSM_CALL_COORDINATES_BOUND, GSM_CALL_COORDINATES_BOUND) );
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
