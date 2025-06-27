package com.zipinfo.project.comment.model.service;

import java.util.List;

import com.zipinfo.project.announce.model.dto.Comment;

public interface CommentService {

	List<Comment> select(int boardNo);

	int insert(Comment comment);

	int delete(int commentNo);

	int update(Comment comment);

}
