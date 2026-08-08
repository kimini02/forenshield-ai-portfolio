package com.example.demo.service.overlay;

import com.example.demo.dto.OverlayJobMessage;

public interface OverlayJobEnqueuer {
    void enqueue(OverlayJobMessage message);
}
