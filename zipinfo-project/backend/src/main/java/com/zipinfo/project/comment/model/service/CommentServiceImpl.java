package com.zipinfo.project.comment.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.announce.model.dto.Comment;
import com.zipinfo.project.comment.model.mapper.CommentMapper;



@Service
@Transactional(rollbackFor = Exception.class)
public class CommentServiceImpl implements CommentService{

		@Autowired
		private CommentMapper mapper;
		
		// 댓글 목록 조회 서비스
		
		/* 이주원
		 * 댓글 조회 서비스
		 */
		@Override
		public List<Comment> select(int boardNo) {
			return mapper.select(boardNo);
		}
		
		
		/* 이주원
		 * 댓글 등록 서비스
		 */
		// 댓글/답글 등록 서비스
		@Override
		public int insert(Comment comment) {
			return mapper.insert(comment);
		}
		
		/* 이주원
		 * 댓글 삭제 서비스
		 */
		// 댓글 삭제
		@Override
		public int delete(int commentNo) {
			return mapper.delete(commentNo);
		}
		
		/* 이주원
		 * 댓글 수정 서비스
		 */
		// 댓글 수정
		@Override
		public int update(Comment comment) {
			return mapper.update(comment);
		}
		
		
		
		
}