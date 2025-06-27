package com.zipinfo.project.announce.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.announce.model.dto.Announce;

@Mapper
public interface EditAnnounceMapper {

    /**
     * 공지사항 등록
     * @param announce 등록할 공지사항 DTO
     * @return 성공 시 1 이상의 영향 받은 행 수
     */
    int insertAnnounce(Announce announce);

    /**
     * 공지사항 수정
     * @param announce 수정할 공지사항 DTO (announceNo 포함)
     * @return 성공 시 1 이상의 영향 받은 행 수
     */
    int updateAnnounce(Announce announce);

    /**
     * 공지사항 삭제 (논리 삭제)
     * @param announceNo 삭제할 공지사항 번호
     * @return 성공 시 1 이상의 영향 받은 행 수
     */
    int deleteAnnounce(@Param("announceNo") int announceNo);

    /**
     * 공지사항 단일 조회
     * @param announceNo 조회할 공지사항 번호
     * @return 조회된 공지사항 DTO, 없으면 null
     */
    Announce selectAnnounce(@Param("announceNo") int announceNo);

    /**
     * 공지사항 이미지 등록
     * @param announceNo 공지사항 번호
     * @param renamedFileName 저장된 파일명
     * @param order 이미지 순서
     * @return 성공 시 1 이상의 영향 받은 행 수
     */
    int insertAnnounceImage(@Param("announceNo") int announceNo, 
                            @Param("renamedFileName") String renamedFileName, 
                            @Param("order") int order);

    /**
     * 공지사항 이미지 삭제 (order 기준)
     * @param announceNo 공지사항 번호
     * @param order 삭제할 이미지 순서
     * @return 성공 시 1 이상의 영향 받은 행 수
     */
    int deleteAnnounceImage(@Param("announceNo") int announceNo, @Param("order") int order);

    /**
     * 특정 공지사항의 이미지 개수 조회 (order 기준)
     * @param announceNo 공지사항 번호
     * @param order 이미지 순서
     * @return 이미지 개수
     */
    int countImageByAnnounceNoAndOrder(@Param("announceNo") int announceNo, @Param("order") int order);

    /**
     * 공지사항 이미지 수정 (order 기준)
     * @param announceNo 공지사항 번호
     * @param renamedFileName 변경된 파일명
     * @param order 이미지 순서
     * @return 성공 시 1 이상의 영향 받은 행 수
     */
    int updateAnnounceImage(@Param("announceNo") int announceNo, 
                            @Param("renamedFileName") String renamedFileName, 
                            @Param("order") int order);
    
    
}
