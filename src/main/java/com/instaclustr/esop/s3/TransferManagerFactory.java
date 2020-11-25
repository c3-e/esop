package com.instaclustr.esop.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.inject.Provider;
import com.instaclustr.esop.impl.AbstractOperationRequest;
import io.kubernetes.client.apis.CoreV1Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferManagerFactory {

    private static final Logger logger = LoggerFactory.getLogger(TransferManagerFactory.class);

    private final Provider<CoreV1Api> coreV1ApiProvider;
    private final boolean enablePathStyleAccess;

    public TransferManagerFactory(final Provider<CoreV1Api> coreV1ApiProvider) {
        this(coreV1ApiProvider, false);
    }

    public TransferManagerFactory(final Provider<CoreV1Api> coreV1ApiProvider, final boolean enablePathStyleAccess) {
        this.coreV1ApiProvider = coreV1ApiProvider;
        this.enablePathStyleAccess = enablePathStyleAccess;
    }

    public TransferManager build(final AbstractOperationRequest operationRequest) {
        final AmazonS3 amazonS3 = provideAmazonS3(coreV1ApiProvider, operationRequest);
        return TransferManagerBuilder.standard().withS3Client(amazonS3).build();
    }

    protected AmazonS3 provideAmazonS3(final Provider<CoreV1Api> coreV1ApiProvider, final AbstractOperationRequest operationRequest) {
        final S3Configuration s3Conf = resolveS3ConfigurationFromEnvProperties();

        final AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();

        if (s3Conf.awsEndpoint != null) {
            // AWS_REGION must be set if AWS_ENDPOINT is set
            if (s3Conf.awsRegion == null) {
                throw new IllegalArgumentException("AWS_REGION must be set if AWS_ENDPOINT is set.");
            }

            builder.withEndpointConfiguration(new EndpointConfiguration(s3Conf.awsEndpoint, s3Conf.awsRegion.toLowerCase()));
        } else if (s3Conf.awsRegion != null) {
            builder.withRegion(Regions.fromName(s3Conf.awsRegion.toLowerCase()));
        }

        if (enablePathStyleAccess) {
            // for being able to work with Oracle "s3"
            builder.enablePathStyleAccess();
        }

        if (operationRequest.insecure || (operationRequest.proxySettings != null && operationRequest.proxySettings.useProxy)) {
            final ClientConfiguration clientConfiguration = new ClientConfiguration();

            if (operationRequest.insecure) {
                clientConfiguration.withProtocol(Protocol.HTTP);
            }

            if (operationRequest.proxySettings != null && operationRequest.proxySettings.useProxy) {

                if (operationRequest.proxySettings.proxyProtocol != null) {
                    clientConfiguration.setProxyProtocol(operationRequest.proxySettings.proxyProtocol);
                }

                if (operationRequest.proxySettings.proxyHost != null) {
                    clientConfiguration.setProxyHost(operationRequest.proxySettings.proxyHost);
                }

                if (operationRequest.proxySettings.proxyPort != null) {
                    clientConfiguration.setProxyPort(operationRequest.proxySettings.proxyPort);
                }

                if (operationRequest.proxySettings.proxyPassword != null) {
                    clientConfiguration.setProxyPassword(operationRequest.proxySettings.proxyPassword);
                }

                if (operationRequest.proxySettings.proxyUsername != null) {
                    clientConfiguration.setProxyUsername(operationRequest.proxySettings.proxyUsername);
                }
            }

            builder.withClientConfiguration(clientConfiguration);
        }

        logger.info("Use DefaultAWSCredentialsProviderChain");
        builder.setCredentials(new DefaultAWSCredentialsProviderChain());

        return builder.build();
    }

    public S3Configuration resolveS3ConfigurationFromEnvProperties() {
        final S3Configuration s3Configuration = new S3Configuration();

        s3Configuration.awsRegion = System.getenv("AWS_REGION");
        s3Configuration.awsEndpoint = System.getenv("AWS_ENDPOINT");

        return s3Configuration;
    }

    public static final class S3Configuration {
        public String awsRegion;
        public String awsEndpoint;
    }
}