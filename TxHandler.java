import java.util.ArrayList;

public class TxHandler {

    private UTXOPool _pool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        _pool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        return allClaimedOutputsInPool(tx) &&
                signaturesAreValid(tx) &&
                noDoubleSpend(tx) &&
                allOutputsAreNonNegative(tx) &&
                sumsIsCorrect(tx);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> result = new ArrayList<>();
        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                for (Transaction.Input input : tx.getInputs())
                    _pool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
                for (int i = 0; i < tx.numOutputs(); i++)
                    _pool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));

                result.add(tx);
            }
        }

        return result.toArray(new Transaction[0]);
    }

    private boolean allClaimedOutputsInPool(Transaction tx) {
        return tx.getInputs().stream().allMatch(input ->
                _pool.contains(new UTXO(input.prevTxHash, input.outputIndex))
        );
    }

    private boolean signaturesAreValid(Transaction tx) {
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            Transaction.Output output = _pool.getTxOutput(new UTXO(
                    input.prevTxHash,
                    input.outputIndex
            ));
            boolean isValid = Crypto.verifySignature(
                    output.address,
                    tx.getRawDataToSign(i),
                    input.signature
            );
            if (!isValid)
                return false;
        }
        return true;
    }

    private boolean noDoubleSpend(Transaction tx) {
        long utxoCount = tx.getInputs().stream()
                .map(x -> new UTXO(x.prevTxHash, x.outputIndex))
                .distinct()
                .count();

        return utxoCount == tx.numInputs();
    }

    private boolean allOutputsAreNonNegative(Transaction tx) {
        return tx.getOutputs().stream().allMatch(output -> output.value >= 0);
    }

    private boolean sumsIsCorrect(Transaction tx) {
        double inputSum = tx.getInputs().stream()
                .mapToDouble(x -> _pool.getTxOutput(new UTXO(x.prevTxHash, x.outputIndex)).value)
                .sum();
        double outputSum = tx.getOutputs().stream()
                .mapToDouble(x -> x.value)
                .sum();

        return inputSum >= outputSum;
    }
}
