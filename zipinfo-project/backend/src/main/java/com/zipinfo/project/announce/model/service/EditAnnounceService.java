package com.zipinfo.project.announce.model.service;

import java.util.Map;

import com.zipinfo.project.announce.model.dto.Announce;

public interface EditAnnounceService {
    int boardInsert(Announce announce);

    int boardUpdate(Announce announce);

    int boardDelete(Map<String, Integer> params);

    Announce selectBoard(int announceNo);
}
