package edu.indiana.soic.spidal;

import com.google.common.base.Optional;
import org.apache.commons.cli.*;

public class Utils {
    public static Optional<CommandLine> parseCommandLineArguments(
        String[] args, Options opts) {

        CommandLineParser optParser = new GnuParser();

        try {
            return Optional.fromNullable(optParser.parse(opts, args));
        }
        catch (ParseException e) {
            System.out.println(e);
        }
        return Optional.fromNullable(null);
    }
}
