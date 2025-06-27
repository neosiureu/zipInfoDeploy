package com.zipinfo.project.comment.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.announce.model.dto.Comment;
import com.zipinfo.project.comment.model.service.CommentService;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("boardComment")
public class CommentController {

		@Autowired
		private CommentService service;
		
		/** 댓글 목록 조회
		 * @param boardNo
		 * @return
		 */
		@GetMapping("")
		public List<Comment> select(@RequestParam("boardNo") int boardNo) {
			List<Comment>  select =service.select(boardNo);
			log.info("조회된 댓글"+select);
			return select;
		}
		
		/** 댓글/답글 등록
		 * @return
		 */
		@PostMapping("")
		public int insert(@RequestBody Comment comment) {
			int insert =service.insert(comment);
			log.info("삽입하려는 댓글"+insert);

			return insert;
		}
		
		/** 댓글 삭제
		 * @param commentNo
		 * @return
		 */
		@DeleteMapping("{commentNo}")
		public int delete(@PathVariable("commentNo") int commentNo) {
			log.debug("삭제할 댓글 번호"+ commentNo);
			int delete =service.delete(commentNo);
			log.debug("프론트단으로 넘겨줄 것"+ delete);

			return delete;
		}
		
		
		
		/** 댓글 수정
		 * @param comment
		 * @return
		 */
		@PutMapping("{commentNo}")
		public int update(@PathVariable("commentNo") int commentNo, @RequestBody Comment comment) {
		    comment.setCommentNo(commentNo);
			int update =service.update(comment);
			return update;
		}
		
		
		
		
		
		
	}