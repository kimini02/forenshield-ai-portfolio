package com.example.demo.util;

import com.example.demo.service.evidence.HashService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MerkleTreeUtilTest {

    private final HashService hashService = new HashService();

    @Test
    void computeRoot_isDeterministicForSortedLeaves() {
        List<String> leaves = List.of(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );

        String first = MerkleTreeUtil.computeRoot(leaves, hashService);
        String second = MerkleTreeUtil.computeRoot(List.of(leaves.get(1), leaves.get(0)), hashService);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }
}
