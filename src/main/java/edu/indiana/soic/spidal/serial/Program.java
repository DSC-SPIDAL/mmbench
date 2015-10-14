package edu.indiana.soic.spidal.serial;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import edu.indiana.soic.spidal.Utils;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Program {
    private static String programName = "mmbench";
    private static String opDataPoints = "dataPoints";
    private static String opT = "tpp";
    private static String opP = "ppn";
    private static String opN = "n";
    private static String opRank = "rank";
    private static String opThread = "thread";
    private static String opIterations = "iterations";
    private static String opBlockSize = "blockSize";
    private static String opPrintFreq = "printFreq";
    private static String opInitialPoints = "initialPoints";
    private static String opOutDir = "outputDir";
    private static Options options = new Options();
    static {
        options.addOption(opDataPoints, true, "Number of data points");
        options.addOption(opT, true, "Number of threads per process to mimic");
        options.addOption(opP, true, "Number of MPI processes per node to mimic");
        options.addOption(opN, true, "Number of nodes to mimic");
        options.addOption(opRank, true, "The rank to mimic");
        options.addOption(opThread, true, "The thread to mimic");
        options.addOption(opIterations, true, "Iteration count");
        options.addOption(opBlockSize, true, "Block size");
        options.addOption(opPrintFreq, true, "Print frequency");
        options.addOption(opInitialPoints, true, "Initial points file");
        options.addOption(opOutDir, true, "Outupt dir");
    }

    static int targetDimension = 3;

    public static void main(String[] args)
        throws IOException, InterruptedException {
        Optional<CommandLine> parserResult =
            Utils.parseCommandLineArguments(args, options);

        if (!parserResult.isPresent()) {
            System.out.println("Argument passing failed");
            new HelpFormatter()
                .printHelp(programName, options);
            return;
        }

        System.out.println("\n== " + programName + " run started on " + new Date() + " ==\n");
        CommandLine cmd = parserResult.get();
        if (!(cmd.hasOption(opDataPoints) && cmd.hasOption(opT) && cmd.hasOption(opP) && cmd.hasOption(opN) && cmd.hasOption(opRank) && cmd.hasOption(opThread) && cmd.hasOption(opIterations))){
            new HelpFormatter().printHelp(programName, options);
            return;
        }

        int dataPoints = Integer.parseInt(cmd.getOptionValue(opDataPoints));
        int tpp = Integer.parseInt(cmd.getOptionValue(opT));
        int ppn = Integer.parseInt(cmd.getOptionValue(opP));
        int n = Integer.parseInt(cmd.getOptionValue(opN));
        int rank = Integer.parseInt(cmd.getOptionValue(opRank));
        int thread = Integer.parseInt(cmd.getOptionValue(opThread));
        int iterations = Integer.parseInt(cmd.getOptionValue(opIterations));
        int blockSize = cmd.hasOption(opBlockSize) ? Integer.parseInt(cmd.getOptionValue(opIterations)) : 64;
        int printFreq = cmd.hasOption(opPrintFreq) ? Integer.parseInt(cmd.getOptionValue(opPrintFreq)) : 100;
        String initialPointsFile = cmd.hasOption(opInitialPoints) ? cmd.getOptionValue(opInitialPoints) : "";
        String outDir = cmd.hasOption(opOutDir) ? cmd.getOptionValue(opOutDir) : ".";

        int worldSize = ppn * n;
        int globalColCount = dataPoints;

        // Decompose among processes
        int q = dataPoints / worldSize;
        int r = dataPoints % worldSize;
        int procRowCount = rank < r ? q+1 : q;
        int procRowOffset = rank * q + (rank < r ? rank : r);

        // Decompose among threads
        q = procRowCount / tpp;
        r = procRowCount % tpp;
        int threadRowCount = thread < r ? q+1 : q;
        int threadRowOffset = thread * q + (thread < r ? thread : r);


        Path outputFile = Paths.get(outDir, "mmout." + tpp + "x" + ppn + "x" + n + "." + dataPoints + ".r" + rank  +".t" + thread + "." + getMachineName() + ".txt");
        BufferedWriter bw = Files.newBufferedWriter(
            outputFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        PrintWriter writer = new PrintWriter(bw, true);
        Stopwatch timer = Stopwatch.createUnstarted();
        long[] timings = new long[iterations];
        for (int i = 0; i < iterations; ++i){
            if (i % printFreq == 0){
                System.out.print(" Iterations [" + i + "," + (printFreq + i - 1) + "] ... " );
            }

            timer.start();
            double result = matrixMultiplyWithThreadOffset(threadRowCount, targetDimension, dataPoints, blockSize, threadRowOffset, procRowOffset);
            timer.stop();
            timings[i] = timer.elapsed(TimeUnit.MILLISECONDS);
            timer.reset();
            dummyPrint(result);
            if ((i+1) % printFreq == 0){
                double[] totAndAvg = getTotalAndAverageTiming(timings, i - (printFreq - 1), i);
                System.out.println("Done. Total: " + totAndAvg[0] + " ms Avg: " + totAndAvg[1] + " ms");
            }
        }

        double[] totAndAvg = getTotalAndAverageTiming(timings, 0, timings.length - 1);
        System.out.println("Total: " + totAndAvg[0] + " ms Avg: " + totAndAvg[1] + " ms");

        String str = Arrays.toString(timings);
        writer.println(str.substring(1, str.length() - 1).replace(',', '\t'));
        System.out.println("\n== " + programName + " run ended on " + new Date() + " ==\n");

        bw.flush();
        bw.close();
        writer.flush();
        writer.close();

    }

    private static double[] getTotalAndAverageTiming(
        long[] timings, int startInc, int endInc) {
        double[] results = new double[2];
        long accum = 0;
        for (int i = startInc; i <= endInc; ++i){
            accum += timings[i];
        }
        results[0] = accum;
        results [1] = accum*1.0 / ((endInc - startInc)+1);
        return results;
    }

    private static String getMachineName()
        throws IOException, InterruptedException {
        ProcessBuilder ps = new ProcessBuilder("hostname");

        //From the DOC:  Initially, this property is false, meaning that the
        //standard output and error output of a subprocess are sent to two
        //separate streams
        ps.redirectErrorStream(true);

        Process pr = ps.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String line;
        String out = "";
        while ((line = in.readLine()) != null) {
            out += line + ".";
        }
        pr.waitFor();
        in.close();
        return out.substring(0,out.length()-1);
    }

    // To avoid any optimization
    private static void dummyPrint(double a){

        if (a == (int)(Math.random()*10)) {
            System.out.print(a);
        }
    }

    private static short[][] generateWeights(
        int rows, int cols, double w) {
        assert w <= 1 && w > 0;
        short[][] weights = new short[rows][];
        for (int i = 0; i < rows; ++i){
            weights[i] = new short[cols];
            for (int j = 0; j < cols; ++j){
                weights[i][j] = (short) (w * Short.MAX_VALUE);
            }
        }
        return weights;
    }

    private static double[] generateDiagonalValues(
        int globalColCount, int procRowCount) {
        double[] v = new double[procRowCount];
        for (int i = 0; i < procRowCount; ++i){
            for (int j = 0; j < globalColCount; ++j){
                v[i] += 1; // constant weight of 1
            }
            v[i] += 1;
        }
        return v;
    }

    public static double matrixMultiplyWithThreadOffset(
        int aHeight, int bWidth, int comm, int bz, int threadRowOffset, int procRowOffset) {
        double c = 0.0;

        int aHeightBlocks = aHeight / bz; // size = Height of A
        int aLastBlockHeight = aHeight - (aHeightBlocks * bz);
        if (aLastBlockHeight > 0) {
            aHeightBlocks++;
        }

        int bWidthBlocks = bWidth / bz; // size = Width of B
        int bLastBlockWidth = bWidth - (bWidthBlocks * bz);
        if (bLastBlockWidth > 0) {
            bWidthBlocks++;
        }

        int commnBlocks = comm / bz; // size = Width of A or Height of B
        int commLastBlockWidth = comm - (commnBlocks * bz);
        if (commLastBlockWidth > 0) {
            commnBlocks++;
        }

        int aBlockHeight = bz;
        int bBlockWidth = bz;
        int commBlockWidth = bz;

        for (int ib = 0; ib < aHeightBlocks; ib++) {
            if (aLastBlockHeight > 0 && ib == (aHeightBlocks - 1)) {
                aBlockHeight = aLastBlockHeight;
            }
            bBlockWidth = bz;
            commBlockWidth = bz;
            for (int jb = 0; jb < bWidthBlocks; jb++) {
                if (bLastBlockWidth > 0 && jb == (bWidthBlocks - 1)) {
                    bBlockWidth = bLastBlockWidth;
                }
                commBlockWidth = bz;
                for (int kb = 0; kb < commnBlocks; kb++) {
                    if (commLastBlockWidth > 0 && kb == (commnBlocks - 1)) {
                        commBlockWidth = commLastBlockWidth;
                    }

                    for (int i = ib * bz; i < (ib * bz) + aBlockHeight; i++) {
                        for (int j = jb * bz; j < (jb * bz) + bBlockWidth;
                             j++) {
                            for (int k = kb * bz;
                                 k < (kb * bz) + commBlockWidth; k++) {
                                double aVal = 0;
                                if (i + procRowOffset == k) {
                                    aVal = Math.random()*i;
                                }
                                else {
                                    //reverse the value from weight
                                    aVal = -(Math.random() * (i+threadRowOffset)*k * 1.0 / Short.MAX_VALUE);
                                }

                                if (aVal != 0 && Math.random()*k*j != 0) {
                                    c += aVal * Math.random()*k*j;
                                }
                            }
                        }
                    }
                }
            }
        }

        return c;
    }

    private static double[][] readInitMapping(
        String initialPointsFile, int numPoints, int targetDimension) {
        try {
            BufferedReader br = Files.newBufferedReader(
                Paths.get(initialPointsFile), Charset.defaultCharset());
            double x[][] = new double[numPoints][targetDimension];
            String line;
            Pattern pattern = Pattern.compile("[\t]");
            int row = 0;
            while ((line = br.readLine()) != null) {
                if (Strings.isNullOrEmpty(line))
                    continue; // continue on empty lines - "while" will break on null anyway;

                String[] splits = pattern.split(line.trim());

                for (int i = 0; i < splits.length; ++i){
                    x[row][i] = Double.parseDouble(splits[i].trim());
                }
                ++row;
            }
            return x;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static double[][] generateInitMapping(int numPoints,
                                          int targetDimension) {
        double[][] points = new double[numPoints][];
        // Use Random class for generating random initial mapping solution.
        Random rand = new Random(System.currentTimeMillis());
        for (int i = 0; i < numPoints; i++) {
            points[i] = new double[targetDimension];
            for (int j = 0; j < targetDimension; j++) {
                points[i][j] =
                    rand.nextBoolean() ? rand.nextDouble() : -rand.nextDouble();
            }
        }
        return points;
    }
}
