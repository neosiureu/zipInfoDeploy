package com.zipinfo.project.admin.model.mapper;

import java.util.List;
import com.zipinfo.project.admin.model.dto.HelpMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HelpMessageMapper {
    List<HelpMessage> selectAllMessages();
    HelpMessage selectMessageById(Long messageNo);
}
