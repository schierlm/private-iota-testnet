package iotatools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.*;

import jota.IotaAPI;
import jota.dto.response.GetAttachToTangleResponse;
import jota.dto.response.GetNodeInfoResponse;
import jota.dto.response.GetTransactionsToApproveResponse;
import jota.model.Bundle;
import jota.model.Transaction;
import jota.utils.Converter;

/**
 * Run a coordinator for IOTA testnet. Whenever this coordinator is run, the
 * milestone index will be incremented by one.
 * 
 * (c) 2017 Michael Schierl. Licensed under MIT License.
 */
public class TestnetCoordinator {

	public static final String NULL_HASH = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";
	public static final String TESTNET_COORDINATOR_ADDRESS = "EQQFCZBIHRHWPXKMTOLMYUYPCN9XLMJPYZVFJSAY9FQHCCLWTOLLUGKKMXYFDBOOYFBLBI9WUEILGECYM";
	public static final String NULL_ADDRESS = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";
	public static final int TAG_TRINARY_SIZE = 81;

	public static void main(String[] args) throws Exception {
		if (args.length == 0)
			args = new String[] { "localhost", "14700" };
		else if (args.length == 1)
			args = new String[] { "localhost", "14700", args[0] };

		IotaAPI api = new IotaAPI.Builder().host(args[0]).port(args[1]).build();
		GetNodeInfoResponse nodeInfo = api.getNodeInfo();
		int milestone = nodeInfo.getLatestMilestoneIndex();
		if (nodeInfo.getLatestMilestone().equals(NULL_HASH)) {
			// As of 1.4.2.4, at least two milestones are required so that the latest solid subtangle milestone gets updated.
			newMilestone(api, NULL_HASH, NULL_HASH, milestone + 1);
			newMilestone(api, NULL_HASH, NULL_HASH, milestone + 2);
		} else if (nodeInfo.getLatestSolidSubtangleMilestone().equals(NULL_HASH)) {
			newMilestone(api, NULL_HASH, NULL_HASH, milestone + 1);
		} else {
			GetTransactionsToApproveResponse x = api.getTransactionsToApprove(10);
			String secondTransaction = args.length > 2 ? args[2] : x.getBranchTransaction();
			newMilestone(api, x.getTrunkTransaction(), secondTransaction, milestone + 1);
		}
		System.out.println("New milestone created.");
	}



	static void newMilestone(IotaAPI api, String tip1, String tip2, long index) throws Exception {
		final Bundle bundle = new Bundle();
		String tag = Converter.trytes(Converter.trits(index, TAG_TRINARY_SIZE));
		long timestamp = System.currentTimeMillis() / 1000;
		bundle.addEntry(1, TESTNET_COORDINATOR_ADDRESS, 0, tag, timestamp);
		bundle.addEntry(1, NULL_ADDRESS, 0, tag, timestamp);
		bundle.finalize(null);
		bundle.addTrytes(Collections.<String> emptyList());
		List<String> trytes = new ArrayList<>();
		for (Transaction trx : bundle.getTransactions()) {
			trytes.add(trx.toTrytes());
		}
		Collections.reverse(trytes);
		GetAttachToTangleResponse rrr = api.attachToTangle(tip1, tip2, 13, (String[]) trytes.toArray(new String[trytes.size()]));
		api.storeTransactions(rrr.getTrytes());
		api.broadcastTransactions(rrr.getTrytes());
	}
}
