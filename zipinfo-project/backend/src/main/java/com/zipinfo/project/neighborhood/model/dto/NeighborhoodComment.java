package com.zipinfo.project.neighborhood.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class NeighborhoodComment {
	private int commentNo;
	private int memberNo;
	private String commentContent;
	private String commentDate;
	private String commentDelFl;
	private int commentParentNo;
	private int boardNo;


}
