## Private IOTA Testnet

When you want to perform some test/auditing against the IOTA Reference implementation (IRI), you probably want to test your assumptions against your own testnet (for some scenarios one node is enough, other require more nodes). Using the public testnet has the disadvantage that you are not alone on your tangle, so somebody else might confirm your transactions you like to stay unconfirmed, or breakpoints you add inside IRI can trigger by transactions done by others thyn you. Currently you will face some obstacles when trying to do so:

- The official wallet will not connect to any node which has not received at least one milestone from the coordinator
- As IOTA is not mineable, your accounts will start with 0 IOTA, and you cannot change this by mining some

To get around these obstacles, you need to perform these steps

- Build your own Snapshot.txt and recompile IRI to use it
- Once your recompiled IRI is started (or later whenever you want the milestone index to increase), run a testnet coordinator that will create a new milestone for you

This repository contains tools (written in Java) to build your own Snapshot and to run a coordinator once to create a new milestone.

## Step by step instructions

Get [iota.lib.java](https://github.com/iotaledger/iota.lib.java/) and install it into your local Maven repository:

    mvn install

Then get and compile this ([private-iota-testnet](https://github.com/schierlm/private-iota-testnet)) repository

    mvn package

Now it is time to build your own Snapshot. First decide how you want to split the 2 779 530 283 277 761 available IOTA to addresses, and which of them should belong to the same wallet.

Then start the interactive process:

    java -jar target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar SnapshotBuilder

It will ask you about the IOTA you want to assign (you can also use suffixes like Gi or Pi). Once you have assigned all IOTA, it will write a `Snapshot.txt` (to be compiled into iri) and a `Snapshot.log` (for you to remember the seeds to the addresses).

Get [iri](https://github.com/iotaledger/iri/) if you do not already have it.

Copy `Snapshot.txt` to iri/`src/main/resources`.

Comment out the [part that validates the snapshot signature](https://github.com/iotaledger/iri/blob/3ce0b5bbf737c37ce721af5bfa1ae7f246cbb2ae/src/main/java/com/iota/iri/Snapshot.java#L52-L72). It would also be possible to recreate the signature with a different signing key, but then you have to replace the signing key in the source code instead. So I think it is not worth the hassle and just decided to comment out the signature verification (The nodes will be only used by yourself, won't they?).

Compile and run iri as usual. When starting iri, make sure to include the `--testnet` switch, and that the current directory does not contain any `testnetdb` files from previous runs.

    java -jar iri.jar --testnet -p 14700

Before you can connect to your iri with your wallet, you need to run the coordinator to create the first milestone

    java -jar target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar Coordinator localhost 14700

After that you can use your wallet, log into one of your seeds, and attach addresses until you see your full balance.

In case you want a new milestone, just run the coordinator again.


***Have fun!***

## Reducing PoW

Testnet by default requires PoW for minWeightMagnitude=13. When performing larger scale tests, you might want to decrease this. To do so, you will have to
patch iri at [two](https://github.com/iotaledger/iri/blob/6e23c046ec2232ca2031018a6bbee4abaad7d9a7/src/main/java/com/iota/iri/conf/Configuration.java#L97-L98) [places](https://github.com/iotaledger/iri/blob/64b3d723331bfdfa14ed883de447eb2363d3821b/src/main/java/com/iota/iri/TransactionValidator.java#L54-L56) and recompile it. To build the package use `mvn package -Dmaven.test.skip=true` to prevent that tests will terminate the building process.

When using the official wallet, you also have to patch this. If you (like me) have trouble recompiling the Windows wallet, you can instead patch it in-place. Have a look at `AppData\Local\Programs\iota\resources\ui\js\ui.update.js` (search for `connection.minWeightMagnitude`) and `AppData\Local\Programs\iota\resources\app.asar` (search for `var minWeightMagnitudeMinimum`). Note that the second file is a binary file, so when patching it make sure not to destroy any control characters (use a hex editor or an editor like Notepad++ that can keep them intact), and to keep the file size the same. Custom code usually passes the minWeightMagnitude as a parameter anyway.

In most cases, however, it is enough to make sure you use minWeightMagnitude=13 instead of 14 and it will be "fast enough".
