package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BlockchainAnchorStatusResponse {

    private Long evidenceId;
    private BlockchainAnchorRecordDto evidenceHashAnchor;
    private List<BlockchainAnchorRecordDto> reportHashAnchors;
    private BlockchainAnchorRecordDto latestMerkleRootAnchor;
}
