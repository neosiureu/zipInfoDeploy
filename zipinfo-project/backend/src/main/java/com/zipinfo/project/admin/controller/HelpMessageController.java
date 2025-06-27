package com.zipinfo.project.admin.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.service.HelpMessageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/help")
@RequiredArgsConstructor
public class HelpMessageController {

    private final HelpMessageService helpMessageService;

    // 전체 문의 목록 반환
    @GetMapping("/list")
    public ResponseEntity<List<HelpMessage>> getAllMessages() {
        return ResponseEntity.ok(helpMessageService.getAllMessages());
    }

    // 특정 문의 상세 내용 반환
    @GetMapping("/{id}")
    public ResponseEntity<HelpMessage> getMessageDetail(@PathVariable Long id) {
        return ResponseEntity.ok(helpMessageService.getMessageById(id));
    }
}
