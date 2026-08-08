package com.example.demo.service.evidence;

import com.example.demo.exception.HashGenerationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class HashService {

	private static final String SHA_256 = "SHA-256";
	private static final int BUFFER_SIZE = 8192;

	public String generateSha256(byte[] fileBytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance(SHA_256);
			byte[] hashBytes = digest.digest(fileBytes);
			return HexFormat.of().formatHex(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new HashGenerationException("SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String generateSha256(InputStream inputStream) {
		try {
			MessageDigest digest = MessageDigest.getInstance(SHA_256);
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new HashGenerationException("SHA-256 생성에 실패했습니다.", e);
		} catch (IOException e) {
			throw new HashGenerationException("파일 읽기 중 SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String generateSha256(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream()) {
			return generateSha256(inputStream);
		} catch (IOException e) {
			throw new HashGenerationException("업로드 파일 SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String generateSha256(Path filePath) {
		try (InputStream inputStream = Files.newInputStream(filePath)) {
			return generateSha256(inputStream);
		} catch (IOException e) {
			throw new HashGenerationException("저장된 원본 파일 SHA-256 생성에 실패했습니다.", e);
		}
	}

	/**
	 * 업로드 스트림을 디스크에 저장하면서 SHA-256을 한 패스로 계산한다.
	 * 저장 후 파일을 다시 읽어 해시하지 않으므로 대용량 업로드 시 디스크 I/O·피크 부하를 줄인다.
	 */
	public String saveAndGenerateSha256(InputStream inputStream, Path destination) {
		try {
			MessageDigest digest = MessageDigest.getInstance(SHA_256);
			try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);
					OutputStream outputStream = Files.newOutputStream(destination)) {
				byte[] buffer = new byte[BUFFER_SIZE];
				int bytesRead;
				while ((bytesRead = digestInputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new HashGenerationException("SHA-256 생성에 실패했습니다.", e);
		} catch (IOException e) {
			throw new HashGenerationException("파일 저장 중 SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String saveAndGenerateSha256(MultipartFile file, Path destination) {
		try (InputStream inputStream = file.getInputStream()) {
			return saveAndGenerateSha256(inputStream, destination);
		} catch (IOException e) {
			throw new HashGenerationException("업로드 파일 저장 중 SHA-256 생성에 실패했습니다.", e);
		}
	}
}
