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
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark for {@link ThreadMXBean#getThreadCpuTime(long)}.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime,Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GetThreadCpuTimeBenchmark {
  public static int NUM_THREADS = 100;

  private ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
  private volatile boolean running;
  private volatile int result;
  private static Random random = new Random(1234567890L);
  private Thread[] workers = new Thread[NUM_THREADS];
  private long[] workersCpuTime = new long[NUM_THREADS];

  private class Worker implements Runnable {
    @Override
    public void run() {
      while (running) {
        result = random.nextInt(Math.abs(result) + 1);
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Collect CPU time from all worker threads.
   */
  public long[] collectWorkerCpuTime() {
    for (int i = 0; i < NUM_THREADS; i++) {
      workersCpuTime[i] = threadMXBean.getThreadCpuTime(workers[i].getId());
    }
    return workersCpuTime;
  }

  /**
   * Create a number of worker threads that do some work.
   */
  @Setup(Level.Trial)
  public void createThreads() {
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
  public void getCpuThreadTime(Blackhole bh) {
    if (!threadMXBean.isThreadCpuTimeEnabled()) {
      throw new RuntimeException("threadCpuTimeEnabled == false");
    }
    bh.consume(collectWorkerCpuTime());
  }
}
