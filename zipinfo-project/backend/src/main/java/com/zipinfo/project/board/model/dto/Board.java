package com.zipinfo.project.board.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Board {
    private int boardNo;
    private String title;
    private String content;
    private String author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String boardType; // "NOTICE" 등으로 구분
	public int getMemberNo() {
		// TODO Auto-generated method stub
		return 0;
	}
}
