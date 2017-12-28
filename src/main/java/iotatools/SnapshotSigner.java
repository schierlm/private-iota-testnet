package iotatools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import jota.model.Bundle;
import jota.pow.ICurl;
import jota.pow.SpongeFactory;
import jota.utils.Converter;

/**
 * Sign a snapshot with a key built with {@link KeyfileBuilder}.
 * 
 * (c) 2017 Michael Schierl. Licensed under MIT License.
 */
public class SnapshotSigner {

	private static final String NULL_ADDRESS = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";

	private static final String TRYTE_ALPHABET = "9ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("Usage: SnapshotSigner <keyfile> <keyIndex>");
			return;
		}
		StringBuilder seedBuilder = new StringBuilder();
		String[][] keyfile = KeyfileBuilder.loadKeyfile(new File(args[0]), seedBuilder);
		int keyIndex = Integer.parseInt(args[1]);
		String seed = seedBuilder.toString();
		ICurl kerl = SpongeFactory.create(SpongeFactory.Mode.KERL);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("Snapshot.txt")))) {
			int[] trits = new int[243 * 3];
			String line;
			while ((line = reader.readLine()) != null) {
				StringBuilder sb = new StringBuilder(80);
				for (int i = 0; i < line.length(); i++) {
					int asciiValue = line.charAt(i);
					int firstValue = asciiValue % 27;
					int secondValue = (asciiValue - firstValue) / 27;
					sb.append(TRYTE_ALPHABET.charAt(firstValue));
					sb.append(TRYTE_ALPHABET.charAt(secondValue));
				}
				Converter.copyTrits(sb.toString(), trits);
				kerl.absorb(trits);
				Arrays.fill(trits, 0);
			}
		}
		int[] trits = new int[243];
		kerl.squeeze(trits);
		System.out.println("Snapshot hash: " + Converter.trytes(trits));
		System.out.println("Public subkey: " + keyfile[0][keyIndex]);
		int[] bundle = new Bundle().normalizedBundle(Converter.trytes(trits));
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("Snapshot.sig")))) {
			CurlSigning signing = new CurlSigning(SpongeFactory.create(SpongeFactory.Mode.CURLP81));
			final int[] key = signing.key(Converter.trits(seed), keyIndex, 3);
			if (!keyfile[0][keyIndex].equals(Converter.trytes(signing.address(signing.digests(key))))) {
				System.out.println("Keyfile corrupted");
				return;
			}
			for (int i = 0; i < 3; i++) {
				bw.write(Converter.trytes(signing.signatureFragment(Arrays.copyOfRange(bundle, i * 27, (i + 1) * 27), Arrays.copyOfRange(key, i * 6561, (i + 1) * 6561))));
				bw.newLine();
			}
			for (int i = 0; i < keyfile.length - 1; i++) {
				String subkey = keyfile[i][keyIndex ^ 1];
				bw.write(subkey == null ? NULL_ADDRESS : subkey);
				keyIndex /= 2;
			}
			bw.newLine();
		}
		System.out.println("Snapshot.sig created.");
	}
}
