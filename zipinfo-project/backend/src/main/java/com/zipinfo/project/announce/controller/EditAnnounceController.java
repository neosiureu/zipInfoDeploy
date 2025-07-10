package com.zipinfo.project.announce.controller;

import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.zipinfo.project.announce.model.service.EditAnnounceService;
import com.zipinfo.project.editneighborhood.model.service.EditneighborhoodService;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.announce.model.dto.Announce;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/announce")  // 공지사항 관련 API 기본 경로 설정
@RequiredArgsConstructor
public class EditAnnounceController {

    @Value("${my.announce.web-path}")  // application.properties에서 공지사항 이미지 웹 접근 경로 읽기
    private String announceWebPath;

    @Value("${my.announce.folder-path}")  // application.properties에서 공지사항 이미지 저장 폴더 경로 읽기
    private String announceFolderPath;

    private final EditAnnounceService editAnnounceService;  // 공지사항 서비스 의존성 주입
    
    private final EditneighborhoodService editneighborhoodService;

    /**  
     * 공지사항 작성 처리 메서드
     * @param announce : 클라이언트에서 전달한 공지사항 DTO(제목, 내용 등 포함)
     * @param loginMember : 세션에서 가져온 로그인 회원 정보
     * @return 작성된 공지사항 번호 반환
     * @throws Exception
     */
    @PostMapping("/write")
    public int announceInsert(
        @RequestBody Announce announce,
        @AuthenticationPrincipal Member loginMember
    ) throws Exception {
    	
    	System.out.println("응애" + loginMember.getMemberAuth());

        announce.setMemberNo(loginMember.getMemberNo());

        String processedContent = editneighborhoodService.processImagesInContent(announce.getAnnounce());
        announce.setAnnounce(processedContent);

        return editAnnounceService.insertAnnounce(announce);
    }

    /**
     * 에디터 이미지 업로드 요청 처리 메서드
     * MultipartFile로 받은 이미지 파일을 서버에 저장하고,
     * 저장된 파일의 URL을 문자열로 반환
     * @param image : 업로드 된 MultipartFile 이미지
     * @return 저장된 이미지 URL 문자열
     * @throws Exception
     */
    @PostMapping("/uploadImage")
    @ResponseBody
    public String uploadImage(@RequestParam("image") MultipartFile image) throws Exception {

        // 저장 폴더 생성 확인
        File dir = new File(announceFolderPath);
        if (!dir.exists()) dir.mkdirs();

        // 원본 파일명에서 확장자 추출
        String originalName = image.getOriginalFilename();
        String ext = originalName.substring(originalName.lastIndexOf("."));

        // UUID 기반 랜덤 파일명 생성
        String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + ext;

        // 서버에 파일 저장
        File dest = new File(announceFolderPath, fileName);
        image.transferTo(dest);

        // 저장된 이미지의 웹 URL 반환
        return announceWebPath + fileName;  // 예: /images/announceImg/abc123.jpg
    }
    

    /** 공지 사항 수정
     * @param announceNo
     * @param announce
     * @param loginMember
     * @return
     * @throws Exception
     */
    @PutMapping("/edit/{announceNo}")
    public int editAnnounce(
        @PathVariable("announceNo") int announceNo,
        @RequestBody Announce announce,
        @AuthenticationPrincipal Member loginMember
    ) throws Exception {
    	
        announce.setMemberNo(loginMember.getMemberNo());

        String processedContent = editneighborhoodService.processImagesInContent(announce.getAnnounce());
        announce.setAnnounce(processedContent);

        announce.setAnnounceNo(announceNo);
        announce.setMemberNo(loginMember.getMemberNo());
        return editAnnounceService.updateAnnounce(announce);
    }



    
    /**
     * 공지사항 삭제 처리 (논리 삭제 또는 실제 삭제)
     * @param announce 삭제할 게시글 정보 (announceNo 포함)
     * @return 삭제 성공 시 1, 실패 시 0 또는 예외 발생
     */
    @PostMapping("/detail/delete")
    public int deleteAnnounce(
        @RequestBody Announce announce,
        @AuthenticationPrincipal Member loginMember
    ) {
        if (loginMember.getMemberAuth() != 0) {
            throw new AccessDeniedException("관리자만 공지사항을 삭제할 수 있습니다.");
        }

        if (announce != null && announce.getAnnounceNo() != null) {
            return editAnnounceService.deleteAnnounce(announce.getAnnounceNo());
        }
        return 0;
    }

}

