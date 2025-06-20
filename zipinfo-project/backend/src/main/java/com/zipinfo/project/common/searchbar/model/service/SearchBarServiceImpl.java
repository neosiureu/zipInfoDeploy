package com.zipinfo.project.common.searchbar.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zipinfo.project.common.searchbar.model.dto.Sigungu;
import com.zipinfo.project.common.searchbar.model.mapper.SearchBarMapper;

@Service
public class SearchBarServiceImpl implements SearchBarService{
	@Autowired
	public SearchBarMapper mapper;
	
	/** 시/도 코드를 매개변수로 받아 해당 시군구 목록을 반환하는 함수.
	 *
	 */
	@Override
	public List<Sigungu> selectSigungu(int sido) {
		// TODO Auto-generated method stub
		return mapper.selectSigungu( sido);
	}

}
