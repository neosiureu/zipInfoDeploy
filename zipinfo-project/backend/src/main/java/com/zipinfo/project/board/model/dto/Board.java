package com.zipinfo.project.board.model.dto;

import java.util.Date;

import lombok.Data;

@Data
public class Board {
    private int boardNo;               // BOARD_NO
    private String boardTitle;         // BOARD_TITLE
    private String boardContent;       // BOARD_CONTENT
    private Date boardWriteDate;       // BOARD_WRITE_DATE
    private Date boardUpdateDate;      // BOARD_UPDATE_DATE (필요시)
    private int readCount;             // READ_COUNT
    private String boardDelFl;         // BOARD_DEL_FL ('Y'/'N')
    private int memberNo;              // MEMBER_NO
    private int townNo;                // TOWN_NO
    private String boardSubject;       // BOARD_SUBJECT ('Q' or 'R' 등)

    // 추가 필드: 회원 닉네임과 권한
    private String memberNickname;    // MEMBER_NICKNAME
    private int memberAuth;           // MEMBER_AUTH
}
