package com.instaclustr.esop.s3.ceph;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.instaclustr.esop.guice.BackupRestoreBindings;
import com.instaclustr.esop.impl.AbstractOperationRequest;
import com.instaclustr.esop.s3.TransferManagerFactory;
import io.kubernetes.client.apis.CoreV1Api;

public class CephModule extends AbstractModule {

    @Override
    protected void configure() {
        BackupRestoreBindings.installBindings(binder(),
                                              "ceph",
                                              CephRestorer.class,
                                              CephBackuper.class,
                                              CephBucketService.class);
    }

    @Provides
    @Singleton
    public CephS3TransferManagerFactory provideTransferManagerFactory(final Provider<CoreV1Api> coreV1ApiProvider) {
        return new CephS3TransferManagerFactory(coreV1ApiProvider);
    }

    public static final class CephS3TransferManagerFactory extends TransferManagerFactory {

        public CephS3TransferManagerFactory(final Provider<CoreV1Api> coreV1ApiProvider) {
            super(coreV1ApiProvider, false);
        }

        @Override
        protected AmazonS3 provideAmazonS3(final Provider<CoreV1Api> coreV1ApiProvider, final AbstractOperationRequest operationRequest) {

            final S3Configuration s3Conf = resolveS3ConfigurationFromEnvProperties();

            AWSCredentialsProvider credentials = new DefaultAWSCredentialsProviderChain();

            ClientConfiguration clientConfig = new ClientConfiguration();

            if (operationRequest.insecure) {
                clientConfig.withProtocol(Protocol.HTTP);
            }

            final AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
            builder.setCredentials(credentials);
            builder.setClientConfiguration(clientConfig);


            if (s3Conf.awsEndpoint != null) {
                builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Conf.awsEndpoint, s3Conf.awsRegion));

            } else {
                throw new IllegalStateException("You have to specify endpoint for Ceph module, either via "
                                                    + "AWS_ENDPOINT environment variable or via awsendpoint K8S property in secret");
            }

            return builder.build();
        }
    }
}
