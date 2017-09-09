package iotatools;

import java.util.Arrays;

/**
 * Tools needed to run a private IOTA testnet.
 * 
 * (c) 2017 Michael Schierl. Licensed under MIT License.
 */
public class Main {

	public static void main(String[] args) throws Exception {
		if (args.length == 1 && args[0].equals("SnapshotBuilder")) {
			TestnetSnapshotBuilder.main(args);
		} else if (args.length >= 1 && args[0].equals("Coordinator")) {
			TestnetCoordinator.main(Arrays.copyOfRange(args, 1, args.length));
		} else {
			System.out.println("Do you want Coordinator or SnapshotBuilder?");
		}
	}
}
