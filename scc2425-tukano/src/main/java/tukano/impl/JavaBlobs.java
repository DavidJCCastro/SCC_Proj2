package tukano.impl;

import static java.lang.String.format;
import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.S3Storage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {

    private static Blobs instance;
    private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

    private static final String ADMIN = "admin"; // Only admin can delete

    public String baseURI;
    private BlobStorage storage;

    synchronized public static Blobs getInstance() {
        if (instance == null)
            instance = new JavaBlobs();
        return instance;
    }

    private JavaBlobs() {
        String endpoint = System.getenv("S3_ENDPOINT");
        String accessKey = System.getenv("S3_ACCESS_KEY");
        String secretKey = System.getenv("S3_SECRET_KEY");
        String bucketName = System.getenv("S3_BUCKET");
        Log.info(() -> format("S3 configuration: endpoint = %s, accessKey = %s, secretKey = %s, bucketName = %s\n",
                endpoint, accessKey, secretKey, bucketName));

        if (endpoint == null || accessKey == null || secretKey == null || bucketName == null) {
            throw new IllegalStateException("S3 configuration is missing!");
        }

        storage = new S3Storage(endpoint, accessKey, secretKey, bucketName);
        baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);

    }

    @Override
    public Result<Void> upload(String blobId, byte[] bytes, String token) {
        Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)),
                token));

        String userId = blobId.split("\\+")[0];
        Log.info(() -> "Extracted userId: " + userId + "\n");

        var sessionValidation = JavaAuth.validateSession(userId);
        if (!sessionValidation.isOK()) {
            Log.severe("Upload failed: Session validation failed for userId " + userId + "\n");
            return error(FORBIDDEN);
        }

        if (!validBlobId(blobId, token)) {
            Log.severe("Upload failed: Token validation failed for blobId " + blobId + "\n");
            return error(FORBIDDEN);
        }

        Log.info(() -> "Validation successful. Writing blob... \n" );
        return storage.write(toPath(blobId), bytes);
    }

    @Override
    public Result<byte[]> download(String blobId, String token) {
        Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

        if (!JavaAuth.validateSession().isOK())
            return error(FORBIDDEN);

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        return storage.read(toPath(blobId));
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

        if (!JavaAuth.validateSession(ADMIN).isOK())
            return error(FORBIDDEN);

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        Log.info(() -> "Deleting blob: " + blobId + "\n");
        return storage.delete(toPath(blobId));
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String token) {
        Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

        if (!JavaAuth.validateSession(ADMIN).isOK())
            return error(FORBIDDEN);

        if (!Token.isValid(token, userId))
            return error(FORBIDDEN);

        Log.info(() -> "Deleting all blobs for userId: " + userId + "\n");
        Log.info(() -> "toPath(userId): " + toPath(userId) + "\n");

        return storage.deleteAll(toPath(userId));
    }

    private boolean validBlobId(String blobId, String token) {
        return Token.isValid(token, blobId);
    }

    private String toPath(String blobId) {
        return blobId.replace("+", "/");
    }
}