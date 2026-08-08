package com.example.demo.service.evidence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HashServiceTest {

	private HashService hashService;

	@BeforeEach
	void setUp() {
		hashService = new HashService();
	}

	@Test
	@DisplayName("동일한 바이트 배열은 동일한 SHA-256 해시를 반환한다")
	void generateSha256_sameBytes_returnsSameHash() {
		byte[] content = "sample evidence content".getBytes(StandardCharsets.UTF_8);

		String firstHash = hashService.generateSha256(content);
		String secondHash = hashService.generateSha256(content);

		assertThat(firstHash).isEqualTo(secondHash);
		assertThat(firstHash).hasSize(64);
	}

	@Test
	@DisplayName("수정된 바이트 배열은 다른 SHA-256 해시를 반환한다")
	void generateSha256_modifiedBytes_returnsDifferentHash() {
		byte[] original = "original evidence content".getBytes(StandardCharsets.UTF_8);
		byte[] modified = "modified evidence content".getBytes(StandardCharsets.UTF_8);

		String originalHash = hashService.generateSha256(original);
		String modifiedHash = hashService.generateSha256(modified);

		assertThat(originalHash).isNotEqualTo(modifiedHash);
	}

	@Test
	@DisplayName("MultipartFile 기준 SHA-256 해시를 생성한다")
	void generateSha256_multipartFile_returnsHexString() {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"sample.mp4",
				"video/mp4",
				"fake video bytes".getBytes(StandardCharsets.UTF_8)
		);

		String hashValue = hashService.generateSha256(file);

		assertThat(hashValue).matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("저장과 동시 해시는 저장 후 재해시와 동일하고 파일 내용도 보존한다")
	void saveAndGenerateSha256_matchesPostSaveHash(@TempDir Path tempDir) throws Exception {
		byte[] content = "digest-stream-evidence-bytes".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"evidence.mp4",
				"video/mp4",
				content
		);
		Path destination = tempDir.resolve("saved-evidence.mp4");

		String streamedHash = hashService.saveAndGenerateSha256(file, destination);
		String rereadHash = hashService.generateSha256(destination);

		assertThat(streamedHash).isEqualTo(rereadHash);
		assertThat(streamedHash).isEqualTo(hashService.generateSha256(content));
		assertThat(Files.readAllBytes(destination)).isEqualTo(content);
	}
}
