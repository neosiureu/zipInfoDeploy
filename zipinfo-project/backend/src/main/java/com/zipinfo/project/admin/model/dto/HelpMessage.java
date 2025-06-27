package com.zipinfo.project.admin.model.dto;

import java.sql.Date;

import lombok.Data;

@Data
public class HelpMessage {
    private Long messageNo;
    private String messageTitle;
    private String messageContent;
    private Date messageWriteDate;
    private String messageReadFl;
    private String userDelFl;
    private String adminDelFl;
    private Long senderNo;
    private Long receiverNo;
}