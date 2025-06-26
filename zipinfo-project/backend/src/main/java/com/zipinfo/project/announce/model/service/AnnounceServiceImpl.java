package com.zipinfo.project.announce.model.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.mapper.AnnounceMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공지사항 조회 관련 비즈니스 로직 구현 클래스
 */
@Service
@RequiredArgsConstructor
public class AnnounceServiceImpl implements AnnounceService {

    private final AnnounceMapper announceMapper;

    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 기본 페이지 크기 10으로 공지사항 목록 조회 편의 메서드
     */
    public List<Announce> selectAnnounceList(int cp) {
        return selectAnnounceList(cp, DEFAULT_PAGE_SIZE);
    }

    /**
     * 기본 페이지 크기 10으로 검색 목록 조회 편의 메서드
     */
    public List<Announce> searchList(String key, String query, int cp) {
        return searchList(key, query, cp, DEFAULT_PAGE_SIZE);
    }

    @Override
    public List<Announce> selectAnnounceList(int cp, int size) {
        return announceMapper.selectAnnounceListWithSize(cp, size);
    }

    @Override
    public List<Announce> searchList(String key, String query, int cp, int size) {
        return announceMapper.searchListWithSize(key, query, cp, size);
    }

    @Override
    public Announce selectOne(int announceNo) {
        return announceMapper.selectOne(announceNo);
    }

    @Override
    public int increaseViewCount(int announceNo) {
        return announceMapper.increaseViewCount(announceNo);
    }

    @Override
    public int countAnnounce() {
        return announceMapper.countAnnounce();
    }

    @Override
    public int countSearchAnnounce(String key, String query) {
        return announceMapper.countSearchAnnounce(key, query);
    }
}
