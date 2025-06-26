package com.zipinfo.project.announce.model.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.mapper.AnnounceMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공지사항 게시판 관련 비즈니스 로직 처리 서비스 구현체
 * - MyBatis Mapper를 통해 DB 접근
 * - 게시글 목록 조회, 검색, 상세 조회, 등록, 수정, 삭제, 조회수 증가 기능을 처리
 */
@Service
@RequiredArgsConstructor
public class AnnounceServiceImpl implements AnnounceService {

    private final AnnounceMapper announceMapper;

    /**
     * 게시글 목록 조회 (페이징)
     * @param cp 현재 페이지 번호
     * @return 게시글 목록 리스트
     */
    @Override
    public List<Announce> selectAnnounceList(int cp) {
        return announceMapper.selectAnnounceList(cp);
    }

    /**
     * 게시글 검색 목록 조회
     * @param key 검색 타입 (t: 제목, c: 내용, tc: 제목+내용, 기타: 작성자 닉네임 등)
     * @param query 검색어
     * @param cp 현재 페이지 번호
     * @return 검색 결과 게시글 리스트
     */
    @Override
    public List<Announce> searchList(String key, String query, int cp) {
        return announceMapper.searchList(key, query, cp);
    }

    /**
     * 게시글 단일 조회 (다양한 조건 지원)
     * @param map 조회 조건 (예: announceNo)
     * @return 단일 게시글 DTO
     */
    @Override
    public Announce selectOne(Map<String, Object> map) {
        return announceMapper.selectOne(map);
    }

    /**
     * 게시글 단일 조회 (announceNo 기준)
     * @param announceNo 게시글 번호
     * @return 단일 게시글 DTO
     */
    @Override
    public Announce selectAnnounce(int announceNo) {
        Map<String, Object> param = Map.of("announceNo", announceNo);
        return announceMapper.selectOne(param);
    }

    /**
     * 게시글 조회수 1 증가
     * @param announceNo 조회수를 증가시킬 게시글 번호
     * @return 영향받은 행 수
     */
    @Override
    public int increaseViewCount(int announceNo) {
        return announceMapper.increaseViewCount(announceNo);
    }

    /**
     * 게시글 등록
     * @param announce 등록할 게시글 DTO
     * @return 영향받은 행 수
     */
    @Override
    public int insertAnnounce(Announce announce) {
        return announceMapper.insertAnnounce(announce);
    }

    /**
     * 게시글 수정
     * @param announce 수정할 게시글 DTO
     * @return 영향받은 행 수
     */
    @Override
    public int updateAnnounce(Announce announce) {
        return announceMapper.updateAnnounce(announce);
    }

    /**
     * 게시글 삭제 (논리 삭제)
     * @param map 삭제 조건 (예: announceNo)
     * @return 영향받은 행 수
     */
	@Override
	public int deleteAnnounce(Map<String, Object> map) {
        return announceMapper.deleteAnnounce(map);
    }

	 @Override
	    public int deleteAnnounce(int announceNo) {
	        return announceMapper.deleteAnnounce(Map.of("announceNo", announceNo));
	    }
}
