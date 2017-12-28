package iotatools;

import static jota.pow.JCurl.HASH_LENGTH;

import jota.error.ArgumentException;
import jota.pow.ICurl;
import jota.utils.Checksum;
import jota.utils.Converter;
import jota.utils.IotaAPIUtils;

/**
 * TODO: Remove me when
 * <a href="https://github.com/iotaledger/iota.lib.java/issues/78">this
 * issue</a> is fixed.
 */
public class CurlSigning {
	public final static int KEY_LENGTH = 6561;

	/** @see IotaAPIUtils#newAddress(String, int, int, boolean, ICurl) */
	public static String newAddress(String seed, int security, int index, boolean checksum, ICurl curl) throws ArgumentException {
		CurlSigning signing = new CurlSigning(curl);
		final int[] key = signing.key(Converter.trits(seed), index, security);
		final int[] digests = signing.digests(key);
		final int[] addressTrits = signing.address(digests);
		String address = Converter.trytes(addressTrits);
		if (checksum) {
			address = Checksum.addChecksum(address);
		}
		return address;
	}

	private ICurl curl;

	public CurlSigning(ICurl curl) {
		this.curl = curl;
	}

	public int[] key(final int[] inSeed, final int index, int security) throws ArgumentException {
		int[] seed = inSeed.clone();
		for (int i = 0; i < index; i++) {
			for (int j = 0; j < seed.length; j++) {
				if (++seed[j] > 1) {
					seed[j] = -1;
				} else {
					break;
				}
			}
		}
		curl.reset();
		curl.absorb(seed, 0, seed.length);
		curl.squeeze(seed, 0, seed.length);
		curl.reset();
		curl.absorb(seed, 0, seed.length);
		final int[] key = new int[security * HASH_LENGTH * 27];
		final int[] buffer = new int[seed.length];
		int offset = 0;
		while (security-- > 0) {
			for (int i = 0; i < 27; i++) {
				curl.squeeze(buffer, 0, seed.length);
				System.arraycopy(buffer, 0, key, offset, HASH_LENGTH);
				offset += HASH_LENGTH;
			}
		}
		return key;
	}

	public int[] signatureFragment(int[] normalizedBundleFragment, int[] keyFragment) {
		int[] signatureFragment = keyFragment.clone();
		for (int i = 0; i < 27; i++) {
			for (int j = 0; j < 13 - normalizedBundleFragment[i]; j++) {
				curl.reset()
						.absorb(signatureFragment, i * HASH_LENGTH, HASH_LENGTH)
						.squeeze(signatureFragment, i * HASH_LENGTH, HASH_LENGTH);
			}
		}
		return signatureFragment;
	}

	public int[] address(int[] digests) {
		int[] address = new int[HASH_LENGTH];
		curl.reset()
				.absorb(digests)
				.squeeze(address);
		return address;
	}

	public int[] digests(int[] key) {
		int security = (int) Math.floor(key.length / KEY_LENGTH);
		int[] digests = new int[security * HASH_LENGTH];
		int[] keyFragment = new int[KEY_LENGTH];
		for (int i = 0; i < Math.floor(key.length / KEY_LENGTH); i++) {
			System.arraycopy(key, i * KEY_LENGTH, keyFragment, 0, KEY_LENGTH);
			for (int j = 0; j < 27; j++) {
				for (int k = 0; k < 26; k++) {
					curl.reset()
							.absorb(keyFragment, j * HASH_LENGTH, HASH_LENGTH)
							.squeeze(keyFragment, j * HASH_LENGTH, HASH_LENGTH);
				}
			}
			curl.reset();
			curl.absorb(keyFragment, 0, keyFragment.length);
			curl.squeeze(digests, i * HASH_LENGTH, HASH_LENGTH);
		}
		return digests;
	}
}
