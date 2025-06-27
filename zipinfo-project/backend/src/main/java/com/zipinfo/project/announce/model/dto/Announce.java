package com.zipinfo.project.announce.model.dto;

import lombok.Data;
import java.util.Date;

@Data
public class Announce {
	private Integer announceNo;            // ANNOUNCE_NO (게시글 번호)
    private String announceTitle;       // ANNOUNCE_TITLE (게시글 제목)
    private String announce;            // ANNOUNCE (게시글 내용)
    private Date announceWriteDate;     // ANNOUNCE_WRITE_DATE (작성일)
    private int announceReadCount;      // ANNOUNCE_READ_COUNT (조회수)
    private String announceDelFl;       // ANNOUNCE_DEL_FL (게시글 삭제 여부 Y/N)
    private int memberNo;               // MEMBER_NO (회원 번호)
}
