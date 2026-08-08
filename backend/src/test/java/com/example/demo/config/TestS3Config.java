package com.example.demo.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Configuration
@Profile("test")
public class TestS3Config {

    @Bean
    public S3Client s3Client() {
        Map<String, byte[]> store = new ConcurrentHashMap<>();
        S3Client client = Mockito.mock(S3Client.class);

        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    PutObjectRequest request = invocation.getArgument(0);
                    RequestBody body = invocation.getArgument(1);
                    store.put(objectKey(request.bucket(), request.key()), readRequestBody(body));
                    return PutObjectResponse.builder().build();
                });

        when(client.copyObject(any(CopyObjectRequest.class)))
                .thenAnswer(invocation -> {
                    CopyObjectRequest request = invocation.getArgument(0);
                    String sourceKey = objectKey(request.sourceBucket(), request.sourceKey());
                    String destinationKey = objectKey(request.destinationBucket(), request.destinationKey());
                    byte[] sourceBytes = store.get(sourceKey);
                    if (sourceBytes != null) {
                        store.put(destinationKey, sourceBytes);
                    }
                    return CopyObjectResponse.builder().build();
                });

        when(client.getObject(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    byte[] bytes = store.getOrDefault(objectKey(request.bucket(), request.key()), new byte[0]);
                    return new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(bytes)
                    );
                });

        when(client.headObject(any(HeadObjectRequest.class)))
                .thenAnswer(invocation -> {
                    HeadObjectRequest request = invocation.getArgument(0);
                    if (!store.containsKey(objectKey(request.bucket(), request.key()))) {
                        throw software.amazon.awssdk.services.s3.model.NoSuchKeyException.builder()
                                .message("Not found")
                                .build();
                    }
                    return HeadObjectResponse.builder().build();
                });

        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenAnswer(invocation -> {
                    DeleteObjectRequest request = invocation.getArgument(0);
                    store.remove(objectKey(request.bucket(), request.key()));
                    return DeleteObjectResponse.builder().build();
                });

        return client;
    }

    private static String objectKey(String bucket, String key) {
        return bucket + "::" + key;
    }

    private static byte[] readRequestBody(RequestBody body) throws IOException {
        try (InputStream inputStream = body.contentStreamProvider().newStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        }
    }
}
