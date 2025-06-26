package com.zipinfo.project.admin.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.sale.model.dto.Sale;

@Mapper
public interface AdminSaleMapper {

    /** 관리자 분양 매물 목록 조회
     * @return
     */
    List<Sale> selectSaleList();

    /** 관리자 분양 매물 등록
     * @param sale
     * @return
     */
    int addSale(Sale sale); // saleStockNo는 자동 채번됨

    /** 관리자 분양 매물 이미지 등록
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

}
