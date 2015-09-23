package edu.indiana.soic.spidal.tools;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import edu.indiana.soic.spidal.Utils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AnalyzeResults {
    private static String programName = "analyzer";
    private static String opOutDir = "outputDir";
    private static String opCut = "cut";
    private static String opToEmail = "to";
    private static String opFromEmail = "from";
    private static String opFromEmailPw = "frompw";

    private static Options options = new Options();
    static {
        options.addOption(opOutDir, true, "Outupt dir");
        options.addOption(opCut, true, "Cut");
        options.addOption(opToEmail, true, "Comma separated list of email addresses to send results");
        options.addOption(opFromEmail, true, "From email address -- must be a gmail address");
        options.addOption(opFromEmailPw, true, "Password for from email address");
    }

    private static final Pattern patternSpace = Pattern.compile(" ");

    public static void main(String[] args)
        throws IOException, InterruptedException, AddressException {
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
        if (!(cmd.hasOption(opOutDir) && cmd.hasOption(opCut) && cmd.hasOption(
            opToEmail) && cmd.hasOption(opFromEmail) && cmd.hasOption(
            opFromEmailPw))) {
            new HelpFormatter().printHelp(programName, options);
            return;
        }

        String outDir = cmd.hasOption(opOutDir) ? cmd.getOptionValue(opOutDir) : ".";
        double cut = Double.parseDouble(cmd.getOptionValue(opCut));
        InternetAddress [] toEmails = InternetAddress.parse(cmd.getOptionValue(opToEmail).trim());
        String fromEmail = cmd.getOptionValue(opFromEmail).trim();
        String fromEmailPw = cmd.getOptionValue(opFromEmailPw);

        Stream<Path> dirs = Files.list(Paths.get(outDir)).filter(f -> Files.isDirectory(f));
        Object [] values = dirs.map(dir -> {
            try {
                String dirName = com.google.common.io.Files.getNameWithoutExtension(dir.toString());
                Stream<Path> files = Files.list(dir).filter(f -> !Files.isDirectory(f));
                double totalForNode = files.parallel().mapToDouble(AnalyzeResults::getTotal).sum();
                return new NodeInfo(dirName, totalForNode);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }).toArray();
        java.util.Optional<Object> tmp = Stream.of(values).min((o1, o2) -> {
            double d1 = ((NodeInfo) o1).total;
            double d2 = ((NodeInfo) o2).total;
            return d1 < d2 ? -1 : (d1 == d2 ? 0 : 1);
        });
        double min = tmp.isPresent() ? ((NodeInfo)tmp.get()).total : -1.0;
        java.util.Optional<String> str = Stream.of(values).filter(
            v -> ((NodeInfo) v).total >= cut * min).map(
            v -> ((NodeInfo) v).name + " total " + ((NodeInfo)v).total
                 + " ms > (cut=" + cut + " * min=" + min + ") " + (cut * min) + " ms").reduce(
            (s1, s2) -> s1 + "\n" + s2);
        if (str.isPresent()){
            sendEmail(fromEmail, fromEmailPw, toEmails, str.get());
        }

    }

    private static class NodeInfo{
        String name;
        double total;

        public NodeInfo(String name, double total) {
            this.name = name;
            this.total = total;
        }
    }

    private static void sendEmail(String username, String password, InternetAddress[] toEmails, String msg){
        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                                              new javax.mail.Authenticator() {
                                                  protected PasswordAuthentication getPasswordAuthentication() {
                                                      return new PasswordAuthentication(username, password);
                                                  }
                                              });

        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO,
                                  toEmails);
            message.setSubject("Slow Performance on Juliet " + new Date());
            message.setText(msg);

            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public static double getTotal(Path path){
        try(BufferedReader reader = Files.newBufferedReader(path)) {
            String s;
            while ((s = reader.readLine()) != null){
                if (Strings.isNullOrEmpty(s) || !s.startsWith("Total")) continue;
                String[] splits = patternSpace.split(s);
                if (splits.length != 6) continue;
                return Double.parseDouble(splits[1]);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return 0.0d;
    }
}
