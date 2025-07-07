package com.zipinfo.project.comment.model.service;

import java.util.List;

import com.zipinfo.project.announce.model.dto.Comment;

public interface CommentService {

	
	/* 이주원
	 * 댓글 조회 서비스
	 * @param boardNo
	 * @return
	 */
	List<Comment> select(int boardNo);

	/* 이주원
	 *  댓글 삽입 서비스
	 * @param boardNo
	 * @return
	 */
	int insert(Comment comment);

	/* 이주원
	 * 댓글 삭제 서비스
	 * @param boardNo
	 * @return
	 */
	int delete(int commentNo);

	
	
	/* 이주원
	 * 댓글 수정 서비스
	 * @param comment
	 * @return
	 */
	int update(Comment comment);

}
