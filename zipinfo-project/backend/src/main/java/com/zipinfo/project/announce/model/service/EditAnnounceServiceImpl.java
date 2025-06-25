package com.zipinfo.project.announce.model.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.mapper.EditAnnounceMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditAnnounceServiceImpl implements EditAnnounceService {

    private final EditAnnounceMapper announceMapper;

    @Override
    public int boardInsert(Announce announce) {
        return announceMapper.insertBoard(announce);
    }

    @Override
    public int boardUpdate(Announce announce) {
        return announceMapper.updateBoard(announce);
    }

    @Override
    public int boardDelete(Map<String, Integer> params) {
        return announceMapper.deleteBoard(params);
    }

    @Override
    public Announce selectBoard(int announceNo) {
        return announceMapper.selectBoard(announceNo);
    }
}
