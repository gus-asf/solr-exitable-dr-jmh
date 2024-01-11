package org.apache.solr.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark for {@link com.sun.management.ThreadMXBean#getThreadAllocatedBytes(long)}.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime,Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GetThreadAllocatedBytesBenchmark {
  public static int NUM_THREADS = 100;
  public static int MAX_LIST_SIZE = 100;

  private ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
  private com.sun.management.ThreadMXBean sunThreadMXBean;
  private volatile boolean running;
  private volatile int result;
  private static Random random = new Random(1234567890L);
  private Thread[] workers = new Thread[NUM_THREADS];
  private long[] workersAllocated = new long[NUM_THREADS];

  private class Worker implements Runnable {
    List<String> list;
    @Override
    public void run() {
      while (running) {
        result = random.nextInt(Math.abs(result) + 1);
        if (list == null || list.size() > MAX_LIST_SIZE) {
          list = new ArrayList<>();
        }
        list.add(Integer.toBinaryString(result));
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Collect allocations from all worker threads.
   */
  public long[] collectWorkerAllocatedBytes() {
    for (int i = 0; i < NUM_THREADS; i++) {
      workersAllocated[i] = sunThreadMXBean.getThreadAllocatedBytes(workers[i].getId());
    }
    return workersAllocated;
  }

  /**
   * Create a number of worker threads that make some allocations.
   */
  @Setup(Level.Trial)
  public void createThreads() {
    if (threadMXBean instanceof com.sun.management.ThreadMXBean) {
      sunThreadMXBean = (com.sun.management.ThreadMXBean) threadMXBean;
    } else {
      throw new RuntimeException("ThreadMXBean is an instance of " + threadMXBean.getClass().getName());
    }
    running = true;
    for (int i = 0; i < NUM_THREADS; i++) {
      workers[i] = new Thread(new Worker());
      workers[i].start();
    }
  }

  @TearDown(Level.Trial)
  public void closeThreads() {
    running = false;
  }

  /**
   * Results from benchmarking this method should be divided by {@link #NUM_THREADS}.
   */
  @Benchmark
  public void getThreadAllocatedBytes(Blackhole bh) {
    if (!sunThreadMXBean.isThreadAllocatedMemoryEnabled()) {
      throw new RuntimeException("threadAllocatedMemoryEnabled == false");
    }
    bh.consume(collectWorkerAllocatedBytes());
  }
}
