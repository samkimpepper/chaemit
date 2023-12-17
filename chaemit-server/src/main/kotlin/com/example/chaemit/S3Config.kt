package com.example.chaemit

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class S3Config(
    @Value("\${cloud.aws.credentials.access-key}")
    private val accessKey: String,
    @Value("\${cloud.aws.credentials.secret-key}")
    private val secretKey: String,
    @Value("\${cloud.aws.region.static}")
    private val region: String,
) {

    @Bean
    fun amazonS3Client(): AmazonS3Client {
        val credentials = BasicAWSCredentials(accessKey, secretKey)

        val s3Client: AmazonS3 = AmazonS3ClientBuilder
            .standard()
            .withRegion(region)
            .withCredentials(AWSStaticCredentialsProvider(credentials))
            .build()

        return s3Client as AmazonS3Client
    }
}