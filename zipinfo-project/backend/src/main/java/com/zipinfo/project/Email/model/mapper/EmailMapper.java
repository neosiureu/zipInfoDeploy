package com.zipinfo.project.email.model.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;



@Mapper
public interface EmailMapper {

	int updateAuthKey(Map<String, String> map);


	int insertAuthKey(Map<String, String> map);


	int verifyCode(Map<String, String> map);

}
