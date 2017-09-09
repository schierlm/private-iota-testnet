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

Then compile this repository

    mvn package

Now it is time to build your own Snapshot. First decide how you want to split the 2 779 530 283 277 761 available IOTA to addresses, and which of them should belong to the same wallet.

Then start the interactive process:

    java -jar target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar SnapshotBuilder

It will ask you about the IOTA you want to assign (you can also use suffixes like Gi or Pi). Once you have assigned all IOTA, it will write a `Snapshot.txt` (to be compiled into iri) and a `Snapshot.log` (for you to remember the seeds to the addresses).

Get [iri](https://github.com/iotaledger/iri/) if you do not already have it.

Copy `Snapshot.txt` to iri/`src/main/ressources`.

Comment out the [part that validates the snapshot signature](https://github.com/iotaledger/iri/blob/9d4abe2d59d336c0ecec1f826554bc2c1f29d278/src/main/java/com/iota/iri/Snapshot.java#L49-L65). It would also be possible to recreate the signature with a different signing key, but then you have to replace the signing key in the source code instead. So I think it is not worth the hassle and just decided to comment out the signature verification (The nodes will be only used by yourself, won't they?).

Compile and run iri as usual. When starting iri, make sure to include the `--testnet` switch, and that the current directory does not contain any `testnetdb` files from previous runs.

    java -jar iri.jar --testnet -p 14700

Before you can connect to your iri with your wallet, you need to run the coordinator to create the first milestone

    java -jar target/iota-testnet-tools-0.1-SNAPSHOT-jar-with-dependencies.jar Coordinator localhost 14700

After that you can use your wallet, log into one of your seeds, and attach addresses until you see your full balance.

In case you want a new milestone, just run the coordinator again.


***Have fun!***