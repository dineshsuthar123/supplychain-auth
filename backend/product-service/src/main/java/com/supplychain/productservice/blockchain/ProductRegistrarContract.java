package com.supplychain.productservice.blockchain;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Hand-crafted Web3j wrapper for the ProductRegistrar Solidity contract.
 *
 * This class mirrors what {@code web3j generate} would produce from the ABI.
 * Regenerate with:
 *   web3j generate solidity -b artifacts/contracts/ProductRegistrar.sol/ProductRegistrar.json \
 *       -o ../backend/product-service/src/main/java \
 *       -p com.supplychain.productservice.blockchain
 */
public class ProductRegistrarContract extends Contract {

    public static final String BINARY = ""; // Not needed at runtime; contract already deployed

    protected ProductRegistrarContract(String contractAddress, Web3j web3j,
                                        Credentials credentials, ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, credentials, gasProvider);
    }

    protected ProductRegistrarContract(String contractAddress, Web3j web3j,
                                        TransactionManager txManager, ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, txManager, gasProvider);
    }

    /**
     * Load an existing deployed contract instance.
     */
    public static ProductRegistrarContract load(String contractAddress, Web3j web3j,
                                                 Credentials credentials,
                                                 ContractGasProvider gasProvider) {
        return new ProductRegistrarContract(contractAddress, web3j, credentials, gasProvider);
    }

    /**
     * Call: register(string productId, bytes32 hash)
     */
    public RemoteFunctionCall<TransactionReceipt> register(String productId, byte[] hash) {
        final Function function = new Function(
                "register",
                Arrays.asList(new Utf8String(productId), new Bytes32(hash)),
                Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Call: verify(string productId, bytes32 hash) returns (bool)
     */
    public RemoteFunctionCall<Boolean> verify(String productId, byte[] hash) {
        final Function function = new Function(
                "verify",
                Arrays.asList(new Utf8String(productId), new Bytes32(hash)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /**
     * Call: getHash(string productId) returns (bytes32)
     */
    public RemoteFunctionCall<byte[]> getHash(String productId) {
        final Function function = new Function(
                "getHash",
                Collections.singletonList(new Utf8String(productId)),
                Collections.singletonList(new TypeReference<Bytes32>() {})
        );
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }
}
