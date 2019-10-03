**NOTE** Meanwhile, the IOTA Foundation published its own official documentation how to run a private tangle, so this repo is now obsolete. New users please go to

https://github.com/iotaledger/compass/blob/master/docs/HOWTO_private_tangle.md

---

## Private IOTA Testnet

When you want to perform some test/auditing against the IOTA Reference implementation (IRI), you probably want to test your assumptions against your own testnet (for some scenarios one node is enough, other require more nodes). Using the public testnet has the disadvantage that you are not alone on your tangle, so somebody else might confirm transactions you want to stay unconfirmed, or trigger your breakpoints via their own transactions. There are a few obstacles with running your own private testnet, including:

- The official wallet will not connect to any node which has not received at least one milestone from the coordinator
- As IOTA is not mineable, your accounts will start with 0 IOTA, and you cannot change this by mining some

To get around these obstacles, you need to perform these steps

- Build your own Snapshot.txt and recompile IRI to use it
- Once your recompiled IRI is started (or later whenever you want the milestone index to increase), run a testnet coordinator that will create a new milestone for you

This repository contains tools (written in Java) to build your own Snapshot and to run a coordinator once to create a new milestone.

## Step by step instructions

Get and compile this ([private-iota-testnet](https://github.com/schierlm/private-iota-testnet)) repository

    mvn package

Now it is time to build your own Snapshot. First decide how you want to split the 2,779,530,283,277,761 available IOTA to addresses, and which of them should belong to the same wallet.

Then start the interactive process:

    java -jar target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar SnapshotBuilder

It will ask you about the IOTA you want to assign (you can also use suffixes like Gi or Pi). Once you have assigned all IOTA, it will write a `Snapshot.txt` (to be compiled into iri) and a `Snapshot.log` (for you to remember the seeds to the addresses).

Get [iri](https://github.com/iotaledger/iri/) if you do not already have it. This process has (last) been tested with iri 1.5.4, so if you are trying a later version, you do it at your own risk. Please re-test with 1.5.4 before opening a ticket.

As of iri 1.4.2.4 it is no longer required to patch and recompile iri, so you can decide by yourself if you prefer to compile it yourself or if you take the precompiled jar file.

Copy `Snapshot.txt` in the same directory as `iri-1.5.4.jar`.

When starting iri, make sure to include the `--testnet` switch, as well as `--testnet-no-coo-validation` to skip milestone validation, and your custom snapshot. The current directory should not contain any `testnetdb` files from previous runs.

    java -jar iri-1.5.4.jar --testnet --testnet-no-coo-validation --snapshot=Snapshot.txt -p 14700

Before you can connect to your iri with your wallet, you need to run the coordinator to create the first milestone

    java -jar target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar Coordinator localhost 14700

After that you can use your wallet, log into one of your seeds, and attach addresses until you see your full balance.

In case you want a new milestone, just run the coordinator again.

***Have fun!***

## Reducing PoW

Testnet by default requires PoW for minWeightMagnitude=9. When performing larger scale tests, you might want to decrease this. To do so, no more patching of iri is needed; just pass the `--mwm` option with your desired value.
Be aware that the UDP protocol between neighbors will break as the packets are not big enough to hold the full transaction hash if the hash does not end with a sufficient amount of zeroes (so best test with only a single isolated node or increase `TESTNET_PACKET_SIZE`).

When using the official wallet, you also have to patch this, since the wallet does not allow MWM smaller than 13. If you (like me) have trouble recompiling the Windows wallet, you can instead patch it in-place. Have a look at `AppData\Local\Programs\iota\resources\ui\js\ui.update.js` (search for `connection.minWeightMagnitude`) and `AppData\Local\Programs\iota\resources\app.asar` (search for `var minWeightMagnitudeMinimum`). Note that the second file is a binary file, so when patching it make sure not to destroy any control characters (use a hex editor or an editor like Notepad++ that can keep them intact), and to keep the file size the same. Custom code usually passes the minWeightMagnitude as a parameter anyway.

In most cases, however, it is enough to make sure you use minWeightMagnitude=9 (or 13) instead of 14 and it will be "fast enough".

## Signing snapshots and milestones

iri uses Merkle-Winternitz signatures to sign snapshots and milestones, using CurlP27 and CurlP81 hash function, respectively. As the Merkle hash tree for
the milestons is rather large (1048576 subkeys) and generation can take a while, this project provides a way to compute partial Merkle hash trees where
only some indices (e. g. 243000 to 243100) are filled. This makes generation much faster, while still allowing to test signature verification in real-life
conditions.

For signed milestones, you need to change the Coordinator address using `--testnet-coordinator` switch and may also need to enable milestone signature verification (by omitting `--testnet-no-coo-validation` switch).

For signed snapshots, you need to patch the [Snapshot signing public key](https://github.com/iotaledger/iri/blob/12a4bc6307426ee17ca47dd805acac634e26fff8/src/main/java/com/iota/iri/Snapshot.java#L23) in the iri source (as well as replacing the included snapshot and signature file in `src/main/resources`) before recompiling iri.


Generate key files for snapshots and milestones:

    java -cp target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar iotatools.KeyfileBuilder Snapshot.key 3 CURLP81 6 1 3

    java -cp target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar iotatools.KeyfileBuilder Coordinator.key 1 CURLP27 20 243000 100

The keyfile parameters are security (3 for snapshot, 1 for milestones), hash algorithm, Merkle tree depth, and optionally first used key and number of keys
(to make generation faster). The pubkey is printed on the console and also in the last line of the key file.


Signing a snapshot:

    java -cp target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar iotatools.SnapshotSigner Snapshot.key 1

Creating a signed milestone:

    java -cp target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar iotatools.SignedCoordinator localhost 14700

Always remember: With great power comes great responsibility - I do not want to see this repo end as the base of yet another Aidos Kuneen...
