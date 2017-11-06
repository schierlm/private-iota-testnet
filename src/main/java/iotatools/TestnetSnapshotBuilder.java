package iotatools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.output.TeeOutputStream;

import jota.utils.Checksum;
import jota.utils.IotaAPIUtils;
import jota.utils.SeedRandomGenerator;

/**
 * Build a snapshot for IOTA testnet. Balances are read interactively from
 * stdin.
 * 
 * (c) 2017 Michael Schierl. Licensed under MIT License.
 */
public class TestnetSnapshotBuilder {
	public static void main(String[] args) throws Exception {
		long remainingIOTA = 2_779_530_283_277_761L;
		try (OutputStream logStream = new FileOutputStream("Snapshot.log");
				OutputStream snapshotStream = new FileOutputStream("Snapshot.txt");
				BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				PrintStream out = new PrintStream(new TeeOutputStream(System.out, logStream), true)) {

			out.println("Generating new Snapshot.txt file...");
			out.println("Enter amounts to be stored on the given addresses.");
			out.println("Amounts can be given as numbers, or with suffixes Ki Mi Gi Ti Pi.");
			out.println("Hit Return without entering an amount to switch to another seed.");
			out.println("Hit return for the first address of a seed assign the remaining IOTA");
			out.println("and finish the process.");

			out.println();
			out.println("Remaining IOTA: "+remainingIOTA);

			while (remainingIOTA > 0) {
				out.println();
				String seed = SeedRandomGenerator.generateNewSeed();
				out.println("Seed: " + seed);
				for (int i = 0;; i++) {
					String addr = IotaAPIUtils.newAddress(seed, 2, i, false, null);
					out.print("  " + Checksum.addChecksum(addr) + ": ");
					out.flush();
					String line = in.readLine().trim();
					logStream.write((line + System.lineSeparator()).getBytes());
					logStream.flush();
					if (line.isEmpty() && i == 0) {
						out.println("  " + Checksum.addChecksum(addr) + ": "+remainingIOTA);
						snapshotStream.write((addr + ";" + remainingIOTA + "\n").getBytes(StandardCharsets.ISO_8859_1));
						remainingIOTA = 0;
					}
					if (line.isEmpty())
						break;
					if (line.toLowerCase().endsWith("i"))
						line = line.substring(0, line.length() - 1).trim();
					int suffix = "KMGTP".indexOf(line.toUpperCase().charAt(line.length() - 1));
					long value;
					if (suffix >= 0) {
						value = Long.parseLong(line.substring(0, line.length() - 1).trim());
						for (int j = 0; j < suffix + 1; j++) {
							value *= 1000;
						}
					} else {
						value = Long.parseLong(line);
					}
					if (value > remainingIOTA) {
						snapshotStream.close();
						new File("Snapshot.txt").delete();
						throw new NumberFormatException("Cheater. You do not have that many IOTA any more...");
					}
					snapshotStream.write((addr + ";" + value + "\n").getBytes(StandardCharsets.ISO_8859_1));
					remainingIOTA -= value;
				}
			}
			out.println();
			out.println("Done.");
		}
	}
}
