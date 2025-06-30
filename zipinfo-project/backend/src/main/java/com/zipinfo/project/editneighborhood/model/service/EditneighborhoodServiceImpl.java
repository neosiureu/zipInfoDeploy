package com.zipinfo.project.editneighborhood.model.service;

import org.springframework.stereotype.Service;

import com.zipinfo.project.editneighborhood.model.mapper.EditNeighborhoodMapper;
import com.zipinfo.project.neighborhood.model.dto.Neighborhood;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor

@Slf4j
public class EditneighborhoodServiceImpl implements EditneighborhoodService {
	private final EditNeighborhoodMapper editNeighborhoodMapper;

	@Override
	public int boardInsert(Neighborhood inputBoard) {

	int result= editNeighborhoodMapper.boardInsert(inputBoard);
	return result;
	}

}
