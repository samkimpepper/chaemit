package com.example.chaemit

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.util.IOUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class S3UploadService(
    private val amazonS3: AmazonS3,

    @Value("\${cloud.aws.s3.bucket}")
    private val bucket: String,
) {

    fun savefile(multipartFile: MultipartFile): String {
        val originalFileName = multipartFile.originalFilename

        var metadata: ObjectMetadata = ObjectMetadata()
        metadata.contentLength = multipartFile.size
        metadata.contentType = multipartFile.contentType

        amazonS3.putObject(bucket, originalFileName, multipartFile.inputStream, metadata)
        return amazonS3.getUrl(bucket,originalFileName).toString()
    }

    fun getFile(url: String): ByteArray {
        val fileName = url.substringAfterLast("/")

        val s3OBject = amazonS3.getObject(bucket, fileName)
        val s3InputStream = s3OBject.objectContent
        try {
            return IOUtils.toByteArray(s3InputStream)
        } finally {
            s3InputStream.close()
        }
    }
}