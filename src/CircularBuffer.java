import java.util.Queue;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Oleksandr_Gorbunov on 9/29/2014.
 */
public class CircularBuffer<T> {
    private final Queue<T> tasksQueue;
    private final int COUNT;

    private final Lock lock = new ReentrantLock();
    private final Condition fullCondition = lock.newCondition();
    private final Condition emptyCondition = lock.newCondition();

    public CircularBuffer(final int count) {
        COUNT = count;
        this.tasksQueue = new LinkedList<>();
    }

    public int getSize() {
        return tasksQueue.size();
    }

    public boolean addElement(T element) throws InterruptedException {
        lock.lock();
        try {
            while (isFull()) {
                emptyCondition.await();
            }

            boolean result = tasksQueue.add(element);
            fullCondition.signalAll();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public T removeElement() throws InterruptedException {
        lock.lock();
        try {
            while ( tasksQueue.isEmpty() ) {
                fullCondition.await();
            }

            T result = tasksQueue.remove();
            emptyCondition.signalAll();
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return tasksQueue.toString();
    }

    public boolean isFull() {
        return tasksQueue.size() == COUNT;
    }

    public boolean isEmpty() {
        return tasksQueue.isEmpty();
    }

    public static void main(String[] args) {
        final CircularBuffer<Integer> cb = new CircularBuffer<>(23);

        final Random randomizer = new Random();
        final int MAX_MARGIN = 120_000;
        final CountDownLatch latch = new CountDownLatch(1);

        class Producer extends Thread {
            private volatile boolean valid = true;

            public Producer(String name) {
                super(name);
            }

            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while(valid) {
                    Integer element = randomizer.nextInt(MAX_MARGIN);
                    try {
                        cb.addElement(element);
                        System.out.println(Thread.currentThread().getName() + ": Element " + element + " added. Queue size: " + cb.getSize());
                        Thread.sleep(randomizer.nextInt(MAX_MARGIN) % 2_000);
                    } catch (InterruptedException e) {
                        valid = false;
                        System.err.println("EXCEPTION HANDLED in Thread " + Thread.currentThread().getName());
                    }
                }
            }
        }

        class Consumer extends Thread {
            private volatile boolean valid = true;
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
                    Integer element = null;
                    try {
                        element = cb.removeElement();
                        System.out.println(Thread.currentThread().getName() + ": Element " + element + " removed. Queue size: " + cb.getSize());
                        Thread.sleep(randomizer.nextInt(MAX_MARGIN) % 3_000);
                    } catch (InterruptedException e) {
                        valid = false;
                        System.err.println("EXCEPTION HANDLED in Thread " + Thread.currentThread().getName());
                    }
                }
            }
        }

        Producer[] producers = new Producer[5];
        for (int i = 0; i < producers.length; i++) {
            producers[i] = new Producer("Producer_" + i);
            producers[i].start();
        }

        Consumer[] consumers = new Consumer[3];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new Consumer("Consumer_" + i);
            consumers[i].start();
        }

        latch.countDown();

        for (int k = 0; k < 4; k++) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                System.err.println(e.getCause());
            }
            producers[k].interrupt();
        }

        try {
            Thread.sleep(15_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Producer producer : producers) {
            if (producer.isAlive())
                producer.interrupt();
        }

        for (Consumer consumer : consumers) {
            if (consumer.isAlive())
                consumer.interrupt();
        }

        System.out.println(cb);
    }
}
