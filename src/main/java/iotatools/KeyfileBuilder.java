package iotatools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import jota.pow.ICurl;
import jota.pow.SpongeFactory;
import jota.utils.Converter;
import jota.utils.SeedRandomGenerator;

/**
 * Build a keyfile for Merkle-Winternitz signature scheme. Can be used for
 * {@link SnapshotSigner} or {@link SigningCoordinator}.
 * 
 * (c) 2017 Michael Schierl. Licensed under MIT License.
 */
public class KeyfileBuilder {

	private static final String NULL_ADDRESS = "999999999999999999999999999999999999999999999999999999999999999999999999999999999";

	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			System.out.println("Usage: KeyfileBuilder <keyfile> <security> <algorithm> <pubkeyDepth> [[<firstIndex>] <pubkeyCount>]");
			return;
		}
		final String filename = args[0];
		final int security = Integer.parseInt(args[1]);
		SpongeFactory.Mode algorithm = SpongeFactory.Mode.valueOf(args[2]);
		final int pubkeyDepth = Integer.parseInt(args[3]);
		final int firstIndex, pubkeyCount;
		if (args.length == 4) {
			firstIndex = 0;
			pubkeyCount = 1 << pubkeyDepth;
		} else if (args.length == 5) {
			firstIndex = 0;
			pubkeyCount = Integer.parseInt(args[4]);
		} else {
			firstIndex = Integer.parseInt(args[4]);
			pubkeyCount = Integer.parseInt(args[5]);
		}
		final String seed = SeedRandomGenerator.generateNewSeed();
		System.out.println("Seed: " + seed);
		ICurl curl = SpongeFactory.create(algorithm);
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
			bw.write(pubkeyDepth + " " + seed);
			bw.newLine();
			String[] keys = new String[1 << pubkeyDepth];
			for (int i = 0; i < pubkeyCount; i++) {
				int idx = firstIndex + i;
				System.out.println("Generating subkey " + idx);
				keys[idx] = CurlSigning.newAddress(seed, security, idx, false, curl);
			}
			writeKeys(bw, keys);
			int[] trits = new int[243];
			while (keys.length > 1) {
				String[] nextKeys = new String[keys.length / 2];
				for (int i = 0; i < nextKeys.length; i++) {
					if (keys[i * 2] == null && keys[i * 2 + 1] == null) {
						// leave the combined key null as well
						continue;
					}
					curl.reset();
					String k1 = keys[i * 2], k2 = keys[i * 2 + 1];
					Converter.copyTrits(k1 == null ? NULL_ADDRESS : k1, trits);
					curl.absorb(trits);
					Converter.copyTrits(k2 == null ? NULL_ADDRESS : k2, trits);
					curl.absorb(trits);
					curl.squeeze(trits, 0, trits.length);
					nextKeys[i] = Converter.trytes(trits);
				}
				keys = nextKeys;
				writeKeys(bw, keys);
			}
			System.out.println("Public key: " + keys[0]);
			System.out.println("Keyfile created.");
		}
	}

	private static void writeKeys(BufferedWriter bw, String[] keys) throws IOException {
		int leadingNulls = 0;
		while (keys[leadingNulls] == null)
			leadingNulls++;
		bw.write(leadingNulls + " ");
		for (int i = leadingNulls; i < keys.length; i++) {
			if (keys[i] == null)
				break;
			bw.write(keys[i]);
		}
		bw.newLine();
	}

	public static String[][] loadKeyfile(File keyfile, StringBuilder seedBuilder) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
			String[] fields = br.readLine().split(" ");
			int depth = Integer.parseInt(fields[0]);
			seedBuilder.append(fields[1]);
			String[][] result = new String[depth + 1][];
			for (int i = 0; i <= depth; i++) {
				result[i] = new String[1 << (depth - i)];
				fields = br.readLine().split(" ");
				int leadingNulls = Integer.parseInt(fields[0]);
				for (int j = 0; j < fields[1].length() / 81; j++) {
					result[i][j + leadingNulls] = fields[1].substring(j * 81, j * 81 + 81);
				}
			}
			return result;
		}
	}
}
