package com.zipinfo.project.editneighborhood.model.service;

import com.zipinfo.project.neighborhood.model.dto.Neighborhood;

public interface EditneighborhoodService {

	int boardInsert(Neighborhood inputBoard);

	String processImagesInContent(String boardContent) throws Exception;

	int boardUpdate(Neighborhood inputBoard);


	int boardDelete(Neighborhood inputBoard);

}
