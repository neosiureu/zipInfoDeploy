package com.zipinfo.project.announce.model.service;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.mapper.EditAnnounceMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공지사항 관련 서비스 구현체
 * - 공지사항 CRUD 및 이미지 파일 저장 기능 포함
 */
@Service
@RequiredArgsConstructor
public class EditAnnounceServiceImpl implements EditAnnounceService {

    // MyBatis 매퍼 주입
    private final EditAnnounceMapper editAnnounceMapper;

    // 공지사항 이미지 업로드 절대 경로 (윈도우 기준)
    private final String uploadDir = "C:/uploadFiles/announceImg";
    
	@Value("${my.announce.web-path}")
	private String announceWebPath;
	
	@Value("${my.announce.folder-path}")
	private String announceFolderPath;
	

    /**
     * 공지사항 이미지 파일을 서버에 저장하고,
     * 웹에서 접근 가능한 이미지 URL 경로를 반환한다.
     * 
     * @param file - 업로드된 MultipartFile 이미지
     * @return 웹에서 접근 가능한 이미지 URL 경로 (예: /images/announceImg/uuid_파일명.jpg)
     * @throws IOException 파일 저장 실패 시 예외 발생
     */
    public String saveImage(MultipartFile file) throws IOException {
        // 업로드 폴더가 없으면 생성
        File dir = new File(announceFolderPath);
        if (!dir.exists()) dir.mkdirs();

        // 원본 파일명과 고유한 UUID를 조합해 파일명 생성
        String originalName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalName;

        // 실제 저장할 파일 객체 생성
        File dest = new File(dir, uniqueFileName);

        // 서버에 파일 저장
        file.transferTo(dest);
        
        String finalPath = announceWebPath + uniqueFileName;

        // 웹에서 접근 가능한 URL 경로 반환
        return finalPath;
    }

    /**
     * 공지사항 등록
     * 
     * @param announce 등록할 공지사항 DTO
     * @return 삽입된 행(row) 수
     */
    @Override
    public int insertAnnounce(Announce announce) {
        return editAnnounceMapper.insertAnnounce(announce);
    }

    /**
     * 공지사항 수정
     * 
     * @param announce 수정할 공지사항 DTO (announceNo 포함)
     * @return 수정된 행(row) 수
     */
    @Override
    public int updateAnnounce(Announce announce) {
        return editAnnounceMapper.updateAnnounce(announce);
    }

    /**
     * 공지사항 삭제 (논리 삭제)
     * 
     * @param announceNo 삭제할 공지사항 번호
     * @return 삭제 처리된 행(row) 수
     */
    @Override
    public int deleteAnnounce(int announceNo) {
        return editAnnounceMapper.deleteAnnounce(announceNo);
    }

    /**
     * 특정 공지사항 단일 조회
     * 
     * @param announceNo 조회할 공지사항 번호
     * @return 조회된 공지사항 DTO (없으면 null)
     */
    @Override
    public Announce selectOne(int announceNo) {
        return editAnnounceMapper.selectAnnounce(announceNo);
    }

    /**
     * 수정 폼 등에 기존 공지사항 내용을 불러올 때 사용
     * 
     * @param announceNo 조회할 공지사항 번호
     * @return 조회된 공지사항 DTO
     */
    @Override
    public Announce selectAnnounceByNo(int announceNo) {
        return editAnnounceMapper.selectAnnounce(announceNo);
    }

}
