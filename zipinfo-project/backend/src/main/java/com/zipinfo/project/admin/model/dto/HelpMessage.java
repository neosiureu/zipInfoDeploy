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
    private int senderNo;
    private int receiverNo;
    private int memberNo;
    private String replyYn;
    private String replyDate;
    
    private int fileNo;
    private String fileUrl;
    private String fileOriginName;
    private String fileRename;
    
    private String memberNickname;
    private String replyContent;
    private String adminNickname;

    public String getReplyDate() {
        return replyDate;
    }
    public void setReplyDate(String replyDate) {
        this.replyDate = replyDate;
    }
}
