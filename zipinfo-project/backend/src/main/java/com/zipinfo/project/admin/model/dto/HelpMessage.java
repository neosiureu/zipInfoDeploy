package com.zipinfo.project.admin.model.dto;

import java.sql.Date;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class HelpMessage {
    private int messageNo;
    private String messageTitle;
    private String messageContent;
    private String messageWriteDate;
    private String messageReadFl;
    private String userDelFl;
    private String adminDelFl;
    private int senderNo;
    private int receiverNo;
    private int memberNo;
    private String replyYn;
    private int inquiredNo;
    
    private int fileNo;
    private String fileUrl;
    private String fileOriginName;
    private String fileRename;
    
    private MultipartFile messageFile;
    private String memberNickname;
    private String replyContent;
}
