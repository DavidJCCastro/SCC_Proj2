package tukano.impl.storage;

import java.io.IOException;
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
            Log.info("Bucket already exists");
        }
    }

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build(), RequestBody.fromBytes(bytes));
                    Log.info("Uploaded object to S3");
            return ok();
        } catch (S3Exception e) {
            Log.info("Failed to upload object to S3");
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
                    Log.info("Downloaded object from S3");
            return ok(response.readAllBytes());
        } catch (NoSuchKeyException e) {
            Log.info("Object not found in S3");
            return error(NOT_FOUND);
        } catch (S3Exception | IOException e) {
            Log.info("Failed to download object from S3");
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
            Log.info("Downloaded object from S3");
            return ok();
        } catch (NoSuchKeyException e) {
            Log.info("Object not found in S3");
            return error(NOT_FOUND);
        } catch (S3Exception | IOException e) {
            Log.info("Failed to download object from S3");
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
            Log.info("Deleted object from S3");
            return ok();
        } catch (NoSuchKeyException e) {
            Log.info("Object not found in S3");
            return error(NOT_FOUND);
        } catch (S3Exception e) {
            Log.info("Failed to delete object from S3");
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }
}
