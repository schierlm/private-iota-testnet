package iotatools;

import jota.IotaAPI;
import jota.dto.response.GetNodeInfoResponse;
import jota.dto.response.GetTransactionsToApproveResponse;
import org.apache.commons.cli.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static iotatools.TestnetCoordinator.NULL_HASH;
import static iotatools.TestnetCoordinator.newMilestone;

public class PeriodicCoordinator {

    private final static Logger logger = Logger.getLogger(PeriodicCoordinator.class.getName());

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {

        // parse arguments
        CommandLine parameter = parseArgs(args);
        String host = parameter.getOptionValue("host", "localhost");
        String port = parameter.getOptionValue("port", "14265");
        final Integer interval = Integer.valueOf(parameter.getOptionValue("interval", "60"));
        final IotaAPI api = new IotaAPI.Builder().host(host).port(port).build();

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    GetNodeInfoResponse nodeInfo = api.getNodeInfo();
                    int updatedMilestone = nodeInfo.getLatestMilestoneIndex() + 1;
                    if (nodeInfo.getLatestMilestone().equals(NULL_HASH)) {
                        newMilestone(api, NULL_HASH, NULL_HASH, updatedMilestone);
                    } else {
                        GetTransactionsToApproveResponse x = api.getTransactionsToApprove(10);
                        newMilestone(api, x.getTrunkTransaction(), x.getBranchTransaction(), updatedMilestone);
                    }
                    logger.info("New milestone " + updatedMilestone + " created.");
                }
                catch (Exception e) {
                    logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
                }

                executor.schedule(this, interval, TimeUnit.SECONDS);
            }
        };

        // start execution
        task.run();
    }

    private  static CommandLine parseArgs(String[] args) {
        Options options = new Options();
        options.addOption("h", "host", true, "IRI host");
        options.addOption("p", "port", true, "IRI port");
        options.addOption("i", "interval", true, "Interval (seconds) for issuing new milestones");

        try {
            CommandLineParser parser = new DefaultParser();
            return parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            logger.info(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
        }
        return null;
    }
}
