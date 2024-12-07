package tukano.impl.storage;

import java.io.IOException;
import static java.lang.String.format;
import java.net.URI;
import java.util.function.Consumer;
import java.util.logging.Logger;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tukano.api.Result;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

public class S3Storage implements BlobStorage {
    private static Logger Log = Logger.getLogger(S3Storage.class.getName());
    private final String bucketName;
    private final S3Client s3Client;

    public S3Storage(String endpoint, String accessKey, String secretKey, String bucketName) {
        this.bucketName = bucketName;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.EU_NORTH_1) // Region is required but doesn’t matter for MinIO
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            Log.info("Bucket already exists \n");
        }
    }

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build(), RequestBody.fromBytes(bytes));
                    Log.info("Uploaded object to S3 \n");
            return ok();
        } catch (S3Exception e) {
            Log.info("Failed to upload object to S3 \n");
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<byte[]> read(String path) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build());
                    Log.info("Downloaded object from S3 \n");
            return ok(response.readAllBytes());
        } catch (NoSuchKeyException e) {
            Log.info("Object not found in S3 \n");
            return error(NOT_FOUND);
        } catch (S3Exception | IOException e) {
            Log.info("Failed to download object from S3 \n");
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build());
            sink.accept(response.readAllBytes());
            Log.info("Downloaded object from S3 \n");
            return ok();
        } catch (NoSuchKeyException e) {
            Log.info("Object not found in S3 \n");
            return error(NOT_FOUND);
        } catch (S3Exception | IOException e) {
            Log.info("Failed to download object from S3 \n");
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> delete(String path) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build());
            Log.info("Deleted object from S3 \n");
            return ok();
        } catch (NoSuchKeyException e) {
            Log.info("Object not found in S3 \n");
            return error(NOT_FOUND);
        } catch (S3Exception e) {
            Log.info("Failed to delete object from S3 \n");
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deleteAll(String prefix) {
        try {
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listObjectsResponse;
            do {
                listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);

                for (var s3Object : listObjectsResponse.contents()) {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Object.key())
                            .build());
                    Log.info(() -> format("Deleted object: %s\n", s3Object.key()));
                }

                listObjectsRequest = listObjectsRequest.toBuilder()
                        .continuationToken(listObjectsResponse.nextContinuationToken())
                        .build();
            } while (listObjectsResponse.isTruncated());

            Log.info(() -> format("Successfully deleted all objects with prefix: %s\n", prefix));
            return ok();
        } catch (S3Exception e) {
            Log.severe(() -> "Failed to delete objects from S3: " + e.getMessage() + "\n");
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }
}
