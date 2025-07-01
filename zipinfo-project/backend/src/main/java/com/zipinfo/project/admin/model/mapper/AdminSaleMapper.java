package com.zipinfo.project.admin.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.sale.model.dto.Sale;

@Mapper
public interface AdminSaleMapper {

    /** 관리자 분양 정보 목록 조회
     * @return
     */
    List<Sale> selectSaleList();

    /** 관리자 분양 정보 등록
     * @param sale
     * @return
     */
    int addSale(Sale sale); // saleStockNo는 자동 채번됨

    /** 관리자 분양 정보 이미지 등록
     * @param saleStockNo
     * @param imageUrl
     * @param imageOrder
     * @param originName
     * @param rename
     * @return
     */
    int addSaleImage(
	    @Param("saleStockNo") Long saleStockNo,
	    @Param("imageUrl") String imageUrl,
	    @Param("imageOrder") int imageOrder,
	    @Param("originName") String originName,
	    @Param("rename") String rename
	);
    
    /** 매물 좌표 저장
     * @param saleStockNo
     * @param lat
     * @param lng
     * @return
     */
    int addSaleCoord(
        @Param("saleStockNo") Long saleStockNo,
        @Param("lat") double lat,
        @Param("lng") double lng
    );
    
    /** 관리자 매물 상세 조회(수정용)
     * @param id
     * @return
     */
    Sale selectSaleById(@Param("id") Long id);
    
    /** 기존 이미지 조회(수정용)
     * @param saleNo
     * @return
     */
    List<Map<String, Object>> selectSaleImages(@Param("saleNo") Long saleNo);

    /** 관리자 분양 정보 수정
     * @param sale
     * @return
     */
    int updateSale(Sale sale);
    
    /** 관리자 분양 정보 좌표 수정
     * @param saleNo
     * @param lat
     * @param lng
     * @return
     */
    int updateSaleCoord(@Param("saleNo") Long saleNo,
                        @Param("lat") double lat,
                        @Param("lng") double lng);
    
    /** 관리자 분양 이미지 수정
     * @param saleNo
     * @param type
     * @return
     */
    List<String> selectImageRenamesByType(@Param("saleNo") Long saleNo,
                                          @Param("type") String type);

    /** 관리자 분양 정보 이미지 삭제
     * @param saleStockNo
     */
    void deleteSaleImages(@Param("saleStockNo") int saleStockNo);

    /** 관리자 분양 정보 좌표 삭제
     * @param saleStockNo
     */
    void deleteSaleCoord(@Param("saleStockNo") int saleStockNo);

    /** 관리자 분양 정보 최종 삭제
     * @param id
     */
    void deleteSale(@Param("id") int id);

}