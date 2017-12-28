package iotatools;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cfb.pearldiver.PearlDiverLocalPoW;
import jota.IotaAPI;
import jota.IotaLocalPoW;
import jota.dto.response.GetNodeInfoResponse;
import jota.dto.response.GetTransactionsToApproveResponse;
import jota.model.Bundle;
import jota.model.Transaction;
import jota.pow.ICurl;
import jota.pow.SpongeFactory;
import jota.utils.Converter;

/**
 * Run a coordinator that produces signed milestones.
 * 
 * (c) 2017 Michael Schierl. Licensed under MIT License.
 */
public class SignedCoordinator {

	private static final String NULL_HASH = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";
	private static final String NULL_ADDRESS = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";
	private static final int TAG_TRINARY_SIZE = 81;

	public static void main(String[] args) throws Exception {
		if (args.length == 0)
			args = new String[] { "localhost", "14700" };
		else if (args.length == 1)
			args = new String[] { "localhost", "14700", args[0] };

		IotaAPI api = new IotaAPI.Builder().host(args[0]).port(args[1]).build();
		GetNodeInfoResponse nodeInfo = api.getNodeInfo();
		int milestone = nodeInfo.getLatestMilestoneIndex();
		if (nodeInfo.getLatestMilestone().equals(NULL_HASH)) {
			newMilestone(api, NULL_HASH, NULL_HASH, milestone + 1);
		} else {
			GetTransactionsToApproveResponse x = api.getTransactionsToApprove(10);
			String secondTransaction = args.length > 2 ? args[2] : x.getBranchTransaction();
			newMilestone(api, x.getTrunkTransaction(), secondTransaction, milestone + 1);
		}
		System.out.println("New milestone created.");
	}

	private static void newMilestone(IotaAPI api, String tip1, String tip2, long index) throws Exception {
		StringBuilder seedBuilder = new StringBuilder();
		String[][] keyfile = KeyfileBuilder.loadKeyfile(new File("Coordinator.key"), seedBuilder);
		String seed = seedBuilder.toString(), coordinatorAddress = keyfile[keyfile.length - 1][0];
		System.out.println("Coordinator address: " + coordinatorAddress);
		System.out.println("Milestone index: " + index);
		System.out.println("Public subkey: " + keyfile[0][(int) index]);
		int keyIndex = (int) index;
		StringBuilder merklePath = new StringBuilder();
		for (int i = 0; i < keyfile.length - 1; i++) {
			String subkey = keyfile[i][keyIndex ^ 1];
			merklePath.append(subkey == null ? NULL_ADDRESS : subkey);
			keyIndex /= 2;
		}
		String tag = Converter.trytes(Converter.trits(index, TAG_TRINARY_SIZE));
		long timestamp = System.currentTimeMillis() / 1000;
		Bundle bundle;
		bundle = new Bundle();
		bundle.addEntry(1, coordinatorAddress, 0, tag, timestamp);
		bundle.addEntry(1, NULL_ADDRESS, 0, tag, timestamp);
		int[] hash = new int[243];
		String hashInTrytes;
		ICurl curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
		curl.reset();
		for (int i = 0; i < bundle.getTransactions().size(); i++) {
			int[] valueTrits = Converter.trits(bundle.getTransactions().get(i).getValue(), 81);
			int[] timestampTrits = Converter.trits(bundle.getTransactions().get(i).getTimestamp(), 27);
			bundle.getTransactions().get(i).setCurrentIndex(i);
			int[] currentIndexTrits = Converter.trits(bundle.getTransactions().get(i).getCurrentIndex(), 27);
			bundle.getTransactions().get(i).setLastIndex(bundle.getTransactions().size() - 1);
			int[] lastIndexTrits = Converter.trits(bundle.getTransactions().get(i).getLastIndex(), 27);
			int[] t = Converter.trits(bundle.getTransactions().get(i).getAddress() + Converter.trytes(valueTrits) + bundle.getTransactions().get(i).getObsoleteTag() + Converter.trytes(timestampTrits) + Converter.trytes(currentIndexTrits) + Converter.trytes(lastIndexTrits));
			curl.absorb(t, 0, t.length);
		}
		curl.squeeze(hash, 0, hash.length);
		hashInTrytes = Converter.trytes(hash);
		for (int i = 0; i < bundle.getTransactions().size(); i++) {
			bundle.getTransactions().get(i).setBundle(hashInTrytes);
		}
		bundle.addTrytes(Collections.<String> emptyList());
		List<String> trytes = new ArrayList<>();
		for (Transaction trx : bundle.getTransactions()) {
			trytes.add(trx.toTrytes());
		}
		Collections.reverse(trytes);
		String[] trytes1 = (String[]) trytes.toArray(new String[2]);
		final String[] resultTrytes = new String[2];
		IotaLocalPoW localPoW = new PearlDiverLocalPoW();
		CurlSigning signing = new CurlSigning(SpongeFactory.create(SpongeFactory.Mode.CURLP27));
		Transaction txn1 = new Transaction(trytes1[0]);
		txn1.setSignatureFragments(merklePath.append(txn1.getSignatureFragments().substring(merklePath.length())).toString());
		txn1.setTrunkTransaction(tip1);
		txn1.setBranchTransaction(tip2);
		if (txn1.getTag().isEmpty() || txn1.getTag().matches("9*"))
			txn1.setTag(txn1.getObsoleteTag());
		txn1.setAttachmentTimestamp(System.currentTimeMillis());
		txn1.setAttachmentTimestampLowerBound(0);
		txn1.setAttachmentTimestampUpperBound(3_812_798_742_493L);
		resultTrytes[0] = localPoW.performPoW(txn1.toTrytes(), 13);
		String previousTransaction = new Transaction(resultTrytes[0]).getHash();
		System.out.println("Signed trunk transaction: " + previousTransaction);
		Transaction txn2 = new Transaction(trytes1[1]);
		int[] normalizedBundle = bundle.normalizedBundle(previousTransaction);
		final int[] key = signing.key(Converter.trits(seed), (int) index, 1);
		if (!keyfile[0][(int) index].equals(Converter.trytes(signing.address(signing.digests(key))))) {
			System.out.println("Keyfile corrupted");
			return;
		}
		txn2.setSignatureFragments(Converter.trytes(signing.signatureFragment(Arrays.copyOfRange(normalizedBundle, 0, 27), key)));
		txn2.setTrunkTransaction(previousTransaction);
		txn2.setBranchTransaction(tip1);
		if (txn2.getTag().isEmpty() || txn2.getTag().matches("9*"))
			txn2.setTag(txn2.getObsoleteTag());
		txn2.setAttachmentTimestamp(System.currentTimeMillis());
		txn2.setAttachmentTimestampLowerBound(0);
		txn2.setAttachmentTimestampUpperBound(3_812_798_742_493L);
		resultTrytes[1] = localPoW.performPoW(txn2.toTrytes(), 13);
		api.storeTransactions(resultTrytes);
		api.broadcastTransactions(resultTrytes);
	}
}
