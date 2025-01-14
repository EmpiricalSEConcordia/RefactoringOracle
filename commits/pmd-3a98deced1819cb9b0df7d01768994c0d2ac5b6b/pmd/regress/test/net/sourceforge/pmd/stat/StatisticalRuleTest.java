package test.net.sourceforge.pmd.stat;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.stat.DataPoint;
import net.sourceforge.pmd.stat.Metric;
import net.sourceforge.pmd.stat.StatisticalRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * This class tests the Statistical Rules in PMD.
 *
 * The idea is, that we fill up 999 datapoints into
 * the Stat Rule, and then throw random parameters
 * at it.
 *
 * The three parameters which are checked are:
 * 		sigma - # Sigmas over the mean.
 * 		topscore - Only the top 5 or so items.
 * 		minimum - Only things of score 10 or better
 *
 * When more than one parameter is lumped together, then
 * we expect the one which would return the fewest to
 * determine what gets sent back.
 *
 * So, we throw each collection of parameters, where each
 * one is a different order into the system.  We check the
 * results off of what the smallest value should be.
 *
 * If you are going to work with StatisticalRule any, please
 * bump the "NUM_TESTS" number up to something like 128.  That
 * way you are more likely to identify problems.  It is set low
 * now to make building and running tests easier (when we aren't
 * touching the file.)
 *
 * Note also, that when verifying the Sigma, I wasn't quite able
 * to determine how many results it would return (it would vary
 * from -2 to 2 of what I expected.)  That is what the delta
 * parameter on the verify method takes.  If you can figure it
 * out exactly, (without stealing code from the StatRule) then
 * feel free to change it and tighten the deltas.
 */
public class StatisticalRuleTest extends TestCase {
    private DataPoint points[] = new DataPoint[1000];
    private MockStatisticalRule IUT = null;
    private String testName = null;
    private Random random = new Random();

    public static final double MAX_MINIMUM = 1000.0;
    public static final double NO_MINIMUM = -1.0;
    public static final double MAX_SIGMA = 5.0;
    public static final double NO_SIGMA = -1.0;
    public static final int MIN_TOPSCORE = 0;
    public static final int NO_TOPSCORE = -1;


    public static final double MEAN = 499.5;
    public static final double SIGMA = 288.6750;
    public static final int NUM_TESTS = 10;

    public static final double DELTA = 0.005;

    public StatisticalRuleTest(String name) {
        super(name);
        this.testName = name;
    }

    public void setUp() {
        IUT = new MockStatisticalRule();
        if (testName.endsWith("0")) {
            for (int i = 0; i < 1000; i++) {
                points[i] = new DataPoint();
                points[i].setScore(1.0 * i);
                points[i].setLineNumber(i);
                points[i].setMessage("DataPoint[" + Integer.toString(i) + "]");

                IUT.addDataPoint(points[i]);
            }
        } else if (testName.endsWith("1")) {
            for (int i = 999; i >= 0; i--) {
                points[i] = new DataPoint();
                points[i].setScore(1.0 * i);
                points[i].setLineNumber(i);
                points[i].setMessage("DataPoint[" + Integer.toString(i) + "]");

                IUT.addDataPoint(points[i]);
            }
        } else {
            List lPoints = new ArrayList();
            for (int i = 0; i < 1000; i++) {
                DataPoint point = new DataPoint();
                point.setScore(1.0 * i);
                point.setLineNumber(i);
                point.setMessage("DataPoint[" + Integer.toString(i) + "]");

                lPoints.add(point);
            }

            Collections.shuffle(lPoints);
            for (int i = 0; i < 1000; i++) {
                IUT.addDataPoint((DataPoint) lPoints.get(i));
            }
        }

    }

    /**
     * This test verifies that the Stat rule creates a Metric,
     * with the proper values.
     */
    public void testMetrics() throws Throwable {
        Report report = makeReport(IUT);
        Iterator metrics = report.metrics();

        assertTrue(metrics.hasNext());
        Object o = metrics.next();

        assertTrue(o instanceof Metric);
        Metric m = (Metric) o;

        assertEquals("test.net.sourceforge.pmd.stat.MockStatisticalRule", m.getMetricName());

        assertEquals(0.0, m.getLowValue(), 0.05);
        assertEquals(999.0, m.getHighValue(), 0.05);
        assertEquals(MEAN, m.getAverage(), 0.05);
        assertEquals(SIGMA, m.getStandardDeviation(), 0.05);
    }

    /**
     * This returns a Random value for Sigma which will
     * return some values.
     */
    public double randomSigma() {
        return random.nextDouble() * 1.0;
    }

    /**
     * This returns a Random value for Sigma which value
     * is greater than the parameter.
     */
    public double randomSigma(int minimum) {
        double minSigma = ((999 - minimum) - MEAN) / SIGMA;

        if ((minSigma <= 0) || (minSigma > 2))
            return randomSigma();

        return minSigma + (random.nextDouble() * (2 - minSigma));
    }

    /**
     * This returns the expected number of results when
     * the Sigma rating is the smallest.
     */
    public int expectedSigma(double sigma) {
        long expectedMin = Math.round(MEAN + (sigma * SIGMA));

        if ((999 - expectedMin) < 0)
            return 0;
        return 999 - (int) expectedMin;
    }

    /**
     * This generates a random minimum value for testing.
     */
    public double randomMinimum() {
        return random.nextDouble() * 999;
    }

    /**
     * This generates a random minimum value for which fewer
     * results would be returned.
     */
    public double randomMinimum(int minimum) {
        double diffTarget = 1.0 * (999 - minimum);
        return (random.nextDouble() * minimum) + diffTarget;
    }

    /**
     * This returns the expected number of reports.
     *
     * If the Minimum comes in at 521.569 then we expect
     * 522, 523, ... 999 will pass.
     */
    public int expectedMinimum(double minimum) {
        Double d = new Double(minimum);
        return 999 - d.intValue();
    }

    public void testExpectedMinimum() {
        for (int i = 0; i < 999; i++) {
            assertEquals("Integer Min", 999 - i, expectedMinimum(i * 1.0));
            assertEquals("Double Min", 999 - i, expectedMinimum((i * 1.0) + 0.5));
        }
    }

    /**
     * This returns a random value for Top Score.
     */
    public int randomTopScore() {
        return random.nextInt(999);
    }

    /**
     * This will return a random value for the Top Score
     * which will return more than the minimum provided.
     */
    public int randomTopScore(double target) {
        if (target < 0)
            return 0;

        return random.nextInt((new Double(target)).intValue());
    }

    /**
     * This will return the expected number of results
     * with the given Top Score.
     */
    public int expectedTopScore(int target) {
        return target;
    }

    // Test Single Datapoint
    public void testSingleDatapoint() {
        StatisticalRule IUT = new MockStatisticalRule();

        DataPoint point = new DataPoint();
        point.setScore(1001.0);
        point.setLineNumber(1001);
        point.setMessage("SingleDataPoint");

        IUT.addProperty("minimum", "1000");

        IUT.addDataPoint(point);

        Report report = makeReport(IUT);

        assertEquals("Expecting only one result.", 1, report.size());
    }

    // Okay, we have three properties we need to
    // test in Combination:
    //  S = Sigma
    //  T = Top Score
    //  M = Minimum
    //
    // They are listed in decreasing order of what
    // to expect.
    //
    // Thus testSM() should have the Sigma less than
    // the minimum, so we expect the Minimum # of results.
    //

    public void testS() throws Throwable {
        verifyResults(MAX_SIGMA, NO_MINIMUM, NO_TOPSCORE, 0, 2);

        for (int i = 0; i < NUM_TESTS; i++) {
            double sigma = randomSigma();
            verifyResults(sigma, -1.0, -1, expectedSigma(sigma), 2);
        }
    }

    public void testS1() throws Throwable {
        testS();
    }

    public void testS2() throws Throwable {
        testS();
    }

    public void testS3() throws Throwable {
        testS();
    }

    public void testS4() throws Throwable {
        testS();
    }

    public void testS5() throws Throwable {
        testS();
    }


    public void testT() throws Throwable {
        verifyResults(NO_SIGMA, NO_MINIMUM, MIN_TOPSCORE, 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            int topScore = randomTopScore();
            verifyResults(-1.0, -1.0, topScore, expectedTopScore(topScore), 0);
        }
    }

    public void testT1() throws Throwable {
        testT();
    }

    public void testT2() throws Throwable {
        testT();
    }

    public void testT3() throws Throwable {
        testT();
    }

    public void testT4() throws Throwable {
        testT();
    }

    public void testT5() throws Throwable {
        testT();
    }

    public void testM() throws Throwable {
        verifyResults(NO_SIGMA, MAX_MINIMUM, NO_TOPSCORE, 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            double minimum = randomMinimum();
            verifyResults(-1.0, minimum, -1, expectedMinimum(minimum), 0);
        }
    }

    public void testM1() throws Throwable {
        testM();
    }

    public void testM2() throws Throwable {
        testM();
    }

    public void testM3() throws Throwable {
        testM();
    }

    public void testM4() throws Throwable {
        testM();
    }

    public void testM5() throws Throwable {
        testM();
    }

    public void testST() throws Throwable {
        verifyResults(randomSigma(), NO_MINIMUM, MIN_TOPSCORE, 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            double sigma = randomSigma();
            int topScore = randomTopScore(expectedSigma(sigma));

            verifyResults(sigma, NO_MINIMUM, topScore, expectedTopScore(topScore), 0);
        }
    }

    public void testST1() throws Throwable {
        testST();
    }

    public void testST2() throws Throwable {
        testST();
    }

    public void testST3() throws Throwable {
        testST();
    }

    public void testST4() throws Throwable {
        testST();
    }

    public void testST5() throws Throwable {
        testST();
    }

    public void testTS() throws Throwable {
        verifyResults(MAX_SIGMA, NO_MINIMUM, randomTopScore(), 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            int topScore = randomTopScore();
            double sigma = randomSigma(expectedTopScore(topScore));

            verifyResults(sigma, -1.0, topScore, expectedSigma(sigma), 2);
        }
    }

    public void testTS1() throws Throwable {
        testTS();
    }

    public void testTS2() throws Throwable {
        testTS();
    }

    public void testTS3() throws Throwable {
        testTS();
    }

    public void testTS4() throws Throwable {
        testTS();
    }

    public void testTS5() throws Throwable {
        testTS();
    }

    public void testSM() throws Throwable {
        verifyResults(randomSigma(), MAX_MINIMUM, NO_TOPSCORE, 0, 0);
        for (int i = 0; i < NUM_TESTS; i++) {
            double sigma = randomSigma();
            double minimum = randomMinimum(expectedSigma(sigma));

            verifyResults(sigma, minimum, -1, expectedMinimum(minimum), 0);
        }

    }

    public void testSM1() throws Throwable {
        testSM();
    }

    public void testSM2() throws Throwable {
        testSM();
    }

    public void testSM3() throws Throwable {
        testSM();
    }

    public void testSM4() throws Throwable {
        testSM();
    }

    public void testSM5() throws Throwable {
        testSM();
    }


    public void testMS() throws Throwable {
        verifyResults(MAX_SIGMA, randomMinimum(), NO_TOPSCORE, 0, 0);
        for (int i = 0; i < NUM_TESTS; i++) {
            double minimum = randomMinimum();
            double sigma = randomSigma(expectedMinimum(minimum));

            verifyResults(sigma, minimum, -1, expectedSigma(sigma), 2);
        }
    }

    public void testMS1() throws Throwable {
        testMS();
    }

    public void testMS2() throws Throwable {
        testMS();
    }

    public void testMS3() throws Throwable {
        testMS();
    }

    public void testMS4() throws Throwable {
        testMS();
    }

    public void testMS5() throws Throwable {
        testMS();
    }


    public void testTM() throws Throwable {
        verifyResults(NO_SIGMA, MAX_MINIMUM, randomTopScore(), 0, 0);
        for (int i = 0; i < NUM_TESTS; i++) {
            int topScore = randomTopScore();
            double minimum = randomMinimum(expectedTopScore(topScore));

            verifyResults(NO_SIGMA, minimum, topScore, expectedMinimum(minimum), 0);
        }
    }

    public void testTM1() throws Throwable {
        testTM();
    }

    public void testTM2() throws Throwable {
        testTM();
    }

    public void testTM3() throws Throwable {
        testTM();
    }

    public void testTM4() throws Throwable {
        testTM();
    }

    public void testTM5() throws Throwable {
        testTM();
    }


    public void testMT() throws Throwable {
        verifyResults(NO_SIGMA, randomMinimum(), MIN_TOPSCORE, 0, 0);
        for (int i = 0; i < NUM_TESTS; i++) {
            double minimum = randomMinimum();
            int topScore = randomTopScore(expectedMinimum(minimum));

            verifyResults(NO_SIGMA, minimum, topScore, expectedTopScore(topScore), 0);
        }
    }

    public void testMT1() throws Throwable {
        testMT();
    }

    public void testMT2() throws Throwable {
        testMT();
    }

    public void testMT3() throws Throwable {
        testMT();
    }

    public void testMT4() throws Throwable {
        testMT();
    }

    public void testMT5() throws Throwable {
        testMT();
    }


    public void testSTM() throws Throwable {
        double sigma = randomSigma();
        verifyResults(sigma, MAX_MINIMUM, randomTopScore(expectedSigma(sigma)), 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            sigma = randomSigma();
            int topScore = randomTopScore(expectedSigma(sigma));
            double minimum = randomMinimum(expectedTopScore(topScore));

            verifyResults(sigma, minimum, topScore, expectedMinimum(minimum), 0);
        }
    }

    public void testSTM1() throws Throwable {
        testSTM();
    }

    public void testSTM2() throws Throwable {
        testSTM();
    }

    public void testSTM3() throws Throwable {
        testSTM();
    }

    public void testSTM4() throws Throwable {
        testSTM();
    }

    public void testSTM5() throws Throwable {
        testSTM();
    }

    public void testSMT() throws Throwable {
        double sigma = randomSigma();
        verifyResults(sigma, randomMinimum(expectedSigma(sigma)), MIN_TOPSCORE, 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            sigma = randomSigma();
            double minimum = randomMinimum(expectedSigma(sigma));
            int topScore = randomTopScore(expectedMinimum(minimum));

            verifyResults(sigma, minimum, topScore, expectedTopScore(topScore), 0);
        }
    }

    public void testSMT1() throws Throwable {
        testSMT();
    }

    public void testSMT2() throws Throwable {
        testSMT();
    }

    public void testSMT3() throws Throwable {
        testSMT();
    }

    public void testSMT4() throws Throwable {
        testSMT();
    }

    public void testSMT5() throws Throwable {
        testSMT();
    }

    public void testTSM() throws Throwable {
        int topScore = randomTopScore();
        verifyResults(randomSigma(expectedTopScore(topScore)), MAX_MINIMUM, topScore, 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            topScore = randomTopScore();
            double sigma = randomSigma(expectedTopScore(topScore));
            double minimum = randomMinimum(expectedSigma(sigma));

            verifyResults(sigma, minimum, topScore, expectedMinimum(minimum), 0);
        }
    }

    public void testTSM1() throws Throwable {
        testTSM();
    }

    public void testTSM2() throws Throwable {
        testTSM();
    }

    public void testTSM3() throws Throwable {
        testTSM();
    }

    public void testTSM4() throws Throwable {
        testTSM();
    }

    public void testTSM5() throws Throwable {
        testTSM();
    }

    public void testTMS() throws Throwable {
        int topScore = randomTopScore();
        verifyResults(MAX_SIGMA, randomMinimum(expectedTopScore(topScore)), topScore, 0, 0);

        for (int i = 0; i < NUM_TESTS; i++) {
            topScore = randomTopScore();
            double minimum = randomMinimum(expectedTopScore(topScore));
            double sigma = randomSigma(expectedMinimum(minimum));

            verifyResults(sigma, minimum, topScore, expectedSigma(sigma), 2);
        }
    }

    public void testTMS1() throws Throwable {
        testTMS();
    }

    public void testTMS2() throws Throwable {
        testTMS();
    }

    public void testTMS3() throws Throwable {
        testTMS();
    }

    public void testTMS4() throws Throwable {
        testTMS();
    }

    public void testTMS5() throws Throwable {
        testTMS();
    }

    /**
     * Verifies what happens when you pass these parameters
     * into the thing.  DELTA is the amount of error allowed.
     * Usually DELTA is only used for Sigma, as we really can't
     * calculate it exactly.
     */

    public void verifyResults(double sigma, double minimum, int topScore, int expected, int delta) {
        try {
            setUp();
            if (sigma >= 0) {
                IUT.addProperty("sigma", Double.toString(sigma));
            }

            if (minimum >= 0) {
                IUT.addProperty("minimum", Double.toString(minimum));
            }

            if (topScore >= 0) {
                IUT.addProperty("topscore", Integer.toString(topScore));
            }

            Report report = makeReport(IUT);
            if (delta == 0) {
                assertEquals("Unexpected number of results: sigma= " + Double.toString(sigma) + " min= " + Double.toString(minimum) + " topscore= " + Integer.toString(topScore), expected, report.size());
            } else {
                String assertStr = "Unexpected number of results: sigma= " + Double.toString(sigma) + " min= " + Double.toString(minimum) + " topscore= " + Integer.toString(topScore) + " expected= " + Integer.toString(expected) + " +/- " + Integer.toString(delta) + " actual-result= " + report.size();

                assertTrue(assertStr, report.size() >= (expected - delta));
                assertTrue(assertStr, report.size() <= (expected + delta));
            }
        } catch (AssertionFailedError afe) {
            System.err.println("******** " + testName + " ***********");
            if (sigma != NO_SIGMA) {
                System.err.println("SIGMA: " + Double.toString(sigma) + " EXPECT: " + Integer.toString(expectedSigma(sigma)));
            }

            if (minimum != NO_MINIMUM) {
                System.err.println("MIN: " + Double.toString(minimum) + " EXPECT: " + Integer.toString(expectedMinimum(minimum)));
            }

            if (topScore != NO_TOPSCORE) {
                System.err.println("TOP: " + Integer.toString(topScore) + " EXPECT: " + Integer.toString(expectedTopScore(topScore)));
            }

            throw afe;

        }
    }

    public Report makeReport(Rule IUT) {
        List list = new ArrayList();
        Report report = new Report();

        RuleContext ctx = new RuleContext();
        ctx.setReport(report);
        ctx.setSourceCodeFilename(testName);

        IUT.apply(list, ctx);

        return report;
    }
}
