package com.example.demo.util;

import com.example.demo.service.evidence.HashService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MerkleTreeUtil {

    private MerkleTreeUtil() {
    }

    public static String computeRoot(List<String> leafHashes, HashService hashService) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            throw new IllegalArgumentException("Merkle tree requires at least one leaf hash");
        }

        List<String> level = leafHashes.stream()
                .map(hash -> hash == null ? "" : hash.trim().toLowerCase())
                .sorted(Comparator.naturalOrder())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        while (level.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = i + 1 < level.size() ? level.get(i + 1) : left;
                nextLevel.add(hashService.generateSha256((left + right).getBytes()));
            }
            level = nextLevel;
        }
        return level.get(0);
    }
}
