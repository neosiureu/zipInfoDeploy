package com.zipinfo.project.board.model.dto;

import lombok.Data;
import java.util.Date;

@Data
public class Board {
	private int boardNo;                // ANNOUNCE_NO
    private String announceTitle;       // ANNOUNCE_TITLE
    private String announce;            // ANNOUNCE
    private Date announceWriteDate;     // ANNOUNCE_WRITE_DATE
    private int announceReadCount;      // ANNOUNCE_READ_COUNT
    private String announceDelFl;       // ANNOUNCE_DEL_FL (Y/N)
    private int memberNo;               // MEMBER_NO
}
