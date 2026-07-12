package br.com.claricejoias_ws.service;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket '{}' criado com sucesso.", bucketName);
        }
    }

    public String upload(MultipartFile file) throws Exception {
        ensureBucketExists();

        // Pega o nome original do arquivo que está sendo enviado
        String nomeOriginal = file.getOriginalFilename();

        // Validação de segurança básica para garantir que o nome não é nulo
        if (nomeOriginal == null || nomeOriginal.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do arquivo original não pode ser nulo ou vazio.");
        }

        // Limpa o caminho para evitar vulnerabilidades de Path Traversal (ex: "../../secret.jpg")
        String nomeLimpo = org.springframework.util.StringUtils.cleanPath(nomeOriginal);
        // Mantém só o nome do arquivo, descartando qualquer diretório embutido que tenha sobrado
        nomeLimpo = java.nio.file.Paths.get(nomeLimpo).getFileName().toString();

        // Prefixo único para nunca sobrescrever um arquivo existente com o mesmo nome
        String objectName = UUID.randomUUID() + "-" + nomeLimpo;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName) // Usa o nome original aqui
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        log.info("Arquivo '{}' enviado para o bucket '{}'.", objectName, bucketName);
        return objectName;
    }

    public String getPresignedUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .method(Method.GET)
                        .expiry(1, TimeUnit.HOURS)
                        .build()
        );
    }

    public String getImagemBase64(String nomeArquivo) throws Exception {
        InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(nomeArquivo)
                        .build()
        );

        byte[] bytes = stream.readAllBytes();
        stream.close();

        // RETORNO CORRIGIDO: Retorna apenas a string pura!
        return Base64.getEncoder().encodeToString(bytes);
    }

    public InputStream download(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    public String getContentType(String objectName) throws Exception {
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
        return stat.contentType() != null ? stat.contentType() : "application/octet-stream";
    }

    public void delete(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
        log.info("Arquivo '{}' removido do bucket '{}'.", objectName, bucketName);
    }
}
