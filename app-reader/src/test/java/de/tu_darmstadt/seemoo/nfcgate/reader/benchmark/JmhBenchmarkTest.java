package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class JmhBenchmarkTest {
    @Test
    public void runParserBenchmarks() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(EMVParserBenchmark.class.getSimpleName())
                .include(MIFAREParserBenchmark.class.getSimpleName())
                .include(APDUStreamBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }
}
