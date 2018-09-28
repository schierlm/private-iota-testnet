package iotatools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		String newMilestoneHash;
		if (nodeInfo.getLatestMilestone().equals(NULL_HASH)) {
			// As of 1.4.2.4, at least two milestones are required so that the latest solid subtangle milestone gets updated.
			newMilestoneHash = newMilestone(api, NULL_HASH, NULL_HASH, milestone + 1);
			newMilestoneHash = newMilestone(api, newMilestoneHash, newMilestoneHash, milestone + 2);
		} else if (nodeInfo.getLatestSolidSubtangleMilestone().equals(NULL_HASH)) {
			newMilestoneHash = newMilestone(api, NULL_HASH, NULL_HASH, milestone + 1);
		} else {
			GetTransactionsToApproveResponse x = api.getTransactionsToApprove(10);
			String secondTransaction = args.length > 2 ? args[2] : x.getBranchTransaction();
			String firstTransaction = x.getTrunkTransaction();
			boolean firstTransactionConfirmingMilestone = isConfirming(api, firstTransaction, nodeInfo.getLatestMilestone());
			boolean secondTransactionConfirmingMilestone = isConfirming(api, secondTransaction, nodeInfo.getLatestMilestone());
			if (!firstTransactionConfirmingMilestone && !secondTransactionConfirmingMilestone) {
				System.out.println("Oops, it happened that the tips do not confirm the previous milestone :-)");
				firstTransaction = nodeInfo.getLatestMilestone();
			}
			newMilestoneHash = newMilestone(api, firstTransaction, secondTransaction, milestone + 1);
		}
		System.out.println("New milestone "+newMilestoneHash+" created.");
	}

	private static boolean isConfirming(IotaAPI api, String confirming, String confirmed) throws Exception {
		// getInclusionStates is broken for non-milestones, therefore approximate the result by a (depth-limited) breadth-first search.
		if (confirming.equals(confirmed))
			return true;
		Set<String> currentDepth = new HashSet<>();
		Set<String> seen = new HashSet<>();
		currentDepth.add(confirming);
		seen.add(confirming);
		for (int depth = 0; depth < 10; depth++) {
			Set<String> nextDepth = new HashSet<>();
			for (String hash : currentDepth) {
				if (hash.equals(confirmed))
					return true;
				if (hash.equals(NULL_HASH))
					continue;
				Transaction tx = new Transaction(api.getTrytes(hash).getTrytes()[0]);
				if (seen.add(tx.getTrunkTransaction()))
					nextDepth.add(tx.getTrunkTransaction());
				if (seen.add(tx.getBranchTransaction()))
					nextDepth.add(tx.getBranchTransaction());
			}
			currentDepth = nextDepth;
		}
		// not found, so we assume it is not
		return false;
	}

	static String newMilestone(IotaAPI api, String tip1, String tip2, long index) throws Exception {
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
		GetAttachToTangleResponse rrr = api.attachToTangle(tip1, tip2, 9, (String[]) trytes.toArray(new String[trytes.size()]));
		String[] finalTrytes = rrr.getTrytes();
		api.storeTransactions(finalTrytes);
		api.broadcastTransactions(finalTrytes);
		return new Transaction(finalTrytes[0]).getHash();
	}
}
