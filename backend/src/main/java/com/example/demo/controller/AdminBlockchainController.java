package com.example.demo.controller;

import com.example.demo.dto.BlockchainAnchorRecordDto;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Blockchain", description = "관리자 블록체인 앵커 API")
@RestController
@RequestMapping("/api/v1/admin/blockchain")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlockchainController {

    private final BlockchainAnchorService blockchainAnchorService;

    @Operation(summary = "Merkle Root 수동 앵커", description = "WBS 2.10.2 · 일별 CoC Merkle Root 블록체인 앵커 트리거")
    @PostMapping("/merkle/anchor")
    public BlockchainAnchorRecordDto anchorMerkleRoot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate batchDate
    ) {
        return blockchainAnchorService.triggerMerkleRootAnchor(batchDate);
    }
}
