package com.zipinfo.project.common.searchbar.model.service;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.common.searchbar.model.dto.Sigungu;

public interface SearchBarService {
	/** 시/도 코드를 매개변수로 받아 해당 시군구 목록을 반환하는 함수.
	 *@param sido : 시/도 코드(두자릿수) ex. 11 -> 서울특별시, 26 -> 부산광역시, 41 -> 경기도 ...
	 */
	List<Sigungu> selectSigungu(int sido);

}
