package com.zipinfo.project.admin.model.mapper;

import com.zipinfo.project.admin.model.dto.Advertisement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 관리자의 회원 및 중개인 신청 관련 DB 접근 매퍼 인터페이스
 * MyBatis가 SQL 매핑을 통해 DB 조작을 수행
 */
@Mapper
public interface AdvertisementMapper {

	int saveFile(@Param("originalName") String originalName,
            @Param("rename") String rename,
            @Param("finalPath") String finalPath,
            @Param("memberNo") int memberNo);

	List<Advertisement> getAdList();

	int updateMainY(int adNo);

	int updateMainN();

	int deleteAd(int adNo);

   
}
