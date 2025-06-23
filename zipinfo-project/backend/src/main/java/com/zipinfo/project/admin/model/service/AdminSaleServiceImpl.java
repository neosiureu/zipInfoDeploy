package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.admin.model.mapper.AdminSaleMapper;
import com.zipinfo.project.sale.model.dto.Sale;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class AdminSaleServiceImpl implements AdminSaleService {
	
	@Autowired
	private AdminSaleMapper mapper;
	
	/**
	 * 분양 매물 조회 서비스
	 */
	@Override
		public List<Sale> selectSaleList() {
			return mapper.selectSaleList();
		}
}
