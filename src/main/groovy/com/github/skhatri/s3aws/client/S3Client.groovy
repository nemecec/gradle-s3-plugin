package com.github.skhatri.s3aws.client

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object

public class S3Client {
    private final AmazonS3Client s3Client;

    private static final String AMZ_REDIRECT_LINK = "x-amz-website-redirect-location";

    public S3Client(String awsProfile, String region) {
        s3Client = "".equals(awsProfile) ?
            new AmazonS3Client(new DefaultAWSCredentialsProviderChain()) :
            new AmazonS3Client(new ProfileCredentialsProvider(awsProfile));
        if (region != null && !"".equals(region)) {
            s3Client.setRegion(Region.getRegion(Regions.fromName(region)))
        }
    }

    public String uploadFile(String bucketName, String key, String fileName, String link, ObjectMetadata metadata, CannedAccessControlList acl) {
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, new File(fileName))

            if (metadata != null) {
                putObjectRequest.withMetadata(metadata)
            }

            if (acl != null) {
                putObjectRequest.withCannedAcl(acl)
            }

            s3Client.putObject(putObjectRequest)
            String linkName = createLinkObject(link, key, bucketName)
            if (linkName == null) {
                // 7 days into the future
                Date expirationTime = dateXDaysInTheFuture(System.currentTimeMillis(), 7)
                linkName = s3Client.generatePresignedUrl(bucketName, key, expirationTime)
            }
            return linkName
        } catch (Exception e) {
            throw new RuntimeException("AWS Upload Exception ", e)
        }
    }

    private String createLinkObject(String link, String key, String bucketName) {
        if (link != null && !link.isEmpty()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setHeader(AMZ_REDIRECT_LINK, createRedirectKey(key));
            metadata.setContentLength(0);
            InputStream inputStream = new ByteArrayInputStream(new byte[0]);
            PutObjectRequest linkPutRequest = new PutObjectRequest(bucketName, link, inputStream, metadata)
            linkPutRequest.setCannedAcl(CannedAccessControlList.Private)
            s3Client.putObject(linkPutRequest);
            // 7 days into the future
            Date expirationTime = dateXDaysInTheFuture(System.currentTimeMillis(), 7)
            return s3Client.generatePresignedUrl(bucketName, link, expirationTime);
        }
    }

    private Date dateXDaysInTheFuture(long baseTime, int numOfDays) {
        // Use milliseconds for calculation to work around issues with daylight saving time
        return new Date(baseTime + numOfDays * 24 * 60 * 60 * 1000)
    }

    private String createRedirectKey(String key) {
        key.startsWith("/") || key.startsWith("http") ? key : "/" + key
    }

    public void downloadFile(String bucketName, String key, String saveTo) {
        try {
            S3Object object = s3Client.getObject(bucketName, key);
            String redirect = object.getObjectMetadata().getRawMetadataValue(AMZ_REDIRECT_LINK)
            if (redirect != null && !redirect.isEmpty()) {
                println "getting redirect resource = $redirect"
                object = s3Client.getObject(bucketName, redirect.substring(1))
            }
            byte[] bytes = object.getObjectContent().getBytes();
            def fs = new FileOutputStream(new File(saveTo));
            fs.write(bytes);
            fs.close();
        } catch (Exception e) {
            throw new RuntimeException("AWS Download Exception ", e);
        }
    }
}
