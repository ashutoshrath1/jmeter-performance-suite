package com.jmeter.suite.runtime;

import com.jmeter.suite.util.Log;
import org.apache.jmeter.engine.DistributedRunner;
import org.apache.jmeter.samplers.Remoteable;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jorphan.collections.HashTree;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a test plan across remote JMeter servers instead of in this JVM.
 *
 * <p>A single JVM caps out somewhere in the low thousands of threads. Past that the load generator
 * becomes the bottleneck and the numbers describe the generator rather than the system under test.
 * Distributed mode spreads the load across hosts running {@code jmeter-server}.
 *
 * <p>Unlike {@code StandardJMeterEngine.run()}, distributed execution is asynchronous: starting
 * returns immediately and each host reports completion separately, so this waits for all of them.
 */
public final class DistributedTestRunner {

    /** How long to wait for every host to report completion before giving up. */
    private static final long COMPLETION_TIMEOUT_HOURS = 12;

    /**
     * Prevents instantiation of this utility type.
     */
    private DistributedTestRunner() {
    }

    /**
     * Runs the plan on every host and blocks until all of them report completion.
     *
     * @throws IllegalStateException when no host completes within the timeout
     */
    public static void run(HashTree testPlanTree, List<String> hosts, Properties remoteProperties)
            throws InterruptedException {
        CompletionListener listener = new CompletionListener();
        CompletionListener.reset(hosts.size());

        // The listener joins the test tree so the remote engines notify it. The whole tree is
        // serialized over RMI, so it must be a serializable TestElement; Remoteable keeps the live
        // instance on this side rather than shipping the callback to the servers.
        testPlanTree.add(testPlanTree.getArray()[0], listener);

        // Properties travel with the run; without them remote engines resolve their own defaults.
        DistributedRunner runner = new DistributedRunner(remoteProperties);
        runner.setStdout(System.out);
        runner.setStdErr(System.err);
        runner.init(hosts, testPlanTree);

        Log.info("Starting distributed run across " + hosts.size() + " host(s): " + hosts);
        runner.start();

        if (!CompletionListener.awaitAll(COMPLETION_TIMEOUT_HOURS, TimeUnit.HOURS)) {
            throw new IllegalStateException(
                    "Distributed run did not complete within " + COMPLETION_TIMEOUT_HOURS + "h");
        }
        Log.info("All remote hosts finished.");
    }

    /**
     * Counts host completions and releases waiters once the last host finishes.
     *
     * <p>The latch is static because JMeter clones and serializes test elements freely: the instance
     * receiving {@code testEnded} is not necessarily the one that was constructed here. Runs are
     * sequential, so a single shared latch is sufficient, and {@link #reset(int)} arms it per run.
     */
    public static final class CompletionListener extends AbstractTestElement
            implements TestStateListener, Remoteable {

        private static final long serialVersionUID = 1L;

        private static transient CountDownLatch finished = new CountDownLatch(0);
        private static transient AtomicInteger outstanding = new AtomicInteger(0);

        /**
         * Arms the latch for a run spanning the given number of hosts.
         */
        static synchronized void reset(int hostCount) {
            outstanding = new AtomicInteger(hostCount);
            finished = new CountDownLatch(1);
        }

        /**
         * Waits for every host to finish, returning false on timeout.
         */
        static boolean awaitAll(long timeout, TimeUnit unit) throws InterruptedException {
            return finished.await(timeout, unit);
        }

        /**
         * Records that a host has begun executing.
         */
        @Override
        public void testStarted(String host) {
            Log.info("Remote host started: " + host);
        }

        /**
         * Ignored: distributed runs always report a host.
         */
        @Override
        public void testStarted() {
            // no-op
        }

        /**
         * Releases the latch once every host has reported completion.
         */
        @Override
        public void testEnded(String host) {
            Log.info("Remote host finished: " + host);
            signalOne();
        }

        /**
         * Treated as a completion signal for runs that report without a host.
         */
        @Override
        public void testEnded() {
            signalOne();
        }

        /**
         * Decrements the outstanding count and releases waiters when it reaches zero.
         */
        private static void signalOne() {
            if (outstanding.decrementAndGet() <= 0) {
                finished.countDown();
            }
        }
    }
}
