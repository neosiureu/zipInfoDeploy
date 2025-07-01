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
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.FileOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;

import com.zipinfo.project.announce.model.service.EditAnnounceService;
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
        @SessionAttribute("loginMember") Member loginMember
    ) throws Exception {

        if (loginMember.getMemberAuth() != 0) {
            throw new AccessDeniedException("관리자만 공지사항을 작성할 수 있습니다.");
        }

        announce.setMemberNo(loginMember.getMemberNo());

        String processedContent = processImagesInContent(announce.getAnnounce());
        announce.setAnnounce(processedContent);

        return editAnnounceService.insertAnnounce(announce);
    }


    /**
     * 공지사항 내용(content) 내 포함된 Base64 이미지 문자열을 찾아
     * 실제 이미지 파일로 저장 후 해당 위치 URL로 변환해주는 메서드
     * @param content : 공지사항 내용 HTML
     * @return 이미지가 URL로 변경된 공지사항 내용
     * @throws Exception
     */
    private String processImagesInContent(String content) throws Exception {
        if (content == null || !content.contains("data:image")) {
            // Base64 이미지가 없으면 원본 내용 그대로 반환
            return content;
        }

        // Base64 이미지 데이터 정규식 패턴 (예: data:image/png;base64,*****)
        Pattern pattern = Pattern.compile("data:image/([^;]+);base64,([^\"]+)");
        Matcher matcher = pattern.matcher(content);

        StringBuffer processedContent = new StringBuffer();

        while (matcher.find()) {
            String imageFormat = matcher.group(1);  // 이미지 포맷 (jpeg, png 등)
            String base64Data = matcher.group(2);   // Base64 이미지 데이터

            // Base64 이미지를 파일로 저장하고, 저장된 이미지 URL 얻기
            String imageUrl = saveBase64Image(base64Data, imageFormat);

            // Base64 문자열을 이미지 URL로 치환
            matcher.appendReplacement(processedContent, imageUrl);
        }
        matcher.appendTail(processedContent);

        return processedContent.toString();
    }

    /**
     * Base64 인코딩된 이미지를 디코딩하여 서버 지정 폴더에 파일로 저장하고
     * 저장된 파일의 웹 경로(URL)를 반환하는 메서드
     * @param base64Data : Base64 인코딩 이미지 데이터
     * @param format : 이미지 포맷(jpeg, png 등)
     * @return 저장된 이미지 웹 URL
     * @throws Exception
     */
    private String saveBase64Image(String base64Data, String format) throws Exception {
        // 저장 폴더가 없으면 생성
        File dir = new File(announceFolderPath);
        if (!dir.exists()) dir.mkdirs();

        // 확장자 설정 (jpeg는 jpg로 변환)
        String extension = format.equals("jpeg") ? ".jpg" : "." + format;

        // 랜덤 UUID 기반 파일명 생성 (16자리)
        String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + extension;

        // Base64 디코딩 후 바이트 배열 생성
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);

        // 파일 객체 생성
        File dest = new File(announceFolderPath, fileName);

        // 파일 출력 스트림으로 이미지 저장
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(imageBytes);
        }

        // 웹에서 접근 가능한 URL 리턴 (localhost 기준)
        return announceWebPath + fileName;
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
        @SessionAttribute("loginMember") Member loginMember
    ) {
        if (loginMember.getMemberAuth() != 0) {
            throw new AccessDeniedException("관리자만 공지사항을 수정할 수 있습니다.");
        }

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
        @SessionAttribute("loginMember") Member loginMember
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

